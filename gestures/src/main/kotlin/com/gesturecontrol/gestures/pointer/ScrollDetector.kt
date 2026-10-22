package com.gesturecontrol.gestures.pointer

import com.gesturecontrol.gestures.mapping.VerticalDirection
import kotlin.math.abs

/** Detects vertical strokes of the two-finger pose; repeated strokes give repeated scroll steps. */
class ScrollDetector(private val config: ScrollConfig) {
    private data class Point(val t: Long, val x: Float, val y: Float)

    private val points = ArrayDeque<Point>()
    private var lastFireTs = Long.MIN_VALUE / 2
    private var lastDirection: VerticalDirection? = null

    /** A detected stroke waiting for confirmation ([ScrollConfig.confirmMs]). */
    private class Pending(val direction: VerticalDirection, val ts: Long, val startY: Float, var extremeY: Float)
    private var pending: Pending? = null

    /** [scale] multiplies the distance and velocity thresholds (the hand size when they are in hand sizes). */
    fun update(t: Long, x: Float, y: Float, scale: Float = 1f): VerticalDirection? {
        pending?.let { p ->
            // The stroke may go on after detection: measure the return from its farthest point.
            p.extremeY = if (p.direction == VerticalDirection.DOWN) maxOf(p.extremeY, y) else minOf(p.extremeY, y)
            val length = abs(p.extremeY - p.startY)
            val back = abs(p.extremeY - y)
            if (back > length * config.maxReturnShare) {
                pending = null // came back: a tap, not a scroll
            } else if (t - p.ts >= config.confirmMs) {
                pending = null
                return fire(p.direction, t)
            }
            if (pending != null) return null
        }
        points.addLast(Point(t, x, y))
        while (t - points.first().t > config.maxDurationMs) points.removeFirst()
        if (t - lastFireTs < config.cooldownMs) {
            // Only motion after the cooldown counts toward the next step.
            points.clear()
            points.addLast(Point(t, x, y))
            return null
        }
        val end = points.last()
        for ((i, start) in points.withIndex()) {
            val dt = end.t - start.t
            if (dt <= 0) break
            if (points.size - i < config.minFrames) break
            val dy = end.y - start.y
            val ady = abs(dy)
            if (ady < config.minDistance * scale) continue
            if (ady < abs(end.x - start.x) * config.axisDominance) continue
            if (ady / (dt / 1000f) < config.minVelocity * scale) continue
            // Frame y grows downward, so a negative dy means the hand moved up.
            val direction = if (dy < 0) VerticalDirection.UP else VerticalDirection.DOWN
            points.clear()
            if (lastDirection != null && direction != lastDirection && t - lastFireTs < config.oppositeSuppressMs) {
                return null
            }
            if (config.confirmMs > 0) {
                pending = Pending(direction, t, start.y, end.y)
                return null
            }
            return fire(direction, t)
        }
        return null
    }

    private fun fire(direction: VerticalDirection, t: Long): VerticalDirection {
        lastFireTs = t
        lastDirection = direction
        return direction
    }

    /** Clears the stroke in progress; suppression of the return stroke survives. */
    fun resetStroke() {
        points.clear()
        pending = null
    }
}
