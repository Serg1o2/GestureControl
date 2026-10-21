package com.gesturecontrol.gestures.mapping

import com.gesturecontrol.gestures.HandFrame
import com.gesturecontrol.gestures.SyntheticHand
import com.gesturecontrol.gestures.SyntheticHand.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationCaptureTest {
    private fun captures(frames: List<HandFrame>): List<NormPoint> {
        val capture = CalibrationCapture()
        return frames.mapNotNull { capture.update(it).captured }
    }

    @Test
    fun stillPointingFingerIsCapturedAtTheFingertip() {
        val frames = SyntheticHand.Sequence(noise = 0.003f).hold(Pose.POINTING, 0.4f, 0.7f, 1100).build()
        val captured = captures(frames)
        assertEquals(1, captured.size)
        val (dx, dy) = SyntheticHand.indexTipOffset(Pose.POINTING)
        assertTrue(captured[0].distanceTo(NormPoint(0.4f + dx, 0.7f + dy)) < 0.005f)
    }

    @Test
    fun movingHandIsNotCaptured() {
        val frames = SyntheticHand.Sequence().move(Pose.POINTING, 0.3f, 0.7f, 0.6f, 0.7f, 2000).build()
        assertEquals(emptyList<NormPoint>(), captures(frames))
    }

    @Test
    fun otherPosesAreNotCaptured() {
        val frames = SyntheticHand.Sequence().hold(Pose.OPEN_PALM, 0.4f, 0.7f, 1500).hold(Pose.FIST, 0.4f, 0.7f, 1500).build()
        assertEquals(emptyList<NormPoint>(), captures(frames))
    }

    @Test
    fun progressGrowsWhileHolding() {
        val capture = CalibrationCapture()
        val states = SyntheticHand.Sequence().hold(Pose.POINTING, 0.4f, 0.7f, 600).build().map { capture.update(it) }
        assertTrue(states.last().progress in 0.5f..0.65f)
        assertTrue(states.all { it.pointing })
    }

    @Test
    fun threeCapturesSolveACalibrationThatHitsTargets() {
        val wrists = listOf(0.35f to 0.55f, 0.65f to 0.55f, 0.5f to 0.85f)
        val capture = CalibrationCapture()
        val points = wrists.map { (x, y) ->
            SyntheticHand.Sequence().hold(Pose.POINTING, x, y, 1100).build()
                .firstNotNullOf { capture.update(it).captured }
        }
        val mapper = Calibration.solve(points)
        assertNotNull(mapper)
        points.zip(Calibration.TARGETS).forEach { (p, target) ->
            assertTrue(mapper!!.map(p).distanceTo(target) < 1e-3f)
        }
    }
}
