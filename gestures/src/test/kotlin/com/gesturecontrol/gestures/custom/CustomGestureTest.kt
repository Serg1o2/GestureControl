package com.gesturecontrol.gestures.custom

import com.gesturecontrol.gestures.Gesture
import com.gesturecontrol.gestures.GestureEngine
import com.gesturecontrol.gestures.HandFrame
import com.gesturecontrol.gestures.SyntheticHand
import com.gesturecontrol.gestures.SyntheticHand.Pose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomGestureTest {
    /** Two fingers moving diagonally down-right. */
    private fun diagonal(seed: Int, dx: Float = 0f, durationMs: Long = 600) =
        SyntheticHand.Sequence(noise = 0.003f, seed = seed)
            .move(Pose.TWO_FINGERS, 0.35f + dx, 0.55f, 0.6f + dx, 0.8f, durationMs).build()

    /** Fist moving straight up. */
    private fun fistUp(seed: Int, dx: Float = 0f, durationMs: Long = 500) =
        SyntheticHand.Sequence(noise = 0.003f, seed = seed)
            .move(Pose.FIST, 0.5f + dx, 0.85f, 0.5f + dx, 0.55f, durationMs).build()

    private fun sample(frames: List<HandFrame>) = SampleBuilder.build(frames.mapNotNull(SampleBuilder::featureFrame))!!

    private fun template(id: Long, name: String, recordings: List<List<HandFrame>>): CustomTemplate {
        val samples = recordings.map(::sample)
        return CustomTemplate(id, name, samples, GestureQuality.radius(samples))
    }

    private val diagonalT = template(1, "diagonal", listOf(diagonal(1), diagonal(2, 0.03f, 650), diagonal(3, -0.02f, 560)))
    private val fistUpT = template(2, "fist up", listOf(fistUp(4), fistUp(5, 0.04f, 540), fistUp(6, -0.03f, 470)))

    private fun fired(frames: List<HandFrame>, templates: List<CustomTemplate> = listOf(diagonalT, fistUpT)): List<String> {
        val r = CustomGestureRecognizer(templates)
        return frames.mapNotNull { r.process(it)?.template?.name }
    }

    /** Hand still before and after, like a real performance. */
    private fun perform(motion: List<HandFrame>, pose: Pose): List<HandFrame> {
        val first = motion.first().landmarks!![0]
        val last = motion.last().landmarks!![0]
        val before = SyntheticHand.Sequence(seed = 9).hold(pose, first.x, first.y, 400).build()
        val after = SyntheticHand.Sequence(seed = 10).hold(pose, last.x, last.y, 800).build()
        val shift = before.last().timestampMs + 33
        val m = motion.map { it.copy(timestampMs = it.timestampMs + shift) }
        val shift2 = m.last().timestampMs + 33
        return before + m + after.map { it.copy(timestampMs = it.timestampMs + shift2) }
    }

    @Test
    fun recordedGestureIsRecognizedOnce() {
        assertEquals(listOf("diagonal"), fired(perform(diagonal(42, 0.01f, 620), Pose.TWO_FINGERS)))
        assertEquals(listOf("fist up"), fired(perform(fistUp(43, 0.02f, 520), Pose.FIST)))
    }

    @Test
    fun fasterAndSlowerPerformancesAreRecognized() {
        assertEquals(listOf("diagonal"), fired(perform(diagonal(44, durationMs = 480), Pose.TWO_FINGERS)))
        assertEquals(listOf("diagonal"), fired(perform(diagonal(45, durationMs = 750), Pose.TWO_FINGERS)))
    }

    @Test
    fun unrelatedMotionDoesNotFire() {
        val palmSide = SyntheticHand.Sequence(seed = 11).move(Pose.OPEN_PALM, 0.3f, 0.7f, 0.7f, 0.7f, 600).build()
        val still = SyntheticHand.Sequence(seed = 12).hold(Pose.POINTING, 0.5f, 0.7f, 2000).build()
        assertEquals(emptyList<String>(), fired(palmSide))
        assertEquals(emptyList<String>(), fired(still))
    }

    @Test
    fun confidenceIsReportedForTheTestIndicator() {
        val r = CustomGestureRecognizer(listOf(diagonalT))
        perform(diagonal(46), Pose.TWO_FINGERS).forEach { r.process(it) }
        val best = r.lastBest
        assertNotNull(best)
        assertEquals("diagonal", best!!.template.name)
    }

    @Test
    fun qualityFlagsOutliersCloseGesturesAndBuiltInConflicts() {
        val recordings = listOf(diagonal(1), diagonal(2, 0.03f, 650), diagonal(3, -0.02f, 560))
        val good = GestureQuality.check(recordings.map(::sample), recordings, listOf(fistUpT)) { GestureEngine() }
        assertTrue("good: $good", good.ok)

        val withOutlier = recordings + listOf(fistUp(7))
        val bad = GestureQuality.check(withOutlier.map(::sample), withOutlier, emptyList()) { GestureEngine() }
        assertEquals(3, bad.outlierIndex)

        val twin = GestureQuality.check(recordings.map(::sample), recordings, listOf(diagonalT)) { GestureEngine() }
        assertFalse("near-identical gesture must conflict: $twin", twin.distinct)

        val palmSwipe = listOf(SyntheticHand.Sequence(seed = 13).move(Pose.OPEN_PALM, 0.2f, 0.7f, 0.7f, 0.7f, 300).build())
        val builtIn = GestureQuality.check(palmSwipe.map(::sample), palmSwipe, emptyList()) { GestureEngine() }
        assertEquals(setOf(Gesture.SWIPE_RIGHT), builtIn.builtInConflicts)
    }
}
