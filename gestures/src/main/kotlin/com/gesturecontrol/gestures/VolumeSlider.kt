package com.gesturecontrol.gestures

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/**
 * Pinch volume slider thresholds. Distances are in hand sizes (wrist to middle MCP, isotropic),
 * so the same motion counts at any distance from the camera; times in ms. Defaults chosen on the
 * replay bench (docs/phantoms.md, "Громкость щипком").
 */
data class VolumeSliderConfig(
    /** Thumb-index distance (hand sizes) under which the pinch closes... */
    val pinchClose: Float = 0.22f,
    /** ...and over which it opens again (hysteresis). */
    val pinchOpen: Float = 0.40f,
    /** Index tip to wrist distance (hand sizes) required for a pinch; rejects a fist. */
    val minIndexReach: Float = 1.0f,
    /**
     * A pinch that closes within this time after thumb and index were open (above [pinchOpen]) is
     * deliberate and arms after [armMs]; a hand that was already pinched arms after [restingArmMs].
     */
    val maxClosingMs: Long = 300,
    /** Arming time of a pinch that did not close from open fingers (a hand already resting pinched). 0 = never arms. */
    val restingArmMs: Long = 700,
    /** The pinch must be held this long, nearly still, before the slider starts (arming). */
    val armMs: Long = 300,
    /** Max palm travel during arming; more motion restarts the arming (a moving pinch is not a slider). */
    val armMaxMotion: Float = 0.12f,
    /** Vertical travel per volume step. */
    val stepHandSizes: Float = 0.25f,
    /** A step boundary must be passed by this much to change the count (no flicker on a boundary). */
    val stepHysteresis: Float = 0.08f,
    /** Travel from the arming point before the first step. */
    val deadZoneHandSizes: Float = 0.25f,
    /**
     * Vertical palm speed (hand sizes / s) above which motion does not count: the reference point moves
     * with the hand. A deliberate slider is dragged slowly; a hand being lowered out of the frame is fast.
     * 0 disables.
     */
    val maxSpeed: Float = 2f,
    /** Window over which [maxSpeed] is measured (longer = less sensitive to landmark jitter). */
    val speedWindowMs: Long = 100,
    /**
     * Steps follow the hand with this delay: a step is emitted only if the pinch is still held this long
     * after its threshold was crossed, so a hand leaving the frame (lowered, lost) changes nothing.
     */
    val stepConfirmMs: Long = 250,
    /** Max steps away from the arming point in one pinch (in each direction). */
    val maxSteps: Int = 10,
    /** A pinch that opens for less than this (detector noise) does not release the slider. */
    val releaseToleranceMs: Long = 120,
    /** After the release nothing else fires for this long: the hand opening into a palm is not a pause. */
    val afterReleaseSuppressMs: Long = 800,
)

/**
 * Pinch-and-drag volume control. Feed smoothed landmarks in time order; returns a step per frame at most.
 *
 * Idle → Arming (pinch closed and nearly still for [VolumeSliderConfig.armMs]) → Active: the palm's
 * vertical offset from the arming point, in hand sizes, maps to a signed step count (up = louder);
 * every change of that count by one emits one step, so moving back within the same pinch undoes steps.
 * Opening the pinch (longer than [VolumeSliderConfig.releaseToleranceMs]) releases it. Not thread-safe.
 */
class VolumeSlider(private val config: VolumeSliderConfig = VolumeSliderConfig()) {
    enum class Step { UP, DOWN }

    private enum class Phase { IDLE, ARMING, ACTIVE }

    private var phase = Phase.IDLE
    /** The current arming started from open fingers (see [VolumeSliderConfig.maxClosingMs]). */
    private var deliberate = false
    private var anchorY = 0f
    private var armStartTs = 0L
    private var lastClosedTs = Long.MIN_VALUE / 2
    private var lastOpenTs = Long.MIN_VALUE / 2
    private var emitted = 0
    private var lastY = 0f
    /** Recent palm positions (y in frame heights) for the windowed speed. */
    private val motion = Trajectory(config.speedWindowMs)
    /** Recent (time, offset in hand sizes) pairs for the confirmation delay. */
    private val history = ArrayDeque<Pair<Long, Float>>()

    /** Release time of the last slider, for the suppression window. */
    var releasedAtTs: Long = Long.MIN_VALUE / 2
        private set

    val isActive: Boolean get() = phase == Phase.ACTIVE

    /**
     * The current pinch has changed the volume at least once. Only then does the slider own the hand:
     * an armed but unused slider does not block a swipe, and its release opens no suppression window.
     */
    var hasStepped: Boolean = false
        private set

