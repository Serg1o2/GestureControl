package com.gesturecontrol.ui.custom

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gesturecontrol.R
import com.gesturecontrol.gestures.custom.ActionSpec
import com.gesturecontrol.gestures.custom.Direction
import com.gesturecontrol.gestures.mapping.GlobalActionKind
import com.gesturecontrol.gestures.mapping.MediaKind
import com.gesturecontrol.ui.zones.labelRes
import com.gesturecontrol.gestures.mapping.ZoneAction

/** Actions without parameters, offered directly in the picker. */
val SIMPLE_ACTIONS: List<ActionSpec> = listOf(
    ActionSpec.Media(MediaKind.PLAY_PAUSE),
    ActionSpec.Media(MediaKind.NEXT),
    ActionSpec.Media(MediaKind.PREVIOUS),
    ActionSpec.TrackSwipe(toLeft = true),
    ActionSpec.TrackSwipe(toLeft = false),
    ActionSpec.Volume(Direction.UP),
    ActionSpec.Volume(Direction.DOWN),
    ActionSpec.Global(GlobalActionKind.BACK),
    ActionSpec.Global(GlobalActionKind.HOME),
    ActionSpec.Global(GlobalActionKind.RECENTS),
    ActionSpec.Global(GlobalActionKind.NOTIFICATIONS),
    ActionSpec.Screenshot,
    ActionSpec.Flashlight,
    ActionSpec.Brightness(Direction.UP),
    ActionSpec.Brightness(Direction.DOWN),
    ActionSpec.None,
)

@Composable
fun ActionSpec.label(appLabel: (String) -> String = { it }): String = when (this) {
    is ActionSpec.Media -> stringResource(ZoneAction.Media(kind).labelRes())
    is ActionSpec.Global -> stringResource(ZoneAction.Global(kind).labelRes())
    is ActionSpec.TrackSwipe -> stringResource(if (toLeft) R.string.spec_track_swipe_left else R.string.spec_track_swipe_right)
    is ActionSpec.Volume -> stringResource(if (direction == Direction.UP) R.string.spec_volume_up else R.string.spec_volume_down)
    ActionSpec.Screenshot -> stringResource(R.string.action_screenshot)
    ActionSpec.Flashlight -> stringResource(R.string.spec_flashlight)
    is ActionSpec.Brightness -> stringResource(if (direction == Direction.UP) R.string.spec_brightness_up else R.string.spec_brightness_down)
    is ActionSpec.LaunchApp -> stringResource(R.string.spec_launch, appLabel(packageName))
    is ActionSpec.TaskerIntent -> stringResource(R.string.spec_tasker, action)
    ActionSpec.None -> stringResource(R.string.spec_none)
}
