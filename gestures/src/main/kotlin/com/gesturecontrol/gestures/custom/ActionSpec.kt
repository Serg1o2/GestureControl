package com.gesturecontrol.gestures.custom

import com.gesturecontrol.gestures.mapping.GlobalActionKind
import com.gesturecontrol.gestures.mapping.MediaKind

enum class Direction { UP, DOWN }

/** Action library for custom gestures. Encoded as "TYPE:param" for storage and JSON export. */
sealed interface ActionSpec {
    data class Media(val kind: MediaKind) : ActionSpec
    data class Volume(val direction: Direction) : ActionSpec
    data class Global(val kind: GlobalActionKind) : ActionSpec

    /** Horizontal swipe across the player artwork (true = to the left). */
    data class TrackSwipe(val toLeft: Boolean) : ActionSpec
    data class LaunchApp(val packageName: String) : ActionSpec
    data object Flashlight : ActionSpec
    data object Screenshot : ActionSpec
    data class Brightness(val direction: Direction) : ActionSpec

    /** Broadcast intent with this action, e.g. for a Tasker "Intent Received" profile. */
    data class TaskerIntent(val action: String) : ActionSpec
    data object None : ActionSpec

    companion object {
        fun encode(spec: ActionSpec): String = when (spec) {
            is Media -> "MEDIA:${spec.kind}"
            is Volume -> "VOLUME:${spec.direction}"
            is Global -> "GLOBAL:${spec.kind}"
            is TrackSwipe -> "TRACK_SWIPE:${if (spec.toLeft) "LEFT" else "RIGHT"}"
            is LaunchApp -> "LAUNCH:${spec.packageName}"
            Flashlight -> "FLASHLIGHT:"
            Screenshot -> "SCREENSHOT:"
            is Brightness -> "BRIGHTNESS:${spec.direction}"
            is TaskerIntent -> "TASKER:${spec.action}"
            None -> "NONE:"
        }

        /** Unknown or malformed values decode to [None] instead of failing. */
        fun decode(text: String): ActionSpec = runCatching {
            val type = text.substringBefore(':')
            val param = text.substringAfter(':', "")
            when (type) {
                "MEDIA" -> Media(MediaKind.valueOf(param))
                "VOLUME" -> Volume(Direction.valueOf(param))
                "GLOBAL" -> Global(GlobalActionKind.valueOf(param))
                "TRACK_SWIPE" -> TrackSwipe(param == "LEFT")
                "LAUNCH" -> LaunchApp(param.also { require(it.isNotBlank()) })
                "FLASHLIGHT" -> Flashlight
                "SCREENSHOT" -> Screenshot
                "BRIGHTNESS" -> Brightness(Direction.valueOf(param))
                "TASKER" -> TaskerIntent(param.also { require(it.isNotBlank()) })
                else -> None
            }
        }.getOrDefault(None)
    }
}
