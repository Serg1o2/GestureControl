package com.gesturecontrol.gestures.custom

import com.gesturecontrol.gestures.SyntheticHand
import com.gesturecontrol.gestures.SyntheticHand.Pose
import com.gesturecontrol.gestures.mapping.GlobalActionKind
import com.gesturecontrol.gestures.mapping.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CustomSupportTest {
    @Test
    fun actionSpecsRoundTrip() {
        val specs = listOf(
            ActionSpec.Media(MediaKind.NEXT), ActionSpec.Volume(Direction.UP),
            ActionSpec.Global(GlobalActionKind.BACK), ActionSpec.TrackSwipe(true), ActionSpec.TrackSwipe(false),
            ActionSpec.LaunchApp("com.spotify.music"), ActionSpec.Flashlight, ActionSpec.Screenshot,
            ActionSpec.Brightness(Direction.DOWN), ActionSpec.TaskerIntent("my.tasker.EVENT:with:colons"), ActionSpec.None,
        )
        specs.forEach { assertEquals(it, ActionSpec.decode(ActionSpec.encode(it))) }
    }

    @Test
    fun malformedActionsDecodeToNone() {
        assertEquals(ActionSpec.None, ActionSpec.decode("MEDIA:LOUDER"))
        assertEquals(ActionSpec.None, ActionSpec.decode("LAUNCH:"))
        assertEquals(ActionSpec.None, ActionSpec.decode("garbage"))
    }

    @Test
    fun samplesRoundTripWithinRounding() {
        val frames = SyntheticHand.Sequence(noise = 0.003f).move(Pose.TWO_FINGERS, 0.3f, 0.6f, 0.6f, 0.8f, 500).build()
        val sample = SampleBuilder.build(frames.mapNotNull(SampleBuilder::featureFrame))!!
        val decoded = SampleCodec.decode(SampleCodec.encode(listOf(sample, sample)))
        assertEquals(2, decoded.size)
        assertEquals(sample.durationMs, decoded[0].durationMs)
        for (i in sample.steps.indices) for (d in sample.steps[i].indices) {
            assertTrue(abs(sample.steps[i][d] - decoded[0].steps[i][d]) < 1e-4f)
        }
    }

    @Test
    fun trimmerKeepsTheMovingPart() {
        val frames = SyntheticHand.Sequence(noise = 0.002f)
            .hold(Pose.FIST, 0.5f, 0.85f, 800)
            .move(Pose.FIST, 0.5f, 0.85f, 0.5f, 0.55f, 500)
            .hold(Pose.FIST, 0.5f, 0.55f, 800)
            .build()
            .mapNotNull(SampleBuilder::featureFrame)
        val trimmed = RecordingTrimmer.trim(frames)
        val start = trimmed.first().t
        val end = trimmed.last().t
        assertTrue("start $start", start in 650..900)
        // Movement ends at 1300 ms; the lagged activity and the 100 ms pad extend it a little.
        assertTrue("end $end", end in 1250..1500)
    }

    @Test
    fun trimmerKeepsTheMiddleOfAHeldPose() {
        val frames = SyntheticHand.Sequence(noise = 0.002f).hold(Pose.TWO_FINGERS, 0.5f, 0.7f, 2000).build()
            .mapNotNull(SampleBuilder::featureFrame)
        val trimmed = RecordingTrimmer.trim(frames)
        val duration = trimmed.last().t - trimmed.first().t
        assertTrue("duration $duration", duration in 700..820)
    }
}
