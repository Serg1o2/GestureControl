package com.gesturecontrol.gestures.ml

import com.gesturecontrol.gestures.HandLandmarks
import com.gesturecontrol.gestures.Landmark
import com.gesturecontrol.gestures.SyntheticHand
import com.gesturecontrol.gestures.SyntheticHand.Pose
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The pinch model's JVM twin (weights exported by `python -m gc_ml.train_pinch`) on canonical hands. */
class PinchDetectorTest {
    private val detector = PinchDetector(
        DenseMlp.parse(javaClass.classLoader.getResource("ml/pinch_mlp_weights.txt")!!.readText()),
        threshold = 0.5f,
    )

    private fun p(lm: List<Landmark>) = detector.probability(lm, 0.75f)

    @Test
    fun openHandsAndFistsAreNotPinches() {
        for (pose in listOf(Pose.OPEN_PALM, Pose.FIST, Pose.TWO_FINGERS)) {
            assertFalse("$pose: ${p(SyntheticHand.pose(pose, 0.5f, 0.6f))}", detector.isPinch(SyntheticHand.pose(pose, 0.5f, 0.6f), 0.75f))
        }
    }

    @Test
    fun anOkHandIsMorePinchThanAnOpenPalm() {
        // "ok": thumb and index tips together, the other fingers extended (the pose the folded-fingers check rejects).
        val ok = SyntheticHand.pose(Pose.OPEN_PALM, 0.5f, 0.6f).toMutableList()
        val pinch = SyntheticHand.pose(Pose.PINCH, 0.5f, 0.6f)
        for (i in listOf(HandLandmarks.THUMB_TIP, HandLandmarks.INDEX_TIP, HandLandmarks.INDEX_DIP)) ok[i] = pinch[i]
        assertTrue("ok ${p(ok)} vs palm ${p(SyntheticHand.pose(Pose.OPEN_PALM, 0.5f, 0.6f))}", p(ok) > p(SyntheticHand.pose(Pose.OPEN_PALM, 0.5f, 0.6f)))
    }
}
