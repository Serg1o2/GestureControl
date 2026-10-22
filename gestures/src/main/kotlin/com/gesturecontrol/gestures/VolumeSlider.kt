package com.gesturecontrol.gestures

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign

/**
 * Pinch volume slider thresholds. Distances are in hand sizes (wrist to middle MCP, isotropic),
 * so the same motion counts at any distance from the camera; times in ms.
 */
data class VolumeSliderConfig(
    /** Thumb-index distance (hand sizes) under which the pinch closes... */
    val pinchClose: Float = 0.30f,
    /** ...and over which it opens again (hysteresis). */
    val pinchOpen: Float = 0.45f,
    /** Index tip to wrist distance (hand sizes) required for a pinch; rejects a fist. */
    val minIndexReach: Float = 1.0f,
    /** The pinch must be held this long, nearly still, before the slider starts (arming). */
    val armMs: Long = 200,
    /** Max palm travel during arming; more motion restarts the arming (a moving pinch is not a slider). */
    val armMaxMotion: Float = 0.12f,
    /** Vertical travel per volume step. */
    val stepHandSizes: Float = 0.25f,
    /** Travel from the arming point before the first step. */
    val deadZoneHandSizes: Float = 0.15f,
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
    private var anchorY = 0f
    private var armStartTs = 0L
    private var lastClosedTs = Long.MIN_VALUE / 2
    private var emitted = 0

    /** Release time of the last slider, for the suppression window. */
    var releasedAtTs: Long = Long.MIN_VALUE / 2
        private set

    val isActive: Boolean get() = phase == Phase.ACTIVE

    /** Signed steps applied so far in the current pinch (for the debug screen). */
    val steps: Int get() = emitted

    /** True during [VolumeSliderConfig.afterReleaseSuppressMs] after a release. */
    fun suppressing(t: Long): Boolean = t - releasedAtTs < config.afterReleaseSuppressMs

    fun update(t: Long, lm: List<Landmark>, aspect: Float): Step? {
        val ratio = PinchGeometry.ratio(lm)
        val limit = if (phase == Phase.IDLE) config.pinchClose else config.pinchOpen
        val closed = PinchGeometry.isPinchHand(lm, config.minIndexReach) && ratio < limit
        val size = isotropicHandSize(lm, aspect).coerceAtLeast(1e-6f)
        val (_, py) = lm.centroid(TrackedPoint.PALM_CENTER.indices)

        if (closed) lastClosedTs = t
        when (phase) {
            Phase.IDLE -> if (closed) startArming(t, py)
            Phase.ARMING -> when {
                !closed && t - lastClosedTs > config.releaseToleranceMs -> phase = Phase.IDLE
                abs(py - anchorY) / size > config.armMaxMotion -> startArming(t, py)
                closed && t - armStartTs >= config.armMs -> {
                    phase = Phase.ACTIVE
                    anchorY = py
                    emitted = 0
                }
            }
            Phase.ACTIVE -> {
                if (!closed && t - lastClosedTs > config.releaseToleranceMs) {
                    release(t)
                    return null
                }
                if (!closed) return null // short dropout: hold the current volume
                // Frame y grows downward, so a hand moving up has a positive offset.
                val target = stepsFor((anchorY - py) / size)
                if (target != emitted) {
                    val up = target > emitted
                    emitted += if (up) 1 else -1
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
    }

    private fun startArming(t: Long, py: Float) {
        phase = Phase.ARMING
        armStartTs = t
        anchorY = py
    }

    private fun release(t: Long) {
        phase = Phase.IDLE
        emitted = 0
        releasedAtTs = t
    }

    private fun stepsFor(offset: Float): Int {
        val travel = abs(offset) - config.deadZoneHandSizes
        if (travel < 0f) return 0
        val steps = floor(travel / config.stepHandSizes).toInt() + 1
        return (sign(offset) * minOf(steps, config.maxSteps)).toInt()
    }
}
