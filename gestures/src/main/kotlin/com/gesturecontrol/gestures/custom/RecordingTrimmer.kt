package com.gesturecontrol.gestures.custom

import kotlin.math.sqrt

/**
 * Trims one recorded repeat to the part where something happens.
 *
 * Activity per frame = change of the step vector (shape + motion) over [LAG] frames, minus the
 * noise floor (30th percentile of all activities, scaled): detector jitter does not grow with the
 * lag, real movement does. The kept segment spans from 5% to 95% of the cumulative activity,
 * padded by [PAD_MS]. A repeat with almost no activity above the floor is a held pose: its middle
 * [STATIC_MS] is kept.
 */
object RecordingTrimmer {
    private const val LAG = 3
    private const val PAD_MS = 100L
    private const val STATIC_MS = 800L
    private const val MIN_MS = 300L
    private const val FLOOR_FACTOR = 1.5f

    /** Total activity above the noise floor below which the repeat counts as a held pose. */
    private const val STATIC_ACTIVITY = 1.0f

    fun trim(frames: List<FeatureFrame>): List<FeatureFrame> {
        if (frames.size < LAG + 3) return frames
        val size = frames.sumOf { it.handSize.toDouble() }.toFloat() / frames.size
        val raw = FloatArray(frames.size)
        for (i in LAG until frames.size) raw[i] = change(frames[i - LAG], frames[i], size)
        val floor = raw.drop(LAG).sorted().let { it[(it.size * 0.3f).toInt()] } * FLOOR_FACTOR
        val activity = FloatArray(frames.size) { maxOf(0f, raw[it] - floor) }
        val total = activity.sum()
        if (total < STATIC_ACTIVITY) {
            val mid = (frames.first().t + frames.last().t) / 2
            return frames.filter { kotlin.math.abs(it.t - mid) <= STATIC_MS / 2 }
        }
        var cum = 0f
        var startIdx = 0
        var endIdx = frames.lastIndex
        var startFound = false
        for (i in frames.indices) {
            cum += activity[i]
            if (!startFound && cum >= total * 0.05f) {
                // The change is measured over LAG frames: the movement started LAG frames earlier.
                startIdx = maxOf(i - LAG, 0)
                startFound = true
            }
            if (cum >= total * 0.95f) {
                endIdx = i
                break
            }
        }
        var from = frames[startIdx].t - PAD_MS
        var to = frames[endIdx].t + PAD_MS
        if (to - from < MIN_MS) {
            val mid = (from + to) / 2
            from = mid - MIN_MS / 2
            to = mid + MIN_MS / 2
        }
        return frames.filter { it.t in from..to }
    }

    private fun change(a: FeatureFrame, b: FeatureFrame, size: Float): Float {
        var s = 0f
        for (d in a.shape.indices) {
            val diff = b.shape[d] - a.shape[d]
            s += diff * diff
        }
        val mx = (b.palmX - a.palmX) / size * SampleBuilder.MOTION_WEIGHT
        val my = (b.palmY - a.palmY) / size * SampleBuilder.MOTION_WEIGHT
        return sqrt(s + mx * mx + my * my)
    }
}
