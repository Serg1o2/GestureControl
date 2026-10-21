package com.gesturecontrol.gestures

import com.gesturecontrol.gestures.SyntheticHand.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureEngineTest {
    private fun engine() = GestureEngine(GestureConfig())
    private fun gestures(frames: List<HandFrame>) = engine().run(frames).map { it.gesture }

    // --- Swipes ---

    @Test
    fun fastSwipeRight() {
        val frames = SyntheticHand.Sequence()
            .hold(Pose.FIST, 0.25f, 0.7f, 200)
            .move(Pose.FIST, 0.25f, 0.7f, 0.70f, 0.72f, 300)
            .hold(Pose.FIST, 0.70f, 0.72f, 200)
            .build()
        assertEquals(listOf(Gesture.SWIPE_RIGHT), gestures(frames))
    }

    @Test
    fun fastSwipeLeft() {
        val frames = SyntheticHand.Sequence()
            .hold(Pose.FIST, 0.75f, 0.7f, 200)
            .move(Pose.FIST, 0.75f, 0.7f, 0.30f, 0.68f, 300)
            .build()
        assertEquals(listOf(Gesture.SWIPE_LEFT), gestures(frames))
    }

    @Test
    fun swipeWithOpenPalmIsSwipeNotPalm() {
        val frames = SyntheticHand.Sequence()
            .move(Pose.OPEN_PALM, 0.2f, 0.7f, 0.7f, 0.7f, 350)
            .build()
        assertEquals(listOf(Gesture.SWIPE_RIGHT), gestures(frames))
    }

    @Test
    fun swipeIsDetectedAtPowerSavingFrameRate() {
        val frames = SyntheticHand.Sequence(fps = 10)
            .move(Pose.FIST, 0.2f, 0.7f, 0.7f, 0.7f, 300)
            .build()
        assertEquals(listOf(Gesture.SWIPE_RIGHT), gestures(frames))
    }

    @Test
    fun fastPointingOrTwoFingerMotionIsNotASwipe() {
        val pointing = SyntheticHand.Sequence()
            .move(Pose.POINTING, 0.2f, 0.7f, 0.7f, 0.7f, 300)
            .build()
        val twoFingers = SyntheticHand.Sequence()
            .move(Pose.TWO_FINGERS, 0.7f, 0.7f, 0.2f, 0.7f, 300)
            .build()
        assertEquals(emptyList<Gesture>(), gestures(pointing))
        assertEquals(emptyList<Gesture>(), gestures(twoFingers))
    }

    @Test
    fun slowDriftIsIgnored() {
        val frames = SyntheticHand.Sequence()
            .move(Pose.FIST, 0.2f, 0.7f, 0.7f, 0.7f, 2000)
            .build()
        assertEquals(emptyList<Gesture>(), gestures(frames))
    }

    @Test
    fun shortMovementIsIgnored() {
        val frames = SyntheticHand.Sequence()
            .move(Pose.FIST, 0.4f, 0.7f, 0.55f, 0.7f, 150)
            .build()
        assertEquals(emptyList<Gesture>(), gestures(frames))
    }

    @Test
    fun verticalAndDiagonalMotionIsIgnored() {
        val vertical = SyntheticHand.Sequence()
            .move(Pose.FIST, 0.5f, 0.9f, 0.5f, 0.4f, 300)
            .build()
        val diagonal = SyntheticHand.Sequence()
            .move(Pose.FIST, 0.3f, 0.9f, 0.6f, 0.5f, 300)
            .build()
        assertEquals(emptyList<Gesture>(), gestures(vertical))
        assertEquals(emptyList<Gesture>(), gestures(diagonal))
    }

    @Test
    fun returnMotionDoesNotTriggerOppositeSwipe() {
        val frames = SyntheticHand.Sequence()
            .move(Pose.FIST, 0.2f, 0.7f, 0.7f, 0.7f, 300)
            .hold(Pose.FIST, 0.7f, 0.7f, 100)
            .move(Pose.FIST, 0.7f, 0.7f, 0.2f, 0.7f, 300)
            .build()
        assertEquals(listOf(Gesture.SWIPE_RIGHT), gestures(frames))
    }

    @Test
    fun repeatedSwipesAfterCooldownBothFire() {
        val frames = SyntheticHand.Sequence()
            .move(Pose.FIST, 0.2f, 0.7f, 0.7f, 0.7f, 300)
            .noHand(800)
            .move(Pose.FIST, 0.2f, 0.7f, 0.7f, 0.7f, 300)
            .build()
        assertEquals(listOf(Gesture.SWIPE_RIGHT, Gesture.SWIPE_RIGHT), gestures(frames))
    }

    @Test
    fun oppositeSwipeAfterSuppressionWindowFires() {
        val frames = SyntheticHand.Sequence()
            .move(Pose.FIST, 0.2f, 0.7f, 0.7f, 0.7f, 300)
            .hold(Pose.FIST, 0.7f, 0.7f, 1000)
            .move(Pose.FIST, 0.7f, 0.7f, 0.2f, 0.7f, 300)
            .build()
        assertEquals(listOf(Gesture.SWIPE_RIGHT, Gesture.SWIPE_LEFT), gestures(frames))
    }

    @Test
    fun singleFrameDropoutDuringSwipeIsTolerated() {
        val base = SyntheticHand.Sequence()
            .move(Pose.FIST, 0.2f, 0.7f, 0.7f, 0.7f, 300)
            .build()
        val frames = base.mapIndexed { i, f -> if (i == 4) HandFrame(f.timestampMs, null) else f }
        assertEquals(listOf(Gesture.SWIPE_RIGHT), gestures(frames))
    }

    @Test
    fun handEnteringAndStayingStillIsIgnored() {
        val frames = SyntheticHand.Sequence()
            .noHand(300)
            .hold(Pose.FIST, 0.5f, 0.7f, 1500)
            .build()
        assertEquals(emptyList<Gesture>(), gestures(frames))
    }

    // --- Open palm hold ---

    @Test
    fun palmHeldLongEnoughFiresOnce() {
        val frames = SyntheticHand.Sequence()
            .hold(Pose.OPEN_PALM, 0.5f, 0.7f, 2000)
            .build()
        val events = engine().run(frames)
        assertEquals(listOf(Gesture.OPEN_PALM_HOLD), events.map { it.gesture })
        assertTrue("fired too early: ${events[0].timestampMs}", events[0].timestampMs >= 500)
        assertTrue("fired too late: ${events[0].timestampMs}", events[0].timestampMs <= 600)
    }

    @Test
    fun shortPalmDoesNotFire() {
        val frames = SyntheticHand.Sequence()
            .hold(Pose.OPEN_PALM, 0.5f, 0.7f, 300)
            .hold(Pose.FIST, 0.5f, 0.7f, 500)
            .build()
        assertEquals(emptyList<Gesture>(), gestures(frames))
    }

    @Test
    fun palmFiresAgainAfterRelease() {
        val frames = SyntheticHand.Sequence()
            .hold(Pose.OPEN_PALM, 0.5f, 0.7f, 700)
            .hold(Pose.FIST, 0.5f, 0.7f, 400)
            .hold(Pose.OPEN_PALM, 0.5f, 0.7f, 700)
            .build()
        assertEquals(listOf(Gesture.OPEN_PALM_HOLD, Gesture.OPEN_PALM_HOLD), gestures(frames))
    }

    @Test
    fun palmFiresAgainAfterHandLeavesFrame() {
        val frames = SyntheticHand.Sequence()
            .hold(Pose.OPEN_PALM, 0.5f, 0.7f, 700)
            .noHand(500)
            .hold(Pose.OPEN_PALM, 0.5f, 0.7f, 700)
            .build()
        assertEquals(listOf(Gesture.OPEN_PALM_HOLD, Gesture.OPEN_PALM_HOLD), gestures(frames))
    }

    @Test
    fun briefMisclassificationDuringHoldIsTolerated() {
        val frames = SyntheticHand.Sequence()
            .hold(Pose.OPEN_PALM, 0.5f, 0.7f, 250)
            .hold(Pose.FIST, 0.5f, 0.7f, 66)
            .hold(Pose.OPEN_PALM, 0.5f, 0.7f, 300)
            .build()
        assertEquals(listOf(Gesture.OPEN_PALM_HOLD), gestures(frames))
    }

    @Test
    fun slowlyMovingPalmDoesNotFire() {
        // 0.4 units/s: too fast for a hold, too slow for a swipe.
        val frames = SyntheticHand.Sequence()
            .move(Pose.OPEN_PALM, 0.2f, 0.7f, 0.6f, 0.7f, 1000)
            .build()
        assertEquals(emptyList<Gesture>(), gestures(frames))
    }

    @Test
    fun palmHoldProgressIsReported() {
        val e = engine()
        SyntheticHand.Sequence().hold(Pose.OPEN_PALM, 0.5f, 0.7f, 300).build().forEach { e.process(it) }
        val state = e.state
        assertTrue("state=$state", state is EngineState.PalmHolding && state.progress in 0.4f..0.7f)
    }

    @Test
    fun noHandState() {
        val e = engine()
        SyntheticHand.Sequence().hold(Pose.FIST, 0.5f, 0.7f, 100).noHand(300).build().forEach { e.process(it) }
        assertEquals(EngineState.NoHand, e.state)
    }

    // --- Mixed ---

    @Test
    fun swipeThenPalm() {
        val frames = SyntheticHand.Sequence()
            .move(Pose.FIST, 0.2f, 0.7f, 0.7f, 0.7f, 300)
            .hold(Pose.FIST, 0.7f, 0.7f, 700)
            .hold(Pose.OPEN_PALM, 0.7f, 0.7f, 700)
            .build()
        assertEquals(listOf(Gesture.SWIPE_RIGHT, Gesture.OPEN_PALM_HOLD), gestures(frames))
    }

    @Test
    fun configThresholdsAreRespected() {
        val strict = GestureEngine(GestureConfig(swipe = SwipeConfig(minDistance = 0.6f)))
        val frames = SyntheticHand.Sequence()
            .move(Pose.FIST, 0.2f, 0.7f, 0.7f, 0.7f, 300)
            .build()
        assertEquals(emptyList<GestureEvent>(), strict.run(frames))
    }
}
