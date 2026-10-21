package com.gesturecontrol.gestures.mapping

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Point in normalized coordinates: (0, 0) top-left, (1, 1) bottom-right. */
data class NormPoint(val x: Float, val y: Float) {
    fun clamped() = NormPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))

    /**
     * Distance in screen-width units: y differences are scaled by [aspect] = height / width,
     * so that a circle on screen stays a circle.
     */
    fun distanceTo(other: NormPoint, aspect: Float = 1f): Float {
        val dx = x - other.x
        val dy = (y - other.y) * aspect
        return sqrt(dx * dx + dy * dy)
    }
}

/** Axis-aligned rectangle in normalized coordinates. */
data class NormRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val center: NormPoint get() = NormPoint((left + right) / 2, (top + bottom) / 2)

    /** Edges are inclusive. */
    operator fun contains(p: NormPoint): Boolean = p.x in left..right && p.y in top..bottom

    /** Point at fractions ([fx], [fy]) of the rectangle. */
    fun pointAt(fx: Float, fy: Float) = NormPoint(left + width * fx, top + height * fy)

    /** Moves the rectangle by (dx, dy), keeping its size and keeping it inside the unit square. */
    fun offsetWithin(dx: Float, dy: Float): NormRect {
        val x = (left + dx).coerceIn(0f, 1f - width)
        val y = (top + dy).coerceIn(0f, 1f - height)
        return NormRect(x, y, x + width, y + height)
    }

    companion object {
        /** Rectangle spanned by two corners in any order, clamped to the unit square. */
        fun fromCorners(a: NormPoint, b: NormPoint): NormRect {
            val p = a.clamped()
            val q = b.clamped()
            return NormRect(min(p.x, q.x), min(p.y, q.y), max(p.x, q.x), max(p.y, q.y))
        }
    }
}
