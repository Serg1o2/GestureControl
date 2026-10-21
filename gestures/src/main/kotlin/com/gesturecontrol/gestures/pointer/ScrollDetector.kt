package com.gesturecontrol.gestures.pointer

import com.gesturecontrol.gestures.mapping.VerticalDirection
import kotlin.math.abs

/** Detects vertical strokes of the two-finger pose; repeated strokes give repeated scroll steps. */
class ScrollDetector(private val config: ScrollConfig) {
    private data class Point(val t: Long, val x: Float, val y: Float)

    private val points = ArrayDeque<Point>()
    private var lastFireTs = Long.MIN_VALUE / 2
    private var lastDirection: VerticalDirection? = null

    fun update(t: Long, x: Float, y: Float): VerticalDirection? {
        points.addLast(Point(t, x, y))
        while (t - points.first().t > config.maxDurationMs) points.removeFirst()
        if (t - lastFireTs < config.cooldownMs) {
            // Only motion after the cooldown counts toward the next step.
            points.clear()
            points.addLast(Point(t, x, y))
            return null
        }
        val end = points.last()
        for (start in points) {
            val dt = end.t - start.t
            if (dt <= 0) break
            val dy = end.y - start.y
            val ady = abs(dy)
            if (ady < config.minDistance) continue
            if (ady < abs(end.x - start.x) * config.axisDominance) continue
            if (ady / (dt / 1000f) < config.minVelocity) continue
            // Frame y grows downward, so a negative dy means the hand moved up.
            val direction = if (dy < 0) VerticalDirection.UP else VerticalDirection.DOWN
            points.clear()
            if (lastDirection != null && direction != lastDirection && t - lastFireTs < config.oppositeSuppressMs) {
                return null
            }
            lastFireTs = t
            lastDirection = direction
            return direction
        }
        return null
    }

    /** Clears the stroke in progress; suppression of the return stroke survives. */
    fun resetStroke() = points.clear()
}
