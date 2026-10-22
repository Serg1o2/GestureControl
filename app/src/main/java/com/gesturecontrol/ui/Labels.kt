package com.gesturecontrol.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gesturecontrol.R
import com.gesturecontrol.gestures.EngineState
import com.gesturecontrol.gestures.Gesture

@Composable
fun EngineState.label(): String = when (this) {
    EngineState.NoHand -> stringResource(R.string.state_no_hand)
    EngineState.Tracking -> stringResource(R.string.state_tracking)
    is EngineState.PalmHolding -> stringResource(R.string.state_palm_holding, (progress * 100).toInt())
    EngineState.PalmLatched -> stringResource(R.string.state_palm_latched)
    is EngineState.Cooldown -> stringResource(R.string.state_cooldown)
    is EngineState.VolumeSliding -> stringResource(R.string.state_volume, if (steps > 0) "+$steps" else "$steps")
}

@Composable
fun Gesture.label(): String = when (this) {
    Gesture.SWIPE_LEFT -> stringResource(R.string.gesture_swipe_left)
    Gesture.SWIPE_RIGHT -> stringResource(R.string.gesture_swipe_right)
    Gesture.OPEN_PALM_HOLD -> stringResource(R.string.gesture_open_palm)
    Gesture.FINGER_LEFT -> stringResource(R.string.gesture_finger_left)
    Gesture.FINGER_RIGHT -> stringResource(R.string.gesture_finger_right)
    Gesture.FIST_OPEN -> stringResource(R.string.gesture_fist_open)
    Gesture.VOLUME_UP -> stringResource(R.string.gesture_volume_up)
    Gesture.VOLUME_DOWN -> stringResource(R.string.gesture_volume_down)
}
