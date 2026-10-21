package com.gesturecontrol.gestures.mapping

import kotlin.math.abs

/**
 * Maps a point in the camera frame (user-view coordinates: upright and already mirrored,
 * so x grows toward the user's right) to a normalized screen point.
 *
 * Affine transform: sx = a*u + b*v + c, sy = d*u + e*v + f.
 */
data class ScreenMapper(
    val a: Float, val b: Float, val c: Float,
    val d: Float, val e: Float, val f: Float,
) {
    /** Unclamped result: points outside the active region land outside [0, 1]. */
    fun mapRaw(frame: NormPoint) = NormPoint(a * frame.x + b * frame.y + c, d * frame.x + e * frame.y + f)

    fun map(frame: NormPoint): NormPoint = mapRaw(frame).clamped()

    fun serialize(): String = listOf(a, b, c, d, e, f).joinToString(",")

    companion object {
        /**
         * Default mapping without calibration: [region] of the frame is stretched to the whole
         * screen, so the hand does not have to reach the frame edges.
         */
        fun fromRegion(region: NormRect = DEFAULT_REGION): ScreenMapper = ScreenMapper(
            a = 1f / region.width, b = 0f, c = -region.left / region.width,
            d = 0f, e = 1f / region.height, f = -region.top / region.height,
        )

        val DEFAULT_REGION = NormRect(0.2f, 0.2f, 0.8f, 0.8f)

        fun parse(text: String): ScreenMapper? {
            val v = text.split(',').mapNotNull { it.trim().toFloatOrNull() }
            if (v.size != 6) return null
            return ScreenMapper(v[0], v[1], v[2], v[3], v[4], v[5])
        }
    }
}

/** Three-point calibration: the user puts the fingertip where each target should be reached. */
object Calibration {
    /** Screen targets shown by the calibration wizard (not collinear). */
    val TARGETS: List<NormPoint> = listOf(
        NormPoint(0.15f, 0.15f),
        NormPoint(0.85f, 0.15f),
        NormPoint(0.5f, 0.85f),
    )

    /**
     * Minimum area of the triangle formed by the captured fingertip positions (in frame units).
     * Smaller triangles mean the hand barely moved and the mapping would amplify jitter hugely.
     */
    const val MIN_FRAME_AREA = 0.004f

    /** Solves the affine transform that maps [frame] points exactly onto [screen] points. */
    fun solve(frame: List<NormPoint>, screen: List<NormPoint> = TARGETS): ScreenMapper? {
        require(frame.size == 3 && screen.size == 3) { "Exactly 3 point pairs are required" }
        val (p0, p1, p2) = frame
        // det of [[u0 v0 1] [u1 v1 1] [u2 v2 1]] = twice the signed triangle area.
        val det = p0.x * (p1.y - p2.y) - p0.y * (p1.x - p2.x) + (p1.x * p2.y - p2.x * p1.y)
        if (abs(det) / 2 < MIN_FRAME_AREA) return null

        fun solveAxis(t0: Float, t1: Float, t2: Float): Triple<Float, Float, Float> {
            // Cramer's rule for [u v 1] * [k1 k2 k3]^T = t.
            val k1 = (t0 * (p1.y - p2.y) - p0.y * (t1 - t2) + (t1 * p2.y - t2 * p1.y)) / det
            val k2 = (p0.x * (t1 - t2) - t0 * (p1.x - p2.x) + (p1.x * t2 - p2.x * t1)) / det
            val k3 = (p0.x * (p1.y * t2 - p2.y * t1) - p0.y * (p1.x * t2 - p2.x * t1) + t0 * (p1.x * p2.y - p2.x * p1.y)) / det
            return Triple(k1, k2, k3)
        }

        val (a, b, c) = solveAxis(screen[0].x, screen[1].x, screen[2].x)
        val (d, e, f) = solveAxis(screen[0].y, screen[1].y, screen[2].y)
        return ScreenMapper(a, b, c, d, e, f)
    }
}
