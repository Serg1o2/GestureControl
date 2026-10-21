package com.gesturecontrol.gestures.pointer

import com.gesturecontrol.gestures.FilterConfig

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
    /**
     * Index tip must be at least this far from the wrist (in hand sizes) for a pinch.
     * Rejects a fist, where thumb and index tips are also close.
     */
    val pinchMinIndexReach: Float = 1.0f,
    /** The press lands where the cursor was this long before the pinch started closing. */
    val pressRollbackMs: Long = 0,

    val dwellMs: Long = 500,
    /** No press of any kind within this time after a press. */
    val pressCooldownMs: Long = 400,

    val scroll: ScrollConfig = ScrollConfig(),
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
)
