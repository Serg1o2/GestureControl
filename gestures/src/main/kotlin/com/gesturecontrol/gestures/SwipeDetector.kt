package com.gesturecontrol.gestures

import kotlin.math.abs

enum class SwipeDirection { LEFT, RIGHT }

/** Detects fast horizontal motion of the tracked point within a short time window. */
class SwipeDetector(private val config: SwipeConfig) {
    private val trajectory = Trajectory(config.maxDurationMs)

    /** Feeds the next point; returns a direction when a swipe completes. */
    fun update(timestampMs: Long, x: Float, y: Float): SwipeDirection? {
        trajectory.add(timestampMs, x, y)
        val end = trajectory.last ?: return null
        // Check every start point in the window: a swipe may start anywhere inside it.
        for (start in trajectory.asList()) {
            val dt = end.t - start.t
            if (dt <= 0) break
            val dx = end.x - start.x
            val dy = end.y - start.y
            val adx = abs(dx)
            if (adx < config.minDistance) continue
            if (adx < abs(dy) * config.axisDominance) continue
            if (adx / (dt / 1000f) < config.minVelocity) continue
            trajectory.clear()
            return if (dx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
        }
        return null
    }

    fun reset() = trajectory.clear()
}
