package com.gesturecontrol.gestures

import com.gesturecontrol.gestures.SyntheticHand.Pose
import com.gesturecontrol.gestures.mapping.VerticalDirection
import com.gesturecontrol.gestures.pointer.PointerConfig
import com.gesturecontrol.gestures.pointer.PointerEngine
import com.gesturecontrol.gestures.pointer.PointerEvent
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Phantom-event protections (docs/phantoms.md). Each case shows the failure with the protection
 * off and the fixed behaviour with it on, so that a regression in either direction is caught.
 */
class StabilityTest {
    private val stable = StabilityConfig(maxJumpHandSizes = 2f, settleMs = 200)

    /** Engine and pointer with every protection off: the behaviour before docs/phantoms.md. */
    private val unprotected = GestureConfig(stability = StabilityConfig(maxJumpHandSizes = 0f))
    private val unprotectedPointer = PointerConfig(
        stability = StabilityConfig(maxJumpHandSizes = 0f),
        pinchMaxClosingMs = 0,
    ).let { it.copy(scroll = it.scroll.copy(confirmMs = 0, minFrames = 2)) }

    private fun gestures(config: GestureConfig, frames: List<HandFrame>) =
        GestureEngine(config).run(frames).map { it.gesture }

    // --- Tracker jumps to the other hand ---

    /** The still hand is lost for one frame and the detector picks up the other (still) hand. */
    private val otherHand = SyntheticHand.Sequence()
        .hold(Pose.FIST, 0.30f, 0.7f, 500)
        .noHand(33)
        .hold(Pose.FIST, 0.75f, 0.7f, 500)
        .build()

    @Test
    fun jumpToTheOtherHandIsNotASwipe() {
        assertEquals(listOf(Gesture.SWIPE_RIGHT), gestures(unprotected, otherHand))
        assertEquals(emptyList<Gesture>(), gestures(GestureConfig(), otherHand))
    }

    @Test
    fun realSwipesSurviveTheJumpCheckAtAnyFrameRate() {
        for (fps in listOf(30, 15, 10)) {
            val frames = SyntheticHand.Sequence(fps = fps)
                .hold(Pose.FIST, 0.25f, 0.7f, 300)
                .move(Pose.FIST, 0.25f, 0.7f, 0.75f, 0.7f, 300)
                .hold(Pose.FIST, 0.75f, 0.7f, 200)
                .build()
            assertEquals("fps $fps", listOf(Gesture.SWIPE_RIGHT), gestures(GestureConfig(stability = stable), frames))
        }
    }

    // --- Hand entering the frame ---

    @Test
    fun handEnteringTheFrameIsNotASwipe() {
        val entering = SyntheticHand.Sequence()
            .noHand(300)
            .move(Pose.FIST, 0.05f, 0.7f, 0.55f, 0.7f, 300)
            .hold(Pose.FIST, 0.55f, 0.7f, 300)
            .build()
        assertEquals(listOf(Gesture.SWIPE_RIGHT), gestures(GestureConfig(), entering))
        assertEquals(emptyList<Gesture>(), gestures(GestureConfig(stability = stable), entering))
    }

    @Test
    fun swipeAfterTheSettleTimeFires() {
        val frames = SyntheticHand.Sequence()
            .hold(Pose.FIST, 0.25f, 0.7f, 250)
            .move(Pose.FIST, 0.25f, 0.7f, 0.75f, 0.7f, 300)
            .build()
        assertEquals(listOf(Gesture.SWIPE_RIGHT), gestures(GestureConfig(stability = stable), frames))
    }

    // --- Distance to the camera ---

    private fun scaledMove(scale: Float, x0: Float, x1: Float, durationMs: Long): List<HandFrame> {
        val frames = mutableListOf<HandFrame>()
        var t = 0L
        while (t <= 300) {
            frames += HandFrame(t, SyntheticHand.pose(Pose.FIST, x0, 0.7f, scale)); t += 33
        }
        val start = t
        while (t - start <= durationMs + 300) {
            val k = ((t - start).toFloat() / durationMs).coerceAtMost(1f)
            frames += HandFrame(t, SyntheticHand.pose(Pose.FIST, x0 + (x1 - x0) * k, 0.7f, scale)); t += 33
        }
        return frames
    }

    private val handUnits = GestureConfig().let {
        it.copy(swipe = it.swipe.copy(inHandSizes = true, minDistance = 1.25f, minVelocity = 4.0f))
    }

