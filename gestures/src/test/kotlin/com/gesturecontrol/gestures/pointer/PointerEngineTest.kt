package com.gesturecontrol.gestures.pointer

import com.gesturecontrol.gestures.HandFrame
import com.gesturecontrol.gestures.SyntheticHand
import com.gesturecontrol.gestures.SyntheticHand.Pose
import com.gesturecontrol.gestures.mapping.NormPoint
import com.gesturecontrol.gestures.mapping.NormRect
import com.gesturecontrol.gestures.mapping.ScreenMapper
import com.gesturecontrol.gestures.mapping.VerticalDirection
import com.gesturecontrol.gestures.mapping.Zone
import com.gesturecontrol.gestures.mapping.ZoneAction
import com.gesturecontrol.gestures.mapping.ZoneSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PointerEngineTest {
    private val aspect = 2f
    private val mapper = ScreenMapper.fromRegion()

    private fun engine(mode: PressMode = PressMode.PINCH, zones: List<Zone> = emptyList()) =
        PointerEngine(PointerConfig(pressMode = mode), aspect).also { it.zones = ZoneSet(zones) }

    private fun run(engine: PointerEngine, frames: List<HandFrame>) = frames.map { engine.process(it) }

    private fun presses(outputs: List<PointerOutput>) = outputs.mapNotNull { it.event as? PointerEvent.Press }

    /** Where the cursor should rest for a pose with the wrist at (wx, wy). */
    private fun expectedCursor(pose: Pose, wx: Float, wy: Float): NormPoint {
        val (dx, dy) = SyntheticHand.indexTipOffset(pose)
        return mapper.map(NormPoint(wx + dx, wy + dy))
    }

    private fun assertNear(expected: NormPoint, actual: NormPoint?, tolerance: Float = 0.015f) {
        assertNotNull("cursor is hidden", actual)
        val d = expected.distanceTo(actual!!, aspect)
        assertTrue("expected $expected, got $actual (distance $d)", d <= tolerance)
    }

    // --- Cursor ---

    @Test
    fun cursorFollowsPointingFinger() {
        val out = run(engine(), SyntheticHand.Sequence().hold(Pose.POINTING, 0.5f, 0.7f, 600).build())
        assertNear(expectedCursor(Pose.POINTING, 0.5f, 0.7f), out.last().cursor)
    }

    @Test
    fun cursorMovesRightWhenHandMovesToUserRight() {
        val out = run(
            engine(),
            SyntheticHand.Sequence()
                .hold(Pose.POINTING, 0.4f, 0.7f, 300)
                .move(Pose.POINTING, 0.4f, 0.7f, 0.6f, 0.7f, 500)
                .hold(Pose.POINTING, 0.6f, 0.7f, 400)
                .build(),
        )
        val start = out[8].cursor!!
        val end = out.last().cursor!!
        assertTrue("cursor must move right: $start -> $end", end.x > start.x + 0.2f)
        assertNear(expectedCursor(Pose.POINTING, 0.6f, 0.7f), end)
    }

    @Test
    fun restingCursorDoesNotJitter() {
        // Landmark noise of +-0.003 frame units, amplified ~1.7x by the default mapping.
        val frames = SyntheticHand.Sequence(noise = 0.003f, seed = 3).hold(Pose.POINTING, 0.5f, 0.7f, 3000).build()
        val cursors = run(engine(), frames).drop(20).map { it.cursor!! }
        var maxStep = 0f
        var changes = 0
        cursors.zipWithNext { a, b ->
            val d = a.distanceTo(b, aspect)
            if (d > 0f) changes++
            maxStep = maxOf(maxStep, d)
        }
        val spread = cursors.maxOf { c -> cursors.maxOf { it.distanceTo(c, aspect) } }
        assertTrue("peak-to-peak $spread", spread < 0.012f)
        assertTrue("cursor moved in $changes of ${cursors.size} frames", changes < cursors.size / 5)
        assertTrue("max step $maxStep", maxStep < 0.012f)
    }

    @Test
    fun inactiveEngineShowsNothingAndPressesNothing() {
        val e = engine().apply { active = false }
        val out = run(
            e,
            SyntheticHand.Sequence().hold(Pose.POINTING, 0.5f, 0.7f, 500).hold(Pose.PINCH, 0.5f, 0.7f, 200).build(),
        )
        assertTrue(out.all { it.cursor == null && it.event == null })
    }

    @Test
    fun cursorHiddenWithoutPointingPose() {
        val fist = run(engine(), SyntheticHand.Sequence().hold(Pose.FIST, 0.5f, 0.7f, 500).build())
        assertTrue(fist.all { it.cursor == null })
        val gone = run(
            engine(),
            SyntheticHand.Sequence().hold(Pose.POINTING, 0.5f, 0.7f, 300).noHand(400).build(),
        )
        assertNull(gone.last().cursor)
        val twoFingers = run(
            engine(),
            SyntheticHand.Sequence().hold(Pose.POINTING, 0.5f, 0.7f, 300).hold(Pose.TWO_FINGERS, 0.5f, 0.7f, 100).build(),
        )
        assertNull(twoFingers.last().cursor)
    }

    // --- Pinch ---

    @Test
    fun pinchPressesWhereTheCursorWasBeforeThePinch() {
        val frames = SyntheticHand.Sequence()
            .hold(Pose.POINTING, 0.5f, 0.7f, 600)
            .hold(Pose.PINCH, 0.5f, 0.7f, 200)
            .hold(Pose.POINTING, 0.5f, 0.7f, 300)
            .build()
        val out = run(engine(), frames)
        val presses = presses(out)
        assertEquals(1, presses.size)
        // The index tip jumps by ~0.13 screen heights in the pinch pose; the press must not.
        assertNear(expectedCursor(Pose.POINTING, 0.5f, 0.7f), presses[0].at)
        val duringPinch = out[(600 + 150) / 33].cursor
        assertNear(expectedCursor(Pose.POINTING, 0.5f, 0.7f), duringPinch)
    }

    @Test
    fun heldPinchPressesOnceAndAReleasedPinchPressesAgain() {
        val frames = SyntheticHand.Sequence()
            .hold(Pose.POINTING, 0.5f, 0.7f, 500)
            .hold(Pose.PINCH, 0.5f, 0.7f, 1500)
            .hold(Pose.POINTING, 0.5f, 0.7f, 500)
            .hold(Pose.PINCH, 0.5f, 0.7f, 200)
            .build()
        assertEquals(2, presses(run(engine(), frames)).size)
    }

    @Test
    fun closingTheHandIntoAFistDoesNotPress() {
        val frames = SyntheticHand.Sequence()
            .hold(Pose.POINTING, 0.5f, 0.7f, 500)
            .hold(Pose.FIST, 0.5f, 0.7f, 800)
            .build()
        assertEquals(emptyList<PointerEvent.Press>(), presses(run(engine(), frames)))
    }

    @Test
    fun pinchWithoutCursorDoesNothing() {
        val frames = SyntheticHand.Sequence().hold(Pose.PINCH, 0.5f, 0.7f, 800).build()
        assertEquals(emptyList<PointerEvent.Press>(), presses(run(engine(), frames)))
    }

    @Test
    fun pinchInZoneReportsTheZone() {
        val c = expectedCursor(Pose.POINTING, 0.5f, 0.7f)
        val zone = Zone(7, NormRect(c.x - 0.1f, c.y - 0.05f, c.x + 0.1f, c.y + 0.05f), ZoneAction.Tap)
        val frames = SyntheticHand.Sequence()
            .hold(Pose.POINTING, 0.5f, 0.7f, 500)
            .hold(Pose.PINCH, 0.5f, 0.7f, 200)
            .build()
        val out = run(engine(zones = listOf(zone)), frames)
        assertEquals(zone, presses(out).single().zone)
        assertEquals(zone, out[10].hoveredZone)
    }

    @Test
    fun pinchModeIgnoresDwell() {
        val c = expectedCursor(Pose.POINTING, 0.5f, 0.7f)
        val zone = Zone(1, NormRect(c.x - 0.1f, c.y - 0.05f, c.x + 0.1f, c.y + 0.05f), ZoneAction.Tap)
        val frames = SyntheticHand.Sequence().hold(Pose.POINTING, 0.5f, 0.7f, 2000).build()
        assertEquals(emptyList<PointerEvent.Press>(), presses(run(engine(zones = listOf(zone)), frames)))
    }

    // --- Dwell ---

    @Test
    fun dwellInZonePressesZoneCenterOnce() {
        val c = expectedCursor(Pose.POINTING, 0.5f, 0.7f)
        val zone = Zone(1, NormRect(c.x - 0.1f, c.y - 0.05f, c.x + 0.1f, c.y + 0.05f), ZoneAction.Tap)
        val frames = SyntheticHand.Sequence().hold(Pose.POINTING, 0.5f, 0.7f, 2500).build()
        val out = run(engine(PressMode.DWELL, listOf(zone)), frames)
        val press = presses(out).single()
        assertEquals(zone, press.zone)
        assertEquals(zone.rect.center, press.at)
        val pressFrame = out.indexOfFirst { it.event != null }
        assertTrue("pressed at frame $pressFrame", pressFrame * 33 in 500..800)
        assertTrue(out[pressFrame - 6].dwellProgress in 0.3f..0.9f)
    }

    @Test
    fun dwellOutsideZonesAndPinchInDwellModeDoNothing() {
        val zone = Zone(1, NormRect(0f, 0.9f, 0.2f, 1f), ZoneAction.Tap)
        val frames = SyntheticHand.Sequence()
            .hold(Pose.POINTING, 0.5f, 0.7f, 1500)
            .hold(Pose.PINCH, 0.5f, 0.7f, 300)
            .build()
        assertEquals(emptyList<PointerEvent.Press>(), presses(run(engine(PressMode.DWELL, listOf(zone)), frames)))
    }

    // --- Scroll ---

    private fun scrolls(frames: List<HandFrame>) =
        run(engine(), frames).mapNotNull { (it.event as? PointerEvent.Scroll)?.direction }

    @Test
    fun twoFingerStrokesScroll() {
        // The hold after the stroke gives the scroll confirmation (ScrollConfig.confirmMs) its frames.
        val up = SyntheticHand.Sequence().move(Pose.TWO_FINGERS, 0.5f, 0.85f, 0.5f, 0.6f, 300)
            .hold(Pose.TWO_FINGERS, 0.5f, 0.6f, 200).build()
        val down = SyntheticHand.Sequence().move(Pose.TWO_FINGERS, 0.5f, 0.6f, 0.5f, 0.85f, 300)
            .hold(Pose.TWO_FINGERS, 0.5f, 0.85f, 200).build()
        assertEquals(listOf(VerticalDirection.UP), scrolls(up))
        assertEquals(listOf(VerticalDirection.DOWN), scrolls(down))
    }

    @Test
    fun returnStrokeIsSuppressedButRepeatedStrokesScrollAgain() {
        val back = SyntheticHand.Sequence()
            .move(Pose.TWO_FINGERS, 0.5f, 0.85f, 0.5f, 0.6f, 300)
            .move(Pose.TWO_FINGERS, 0.5f, 0.6f, 0.5f, 0.85f, 300)
            .build()
        assertEquals(listOf(VerticalDirection.UP), scrolls(back))
        val repeated = SyntheticHand.Sequence()
            .move(Pose.TWO_FINGERS, 0.5f, 0.85f, 0.5f, 0.6f, 300)
            .move(Pose.TWO_FINGERS, 0.5f, 0.6f, 0.5f, 0.85f, 1000) // slow return: not a stroke
            .move(Pose.TWO_FINGERS, 0.5f, 0.85f, 0.5f, 0.6f, 300)
            .hold(Pose.TWO_FINGERS, 0.5f, 0.6f, 200)
            .build()
        assertEquals(listOf(VerticalDirection.UP, VerticalDirection.UP), scrolls(repeated))
    }

    @Test
    fun horizontalTwoFingerMotionDoesNotScroll() {
        val frames = SyntheticHand.Sequence().move(Pose.TWO_FINGERS, 0.3f, 0.7f, 0.7f, 0.72f, 300).build()
        assertEquals(emptyList<VerticalDirection>(), scrolls(frames))
    }
}
