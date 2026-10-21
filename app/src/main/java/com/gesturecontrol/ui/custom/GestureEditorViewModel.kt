package com.gesturecontrol.ui.custom

import android.content.Context
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gesturecontrol.camera.CameraController
import com.gesturecontrol.data.CustomGestureEntity
import com.gesturecontrol.data.CustomGestureRepository
import com.gesturecontrol.data.InstalledApp
import com.gesturecontrol.data.InstalledAppsRepository
import com.gesturecontrol.data.toTemplate
import com.gesturecontrol.gestures.GestureEngine
import com.gesturecontrol.gestures.HandFrame
import com.gesturecontrol.gestures.PowerSaveConfig
import com.gesturecontrol.gestures.PowerSaveThrottler
import com.gesturecontrol.gestures.custom.ActionSpec
import com.gesturecontrol.gestures.custom.CustomGestureRecognizer
import com.gesturecontrol.gestures.custom.CustomTemplate
import com.gesturecontrol.gestures.custom.FeatureFrame
import com.gesturecontrol.gestures.custom.GestureQuality
import com.gesturecontrol.gestures.custom.GestureSample
import com.gesturecontrol.gestures.custom.QualityReport
import com.gesturecontrol.gestures.custom.RecordingTrimmer
import com.gesturecontrol.gestures.custom.SampleBuilder
import com.gesturecontrol.gestures.custom.SampleCodec
import com.gesturecontrol.service.GestureServiceState
import com.gesturecontrol.vision.AnalysisResult
import com.gesturecontrol.vision.HandAnalyzer
import com.gesturecontrol.vision.HandLandmarkerEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface RecordPhase {
    data object Idle : RecordPhase
    data class Countdown(val seconds: Int) : RecordPhase
    data class Recording(val progress: Float) : RecordPhase
}

/** One recorded repeat: its sample and the raw frames (for the built-in conflict check). */
class Repeat(val sample: GestureSample, val frames: List<HandFrame>)

data class EditorUiState(
    val name: String = "",
    val repeats: Int = 0,
    val storedSamples: Boolean = false,
    val phase: RecordPhase = RecordPhase.Idle,
    val handVisible: Boolean = false,
    val quality: QualityReport? = null,
    val action: ActionSpec = ActionSpec.Media(com.gesturecontrol.gestures.mapping.MediaKind.PLAY_PAUSE),
    val sensitivity: Float = 0.5f,
    val cooldownMs: Long = 1000,
    val testing: Boolean = false,
    val testConfidence: Float? = null,
    val testFiredAt: Long = 0,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() && (repeats >= MIN_REPEATS || storedSamples)

    companion object {
        const val MIN_REPEATS = 3
        const val MAX_REPEATS = 5
    }
}

