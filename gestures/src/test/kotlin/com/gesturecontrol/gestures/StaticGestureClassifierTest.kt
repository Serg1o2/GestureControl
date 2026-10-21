package com.gesturecontrol.gestures

import com.gesturecontrol.gestures.SyntheticHand.Pose
import org.junit.Assert.assertEquals
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
    fun posesAreClassified() {
        fun classify(p: Pose) = classifier.classify(SyntheticHand.pose(p, 0.5f, 0.7f))
        assertEquals(HandPose.OPEN_PALM, classify(Pose.OPEN_PALM))
        assertEquals(HandPose.POINTING, classify(Pose.POINTING))
        assertEquals(HandPose.TWO_FINGERS, classify(Pose.TWO_FINGERS))
        assertEquals(HandPose.OTHER, classify(Pose.FIST))
    }

    @Test
    fun pinchRatioSeparatesPinchFromPointingAndIsScaleInvariant() {
        val open = StaticGestureClassifier.pinchRatio(SyntheticHand.pose(Pose.POINTING, 0.5f, 0.7f))
        val closed = StaticGestureClassifier.pinchRatio(SyntheticHand.pose(Pose.PINCH, 0.5f, 0.7f))
        val closedSmall = StaticGestureClassifier.pinchRatio(SyntheticHand.pose(Pose.PINCH, 0.3f, 0.5f, scale = 0.5f))
        assertTrue("open=$open", open > 0.6f)
        assertTrue("closed=$closed", closed < 0.2f)
        assertEquals(closed, closedSmall, 1e-4f)
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