    @Test
    fun farHandSwipeCountsInHandSizes() {
        // Hand at half the usual size moves 0.2 frame widths: ~2 hand sizes, a full swipe.
        val far = scaledMove(0.5f, 0.3f, 0.5f, 250)
        assertEquals(emptyList<Gesture>(), gestures(GestureConfig(), far))
        assertEquals(listOf(Gesture.SWIPE_RIGHT), gestures(handUnits, far))
    }

    @Test
    fun smallMotionOfAHandCloseToTheCameraIsNotASwipeInHandSizes() {
        // Hand at twice the usual size shifts by 0.3 frame widths: under one hand size.
        val near = scaledMove(2f, 0.3f, 0.6f, 250)
        assertEquals(listOf(Gesture.SWIPE_RIGHT), gestures(GestureConfig(), near))
        assertEquals(emptyList<Gesture>(), gestures(handUnits, near))
    }

    // --- Pinch needs a closing motion ---

    private val open = SyntheticHand.pose(Pose.POINTING, 0.5f, 0.7f)
    private val closed = SyntheticHand.pose(Pose.PINCH, 0.5f, 0.7f)

    /** Hand between POINTING (k = 0) and PINCH (k = 1), landmarks interpolated. */
    private fun between(k: Float) = open.zip(closed) { a, b -> Landmark(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k) }

    /** Pointing, then the fingers rest at [restK] for [restMs], then close within 100 ms. */
    private fun pinchAfterRest(restK: Float, restMs: Long): List<HandFrame> {
        val frames = SyntheticHand.Sequence().hold(Pose.POINTING, 0.5f, 0.7f, 600).build().toMutableList()
        var t = frames.last().timestampMs + 33
        val restEnd = t + restMs
        while (t < restEnd) {
            frames += HandFrame(t, between(restK)); t += 33
        }
        for (k in listOf(restK + (1 - restK) / 3, restK + 2 * (1 - restK) / 3)) {
            frames += HandFrame(t, between(k)); t += 33
        }
        repeat(10) { frames += HandFrame(t, closed); t += 33 }
        return frames
    }

    /** Interpolation factor where the thumb-index distance is [ratio] hand sizes. */
    private fun kForRatio(ratio: Float): Float =
        (0..1000).map { it / 1000f }.minBy { kotlin.math.abs(StaticGestureClassifier.pinchRatio(between(it)) - ratio) }

    private fun presses(config: PointerConfig, frames: List<HandFrame>) =
        PointerEngine(config).let { e -> frames.mapNotNull { e.process(it).event as? PointerEvent.Press } }

    @Test
    fun fingersRestingHalfClosedThenTouchingDoNotPress() {
        val closing = PointerConfig()
        // Relaxed hand: thumb and index hover between the open and close thresholds, then touch.
        val relaxed = pinchAfterRest(kForRatio(0.38f), 1000)
        assertEquals(1, presses(unprotectedPointer, relaxed).size)
        assertEquals(0, presses(closing, relaxed).size)
        // A deliberate pinch from open fingers still presses.
        val deliberate = pinchAfterRest(0f, 0)
        assertEquals(1, presses(closing, deliberate).size)
    }

    // --- Two-finger tap is not a scroll ---

    private fun scrolls(config: PointerConfig, frames: List<HandFrame>) =
        PointerEngine(config).let { e -> frames.mapNotNull { (e.process(it).event as? PointerEvent.Scroll)?.direction } }

    @Test
    fun twoFingerTapIsNotAScrollButAStrokeIs() {
        val confirm = PointerConfig()
        val tap = SyntheticHand.Sequence()
            .hold(Pose.TWO_FINGERS, 0.5f, 0.6f, 200)
            .move(Pose.TWO_FINGERS, 0.5f, 0.6f, 0.5f, 0.8f, 150)
            .move(Pose.TWO_FINGERS, 0.5f, 0.8f, 0.5f, 0.6f, 150)
            .hold(Pose.TWO_FINGERS, 0.5f, 0.6f, 300)
            .build()
        val stroke = SyntheticHand.Sequence()
            .hold(Pose.TWO_FINGERS, 0.5f, 0.6f, 200)
            .move(Pose.TWO_FINGERS, 0.5f, 0.6f, 0.5f, 0.85f, 300)
            .hold(Pose.TWO_FINGERS, 0.5f, 0.85f, 300)
            .build()
        assertEquals(listOf(VerticalDirection.DOWN), scrolls(unprotectedPointer, tap))
        assertEquals(emptyList<VerticalDirection>(), scrolls(confirm, tap))
        assertEquals(listOf(VerticalDirection.DOWN), scrolls(confirm, stroke))
    }
}
