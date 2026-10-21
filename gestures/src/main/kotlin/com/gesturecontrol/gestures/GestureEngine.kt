package com.gesturecontrol.gestures

enum class Gesture { SWIPE_LEFT, SWIPE_RIGHT, OPEN_PALM_HOLD }

data class GestureEvent(val gesture: Gesture, val timestampMs: Long)

/** Observable engine state, mainly for the debug screen. */
sealed interface EngineState {
    data object NoHand : EngineState
    data object Tracking : EngineState
    data class PalmHolding(val progress: Float) : EngineState

    /** Palm gesture fired; waiting for the palm to be released before it can fire again. */
    data object PalmLatched : EngineState
    data class Cooldown(val remainingMs: Long) : EngineState
}

/**
 * Gesture state machine. Pure function of the frame sequence: feed frames in timestamp order
 * via [process]; it returns at most one event per frame. Not thread-safe.
 *
 * Rules:
 * - Smoothing (One Euro) is applied to all landmarks.
 * - Swipe: fast horizontal travel of the tracked point (see [SwipeConfig]).
 * - Palm: open palm held still for [PalmConfig.holdMs]; fires once until the palm is released.
 * - Cooldown: after any event nothing fires for [GestureConfig.cooldownMs];
 *   the opposite swipe is suppressed for [SwipeConfig.oppositeSuppressMs].
 */
class GestureEngine(private val config: GestureConfig = GestureConfig()) {
    private val smoother = LandmarkSmoother(config.filter)
    private val classifier = StaticGestureClassifier(config.palm)
    private val swipeDetector = SwipeDetector(config.swipe)
    private val palmTrajectory = Trajectory(config.palm.velocityWindowMs)

    private var lastHandTs: Long? = null
    private var cooldownUntil = Long.MIN_VALUE
    private var lastSwipe: SwipeDirection? = null
    private var lastSwipeTs = Long.MIN_VALUE

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
            val lastSeen = lastHandTs
            if (lastSeen == null || t - lastSeen > config.handLostToleranceMs) {
                resetTracking()
                state = EngineState.NoHand
            }
            return null
        }

        val lastSeen = lastHandTs
        if (lastSeen != null && t - lastSeen > config.handLostToleranceMs) resetTracking()
        lastHandTs = t

        val lm = smoother.smooth(raw, t)
        smoothedLandmarks = lm

        val (sx, sy) = lm.centroid(config.swipe.trackedPoint.indices)
        val (px, py) = lm.centroid(TrackedPoint.PALM_CENTER.indices)
        palmTrajectory.add(t, px, py)
        val pose = classifier.classify(lm)
        val isPalm = pose == HandPose.OPEN_PALM
        // Cursor and scroll poses belong to the pointer layer: a fast pointer move is not a track swipe.
        val pointerPose = pose == HandPose.POINTING || pose == HandPose.TWO_FINGERS
        val palmStill = palmTrajectory.speed(config.palm.velocityWindowMs) <= config.palm.maxVelocity

        updatePalmLatch(t, isPalm)

        if (t < cooldownUntil) {
            // Only fresh motion after the cooldown may produce the next swipe.
            swipeDetector.reset()
            resetPalmHold()
            state = if (palmLatched) EngineState.PalmLatched else EngineState.Cooldown(cooldownUntil - t)
            return null
        }

        if (pointerPose) {
            swipeDetector.reset()
            return updatePalmHold(t, good = false)
        }

        swipeDetector.update(t, sx, sy)?.let { dir ->
            val suppressed = lastSwipe != null && dir != lastSwipe &&
                t - lastSwipeTs < config.swipe.oppositeSuppressMs
            if (!suppressed) {
                lastSwipe = dir
                lastSwipeTs = t
                resetPalmHold()
                val gesture = if (dir == SwipeDirection.RIGHT) Gesture.SWIPE_RIGHT else Gesture.SWIPE_LEFT
                return fire(gesture, t)
            }
        }

        return updatePalmHold(t, isPalm && palmStill)
    }

    fun reset() {
        resetTracking()
        lastHandTs = null
        cooldownUntil = Long.MIN_VALUE
        lastSwipe = null
        lastSwipeTs = Long.MIN_VALUE
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
        cooldownUntil = t + config.cooldownMs
        swipeDetector.reset()
        state = EngineState.Cooldown(config.cooldownMs)
        return GestureEvent(gesture, t)
    }

    private fun resetPalmHold() {
        palmStartTs = null
        palmLastGoodTs = Long.MIN_VALUE
    }

    private fun resetTracking() {
        smoother.reset()
        swipeDetector.reset()
        palmTrajectory.clear()
        resetPalmHold()
        palmLatched = false
        palmLatchReleaseTs = null
        smoothedLandmarks = null
    }
}
