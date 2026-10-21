package com.gesturecontrol.vision

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.gesturecontrol.gestures.EngineState
import com.gesturecontrol.gestures.GestureEngine
import com.gesturecontrol.gestures.GestureEvent
import com.gesturecontrol.gestures.HandFrame
import com.gesturecontrol.gestures.Landmark
import com.gesturecontrol.gestures.PowerSaveThrottler

/** Result of analyzing one camera frame. */
data class AnalysisResult(
    val frame: HandFrame,
    /** Smoothed landmarks in user-view coordinates, null when no hand. */
    val landmarks: List<Landmark>?,
    val state: EngineState,
    val event: GestureEvent?,
    /** Width / height of the analyzed (rotated) image, for drawing overlays. */
    val aspectRatio: Float,
    val fps: Float,
    /** True while analysis runs at the reduced idle frame rate. */
    val powerSaving: Boolean,
)

/**
 * CameraX analyzer: frame -> upright mirrored bitmap -> landmarks -> gesture engine.
 * Runs entirely on the analysis executor thread; frames are never stored.
 */
class HandAnalyzer(
    private val landmarker: HandLandmarkerEngine,
    private val gestureEngine: GestureEngine,
    private val throttler: PowerSaveThrottler,
    private val mirror: Boolean = true,
    private val onResult: (AnalysisResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private var lastTimestampMs = Long.MIN_VALUE
    private var fps = 0f

    override fun analyze(image: ImageProxy) {
        image.use { proxy ->
            val timestampMs = proxy.imageInfo.timestamp / 1_000_000
            // Skipped frames are closed right away and cost no inference.
            if (timestampMs <= lastTimestampMs || !throttler.shouldProcess(timestampMs)) return
            val dt = timestampMs - lastTimestampMs
            lastTimestampMs = timestampMs
            if (dt in 1..2000) fps = fps * 0.8f + (1000f / dt) * 0.2f

            try {
                val bitmap = toUserView(proxy)
                val landmarks = landmarker.detect(bitmap, timestampMs)
                throttler.onProcessed(timestampMs, landmarks != null)
                val frame = HandFrame(timestampMs, landmarks)
                val event = gestureEngine.process(frame)
                onResult(
                    AnalysisResult(
                        frame = frame,
                        landmarks = gestureEngine.smoothedLandmarks,
                        state = gestureEngine.state,
                        event = event,
                        aspectRatio = bitmap.width.toFloat() / bitmap.height,
                        fps = fps,
                        powerSaving = throttler.isIdle,
                    ),
                )
                bitmap.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Frame analysis failed", e)
            }
        }
    }

    /** Rotates the frame upright and mirrors it, so that x grows toward the user's right. */
    private fun toUserView(proxy: ImageProxy): Bitmap {
        val source = proxy.toBitmap()
        val matrix = Matrix().apply {
            postRotate(proxy.imageInfo.rotationDegrees.toFloat())
            if (mirror) postScale(-1f, 1f)
        }
        val result = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, false)
        if (result !== source) source.recycle()
        return result
    }

    private companion object {
        const val TAG = "HandAnalyzer"
    }
}
