package com.gesturecontrol.ui.cursor

import android.content.Context
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gesturecontrol.camera.CameraController
import com.gesturecontrol.data.SettingsRepository
import com.gesturecontrol.gestures.GestureEngine
import com.gesturecontrol.gestures.PowerSaveConfig
import com.gesturecontrol.gestures.PowerSaveThrottler
import com.gesturecontrol.gestures.mapping.Calibration
import com.gesturecontrol.gestures.mapping.CalibrationCapture
import com.gesturecontrol.gestures.mapping.NormPoint
import com.gesturecontrol.gestures.pointer.PointerConfig
import com.gesturecontrol.gestures.pointer.PointerEngine
import com.gesturecontrol.service.GestureServiceState
import com.gesturecontrol.vision.AnalysisResult
import com.gesturecontrol.vision.HandAnalyzer
import com.gesturecontrol.vision.HandLandmarkerEngine
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

enum class CalibrationPhase { CAPTURE, TEST, ERROR }

data class CalibrationUiState(
    val phase: CalibrationPhase = CalibrationPhase.CAPTURE,
    /** Index of the target being captured, 0..2. */
    val step: Int = 0,
    val progress: Float = 0f,
    val pointing: Boolean = false,
    /** Live cursor with the new calibration, in the TEST phase. */
    val testCursor: NormPoint? = null,
    val pinchClosed: Boolean = false,
)

@HiltViewModel
class CalibrationViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val serviceState: GestureServiceState,
) : ViewModel() {

    private val _state = MutableStateFlow(CalibrationUiState())
    val state: StateFlow<CalibrationUiState> = _state.asStateFlow()

    private val camera = CameraController(context)
    private var landmarker: HandLandmarkerEngine? = null

    // Touched only on the analysis thread.
    private val capture = CalibrationCapture()
    private val points = mutableListOf<NormPoint>()
    private var testEngine: PointerEngine? = null
    private var screenAspect = 2f

    fun start(owner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider, rotation: Int, screenAspect: Float) {
        serviceState.debugPreviewActive.value = true
        this.screenAspect = screenAspect
        viewModelScope.launch {
            val engine = landmarker ?: withContext(Dispatchers.Default) { HandLandmarkerEngine(context) }
                .also { landmarker = it }
            val analyzer = HandAnalyzer(
                landmarker = engine,
                gestureEngine = GestureEngine(),
                throttler = PowerSaveThrottler(PowerSaveConfig(enabled = false)),
                onResult = ::onResult,
            )
            camera.bind(owner, analyzer, rotation, surfaceProvider)
        }
    }


    fun stop() {
        camera.unbind()
        serviceState.debugPreviewActive.value = false
    }

    fun restart() = camera.runOnAnalysisThread {
        points.clear()
        capture.reset()
        testEngine = null
        _state.value = CalibrationUiState()
    }

    /** Called on the analysis thread. */
    private fun onResult(result: AnalysisResult) {
        testEngine?.let { engine ->
            val out = engine.process(result.frame)
            _state.update { it.copy(testCursor = out.cursor, pinchClosed = out.pinchClosed) }
            return
        }
        if (_state.value.phase == CalibrationPhase.ERROR) return

        val s = capture.update(result.frame)
        val captured = s.captured
        if (captured == null) {
            _state.update { it.copy(progress = s.progress, pointing = s.pointing) }
            return
        }
        points += captured
        if (points.size < Calibration.TARGETS.size) {
            _state.update { it.copy(step = points.size, progress = 0f) }
            return
        }
        val mapper = Calibration.solve(points.toList())
        points.clear()
        if (mapper == null) {
            _state.update { CalibrationUiState(phase = CalibrationPhase.ERROR) }
            return
        }
        viewModelScope.launch { settings.setCalibration(mapper) }
        testEngine = PointerEngine(PointerConfig(), screenAspect).also { it.mapper = mapper }
        _state.update { CalibrationUiState(phase = CalibrationPhase.TEST, step = 3) }
    }

    override fun onCleared() {
        stop()
        val engine = landmarker
        camera.runOnAnalysisThread { engine?.close() }
        camera.shutdown()
    }
}
