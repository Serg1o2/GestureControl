package com.gesturecontrol.gestures

import com.gesturecontrol.gestures.ml.PoseClassifier
import com.gesturecontrol.gestures.ml.RuleBasedPoseClassifier

enum class Gesture {
    SWIPE_LEFT,
    SWIPE_RIGHT,
    OPEN_PALM_HOLD,

    /** Pointing index finger flicked to the user's left / right. */
    FINGER_LEFT,
    FINGER_RIGHT,

    /** Fist opened into a palm. */
    FIST_OPEN,

    /** One volume step of the pinch slider (see [VolumeSlider]). */
    VOLUME_UP,
    VOLUME_DOWN,
}

data class GestureEvent(val gesture: Gesture, val timestampMs: Long)

/** Observable engine state, mainly for the debug screen. */
sealed interface EngineState {
    data object NoHand : EngineState
    data object Tracking : EngineState
    data class PalmHolding(val progress: Float) : EngineState

    /** Palm gesture fired; waiting for the palm to be released before it can fire again. */
    data object PalmLatched : EngineState
    data class Cooldown(val remainingMs: Long) : EngineState

    /** Pinch volume slider active; [steps] = signed steps applied in this pinch. */
    data class VolumeSliding(val steps: Int) : EngineState
}

/**
 * Gesture state machine. Pure function of the frame sequence: feed frames in timestamp order
 * via [process]; it returns at most one event per frame. Not thread-safe.
 *
 * Rules:
 * - Smoothing (One Euro) is applied to all landmarks.
 * - Swipe: fast horizontal travel of the tracked point (see [SwipeConfig]).
 * - Palm: open palm held still for [PalmConfig.holdMs]; fires once until the palm is released.
 * - Finger swipe: flick of the pointing index finger, only while [fingerSwipesEnabled]
 *   (otherwise the pointing pose belongs to the cursor and never swipes).
 * - Fist open: fist for [FistOpenConfig.minFistMs], then a palm within [FistOpenConfig.maxTransitionMs];
 *   it latches the palm so that keeping the palm open does not also fire the palm hold.
 * - Cooldown: after any event nothing fires for [GestureConfig.cooldownMs];
 *   the opposite swipe is suppressed for [SwipeConfig.oppositeSuppressMs].
 * - Stability ([StabilityConfig]): a palm jump between detections restarts tracking as for a new
 *   hand, and nothing fires during the settle time after the hand appears.
 * - Volume slider ([VolumeSlider], while [volumeSliderEnabled]): a still pinch arms it, vertical motion
 *   emits VOLUME_UP / VOLUME_DOWN steps without the global cooldown; while it is active and for
 *   [VolumeSliderConfig.afterReleaseSuppressMs] after the release nothing else fires.
 */
