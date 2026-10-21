package com.gesturecontrol.ui.zones

import androidx.annotation.StringRes
import com.gesturecontrol.R
import com.gesturecontrol.gestures.mapping.GlobalActionKind
import com.gesturecontrol.gestures.mapping.MediaKind
import com.gesturecontrol.gestures.mapping.VerticalDirection
import com.gesturecontrol.gestures.mapping.ZoneAction

/** All actions a zone can have, in the order shown in the picker. */
val ZONE_ACTIONS: List<ZoneAction> = listOf(
    ZoneAction.Tap,
    ZoneAction.LongPress,
    ZoneAction.Swipe(VerticalDirection.UP),
    ZoneAction.Swipe(VerticalDirection.DOWN),
    ZoneAction.Global(GlobalActionKind.BACK),
    ZoneAction.Global(GlobalActionKind.HOME),
    ZoneAction.Global(GlobalActionKind.RECENTS),
    ZoneAction.Global(GlobalActionKind.NOTIFICATIONS),
    ZoneAction.Global(GlobalActionKind.SCREENSHOT),
    ZoneAction.Media(MediaKind.NEXT),
    ZoneAction.Media(MediaKind.PREVIOUS),
    ZoneAction.Media(MediaKind.PLAY_PAUSE),
)

@StringRes
fun ZoneAction.labelRes(): Int = when (this) {
    ZoneAction.Tap -> R.string.action_tap
    ZoneAction.LongPress -> R.string.action_long_press
    is ZoneAction.Swipe -> if (direction == VerticalDirection.UP) R.string.action_swipe_up else R.string.action_swipe_down
    is ZoneAction.Global -> when (kind) {
        GlobalActionKind.BACK -> R.string.action_back
        GlobalActionKind.HOME -> R.string.action_home
        GlobalActionKind.RECENTS -> R.string.action_recents
        GlobalActionKind.NOTIFICATIONS -> R.string.action_notifications
        GlobalActionKind.SCREENSHOT -> R.string.action_screenshot
    }
    is ZoneAction.Media -> when (kind) {
        MediaKind.NEXT -> R.string.action_media_next
        MediaKind.PREVIOUS -> R.string.action_media_previous
        MediaKind.PLAY_PAUSE -> R.string.action_media_play_pause
    }
}
