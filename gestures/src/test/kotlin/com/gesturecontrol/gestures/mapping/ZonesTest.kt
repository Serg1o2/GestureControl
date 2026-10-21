package com.gesturecontrol.gestures.mapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZonesTest {
    private val bottom = Zone(1, NormRect(0f, 0.8f, 1f, 1f), ZoneAction.Media(MediaKind.PLAY_PAUSE))
    private val button = Zone(2, NormRect(0.4f, 0.85f, 0.6f, 0.95f), ZoneAction.Tap)
    private val list = Zone(3, NormRect(0f, 0.2f, 1f, 0.7f), ZoneAction.Swipe(VerticalDirection.UP))
    private val zones = ZoneSet(listOf(bottom, button, list))

    @Test
    fun hitTestFindsZone() {
        assertEquals(list, zones.hitTest(NormPoint(0.5f, 0.5f)))
        assertEquals(bottom, zones.hitTest(NormPoint(0.1f, 0.9f)))
    }

    @Test
    fun laterZoneWinsOverlap() {
        assertEquals(button, zones.hitTest(NormPoint(0.5f, 0.9f)))
    }

    @Test
    fun edgesAreInsideAndGapsAreOutside() {
        assertEquals(list, zones.hitTest(NormPoint(1f, 0.7f)))
        assertNull(zones.hitTest(NormPoint(0.5f, 0.75f)))
        assertNull(ZoneSet.EMPTY.hitTest(NormPoint(0.5f, 0.5f)))
    }

    @Test
    fun pressOutsideZonesTapsAtCursor() {
        assertEquals(TouchCommand.Tap(NormPoint(0.3f, 0.75f)), CommandPlanner.forPress(NormPoint(0.3f, 0.75f), null))
    }

    @Test
    fun pressInZoneUsesZoneAction() {
        assertEquals(TouchCommand.Tap(NormPoint(0.5f, 0.9f)), CommandPlanner.forPress(NormPoint(0.45f, 0.86f), button))
        assertEquals(TouchCommand.Media(MediaKind.PLAY_PAUSE), CommandPlanner.forPress(NormPoint(0.1f, 0.9f), bottom))
        val long = Zone(4, NormRect(0f, 0f, 0.2f, 0.2f), ZoneAction.LongPress)
        assertEquals(TouchCommand.LongPress(NormPoint(0.1f, 0.1f)), CommandPlanner.forPress(NormPoint(0f, 0f), long))
        val back = Zone(5, NormRect(0f, 0f, 0.2f, 0.2f), ZoneAction.Global(GlobalActionKind.BACK))
        assertEquals(TouchCommand.Global(GlobalActionKind.BACK), CommandPlanner.forPress(NormPoint(0f, 0f), back))
    }

    @Test
    fun zoneSwipeStaysInsideZoneAndGoesUp() {
        val swipe = CommandPlanner.forPress(NormPoint(0.5f, 0.5f), list) as TouchCommand.Swipe
        assertTrue(swipe.from in list.rect && swipe.to in list.rect)
        assertTrue("finger moves up", swipe.to.y < swipe.from.y)
        assertEquals(swipe.from.x, swipe.to.x, 1e-6f)
    }

    @Test
    fun screenScrollDirections() {
        val up = CommandPlanner.forScroll(VerticalDirection.UP)
        val down = CommandPlanner.forScroll(VerticalDirection.DOWN)
        assertTrue(up.to.y < up.from.y)
        assertTrue(down.to.y > down.from.y)
        assertEquals(0.5f, up.from.x, 1e-6f)
    }

    @Test
    fun pressAtCursorIsClampedToScreen() {
        assertEquals(TouchCommand.Tap(NormPoint(1f, 0f)), CommandPlanner.forPress(NormPoint(1.2f, -0.1f), null))
    }
}
