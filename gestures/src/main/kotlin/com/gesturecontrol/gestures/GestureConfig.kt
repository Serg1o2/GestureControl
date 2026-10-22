package com.gesturecontrol.gestures

/**
 * All gesture thresholds in one place. Distances are in normalized frame units
 * (1.0 = full frame width/height), velocities in frame units per second, times in milliseconds.
 */
data class GestureConfig(
    val filter: FilterConfig = FilterConfig(),
    val swipe: SwipeConfig = SwipeConfig(),
    val palm: PalmConfig = PalmConfig(),
    /** Quick horizontal flick of the pointing index finger. */
    val fingerSwipe: SwipeConfig = SwipeConfig(
        trackedPoint = TrackedPoint.INDEX_TIP,
        minDistance = 0.15f,
        minVelocity = 0.7f,
        maxDurationMs = 500,
        minFrames = 3,
    ),
    val fistOpen: FistOpenConfig = FistOpenConfig(),
    /** Global debounce: no gesture can fire within this time after any gesture. */
    val cooldownMs: Long = 600,
    /** Hand absence shorter than this is treated as a detector dropout, not as the hand leaving. */
    val handLostToleranceMs: Long = 150,
    val stability: StabilityConfig = StabilityConfig(),
)

/**
 * Protection against phantom gestures from tracking glitches (see docs/phantoms.md):
 * the detector switching to the other hand, a hand entering the frame, noisy low-light frames.
 */
data class StabilityConfig(
    /**
     * A palm jump between consecutive detections larger than this (in hand sizes, isotropic)
     * means the tracker lost the hand or switched to another one: tracking restarts as for a new hand.
     * 0 disables the check. Default chosen on the replay bench (docs/phantoms.md).
     */
    val maxJumpHandSizes: Float = 2f,
    /**
     * After the hand appears (or tracking restarts) nothing fires for this long and swipes start
     * from scratch, so the motion of a hand entering the frame is not a swipe. 0 disables.
     */
    val settleMs: Long = 0,
)

/** One Euro filter parameters (see https://gery.casiez.net/1euro/). */
data class FilterConfig(
    val enabled: Boolean = true,
    /** Cutoff at zero speed, Hz. Lower = less jitter, more lag. */
    val minCutoff: Float = 1.5f,
    /** Speed coefficient. Higher = less lag during fast motion. */
    val beta: Float = 0.5f,
    /** Cutoff for the derivative, Hz. */
    val dCutoff: Float = 1.0f,
)

enum class TrackedPoint(val indices: List<Int>) {
    WRIST(listOf(HandLandmarks.WRIST)),
    INDEX_TIP(listOf(HandLandmarks.INDEX_TIP)),

    /** Average of the wrist and finger MCP joints: the most stable choice. */
    PALM_CENTER(
        listOf(
            HandLandmarks.WRIST, HandLandmarks.INDEX_MCP, HandLandmarks.MIDDLE_MCP,
            HandLandmarks.RING_MCP, HandLandmarks.PINKY_MCP,
        ),
    ),
}

data class SwipeConfig(
    val trackedPoint: TrackedPoint = TrackedPoint.PALM_CENTER,
    /** Minimum horizontal travel of the tracked point. */
    val minDistance: Float = 0.25f,
    /** Minimum average horizontal speed over the swipe. */
    val minVelocity: Float = 0.8f,
    /** The whole swipe must fit into this window. */
    val maxDurationMs: Long = 600,
    /** |dx| must be at least this many times larger than |dy|. */
    val axisDominance: Float = 1.5f,
    /**
     * After a swipe, the opposite swipe is ignored for this long,
     * so that bringing the hand back does not trigger the reverse action.
     */
    val oppositeSuppressMs: Long = 900,
    /** A swipe must be seen on at least this many frames (start and end included): rejects one-frame jumps. */
    val minFrames: Int = 3,
    /**
     * Poses a palm swipe is made with; empty = any pose except the pointer poses.
     * Casual hand motion (pointing around, gesturing while talking) is mostly in undefined poses.
     */
    val poses: Set<HandPose> = emptySet(),
    /** Share of the swipe's frames that must show one of [poses]. */
    val minPoseShare: Float = 0.6f,
    /**
     * When true, [minDistance] and [minVelocity] are in hand sizes (wrist to middle MCP, isotropic)
     * instead of frame widths: the same motion counts at any distance and in any frame orientation.
     */
    val inHandSizes: Boolean = false,
)

/** Fist that opens into a palm. */
data class FistOpenConfig(
    /** The fist must be seen for at least this long first. */
    val minFistMs: Long = 150,
    /** Max time from the last fist frame to the first open-palm frame. */
    val maxTransitionMs: Long = 400,
)

data class PalmConfig(
    /** How long the open palm must be held still. */
    val holdMs: Long = 500,
    /** Max speed of the palm center while holding. */
    val maxVelocity: Float = 0.25f,
    /** Window over which the palm speed is measured. */
    val velocityWindowMs: Long = 150,
    /** Short misclassifications within the hold are tolerated up to this duration. */
    val dropoutToleranceMs: Long = 120,
    /** A finger is extended when dist(wrist, tip) > dist(wrist, pip) * ratio. */
    val fingerExtensionRatio: Float = 1.1f,
    /** The thumb is extended when dist(tip, pinkyMcp) > dist(ip, pinkyMcp) * ratio. */
    val thumbExtensionRatio: Float = 1.0f,
    /** Whether the thumb must be extended for an open palm. */
    val requireThumb: Boolean = true,
)
