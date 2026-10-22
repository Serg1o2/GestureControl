package com.gesturecontrol.gestures

import com.gesturecontrol.gestures.SyntheticHand.Pose
import com.gesturecontrol.gestures.VolumeSlider.Step
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeSliderTest {
    private val aspect = HandFrame.DEFAULT_ASPECT

    /** Hand size of the synthetic hand in frame heights, to express travel in hand sizes. */
    private val hand = isotropicHandSize(SyntheticHand.pose(Pose.PINCH, 0.5f, 0.6f), aspect)

    private fun run(frames: List<HandFrame>, slider: VolumeSlider = VolumeSlider()): List<Step> =
        frames.mapNotNull { f -> f.landmarks?.let { slider.update(f.timestampMs, it, f.aspect) } }

    /**
     * Open fingers, then a pinch held still for [holdMs], dragged vertically by [handSizes]
     * (positive = up), then held.
     */
    private fun drag(handSizes: Float, holdMs: Long = 400, moveMs: Long = 900, fps: Int = 30): List<HandFrame> {
        val y1 = 0.6f - handSizes * hand
        return SyntheticHand.Sequence(fps = fps)
            .hold(Pose.POINTING, 0.5f, 0.6f, 200)
            .hold(Pose.PINCH, 0.5f, 0.6f, holdMs)
            .move(Pose.PINCH, 0.5f, 0.6f, 0.5f, y1, moveMs)
            .hold(Pose.PINCH, 0.5f, y1, 300)
            .build()
    }

    @Test
    fun stillPinchChangesNothing() {
        val still = SyntheticHand.Sequence().hold(Pose.POINTING, 0.5f, 0.6f, 200).hold(Pose.PINCH, 0.5f, 0.6f, 1500).build()
        assertEquals(emptyList<Step>(), run(still))
    }

    @Test
    fun aHandAlreadyPinchedArmsOnlyAfterALongerHold() {
        // Pinch-shaped from the start (no closing motion): it needs restingArmMs (0.7 s) of stillness.
        val tooSoon = SyntheticHand.Sequence()
            .hold(Pose.PINCH, 0.5f, 0.6f, 500)
            .move(Pose.PINCH, 0.5f, 0.6f, 0.5f, 0.6f - 1.2f * hand, 900)
            .hold(Pose.PINCH, 0.5f, 0.6f - 1.2f * hand, 300)
            .build()
        assertEquals(emptyList<Step>(), run(tooSoon))
        val heldLonger = SyntheticHand.Sequence()
            .hold(Pose.PINCH, 0.5f, 0.6f, 800)
            .move(Pose.PINCH, 0.5f, 0.6f, 0.5f, 0.6f - 1.2f * hand, 900)
            .hold(Pose.PINCH, 0.5f, 0.6f - 1.2f * hand, 300)
            .build()
        assertEquals(List(4) { Step.UP }, run(heldLonger))
    }

    @Test
    fun thePinchModelCountsEvenWithTheOtherFingersExtended() {
        // An "ok" hand: thumb and index touch, other fingers extended. The geometry rejects it; the model's
        // PINCH pose lets it drive the slider.
        val ok = SyntheticHand.pose(Pose.OPEN_PALM, 0.5f, 0.6f).toMutableList()
        val pinch = SyntheticHand.pose(Pose.PINCH, 0.5f, 0.6f)
        for (i in listOf(HandLandmarks.THUMB_TIP, HandLandmarks.INDEX_TIP, HandLandmarks.INDEX_DIP)) ok[i] = pinch[i]
        val slider = VolumeSlider()
        var t = 0L
        val steps = mutableListOf<Step>()
        fun feed(lm: List<Landmark>, pose: HandPose, ms: Long) {
            val end = t + ms
            while (t < end) {
                slider.update(t, lm, aspect, modelPinch = pose == HandPose.PINCH)?.let(steps::add); t += 33
            }
        }
        feed(SyntheticHand.pose(Pose.OPEN_PALM, 0.5f, 0.6f), HandPose.OPEN_PALM, 200)
        feed(ok, HandPose.PINCH, 400)
        val dy = 1.2f * hand
        for (k in 1..27) feed(ok.map { Landmark(it.x, it.y - dy * k / 27) }, HandPose.PINCH, 33)
        feed(ok.map { Landmark(it.x, it.y - dy) }, HandPose.PINCH, 400)
        assertEquals(List(4) { Step.UP }, steps)
        // Without the model's answer the same hand is not a pinch.
        assertFalse(PinchGeometry.isPinchHand(ok, 1.0f))
    }

    @Test
    fun aFastDropIsNotASlider() {
        // Lowering the hand quickly (out of the frame) moves the reference point with it: no steps.
        assertEquals(emptyList<Step>(), run(drag(-2f, moveMs = 250)))
    }

    @Test
    fun dragUpGivesOneStepPerQuarterHandAfterTheDeadZone() {
        // (1.2 - 0.25) / 0.25 = 3.8 -> 3 full steps + the first one at the dead zone edge = 4.
        assertEquals(List(4) { Step.UP }, run(drag(1.2f)))
        assertEquals(List(4) { Step.DOWN }, run(drag(-1.2f)))
    }

    @Test
    fun draggingBackWithinTheSamePinchUndoesTheSteps() {
        val back = SyntheticHand.Sequence()
            .hold(Pose.POINTING, 0.5f, 0.6f, 200)
            .hold(Pose.PINCH, 0.5f, 0.6f, 400)
            .move(Pose.PINCH, 0.5f, 0.6f, 0.5f, 0.6f - 1.2f * hand, 900)
            .hold(Pose.PINCH, 0.5f, 0.6f - 1.2f * hand, 300)
            .move(Pose.PINCH, 0.5f, 0.6f - 1.2f * hand, 0.5f, 0.6f, 900)
            .hold(Pose.PINCH, 0.5f, 0.6f, 300)
            .build()
        val steps = run(back)
        assertEquals(steps.count { it == Step.UP }, steps.count { it == Step.DOWN })
        assertEquals(4, steps.count { it == Step.UP })
    }

    @Test
    fun aPinchThatMovesFromTheStartNeverArms() {
        // No still moment: the arming keeps restarting, so a moving pinch (zoom, gesturing) changes nothing.
        assertEquals(emptyList<Step>(), run(drag(2f, holdMs = 0)))
    }

    @Test
    fun stepsAreCappedPerPinch() {
        assertEquals(List(10) { Step.UP }, run(drag(4f, moveMs = 2500)))
    }

    @Test
    fun quickPinchDoesNothingAndLeavesNoSuppression() {
        val slider = VolumeSlider()
        val frames = SyntheticHand.Sequence()
            .hold(Pose.POINTING, 0.5f, 0.6f, 300)
            .hold(Pose.PINCH, 0.5f, 0.6f, 120)
            .hold(Pose.POINTING, 0.5f, 0.6f, 300)
            .build()
        assertEquals(emptyList<Step>(), run(frames, slider))
        assertFalse(slider.suppressing(frames.last().timestampMs))
    }

    @Test
    fun releaseOpensASuppressionWindow() {
        val slider = VolumeSlider()
        val dragged = drag(0.6f)
        val after = dragged.last().timestampMs + 33
        val frames = dragged + SyntheticHand.Sequence().hold(Pose.OPEN_PALM, 0.5f, 0.6f, 300).build()
            .map { it.copy(timestampMs = it.timestampMs + after) }
        run(frames, slider)
        assertFalse(slider.isActive)
        val released = slider.releasedAtTs
        assertTrue(slider.suppressing(released + 700))
        assertFalse(slider.suppressing(released + 900))
    }

    @Test
    fun aFistIsNotAPinch() {
        val fist = SyntheticHand.Sequence()
            .hold(Pose.FIST, 0.5f, 0.6f, 300)
            .move(Pose.FIST, 0.5f, 0.6f, 0.5f, 0.3f, 600)
            .build()
        assertEquals(emptyList<Step>(), run(fist))
    }

    @Test
    fun aOneFrameDropoutDoesNotReleaseTheSlider() {
        val frames = drag(1.2f).toMutableList()
        // Open the pinch on one frame in the middle of the drag.
        val i = frames.size / 2
        val f = frames[i]
        frames[i] = f.copy(landmarks = SyntheticHand.pose(Pose.POINTING, 0.5f, f.landmarks!![0].y))
        assertEquals(List(4) { Step.UP }, run(frames))
    }

    @Test
    fun worksAtFifteenFramesPerSecond() {
        assertEquals(List(4) { Step.UP }, run(drag(1.2f, fps = 15)))
    }
}