    /** Signed steps applied so far in the current pinch (for the debug screen). */
    val steps: Int get() = emitted

    /** True during [VolumeSliderConfig.afterReleaseSuppressMs] after a release. */
    fun suppressing(t: Long): Boolean = t - releasedAtTs < config.afterReleaseSuppressMs

    /**
     * [modelPinch]: the pinch model ([com.gesturecontrol.gestures.ml.PinchDetector]) sees a pinch in this frame;
     * it also counts as a pinch (e.g. "ok" with the other fingers extended, which the folded-fingers geometry rejects).
     */
    fun update(t: Long, lm: List<Landmark>, aspect: Float, modelPinch: Boolean = false): Step? {
        val ratio = PinchGeometry.ratio(lm)
        val limit = if (phase == Phase.IDLE) config.pinchClose else config.pinchOpen
        val geometric = PinchGeometry.isPinchHand(lm, config.minIndexReach) && ratio < limit
        // The model's pinch still needs the tips close together (a sanity check on its answer).
        val closed = geometric || (modelPinch && ratio < config.pinchOpen)
        val size = isotropicHandSize(lm, aspect).coerceAtLeast(1e-6f)
        val (_, py) = lm.centroid(TrackedPoint.PALM_CENTER.indices)

        if (closed) lastClosedTs = t
        if (ratio >= config.pinchOpen) lastOpenTs = t
        when (phase) {
            Phase.IDLE -> if (closed) {
                deliberate = config.maxClosingMs <= 0 || t - lastOpenTs <= config.maxClosingMs
                if (deliberate || config.restingArmMs > 0) startArming(t, py)
            }
            Phase.ARMING -> when {
                !closed && t - lastClosedTs > config.releaseToleranceMs -> phase = Phase.IDLE
                abs(py - anchorY) / size > config.armMaxMotion -> startArming(t, py)
                closed && t - armStartTs >= (if (deliberate) config.armMs else config.restingArmMs) -> {
                    phase = Phase.ACTIVE
                    anchorY = py
                    emitted = 0
                    history.clear()
                    motion.clear()
                    motion.add(t, 0f, py)
                    lastY = py
                }
            }
            Phase.ACTIVE -> {
                if (!closed && t - lastClosedTs > config.releaseToleranceMs) {
                    release(t)
                    return null
                }
                if (!closed) return null // short dropout: hold the current volume
                motion.add(t, 0f, py)
                if (config.maxSpeed > 0f && motion.speed(config.speedWindowMs) / size > config.maxSpeed) {
                    // Too fast to be a slider: shift the reference point so this motion adds no steps.
                    anchorY += py - lastY
                }
                lastY = py
                // Frame y grows downward, so a hand moving up has a positive offset.
                history.addLast(t to (anchorY - py) / size)
                // The offset as it was stepConfirmMs ago; older entries are no longer needed.
                while (history.size > 1 && t - history[1].first >= config.stepConfirmMs) history.removeFirst()
                val (ts, delayed) = history.first()
                val target = if (t - ts >= config.stepConfirmMs) countWithHysteresis(delayed) else emitted
                if (target != emitted) {
                    val up = target > emitted
                    emitted += if (up) 1 else -1
                    hasStepped = true
                    return if (up) Step.UP else Step.DOWN
                }
            }
        }
        return null
    }

    /** Ends the slider without a suppression window (hand lost, mode switched off). */
    fun reset() {
        phase = Phase.IDLE
        emitted = 0
        hasStepped = false
        history.clear()
    }

    private fun startArming(t: Long, py: Float) {
        phase = Phase.ARMING
        armStartTs = t
        anchorY = py
    }

    private fun release(t: Long) {
        if (hasStepped) releasedAtTs = t
        phase = Phase.IDLE
        emitted = 0
        hasStepped = false
        history.clear()
    }

    /** Step count for [offset], changing from [emitted] only once a boundary is passed by the hysteresis. */
    private fun countWithHysteresis(offset: Float): Int {
        val h = config.stepHysteresis
        return when {
            stepsFor(offset - h) > emitted -> stepsFor(offset - h)
            stepsFor(offset + h) < emitted -> stepsFor(offset + h)
            else -> emitted
        }
    }

    private fun stepsFor(offset: Float): Int {
        val travel = abs(offset) - config.deadZoneHandSizes
        if (travel < 0f) return 0
        val steps = floor(travel / config.stepHandSizes).toInt() + 1
        return (sign(offset) * minOf(steps, config.maxSteps)).toInt()
    }
}
