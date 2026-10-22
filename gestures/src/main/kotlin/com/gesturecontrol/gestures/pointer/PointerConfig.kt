package com.gesturecontrol.gestures.pointer

import com.gesturecontrol.gestures.FilterConfig
import com.gesturecontrol.gestures.StabilityConfig

/** How a "press" is triggered in cursor mode. */
enum class PressMode {
    /** Thumb + index pinch. Outside zones it taps at the cursor. */
    PINCH,

    /** Holding the cursor inside a zone. Works only in zones. */
    DWELL,
}

/**
 * Cursor-mode thresholds. Screen distances are in screen-width units, frame distances in
 * normalized frame units, pinch distances in hand sizes (wrist to middle MCP), times in ms.
 */
data class PointerConfig(
    val pressMode: PressMode = PressMode.PINCH,
    /** One Euro filter on the screen cursor; beta is large because units are whole screens. */
    val filter: FilterConfig = FilterConfig(minCutoff = 1.0f, beta = 8f, dCutoff = 1.0f),
    /** The cursor does not move until the filtered point leaves this radius. Kills residual jitter. */
    val deadZone: Float = 0.006f,
    /** Hide the cursor after the pointing pose has been gone for this long. */
    val cursorHideDelayMs: Long = 300,
    val handLostToleranceMs: Long = 150,

    /** Pinch closes below this thumb-index distance... */
    val pinchClose: Float = 0.30f,
    /** ...and opens above this one (hysteresis). */
    val pinchOpen: Float = 0.45f,
    /** The pinch must stay closed this long to count. */
    val pinchMinClosedMs: Long = 40,
    /** ...and on at least this many consecutive frames (one noisy frame is not a pinch). */
    val pinchMinClosedFrames: Int = 1,
    /**
     * The cursor (pointing pose) must have been shown this long before a pinch can press:
     * a relaxed hand passing through a pointing-like pose does not press. 0 disables.
     */
    val pinchArmMs: Long = 0,
    /**
     * A press needs a closing motion: the pinch must close within this time after the fingers were
     * last open (ratio above [pinchOpen]). A relaxed hand whose thumb and index already touch never
     * presses. 0 disables.
     */
    val pinchMaxClosingMs: Long = 300,
    /**
     * Index tip must be at least this far from the wrist (in hand sizes) for a pinch.
     * Rejects a fist, where thumb and index tips are also close.
     */
    val pinchMinIndexReach: Float = 1.0f,
    /** The press lands where the cursor was this long before the pinch started closing. */
    val pressRollbackMs: Long = 80,

    val dwellMs: Long = 500,
    /** No press of any kind within this time after a press. */
    val pressCooldownMs: Long = 400,

    val scroll: ScrollConfig = ScrollConfig(),
    /** Tracking restarts on palm jumps; no press or scroll during the settle time (see [StabilityConfig]). */
    val stability: StabilityConfig = StabilityConfig(),
)

/** Two-finger vertical scroll, measured on the midpoint of the index and middle fingertips. */
data class ScrollConfig(
    val minDistance: Float = 0.12f,
    val minVelocity: Float = 0.4f,
    val maxDurationMs: Long = 500,
    val axisDominance: Float = 1.5f,
    /** Minimum time between two scroll steps. */
    val cooldownMs: Long = 350,
    /** Moving the hand back is ignored for this long after a scroll. */
    val oppositeSuppressMs: Long = 600,
    /** A stroke must be seen on at least this many frames (start and end included). */
    val minFrames: Int = 3,
    /**
     * A stroke is reported only after it has held for this long without coming back by more than
     * [maxReturnShare] of its length: a two-finger tap (down and back up) is not a scroll. 0 = immediate.
     */
    val confirmMs: Long = 150,
    val maxReturnShare: Float = 0.5f,
    /** When true, [minDistance] and [minVelocity] are in hand sizes instead of frame heights. */
    val inHandSizes: Boolean = false,
)