/** The slider inside [GestureEngine]: steps as gestures, suppression of the other gestures, switches. */
class VolumeSliderEngineTest {
    private val aspect = HandFrame.DEFAULT_ASPECT
    private val hand = isotropicHandSize(SyntheticHand.pose(Pose.PINCH, 0.5f, 0.6f), aspect)

    private fun drag(handSizes: Float) = SyntheticHand.Sequence()
        .hold(Pose.POINTING, 0.5f, 0.6f, 200)
        .hold(Pose.PINCH, 0.5f, 0.6f, 400)
        .move(Pose.PINCH, 0.5f, 0.6f, 0.5f, 0.6f - handSizes * hand, 900)
        .hold(Pose.PINCH, 0.5f, 0.6f - handSizes * hand, 300)

    private fun gestures(frames: List<HandFrame>, config: GestureConfig = GestureConfig(), slider: Boolean = true) =
        GestureEngine(config).also { it.volumeSliderEnabled = slider }.run(frames).map { it.gesture }

    @Test
    fun dragProducesVolumeStepsWithoutCooldownGaps() {
        assertEquals(List(4) { Gesture.VOLUME_UP }, gestures(drag(1.2f).build()))
        assertEquals(List(4) { Gesture.VOLUME_DOWN }, gestures(drag(-1.2f).build()))
    }

