package com.gesturecontrol.gestures

import com.gesturecontrol.gestures.SyntheticHand.Pose
import com.gesturecontrol.gestures.pointer.PointerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitivityTest {
    private fun gestures(s: Sensitivity, frames: List<HandFrame>) =
        GestureEngine(SensitivityMapping.apply(GestureConfig(), s)).run(frames).map { it.gesture }

    private fun swipe(distance: Float) = SyntheticHand.Sequence()
        .hold(Pose.FIST, 0.3f, 0.7f, 300)
        .move(Pose.FIST, 0.3f, 0.7f, 0.3f + distance, 0.7f, 300)
        .hold(Pose.FIST, 0.3f + distance, 0.7f, 300)
        .build()

    @Test
    fun defaultLevelsLeaveTheConfigsUnchanged() {
        assertEquals(GestureConfig(), SensitivityMapping.apply(GestureConfig(), Sensitivity()))
        assertEquals(PointerConfig(), SensitivityMapping.apply(PointerConfig(), Sensitivity()))
        assertTrue(Sensitivity().isDefault)
        assertEquals(2, Sensitivity(swipe = 1, pinch = -2).changedCount)
    }

    @Test
    fun higherSensitivityCatchesAShorterSwipeAndLowerMissesAStandardOne() {
        val short = swipe(0.3f) // just under what the default catches (smoothing lags behind the hand)
        val standard = swipe(0.45f)
        assertEquals("short, default", emptyList<Gesture>(), gestures(Sensitivity(), short))
        assertEquals("short, +2", listOf(Gesture.SWIPE_RIGHT), gestures(Sensitivity(swipe = 2), short))
        assertEquals("standard, default", listOf(Gesture.SWIPE_RIGHT), gestures(Sensitivity(), standard))
        assertEquals("standard, -2", emptyList<Gesture>(), gestures(Sensitivity(swipe = -2), standard))
    }

    @Test
    fun palmHoldTimeFollowsTheLevel() {
        val hold = SyntheticHand.Sequence().hold(Pose.OPEN_PALM, 0.5f, 0.7f, 450).build()
        assertEquals(emptyList<Gesture>(), gestures(Sensitivity(), hold))
        assertEquals(listOf(Gesture.OPEN_PALM_HOLD), gestures(Sensitivity(palmHold = 1), hold))
        val levels = (Sensitivity.MIN..Sensitivity.MAX).map {
            SensitivityMapping.apply(GestureConfig(), Sensitivity(palmHold = it)).palm.holdMs
        }
        assertEquals(levels.sortedDescending(), levels) // more sensitive = shorter hold
    }

    @Test
    fun pinchThresholdKeepsItsHysteresis() {
        for (level in Sensitivity.MIN..Sensitivity.MAX) {
            val c = SensitivityMapping.apply(PointerConfig(), Sensitivity(pinch = level))
            assertTrue("level $level", c.pinchOpen - c.pinchClose >= PointerConfig().let { it.pinchOpen - it.pinchClose } - 1e-6f)
        }
        val easy = SensitivityMapping.apply(PointerConfig(), Sensitivity(pinch = 2))
        val hard = SensitivityMapping.apply(PointerConfig(), Sensitivity(pinch = -2))
        assertTrue(easy.pinchClose > PointerConfig().pinchClose && hard.pinchClose < PointerConfig().pinchClose)
        assertTrue(easy.pinchMaxClosingMs > hard.pinchMaxClosingMs)
    }

    @Test
    fun scrollConfirmsFasterWhenMoreSensitive() {
        val easy = SensitivityMapping.apply(PointerConfig(), Sensitivity(scroll = 2)).scroll
        assertTrue(easy.confirmMs < PointerConfig().scroll.confirmMs)
        assertTrue(easy.minDistance < PointerConfig().scroll.minDistance)
    }

    @Test(expected = IllegalArgumentException::class)
    fun levelsOutsideTheRangeAreRejected() {
        Sensitivity(swipe = 3)
    }
}
