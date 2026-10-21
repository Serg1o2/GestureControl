package com.gesturecontrol.gestures.mapping

enum class GlobalActionKind { BACK, HOME, RECENTS, NOTIFICATIONS }

enum class MediaKind { NEXT, PREVIOUS, PLAY_PAUSE }

/** Direction the finger moves on screen. UP scrolls content toward its end, like a touch swipe up. */
enum class VerticalDirection { UP, DOWN }

/** What happens when a zone is pressed. */
sealed interface ZoneAction {
    /** Tap at the zone center. */
    data object Tap : ZoneAction
    data object LongPress : ZoneAction

    /** Swipe inside the zone, e.g. to scroll a list in that area. */
    data class Swipe(val direction: VerticalDirection) : ZoneAction
    data class Global(val kind: GlobalActionKind) : ZoneAction
    data class Media(val kind: MediaKind) : ZoneAction
}

/** A screen zone of one app; coordinates are normalized to the screen. */
data class Zone(val id: Long, val rect: NormRect, val action: ZoneAction)

/** Zones of the current app in z-order: later zones are drawn on top and win overlaps. */
class ZoneSet(val zones: List<Zone>) {
    fun hitTest(p: NormPoint): Zone? = zones.lastOrNull { p in it.rect }

    companion object {
        val EMPTY = ZoneSet(emptyList())
    }
}

/** A concrete input to perform, in normalized screen coordinates. */
sealed interface TouchCommand {
    data class Tap(val at: NormPoint, val durationMs: Long = TAP_MS) : TouchCommand
    data class LongPress(val at: NormPoint, val durationMs: Long = LONG_PRESS_MS) : TouchCommand
    data class Swipe(val from: NormPoint, val to: NormPoint, val durationMs: Long = SWIPE_MS) : TouchCommand
    data class Global(val kind: GlobalActionKind) : TouchCommand
    data class Media(val kind: MediaKind) : TouchCommand

    companion object {
        const val TAP_MS = 60L
        const val LONG_PRESS_MS = 700L

        /** Slow enough to scroll by a controlled amount instead of flinging. */
        const val SWIPE_MS = 300L
    }
}

/** Turns pointer events into touch commands. */
object CommandPlanner {
    /** Fraction of the screen (or zone) height a scroll swipe travels. */
    private const val SWIPE_SPAN = 0.6f

    /** A press at [at]; outside zones it is a plain tap at the cursor. */
    fun forPress(at: NormPoint, zone: Zone?): TouchCommand {
        if (zone == null) return TouchCommand.Tap(at.clamped())
        val center = zone.rect.center
        return when (val action = zone.action) {
            ZoneAction.Tap -> TouchCommand.Tap(center)
            ZoneAction.LongPress -> TouchCommand.LongPress(center)
            is ZoneAction.Swipe -> swipeIn(zone.rect, action.direction)
            is ZoneAction.Global -> TouchCommand.Global(action.kind)
            is ZoneAction.Media -> TouchCommand.Media(action.kind)
        }
    }

    /** Two-finger scroll: a vertical swipe through the middle of the screen. */
    fun forScroll(direction: VerticalDirection): TouchCommand.Swipe = swipeIn(NormRect(0f, 0f, 1f, 1f), direction)

    private fun swipeIn(rect: NormRect, direction: VerticalDirection): TouchCommand.Swipe {
        val low = 0.5f + SWIPE_SPAN / 2
        val high = 0.5f - SWIPE_SPAN / 2
        return when (direction) {
            VerticalDirection.UP -> TouchCommand.Swipe(rect.pointAt(0.5f, low), rect.pointAt(0.5f, high))
            VerticalDirection.DOWN -> TouchCommand.Swipe(rect.pointAt(0.5f, high), rect.pointAt(0.5f, low))
        }
    }
}