@HiltViewModel
class GestureEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val repository: CustomGestureRepository,
    private val serviceState: GestureServiceState,
    private val installedApps: InstalledAppsRepository,
) : ViewModel() {
    val profile: String = checkNotNull(savedStateHandle[ARG_PROFILE])
    private val gestureId: Long? = savedStateHandle.get<Long>(ARG_ID)?.takeIf { it > 0 }

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    val apps = MutableStateFlow<List<InstalledApp>>(emptyList())

    private val camera = CameraController(context)
    private var landmarker: HandLandmarkerEngine? = null

    private val repeats = mutableListOf<Repeat>()
    private var storedSamples: List<GestureSample> = emptyList()
    private var storedRadius = 0f

    // Touched on the analysis thread.
    @Volatile private var capture: MutableList<HandFrame>? = null
    @Volatile private var tester: CustomGestureRecognizer? = null

    init {
        viewModelScope.launch {
            apps.value = installedApps.loadLaunchableApps()
            val existing = gestureId?.let { repository.byId(it) } ?: return@launch
            storedSamples = SampleCodec.decode(existing.samples)
            storedRadius = existing.radius
            _state.update {
                it.copy(
                    name = existing.name,
                    storedSamples = storedSamples.isNotEmpty(),
                    action = ActionSpec.decode(existing.action),
                    sensitivity = existing.sensitivity,
                    cooldownMs = existing.cooldownMs,
                )
            }
        }
    }

    fun appLabel(packageName: String) = installedApps.labelOf(packageName)

    fun startCamera(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider, rotation: Int) {
        serviceState.debugPreviewActive.value = true
        viewModelScope.launch {
            val engine = landmarker ?: withContext(Dispatchers.Default) { HandLandmarkerEngine(context) }.also { landmarker = it }
            val analyzer = HandAnalyzer(
                landmarker = engine,
                gestureEngine = GestureEngine(),
                throttler = PowerSaveThrottler(PowerSaveConfig(enabled = false)),
                onResult = ::onResult,
            )
            camera.bind(owner, analyzer, rotation, surfaceProvider)
        }
    }

    fun stopCamera() {
        camera.unbind()
        serviceState.debugPreviewActive.value = false
    }

    /** Called on the analysis thread. */
    private fun onResult(result: AnalysisResult) {
        capture?.add(result.frame)
        tester?.let { t ->
            val fired = t.process(result.frame)
            _state.update {
                it.copy(
                    testConfidence = t.lastBest?.confidence,
                    testFiredAt = if (fired != null) System.currentTimeMillis() else it.testFiredAt,
                )
            }
        }
        val visible = result.frame.hasHand
        if (visible != _state.value.handVisible) _state.update { it.copy(handVisible = visible) }
    }

    fun setName(value: String) = _state.update { it.copy(name = value) }
    fun setAction(value: ActionSpec) = _state.update { it.copy(action = value) }
    fun setSensitivity(value: Float) = _state.update { it.copy(sensitivity = value) }
    fun setCooldown(value: Long) = _state.update { it.copy(cooldownMs = value) }

    fun recordRepeat() {
        if (_state.value.phase != RecordPhase.Idle || repeats.size >= EditorUiState.MAX_REPEATS) return
        stopTest()
        viewModelScope.launch {
            for (s in COUNTDOWN_S downTo 1) {
                _state.update { it.copy(phase = RecordPhase.Countdown(s)) }
                delay(1000)
            }
            // Filled on the analysis thread, read here after recording stops.
            val frames = java.util.Collections.synchronizedList(mutableListOf<HandFrame>())
            capture = frames
            val steps = (RECORD_MS / 100).toInt()
            repeat(steps) { i ->
                _state.update { it.copy(phase = RecordPhase.Recording((i + 1f) / steps)) }
                delay(100)
            }
            capture = null
            val snapshot = synchronized(frames) { frames.toList() }
            val repeat = withContext(Dispatchers.Default) { buildRepeat(snapshot) }
            if (repeat != null) repeats += repeat
            _state.update { it.copy(phase = RecordPhase.Idle, repeats = repeats.size) }
            refreshQuality()
        }
    }

    fun removeLastRepeat() {
        if (repeats.isEmpty()) return
        repeats.removeAt(repeats.lastIndex)
        _state.update { it.copy(repeats = repeats.size) }
        viewModelScope.launch { refreshQuality() }
    }

    private fun buildRepeat(frames: List<HandFrame>): Repeat? {
        val features = frames.mapNotNull(SampleBuilder::featureFrame)
        val trimmed: List<FeatureFrame> = RecordingTrimmer.trim(features)
        val sample = SampleBuilder.build(trimmed) ?: return null
        val from = trimmed.first().t
        val to = trimmed.last().t
        return Repeat(sample, frames.filter { it.timestampMs in from..to })
    }

    private suspend fun refreshQuality() {
        if (repeats.size < EditorUiState.MIN_REPEATS) {
            _state.update { it.copy(quality = null) }
            return
        }
        val others = repository.export(profile).filter { it.id != gestureId }.map { it.toTemplate() }
        val report = withContext(Dispatchers.Default) {
            GestureQuality.check(repeats.map { it.sample }, repeats.map { it.frames }, others) { GestureEngine() }
        }
        _state.update { it.copy(quality = report) }
    }

    private fun currentTemplate(): CustomTemplate? {
        val samples = if (repeats.size >= EditorUiState.MIN_REPEATS) repeats.map { it.sample } else storedSamples
        if (samples.isEmpty()) return null
        val radius = if (repeats.size >= EditorUiState.MIN_REPEATS) GestureQuality.radius(samples) else storedRadius
        val s = _state.value
        return CustomTemplate(gestureId ?: -1, s.name, samples, radius, s.sensitivity, s.cooldownMs)
    }

    fun toggleTest() {
        if (tester != null) {
            stopTest()
            return
        }
        val template = currentTemplate() ?: return
        tester = CustomGestureRecognizer(listOf(template))
        _state.update { it.copy(testing = true, testConfidence = null) }
    }

    private fun stopTest() {
        tester = null
        _state.update { it.copy(testing = false, testConfidence = null) }
    }

    fun save() {
        val template = currentTemplate() ?: return
        val s = _state.value
        viewModelScope.launch {
            val entity = CustomGestureEntity(
                id = gestureId ?: 0,
                profile = profile,
                name = s.name.trim(),
                samples = SampleCodec.encode(template.samples),
                radius = template.radius,
                sensitivity = s.sensitivity,
                cooldownMs = s.cooldownMs,
                action = ActionSpec.encode(s.action),
                createdAt = System.currentTimeMillis(),
            )
            if (gestureId == null) repository.add(entity) else repository.update(entity)
            _state.update { it.copy(saved = true) }
        }
    }

    override fun onCleared() {
        stopCamera()
        val engine = landmarker
        camera.runOnAnalysisThread { engine?.close() }
        camera.shutdown()
    }

    companion object {
        const val ARG_PROFILE = "profile"
        const val ARG_ID = "id"
        private const val COUNTDOWN_S = 3
        private const val RECORD_MS = 2500L
    }
}
