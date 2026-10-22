package com.gesturecontrol.gestures

import com.gesturecontrol.gestures.pointer.PointerConfig
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * User sensitivity of the main gestures: level [MIN] (much harder to trigger) .. [MAX] (much easier),
 * 0 = the defaults tuned on the replay bench (docs/phantoms.md). Higher sensitivity means smaller or
 * slower movements count, but also more phantom triggers.
 */
data class Sensitivity(
    /** Palm swipe: travel and speed. */
    val swipe: Int = 0,
    /** Index finger flick: travel and speed. */
    val finger: Int = 0,
    /** Open palm hold: hold time and allowed shake. */
    val palmHold: Int = 0,
    /** Fist that opens: how long the fist must be seen and how slowly it may open. */
    val fistOpen: Int = 0,
    /** Pinch press: how close thumb and index must come and how slowly they may close. */
    val pinch: Int = 0,
    /** Two-finger scroll: travel, speed and confirmation delay. */
    val scroll: Int = 0,
    /** Pinch volume slider: travel per step, dead zone and arming time. */
    val volume: Int = 0,
) {
    init {
        for (level in listOf(swipe, finger, palmHold, fistOpen, pinch, scroll, volume)) {
            require(level in MIN..MAX) { "Sensitivity level $level outside $MIN..$MAX" }
        }
    }

    val isDefault: Boolean get() = this == Sensitivity()

    /** Number of gestures whose sensitivity differs from the default. */
    val changedCount: Int get() = listOf(swipe, finger, palmHold, fistOpen, pinch, scroll, volume).count { it != 0 }

    companion object {
        const val MIN = -2
        const val MAX = 2

        /** One level changes a threshold by this factor (about 25%). */
        const val STEP = 1.25f

        /** Multiplier for "must reach at least" thresholds: < 1 when more sensitive. */
        fun factor(level: Int): Float = STEP.pow(-level)
    }
}

/** Applies [Sensitivity] levels to the engine configurations; level 0 leaves a value unchanged. */
object SensitivityMapping {
    fun apply(config: GestureConfig, s: Sensitivity): GestureConfig {
        val swipe = Sensitivity.factor(s.swipe)
        val finger = Sensitivity.factor(s.finger)
        val palm = Sensitivity.factor(s.palmHold)
        val fist = Sensitivity.factor(s.fistOpen)
        val volume = Sensitivity.factor(s.volume)
        return config.copy(
            volume = config.volume.copy(
                stepHandSizes = config.volume.stepHandSizes * volume,
                deadZoneHandSizes = config.volume.deadZoneHandSizes * volume,
                armMs = scale(config.volume.armMs, volume),
            ),
            swipe = config.swipe.copy(
                minDistance = config.swipe.minDistance * swipe,
                minVelocity = config.swipe.minVelocity * swipe,
            ),
            fingerSwipe = config.fingerSwipe.copy(
                minDistance = config.fingerSwipe.minDistance * finger,
                minVelocity = config.fingerSwipe.minVelocity * finger,
            ),
            palm = config.palm.copy(
                holdMs = scale(config.palm.holdMs, palm),
                maxVelocity = config.palm.maxVelocity / palm,
            ),
            fistOpen = config.fistOpen.copy(
                minFistMs = scale(config.fistOpen.minFistMs, fist),
                maxTransitionMs = scale(config.fistOpen.maxTransitionMs, 1f / fist),
            ),
        )
    }

    fun apply(config: PointerConfig, s: Sensitivity): PointerConfig {
        val pinch = Sensitivity.factor(s.pinch)
        val scroll = Sensitivity.factor(s.scroll)
        // The pinch distance changes by half a step per level: it is a ratio of hand sizes and small changes matter.
        val close = config.pinchClose / pinch.pow(0.5f)
        return config.copy(
            pinchClose = close,
            // Keep the hysteresis gap between closing and opening.
            pinchOpen = maxOf(config.pinchOpen, close + (config.pinchOpen - config.pinchClose)),
            pinchMaxClosingMs = scale(config.pinchMaxClosingMs, 1f / pinch),
            scroll = config.scroll.copy(
                minDistance = config.scroll.minDistance * scroll,
                minVelocity = config.scroll.minVelocity * scroll,
                confirmMs = scale(config.scroll.confirmMs, scroll),
            ),
        )
    }

    private fun scale(ms: Long, factor: Float): Long = (ms * factor).roundToLong()
}

/** Which main gestures are switched on (all by default). Custom gestures are not affected. */
data class GestureToggles(
    val swipe: Boolean = true,
    val finger: Boolean = true,
    val palmHold: Boolean = true,
    val fistOpen: Boolean = true,
    val pinch: Boolean = true,
    val scroll: Boolean = true,
    val volume: Boolean = true,
) {
    val disabledCount: Int get() = listOf(swipe, finger, palmHold, fistOpen, pinch, scroll, volume).count { !it }

    fun applyTo(config: GestureConfig): GestureConfig = config.copy(
        disabled = buildSet {
            addAll(config.disabled)
            if (!swipe) addAll(listOf(Gesture.SWIPE_LEFT, Gesture.SWIPE_RIGHT))
            if (!finger) addAll(listOf(Gesture.FINGER_LEFT, Gesture.FINGER_RIGHT))
            if (!palmHold) add(Gesture.OPEN_PALM_HOLD)
            if (!fistOpen) add(Gesture.FIST_OPEN)
            if (!volume) addAll(listOf(Gesture.VOLUME_UP, Gesture.VOLUME_DOWN))
        },
    )

    fun applyTo(config: PointerConfig): PointerConfig =
        config.copy(pinchEnabled = config.pinchEnabled && pinch, scrollEnabled = config.scrollEnabled && scroll)
}
