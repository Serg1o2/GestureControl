package com.gesturecontrol.gestures

import com.gesturecontrol.gestures.SyntheticHand.Pose
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticGestureClassifierTest {
    private val classifier = StaticGestureClassifier(PalmConfig())

    @Test
    fun openPalmIsDetected() {
        assertTrue(classifier.isOpenPalm(SyntheticHand.pose(Pose.OPEN_PALM, 0.5f, 0.7f)))
    }

    @Test
    fun openPalmIsScaleAndPositionInvariant() {
        assertTrue(classifier.isOpenPalm(SyntheticHand.pose(Pose.OPEN_PALM, 0.2f, 0.4f, scale = 0.5f)))
        assertTrue(classifier.isOpenPalm(SyntheticHand.pose(Pose.OPEN_PALM, 0.6f, 0.9f, scale = 1.6f)))
    }

    @Test
    fun fistIsNotPalm() {
        assertFalse(classifier.isOpenPalm(SyntheticHand.pose(Pose.FIST, 0.5f, 0.7f)))
    }

    @Test
    fun oneCurledFingerIsNotPalm() {
        val palm = SyntheticHand.pose(Pose.OPEN_PALM, 0.5f, 0.7f).toMutableList()
        val fist = SyntheticHand.pose(Pose.FIST, 0.5f, 0.7f)
        for (i in HandLandmarks.MIDDLE_PIP..HandLandmarks.MIDDLE_TIP) palm[i] = fist[i]
        assertFalse(classifier.isOpenPalm(palm))
    }

    @Test
    fun foldedThumbRespectsConfig() {
        val palm = SyntheticHand.pose(Pose.OPEN_PALM, 0.5f, 0.7f).toMutableList()
        val fist = SyntheticHand.pose(Pose.FIST, 0.5f, 0.7f)
        for (i in HandLandmarks.THUMB_CMC..HandLandmarks.THUMB_TIP) palm[i] = fist[i]
        assertFalse(classifier.isOpenPalm(palm))
        assertTrue(StaticGestureClassifier(PalmConfig(requireThumb = false)).isOpenPalm(palm))
    }
}