class GestureEngine(
    private val config: GestureConfig = GestureConfig(),
    /** Rules by default; the ML pose model can be plugged in here. */
    private val poseClassifier: PoseClassifier = RuleBasedPoseClassifier(config.palm),
) {
    private val smoother = LandmarkSmoother(config.filter)
    private val swipeDetector = SwipeDetector(config.swipe)
    private val fingerDetector = SwipeDetector(config.fingerSwipe)
    private val palmTrajectory = Trajectory(config.palm.velocityWindowMs)
    private val volumeSlider = VolumeSlider(config.volume)

    /** Set from outside: off in apps with zones, where the pinch is the cursor's press. */
    @Volatile
    var volumeSliderEnabled: Boolean = true

    /** Set from outside (e.g. off in apps where the pointing finger drives the cursor). */
    @Volatile
    var fingerSwipesEnabled: Boolean = true

    private val guard = TrackingGuard(config.stability, config.handLostToleranceMs)
    private var cooldownUntil = Long.MIN_VALUE
    private var lastSwipe: SwipeDirection? = null
    private var lastSwipeTs = Long.MIN_VALUE
    private var lastFinger: SwipeDirection? = null
    private var lastFingerTs = Long.MIN_VALUE

    private var fistStartTs: Long? = null
    private var fistLastTs = Long.MIN_VALUE

    private var palmStartTs: Long? = null
    private var palmLastGoodTs = Long.MIN_VALUE
    private var palmLatched = false
    private var palmLatchReleaseTs: Long? = null

    var state: EngineState = EngineState.NoHand
        private set

    /** Last smoothed landmarks, for drawing. */
    var smoothedLandmarks: List<Landmark>? = null
        private set

    fun process(frame: HandFrame): GestureEvent? {
        val t = frame.timestampMs
        val raw = frame.landmarks
        if (raw == null) {
            if (guard.onNoHand(t)) {
                resetTracking()
                state = EngineState.NoHand
            }
            return null
        }

        if (guard.onHand(t, raw, frame.aspect)) resetTracking()

        val lm = smoother.smooth(raw, t)
        smoothedLandmarks = lm

        val swipeScale = if (config.swipe.inHandSizes) isotropicHandSize(lm, frame.aspect) else 1f
        val fingerScale = if (config.fingerSwipe.inHandSizes) isotropicHandSize(lm, frame.aspect) else 1f
        val swipeX = if (config.swipe.inHandSizes) frame.aspect else 1f
        val fingerX = if (config.fingerSwipe.inHandSizes) frame.aspect else 1f
        val (sx, sy) = lm.centroid(config.swipe.trackedPoint.indices)
        val (px, py) = lm.centroid(TrackedPoint.PALM_CENTER.indices)
        palmTrajectory.add(t, px, py)
        val pose = poseClassifier.classify(lm, frame.aspect).pose
        val isPalm = pose == HandPose.OPEN_PALM
        // Cursor and scroll poses belong to the pointer layer: a fast pointer move is not a track swipe.
        val pointerPose = pose == HandPose.POINTING || pose == HandPose.TWO_FINGERS
        val palmStill = palmTrajectory.speed(config.palm.velocityWindowMs) <= config.palm.maxVelocity

        updatePalmLatch(t, isPalm)
        val fistOpened = updateFist(t, pose)

        if (!guard.settled(t)) {
            // A hand that has just appeared: its entering motion must not become a swipe or a hold.
            swipeDetector.reset()
            fingerDetector.reset()
            resetPalmHold()
            fistStartTs = null
            state = EngineState.Tracking
            return null
        }

        val sliderOn = volumeSliderEnabled && Gesture.VOLUME_UP !in config.disabled
        if (!sliderOn) {
            volumeSlider.reset()
        } else if (volumeSlider.isActive || t >= cooldownUntil) {
            val step = volumeSlider.update(t, lm, frame.aspect)
            // Until its first step an armed slider leaves the other gestures alone (a swipe resets it in fire()).
            if (volumeSlider.isActive && volumeSlider.hasStepped) {
                suppressOtherGestures()
                state = EngineState.VolumeSliding(volumeSlider.steps)
                return step?.let { GestureEvent(if (it == VolumeSlider.Step.UP) Gesture.VOLUME_UP else Gesture.VOLUME_DOWN, t) }
            }
        }
        if (sliderOn && volumeSlider.suppressing(t)) {
            // The hand opening after the slider must not become a palm hold, a swipe or a fist opening.
            suppressOtherGestures()
            state = EngineState.Cooldown(config.volume.afterReleaseSuppressMs - (t - volumeSlider.releasedAtTs))
            return null
        }

        if (t < cooldownUntil) {
            // Only fresh motion after the cooldown may produce the next swipe.
            swipeDetector.reset()
            fingerDetector.reset()
            resetPalmHold()
            state = if (palmLatched) EngineState.PalmLatched else EngineState.Cooldown(cooldownUntil - t)
            return null
        }

        if (fistOpened && Gesture.FIST_OPEN !in config.disabled) {
            resetPalmHold()
            palmLatched = true
            return fire(Gesture.FIST_OPEN, t)
        }

        if (pose == HandPose.POINTING && fingerSwipesEnabled) {
            swipeDetector.reset()
            val tip = lm[HandLandmarks.INDEX_TIP]
            fingerDetector.update(t, tip.x * fingerX, tip.y, fingerScale)?.let { dir ->
                val suppressed = lastFinger != null && dir != lastFinger &&
                    t - lastFingerTs < config.fingerSwipe.oppositeSuppressMs
                val gesture = if (dir == SwipeDirection.RIGHT) Gesture.FINGER_RIGHT else Gesture.FINGER_LEFT
                if (!suppressed && gesture !in config.disabled) {
                    lastFinger = dir
                    lastFingerTs = t
                    return fire(gesture, t)
                }
            }
            return updatePalmHold(t, good = false)
        }
        fingerDetector.reset()

        if (pointerPose) {
            swipeDetector.reset()
            return updatePalmHold(t, good = false)
        }

        val swipePose = config.swipe.poses.isEmpty() || pose in config.swipe.poses
        swipeDetector.update(t, sx * swipeX, sy, swipeScale, swipePose)?.let { dir ->
            val suppressed = lastSwipe != null && dir != lastSwipe &&
                t - lastSwipeTs < config.swipe.oppositeSuppressMs
            val gesture = if (dir == SwipeDirection.RIGHT) Gesture.SWIPE_RIGHT else Gesture.SWIPE_LEFT
            if (!suppressed && gesture !in config.disabled) {
                lastSwipe = dir
                lastSwipeTs = t
                resetPalmHold()
                return fire(gesture, t)
            }
        }

        return updatePalmHold(t, isPalm && palmStill && Gesture.OPEN_PALM_HOLD !in config.disabled)
    }

    fun reset() {
        resetTracking()
        guard.reset()
        cooldownUntil = Long.MIN_VALUE
        lastSwipe = null
        lastSwipeTs = Long.MIN_VALUE
        lastFinger = null
        lastFingerTs = Long.MIN_VALUE
        state = EngineState.NoHand
    }

    private fun updatePalmHold(t: Long, good: Boolean): GestureEvent? {
        if (palmLatched) {
            state = EngineState.PalmLatched
            return null
        }
        if (good) {
            palmLastGoodTs = t
            val start = palmStartTs ?: t.also { palmStartTs = it }
            val held = t - start
            if (held >= config.palm.holdMs) {
                resetPalmHold()
                palmLatched = true
                return fire(Gesture.OPEN_PALM_HOLD, t)
            }
            state = EngineState.PalmHolding(held.toFloat() / config.palm.holdMs)
            return null
        }
        if (palmStartTs != null && t - palmLastGoodTs <= config.palm.dropoutToleranceMs) {
            return null // tolerate a short misclassification
        }
        resetPalmHold()
        state = EngineState.Tracking
        return null
    }

    /**
     * Tracks fist → palm. Returns true on the frame where a held fist turns into an open palm.
     * Intermediate poses while the fingers unfold are allowed within the transition window.
     */
    private fun updateFist(t: Long, pose: HandPose): Boolean {
        val cfg = config.fistOpen
        when (pose) {
            HandPose.FIST -> {
                if (fistStartTs == null) fistStartTs = t
                fistLastTs = t
            }
            HandPose.OPEN_PALM -> {
                val start = fistStartTs ?: return false
                fistStartTs = null
                return fistLastTs - start >= cfg.minFistMs && t - fistLastTs <= cfg.maxTransitionMs
            }
            else -> if (fistStartTs != null && t - fistLastTs > cfg.maxTransitionMs) fistStartTs = null
        }
        return false
    }

    /** The latch is released once the hand stops being an open palm for longer than the dropout tolerance. */
    private fun updatePalmLatch(t: Long, isPalm: Boolean) {
        if (!palmLatched) return
        if (isPalm) {
            palmLatchReleaseTs = null
            return
        }
        val since = palmLatchReleaseTs ?: t.also { palmLatchReleaseTs = it }
        if (t - since >= config.palm.dropoutToleranceMs) {
            palmLatched = false
            palmLatchReleaseTs = null
        }
    }

    private fun fire(gesture: Gesture, t: Long): GestureEvent {
        volumeSlider.reset()
        cooldownUntil = t + config.cooldownMs
        swipeDetector.reset()
        fingerDetector.reset()
        state = EngineState.Cooldown(config.cooldownMs)
        return GestureEvent(gesture, t)
    }

    private fun suppressOtherGestures() {
        swipeDetector.reset()
        fingerDetector.reset()
        resetPalmHold()
        fistStartTs = null
    }

    private fun resetPalmHold() {
        palmStartTs = null
        palmLastGoodTs = Long.MIN_VALUE
    }

    private fun resetTracking() {
        volumeSlider.reset()
        smoother.reset()
        swipeDetector.reset()
        fingerDetector.reset()
        palmTrajectory.clear()
        fistStartTs = null
        poseClassifier.reset()
        resetPalmHold()
        palmLatched = false
        palmLatchReleaseTs = null
        smoothedLandmarks = null
    }
}

