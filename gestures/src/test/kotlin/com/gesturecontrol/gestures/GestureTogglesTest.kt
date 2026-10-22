package com.gesturecontrol.gestures

import com.gesturecontrol.gestures.SyntheticHand.Pose
import com.gesturecontrol.gestures.pointer.PointerConfig
import com.gesturecontrol.gestures.pointer.PointerEngine
import com.gesturecontrol.gestures.pointer.PointerEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GestureTogglesTest {
    private fun gestures(toggles: GestureToggles, frames: List<HandFrame>) =
        GestureEngine(toggles.applyTo(GestureConfig())).run(frames).map { it.gesture }

    private val swipeThenPalm = SyntheticHand.Sequence()
        .hold(Pose.OPEN_PALM, 0.25f, 0.7f, 300)
        .move(Pose.OPEN_PALM, 0.25f, 0.7f, 0.75f, 0.7f, 300)
        .hold(Pose.OPEN_PALM, 0.75f, 0.7f, 1000)
        .build()

    @Test
    fun allOnByDefault() {
        assertEquals(GestureConfig(), GestureToggles().applyTo(GestureConfig()))
        assertEquals(PointerConfig(), GestureToggles().applyTo(PointerConfig()))
        assertEquals(2, GestureToggles(swipe = false, pinch = false).disabledCount)
    }

    @Test
    fun disabledSwipeNeitherFiresNorBlocksThePalmHold() {
        assertEquals(listOf(Gesture.SWIPE_RIGHT), gestures(GestureToggles(), swipeThenPalm))
        // No swipe and no cooldown from it: the palm held afterwards fires.
        assertEquals(listOf(Gesture.OPEN_PALM_HOLD), gestures(GestureToggles(swipe = false), swipeThenPalm))
    }

    @Test
    fun disabledPalmHoldNeverFires() {
        val hold = SyntheticHand.Sequence().hold(Pose.OPEN_PALM, 0.5f, 0.7f, 1500).build()
        assertEquals(emptyList<Gesture>(), gestures(GestureToggles(palmHold = false), hold))
    }

    @Test
    fun disabledFistOpenLetsThePalmHoldFire() {
        val fistThenPalm = SyntheticHand.Sequence()
            .hold(Pose.FIST, 0.5f, 0.7f, 400)
            .hold(Pose.OPEN_PALM, 0.5f, 0.7f, 1000)
            .build()
        assertEquals(listOf(Gesture.FIST_OPEN), gestures(GestureToggles(), fistThenPalm))
        assertEquals(listOf(Gesture.OPEN_PALM_HOLD), gestures(GestureToggles(fistOpen = false), fistThenPalm))
    }

    @Test
    fun disabledFingerFlickDoesNotFire() {
        val flick = SyntheticHand.Sequence().move(Pose.POINTING, 0.2f, 0.7f, 0.7f, 0.7f, 300)
            .hold(Pose.POINTING, 0.7f, 0.7f, 300).build()
        assertEquals(listOf(Gesture.FINGER_RIGHT), gestures(GestureToggles(), flick))
        assertEquals(emptyList<Gesture>(), gestures(GestureToggles(finger = false), flick))
    }

    @Test
    fun disabledPinchAndScrollProduceNoPointerEvents() {
        val pinch = SyntheticHand.Sequence()
            .hold(Pose.POINTING, 0.5f, 0.7f, 600)
            .hold(Pose.PINCH, 0.5f, 0.7f, 200)
            .hold(Pose.POINTING, 0.5f, 0.7f, 300)
            .build()
        val scroll = SyntheticHand.Sequence()
            .hold(Pose.TWO_FINGERS, 0.5f, 0.6f, 200)
            .move(Pose.TWO_FINGERS, 0.5f, 0.6f, 0.5f, 0.85f, 300)
            .hold(Pose.TWO_FINGERS, 0.5f, 0.85f, 300)
            .build()
        fun events(toggles: GestureToggles, frames: List<HandFrame>): List<PointerEvent> {
            val engine = PointerEngine(toggles.applyTo(PointerConfig()))
            return frames.mapNotNull { engine.process(it).event }
        }
        assertEquals(1, events(GestureToggles(), pinch).size)
        assertEquals(0, events(GestureToggles(pinch = false), pinch).size)
        assertEquals(1, events(GestureToggles(), scroll).size)
        assertEquals(0, events(GestureToggles(scroll = false), scroll).size)
        // With the pinch off the cursor still follows the finger.
        val engine = PointerEngine(GestureToggles(pinch = false).applyTo(PointerConfig()))
        assertNotNull(pinch.map { engine.process(it) }.last().cursor)
    }
}