    @Test
    fun openingTheHandAfterTheSliderIsNotAPause() {
        val y = 0.6f - 0.8f * hand
        val shortHold = drag(0.8f).hold(Pose.OPEN_PALM, 0.5f, y, 1000).build()
        assertEquals(listOf(Gesture.VOLUME_UP, Gesture.VOLUME_UP), gestures(shortHold))
        // A deliberate palm hold after the suppression window still works.
        val longHold = drag(0.8f).hold(Pose.OPEN_PALM, 0.5f, y, 1800).build()
        assertEquals(listOf(Gesture.VOLUME_UP, Gesture.VOLUME_UP, Gesture.OPEN_PALM_HOLD), gestures(longHold))
    }

    @Test
    fun onceTheSliderHasChangedTheVolumeHorizontalMotionIsNotASwipe() {
        val y = 0.6f - 0.8f * hand
        val frames = SyntheticHand.Sequence()
            .hold(Pose.POINTING, 0.3f, 0.6f, 200)
            .hold(Pose.PINCH, 0.3f, 0.6f, 400)
            .move(Pose.PINCH, 0.3f, 0.6f, 0.3f, y, 600)
            .hold(Pose.PINCH, 0.3f, y, 300)
            .move(Pose.PINCH, 0.3f, y, 0.8f, y, 300)
            .hold(Pose.PINCH, 0.8f, y, 300)
            .build()
        assertEquals(listOf(Gesture.VOLUME_UP, Gesture.VOLUME_UP), gestures(frames))
    }

    @Test
    fun anArmedButUnusedSliderDoesNotBlockASwipe() {
        val frames = SyntheticHand.Sequence()
            .hold(Pose.POINTING, 0.3f, 0.6f, 200)
            .hold(Pose.PINCH, 0.3f, 0.6f, 400)
            .move(Pose.PINCH, 0.3f, 0.6f, 0.8f, 0.6f, 300)
            .hold(Pose.PINCH, 0.8f, 0.6f, 300)
            .build()
        assertEquals(listOf(Gesture.SWIPE_RIGHT), gestures(frames))
    }

    @Test
    fun sliderIsSilentInAppsWithZonesOrWhenSwitchedOff() {
        assertEquals(emptyList<Gesture>(), gestures(drag(1.2f).build(), slider = false))
        val off = GestureToggles(volume = false).applyTo(GestureConfig())
        assertEquals(emptyList<Gesture>(), gestures(drag(1.2f).build(), off))
    }

    @Test
    fun higherSensitivityGivesMoreStepsForTheSameDrag() {
        val more = SensitivityMapping.apply(GestureConfig(), Sensitivity(volume = 2))
        val less = SensitivityMapping.apply(GestureConfig(), Sensitivity(volume = -2))
        val normal = gestures(drag(1.2f).build()).size
        assertTrue(gestures(drag(1.2f).build(), more).size > normal)
        assertTrue(gestures(drag(1.2f).build(), less).size < normal)
    }
}
