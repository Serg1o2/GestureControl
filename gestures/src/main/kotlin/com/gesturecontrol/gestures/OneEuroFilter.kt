package com.gesturecontrol.gestures

import kotlin.math.PI
import kotlin.math.abs

/** One Euro filter for a single scalar signal (Casiez et al., CHI 2012). */
class OneEuroFilter(
    private val minCutoff: Float,
    private val beta: Float,
    private val dCutoff: Float,
) {
    private var hasPrev = false
    private var xPrev = 0f
    private var dxPrev = 0f
    private var tPrevMs = 0L

    fun filter(value: Float, timestampMs: Long): Float {
        if (!hasPrev) {
            hasPrev = true
            xPrev = value
            dxPrev = 0f
            tPrevMs = timestampMs
            return value
        }
        val dt = (timestampMs - tPrevMs) / 1000f
        if (dt <= 0f) return xPrev
        tPrevMs = timestampMs

        val dx = (value - xPrev) / dt
        val dxHat = lerp(dxPrev, dx, alpha(dCutoff, dt))
        val cutoff = minCutoff + beta * abs(dxHat)
        val xHat = lerp(xPrev, value, alpha(cutoff, dt))

        xPrev = xHat
        dxPrev = dxHat
        return xHat
    }

    fun reset() {
        hasPrev = false
    }

    private fun alpha(cutoff: Float, dt: Float): Float {
        val tau = 1f / (2f * PI.toFloat() * cutoff)
        return 1f / (1f + tau / dt)
    }

    private fun lerp(prev: Float, current: Float, a: Float) = prev + a * (current - prev)
}

/** Applies independent One Euro filters to x and y of every landmark. */
class LandmarkSmoother(private val config: FilterConfig) {
    private val filters = List(HandLandmarks.COUNT * 2) {
        OneEuroFilter(config.minCutoff, config.beta, config.dCutoff)
    }

    fun smooth(landmarks: List<Landmark>, timestampMs: Long): List<Landmark> {
        if (!config.enabled) return landmarks
        return landmarks.mapIndexed { i, lm ->
            Landmark(
                x = filters[i * 2].filter(lm.x, timestampMs),
                y = filters[i * 2 + 1].filter(lm.y, timestampMs),
                z = lm.z,
            )
        }
    }

    fun reset() = filters.forEach { it.reset() }
}
