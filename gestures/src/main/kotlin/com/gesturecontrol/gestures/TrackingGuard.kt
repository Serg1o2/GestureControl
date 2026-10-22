package com.gesturecontrol.gestures

import kotlin.math.hypot

/**
 * Decides when hand tracking must restart, shared by the gesture and pointer engines:
 * - the hand was missing for longer than [lostToleranceMs];
 * - the raw palm jumped farther than [StabilityConfig.maxJumpHandSizes] between detections
 *   (the detector switched to the other hand or re-acquired the hand elsewhere).
 * Also tells whether the settle time after the restart is over. Not thread-safe.
 */
class TrackingGuard(private val config: StabilityConfig, private val lostToleranceMs: Long) {
    private var lastHandTs: Long? = null
    private var prevX = 0f
    private var prevY = 0f
    private var prevSize = 0f

    /** Start of the current continuous tracking. */
    var trackingSinceTs: Long = Long.MIN_VALUE
        private set

    /** Feeds a detection; returns true when tracking restarts on this frame (new hand). */
    fun onHand(t: Long, raw: List<Landmark>, aspect: Float): Boolean {
        val (cx, cy) = raw.centroid(TrackedPoint.PALM_CENTER.indices)
        val x = cx * aspect
        val size = isotropicHandSize(raw, aspect)
        val last = lastHandTs
        val limit = config.maxJumpHandSizes
        val restart = last == null || t - last > lostToleranceMs ||
            (limit > 0f && hypot(x - prevX, cy - prevY) > limit * maxOf((size + prevSize) / 2f, 1e-6f))
        if (restart) trackingSinceTs = t
        lastHandTs = t
        prevX = x
        prevY = cy
        prevSize = size
        return restart
    }

    /** Feeds a frame without a hand; returns true once the hand counts as gone. */
    fun onNoHand(t: Long): Boolean {
        val last = lastHandTs ?: return true
        if (t - last <= lostToleranceMs) return false
        lastHandTs = null
        return true
    }

    /** False during [StabilityConfig.settleMs] after tracking (re)started. */
    fun settled(t: Long): Boolean = t - trackingSinceTs >= config.settleMs

    fun reset() {
        lastHandTs = null
        trackingSinceTs = Long.MIN_VALUE
    }
}

/** Wrist to middle MCP distance in frame heights (x scaled by [aspect] = width / height). */
fun isotropicHandSize(lm: List<Landmark>, aspect: Float): Float = hypot(
    (lm[HandLandmarks.MIDDLE_MCP].x - lm[HandLandmarks.WRIST].x) * aspect,
    lm[HandLandmarks.MIDDLE_MCP].y - lm[HandLandmarks.WRIST].y,
)
