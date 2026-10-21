package com.gesturecontrol.ui.debug

import android.content.Context
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gesturecontrol.accessibility.PointerBridge
import com.gesturecontrol.actions.ActionDispatcher
import com.gesturecontrol.gestures.mapping.CommandPlanner
import com.gesturecontrol.camera.CameraController
import com.gesturecontrol.gestures.EngineState
import com.gesturecontrol.gestures.Gesture
import com.gesturecontrol.gestures.GestureConfig
import com.gesturecontrol.gestures.GestureEngine
import com.gesturecontrol.gestures.Landmark
import com.gesturecontrol.gestures.PowerSaveConfig
import com.gesturecontrol.gestures.PowerSaveThrottler
import com.gesturecontrol.gestures.pointer.PointerEngine
import com.gesturecontrol.gestures.pointer.PointerEvent
import com.gesturecontrol.gestures.pointer.PointerOutput
import com.gesturecontrol.data.SettingsRepository
import com.gesturecontrol.service.GestureServiceState
import kotlinx.coroutines.flow.first
import com.gesturecontrol.vision.AnalysisResult
import com.gesturecontrol.vision.HandAnalyzer
import com.gesturecontrol.vision.HandLandmarkerEngine
import com.gesturecontrol.vision.PoseClassifierFactory
import com.gesturecontrol.vision.PoseClassifiers
import com.gesturecontrol.vision.LandmarkRecorder
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class DebugUiState(
    val landmarks: List<Landmark>? = null,
    val aspectRatio: Float = 3f / 4f,
    val engineState: EngineState = EngineState.NoHand,
    val fps: Float = 0f,
    val powerSaving: Boolean = false,
    val pointer: PointerOutput? = null,
    val lastPointerEvent: PointerEvent? = null,
    val lastGesture: Gesture? = null,
    val executeActions: Boolean = false,
    val recordingFrames: Int? = null,
    /** Label for the training logger; null records an unlabeled engine test sequence. */
    val trainingLabel: String? = null,
    val savedFile: String? = null,
)

@HiltViewModel
class DebugViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val actionDispatcher: ActionDispatcher,
    private val settings: SettingsRepository,
    private val serviceState: GestureServiceState,
    private val bridge: PointerBridge,
    private val classifierFactory: PoseClassifierFactory,
) : ViewModel() {

    private val _state = MutableStateFlow(DebugUiState())
    val state: StateFlow<DebugUiState> = _state.asStateFlow()

    private val camera = CameraController(context)
    private var landmarker: HandLandmarkerEngine? = null
    private var classifiers: PoseClassifiers? = null

    /** Which pose classifier is really running (the model may have fallen back to rules). */
    val classifierStatus = classifierFactory.status

    @Volatile
    private var recorder: LandmarkRecorder? = null

    fun startCamera(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider, rotation: Int) {
        // Ask the background service to release the camera while this screen uses it.
        serviceState.debugPreviewActive.value = true
        viewModelScope.launch {
            val engine = landmarker ?: withContext(Dispatchers.Default) { HandLandmarkerEngine(context) }
                .also { landmarker = it }
            val throttler = PowerSaveThrottler(PowerSaveConfig(enabled = settings.powerSaveEnabled.first()))
            val pointerSettings = settings.pointerSettings.first()
            classifiers?.let { old -> camera.runOnAnalysisThread { old.close() } }
            val poseClassifiers = withContext(Dispatchers.Default) { classifierFactory.create(settings.classifierKind.first()) }
            classifiers = poseClassifiers
            val pointer = PointerEngine(pointerSettings.toConfig(), poseClassifier = poseClassifiers.forPointer)
                .also { it.mapper = pointerSettings.mapper }
            val gestures = GestureEngine(GestureConfig(), poseClassifiers.forGestures)
            val analyzer = HandAnalyzer(engine, gestures, throttler, pointer, onResult = ::onResult)
            camera.bind(owner, analyzer, rotation, surfaceProvider)
        }
    }

    fun stopCamera() {
        camera.unbind()
        stopRecording()
        serviceState.debugPreviewActive.value = false
    }

    fun setExecuteActions(enabled: Boolean) = _state.update { it.copy(executeActions = enabled) }

    /** Called on the analysis thread. */
    private fun onResult(result: AnalysisResult) {
        val event = result.event
        if (event != null && _state.value.executeActions) {
            when (event.gesture) {
                Gesture.FINGER_LEFT, Gesture.FINGER_RIGHT ->
                    bridge.send(CommandPlanner.forTrackSwipe(toLeft = event.gesture == Gesture.FINGER_LEFT))
                else -> actionDispatcher.dispatch(event.gesture)
            }
        }
        recorder?.let { rec ->
            rec.write(result.frame)
            _state.update { it.copy(recordingFrames = rec.frameCount) }
        }
        _state.update {
            it.copy(
                landmarks = result.landmarks,
                aspectRatio = result.aspectRatio,
                engineState = result.state,
                fps = result.fps,
                powerSaving = result.powerSaving,
                pointer = result.pointer,
                lastPointerEvent = result.pointer?.event ?: it.lastPointerEvent,
                lastGesture = result.event?.gesture ?: it.lastGesture,
            )
        }
    }

    fun setTrainingLabel(label: String?) = _state.update { it.copy(trainingLabel = label) }

    fun toggleRecording() {
        if (recorder != null) {
            stopRecording()
        } else {
            val label = _state.value.trainingLabel
            // Labeled takes go to training/ (for the ML pipeline), unlabeled ones to recordings/.
            val folder = if (label == null) "recordings" else "training"
            val dir = context.getExternalFilesDir(folder) ?: context.filesDir.resolve(folder)
            recorder = LandmarkRecorder(dir, label)
            _state.update { it.copy(recordingFrames = 0, savedFile = null) }
        }
    }

    private fun stopRecording() {
        val rec = recorder ?: return
        recorder = null
        rec.close()
        _state.update { it.copy(recordingFrames = null, savedFile = rec.file.absolutePath) }
    }

    override fun onCleared() {
        stopCamera()
        val engine = landmarker
        val cls = classifiers
        camera.runOnAnalysisThread {
            engine?.close()
            cls?.close()
        }
        camera.shutdown()
    }
}
