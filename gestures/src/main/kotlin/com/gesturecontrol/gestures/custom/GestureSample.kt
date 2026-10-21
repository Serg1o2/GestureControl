package com.gesturecontrol.gestures.custom

import com.gesturecontrol.gestures.HandFrame
import com.gesturecontrol.gestures.HandLandmarks
import com.gesturecontrol.gestures.ml.LandmarkNormalizer
import kotlin.math.sqrt

/** One frame of a custom-gesture trajectory: normalized hand shape + palm center in isotropic units. */
class FeatureFrame(val t: Long, val shape: FloatArray, val palmX: Float, val palmY: Float, val handSize: Float)

/**
 * A recorded or observed gesture resampled to [SampleBuilder.POINTS] time steps.
 * Each step = 42 shape values + 2 motion values (palm offset from the first step, in hand sizes,
 * weighted by [SampleBuilder.MOTION_WEIGHT]).
 */
class GestureSample(val steps: Array<FloatArray>, val durationMs: Long) {
    val dims: Int get() = steps[0].size
}

object SampleBuilder {
    const val POINTS = 16
    const val DIMS = LandmarkNormalizer.FEATURES + 2

    /** Motion matters as much as the whole hand shape: one hand size of travel ~ a big shape change. */
    const val MOTION_WEIGHT = 3f

    private val PALM = intArrayOf(
        HandLandmarks.WRIST, HandLandmarks.INDEX_MCP, HandLandmarks.MIDDLE_MCP,
        HandLandmarks.RING_MCP, HandLandmarks.PINKY_MCP,
    )

    fun featureFrame(frame: HandFrame): FeatureFrame? {
        val lm = frame.landmarks ?: return null
        var px = 0f
        var py = 0f
        for (i in PALM) {
            px += lm[i].x * frame.aspect
            py += lm[i].y
        }
        val wx = lm[HandLandmarks.WRIST].x * frame.aspect
        val wy = lm[HandLandmarks.WRIST].y
        val mx = lm[HandLandmarks.MIDDLE_MCP].x * frame.aspect - wx
        val my = lm[HandLandmarks.MIDDLE_MCP].y - wy
        return FeatureFrame(
            t = frame.timestampMs,
            shape = LandmarkNormalizer.normalize(lm, frame.aspect),
            palmX = px / PALM.size,
            palmY = py / PALM.size,
            handSize = maxOf(sqrt(mx * mx + my * my), 1e-6f),
        )
    }

    /** Resamples [frames] (time ordered, at least 3) uniformly in time to [POINTS] steps. */
    fun build(frames: List<FeatureFrame>): GestureSample? {
        if (frames.size < 3) return null
        val t0 = frames.first().t
        val duration = frames.last().t - t0
        if (duration <= 0) return null
        val size = frames.sumOf { it.handSize.toDouble() }.toFloat() / frames.size
        val steps = Array(POINTS) { FloatArray(DIMS) }
        var j = 0
        for (k in 0 until POINTS) {
            val t = t0 + duration * k / (POINTS - 1).toFloat()
            while (j < frames.size - 2 && frames[j + 1].t < t) j++
            val a = frames[j]
            val b = frames[j + 1]
            val w = if (b.t == a.t) 0f else ((t - a.t) / (b.t - a.t)).coerceIn(0f, 1f)
            val out = steps[k]
            for (d in 0 until LandmarkNormalizer.FEATURES) out[d] = a.shape[d] + (b.shape[d] - a.shape[d]) * w
            val px = a.palmX + (b.palmX - a.palmX) * w
            val py = a.palmY + (b.palmY - a.palmY) * w
            out[DIMS - 2] = (px - frames.first().palmX) / size * MOTION_WEIGHT
            out[DIMS - 1] = (py - frames.first().palmY) / size * MOTION_WEIGHT
        }
        return GestureSample(steps, duration)
    }
}

/** Dynamic time warping with a Sakoe-Chiba band; returns the mean per-step Euclidean distance. */
object Dtw {
    fun distance(a: GestureSample, b: GestureSample, band: Int = 3): Float {
        val n = a.steps.size
        val m = b.steps.size
        val inf = Float.MAX_VALUE / 4
        val cost = Array(n + 1) { FloatArray(m + 1) { inf } }
        val len = Array(n + 1) { IntArray(m + 1) }
        cost[0][0] = 0f
        for (i in 1..n) {
            val from = maxOf(1, i - band)
            val to = minOf(m, i + band)
            for (j in from..to) {
                val d = euclid(a.steps[i - 1], b.steps[j - 1])
                var best = cost[i - 1][j - 1]
                var bestLen = len[i - 1][j - 1]
                if (cost[i - 1][j] < best) {
                    best = cost[i - 1][j]
                    bestLen = len[i - 1][j]
                }
                if (cost[i][j - 1] < best) {
                    best = cost[i][j - 1]
                    bestLen = len[i][j - 1]
                }
                cost[i][j] = best + d
                len[i][j] = bestLen + 1
            }
        }
        return cost[n][m] / maxOf(len[n][m], 1)
    }

    private fun euclid(x: FloatArray, y: FloatArray): Float {
        var s = 0f
        for (i in x.indices) {
            val d = x[i] - y[i]
            s += d * d
        }
        return sqrt(s)
    }
}
