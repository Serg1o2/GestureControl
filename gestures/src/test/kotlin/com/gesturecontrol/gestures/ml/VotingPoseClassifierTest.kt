package com.gesturecontrol.gestures.ml

import com.gesturecontrol.gestures.HandPose
import com.gesturecontrol.gestures.SyntheticHand
import com.gesturecontrol.gestures.SyntheticHand.Pose
import org.junit.Assert.assertEquals
import org.junit.Test

class VotingPoseClassifierTest {
    /** Fake model returning a scripted probability vector per call. */
    private class ScriptedModel(private val outputs: List<FloatArray>) : PoseModel {
        private var i = 0
        override val classes = listOf(HandPose.OTHER, HandPose.OPEN_PALM, HandPose.FIST)
        override fun predict(features: FloatArray) = outputs[minOf(i++, outputs.lastIndex)]
    }

    private val hand = SyntheticHand.pose(Pose.OPEN_PALM, 0.5f, 0.7f)
    private val palm = floatArrayOf(0.05f, 0.9f, 0.05f)
    private val weakFist = floatArrayOf(0.3f, 0.1f, 0.6f)
    private val fist = floatArrayOf(0.02f, 0.03f, 0.95f)

    @Test
    fun singleFrameFlickerIsVotedAway() {
        val c = VotingPoseClassifier(ScriptedModel(listOf(palm, palm, palm, fist, palm)), floatArrayOf(0f, 0.5f, 0.5f))
        val poses = List(5) { c.classify(hand, 0.75f).pose }
        assertEquals(List(5) { HandPose.OPEN_PALM }, poses)
    }

    @Test
    fun belowThresholdBecomesOther() {
        val c = VotingPoseClassifier(ScriptedModel(listOf(weakFist)), floatArrayOf(0f, 0.5f, 0.8f), window = 1)
        assertEquals(HandPose.OTHER, c.classify(hand, 0.75f).pose)
    }

    @Test
    fun stableChangeWinsAfterMajority() {
        val c = VotingPoseClassifier(ScriptedModel(listOf(palm, palm, fist, fist, fist)), floatArrayOf(0f, 0.5f, 0.5f))
        val poses = List(5) { c.classify(hand, 0.75f).pose }
        assertEquals(HandPose.FIST, poses.last())
    }

    @Test
    fun resetClearsHistory() {
        val c = VotingPoseClassifier(ScriptedModel(listOf(palm, palm, palm, fist)), floatArrayOf(0f, 0.5f, 0.5f))
        repeat(3) { c.classify(hand, 0.75f) }
        c.reset()
        assertEquals(HandPose.FIST, c.classify(hand, 0.75f).pose)
    }
}
