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
import com.gesturecontrol.gestures.lowlight.LowLight
import com.gesturecontrol.gestures.lowlight.LowLightConfig
import com.gesturecontrol.gestures.pointer.PointerEngine
import com.gesturecontrol.gestures.pointer.PointerOutput

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
    /** Cursor-mode output, null when cursor mode is off. */
    val pointer: PointerOutput?,
    /** True when the frame was brightened before detection (low light). */
    val brightened: Boolean = false,
)

/**
 * CameraX analyzer: frame -> upright mirrored bitmap -> (brightened if dark) -> landmarks -> gesture engine.
 * Runs entirely on the analysis executor thread; frames are never stored.
 */
class HandAnalyzer(
    private val landmarker: HandLandmarkerEngine,
    private val gestureEngine: GestureEngine,
    private val throttler: PowerSaveThrottler,
    private val pointerEngine: PointerEngine? = null,
    private val mirror: Boolean = true,
    private val lowLight: LowLightConfig = LowLightConfig(),
    private val onResult: (AnalysisResult) -> Unit,
) : ImageAnalysis.Analyzer {

    private var lastTimestampMs = Long.MIN_VALUE
    private var fps = 0f
    /** Reused pixel buffer for the brightness measurement and the low-light curve. */
    private var pixels = IntArray(0)

    override fun analyze(image: ImageProxy) {
        image.use { proxy ->
            val timestampMs = proxy.imageInfo.timestamp / 1_000_000
            // Skipped frames are closed right away and cost no inference.
            if (timestampMs <= lastTimestampMs || !throttler.shouldProcess(timestampMs)) return
            val dt = timestampMs - lastTimestampMs
            lastTimestampMs = timestampMs
            if (dt in 1..2000) fps = fps * 0.8f + (1000f / dt) * 0.2f

            try {
                val upright = toUserView(proxy)
                val (bitmap, brightness, brightened) = brighten(upright)
                val landmarks = landmarker.detect(bitmap, timestampMs)
                throttler.onProcessed(timestampMs, landmarks != null)
                val frame = HandFrame(timestampMs, landmarks, bitmap.width.toFloat() / bitmap.height, brightness)
                val event = gestureEngine.process(frame)
                val pointer = pointerEngine?.process(frame)
                onResult(
                    AnalysisResult(
                        frame = frame,
                        landmarks = gestureEngine.smoothedLandmarks,
                        state = gestureEngine.state,
                        event = event,
                        aspectRatio = frame.aspect,
                        fps = fps,
                        powerSaving = throttler.isIdle,
                        pointer = pointer,
                        brightened = brightened,
                    ),
                )
                bitmap.recycle()
            } catch (e: Exception) {
                Log.e(TAG, "Frame analysis failed", e)
            }
        }
    }

    private data class Brightened(val bitmap: Bitmap, val brightness: Float, val brightened: Boolean)

    /**
     * Measures the mean luma and, when the frame is darker than [LowLightConfig.darkLuma], applies the
     * adaptive gamma curve so that MediaPipe sees a brighter hand (see docs/lowlight.md).
     */
    private fun brighten(bitmap: Bitmap): Brightened {
        val w = bitmap.width
        val h = bitmap.height
        if (pixels.size != w * h) pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        val luma = LowLight.meanLuma(pixels, w, h)
        if (!lowLight.enabled || luma >= lowLight.darkLuma) return Brightened(bitmap, luma, false)
        LowLight.applyLut(pixels, LowLight.gammaLut(luma, lowLight.targetLuma, lowLight.minGamma))
        val target = if (bitmap.isMutable) bitmap else bitmap.copy(Bitmap.Config.ARGB_8888, true).also { bitmap.recycle() }
        target.setPixels(pixels, 0, w, 0, 0, w, h)
        return Brightened(target, luma, true)
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
