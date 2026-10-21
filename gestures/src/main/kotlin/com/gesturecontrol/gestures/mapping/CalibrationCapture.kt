package com.gesturecontrol.gestures.mapping

import com.gesturecontrol.gestures.HandFrame
import com.gesturecontrol.gestures.HandLandmarks
import com.gesturecontrol.gestures.HandPose
import com.gesturecontrol.gestures.PalmConfig
import com.gesturecontrol.gestures.StaticGestureClassifier

data class CaptureState(
    /** Hold progress in [0, 1]. */
    val progress: Float,
    /** Averaged fingertip position (frame, user view) once the hold completes; null otherwise. */
    val captured: NormPoint?,
    /** Whether a pointing hand is currently visible. */
    val pointing: Boolean,
)

/**
 * Captures one calibration point: the index fingertip of a pointing hand held still
 * (within [radius] frame units) for [holdMs]. The result is the average of the held samples.
 */
class CalibrationCapture(
    private val holdMs: Long = 1000,
    private val radius: Float = 0.02f,
) {
    private val classifier = StaticGestureClassifier(PalmConfig())
    private var anchor: NormPoint? = null
    private var startTs = 0L
    private val samples = mutableListOf<NormPoint>()

    fun update(frame: HandFrame): CaptureState {
        val lm = frame.landmarks
        if (lm == null || classifier.classify(lm) != HandPose.POINTING) {
            reset()
            return CaptureState(0f, null, pointing = false)
        }
        val tip = lm[HandLandmarks.INDEX_TIP].let { NormPoint(it.x, it.y) }
        val a = anchor
        if (a == null || a.distanceTo(tip) > radius) {
            anchor = tip
            startTs = frame.timestampMs
            samples.clear()
        }
        samples += tip
        val progress = ((frame.timestampMs - startTs).toFloat() / holdMs).coerceIn(0f, 1f)
        if (progress < 1f) return CaptureState(progress, null, pointing = true)
        val mean = NormPoint(samples.map { it.x }.average().toFloat(), samples.map { it.y }.average().toFloat())
        reset()
        return CaptureState(1f, mean, pointing = true)
    }

    fun reset() {
        anchor = null
        samples.clear()
    }
}
