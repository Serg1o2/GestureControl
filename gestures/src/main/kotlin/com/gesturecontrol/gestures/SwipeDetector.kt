package com.gesturecontrol.gestures

import kotlin.math.abs

enum class SwipeDirection { LEFT, RIGHT }

/** Detects fast horizontal motion of the tracked point within a short time window. */
class SwipeDetector(private val config: SwipeConfig) {
    private val trajectory = Trajectory(config.maxDurationMs)
    /** Per trajectory point: whether the hand pose was eligible for a swipe (see [SwipeConfig.poses]). */
    private val eligible = ArrayDeque<Boolean>()

    /**
     * Feeds the next point; returns a direction when a swipe completes.
     * [scale] multiplies the distance and velocity thresholds (the hand size when they are in hand sizes).
     */
    fun update(timestampMs: Long, x: Float, y: Float, scale: Float = 1f, poseEligible: Boolean = true): SwipeDirection? {
        trajectory.add(timestampMs, x, y)
        eligible.addLast(poseEligible)
        val points = trajectory.asList()
        while (eligible.size > points.size) eligible.removeFirst()
        var eligibleFromStart = eligible.count { it }
        val end = trajectory.last ?: return null
        // Check every start point in the window: a swipe may start anywhere inside it.
        for ((i, start) in points.withIndex()) {
            val dt = end.t - start.t
            if (dt <= 0) break
            if (points.size - i < config.minFrames) break
            if (i > 0 && eligible[i - 1]) eligibleFromStart--
            if (eligibleFromStart < config.minPoseShare * (points.size - i)) continue
            val dx = end.x - start.x
            val dy = end.y - start.y
            val adx = abs(dx)
            if (adx < config.minDistance * scale) continue
            if (adx < abs(dy) * config.axisDominance) continue
            if (adx / (dt / 1000f) < config.minVelocity * scale) continue
            reset()
            return if (dx > 0) SwipeDirection.RIGHT else SwipeDirection.LEFT
        }
        return null
    }

    fun reset() {
        trajectory.clear()
        eligible.clear()
    }
}
