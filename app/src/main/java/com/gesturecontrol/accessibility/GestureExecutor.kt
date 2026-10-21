package com.gesturecontrol.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.KeyguardManager
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import com.gesturecontrol.gestures.mapping.GlobalActionKind
import com.gesturecontrol.gestures.mapping.NormPoint
import com.gesturecontrol.gestures.mapping.TouchCommand

/** Performs touch commands through [AccessibilityService.dispatchGesture] and global actions. */
class GestureExecutor(
    private val service: AccessibilityService,
    private val screenSize: () -> Size,
    private val onHint: (Hint) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())

    fun execute(command: TouchCommand) {
        if (service.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            onHint(Hint.SCREEN_LOCKED)
            return
        }
        when (command) {
            is TouchCommand.Tap -> dispatch(strokeAt(command.at, command.durationMs))
            is TouchCommand.LongPress -> dispatch(strokeAt(command.at, command.durationMs))
            is TouchCommand.Swipe -> dispatch(swipe(command))
            is TouchCommand.Global -> {
                if (!service.performGlobalAction(globalActionCode(command.kind))) onHint(Hint.ACTION_UNAVAILABLE)
            }
            // Media keys are sent by the gesture service itself and never reach here.
            is TouchCommand.Media -> Unit
        }
    }

    private fun dispatch(stroke: GestureDescription.StrokeDescription) {
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        val callback = object : AccessibilityService.GestureResultCallback() {
            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Gesture cancelled")
                onHint(Hint.GESTURE_CANCELLED)
            }
        }
        val accepted = try {
            service.dispatchGesture(gesture, callback, handler)
        } catch (e: RuntimeException) {
            Log.e(TAG, "dispatchGesture failed", e)
            false
        }
        if (!accepted) onHint(Hint.GESTURE_REJECTED)
    }

    private fun strokeAt(p: NormPoint, durationMs: Long): GestureDescription.StrokeDescription {
        val (x, y) = toPx(p)
        val path = Path().apply { moveTo(x, y) }
        return GestureDescription.StrokeDescription(path, 0, durationMs)
    }

    private fun swipe(command: TouchCommand.Swipe): GestureDescription.StrokeDescription {
        val (x0, y0) = toPx(command.from)
        val (x1, y1) = toPx(command.to)
        val path = Path().apply {
            moveTo(x0, y0)
            lineTo(x1, y1)
        }
        return GestureDescription.StrokeDescription(path, 0, command.durationMs)
    }

    /** Gesture coordinates must lie inside the screen. */
    private fun toPx(p: NormPoint): Pair<Float, Float> {
        val size = screenSize()
        val c = p.clamped()
        return c.x * (size.width - 1) to c.y * (size.height - 1)
    }

    private fun globalActionCode(kind: GlobalActionKind) = when (kind) {
        GlobalActionKind.BACK -> AccessibilityService.GLOBAL_ACTION_BACK
        GlobalActionKind.HOME -> AccessibilityService.GLOBAL_ACTION_HOME
        GlobalActionKind.RECENTS -> AccessibilityService.GLOBAL_ACTION_RECENTS
        GlobalActionKind.NOTIFICATIONS -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
        // Available since Android 9; on older versions performGlobalAction returns false -> hint.
        GlobalActionKind.SCREENSHOT -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
    }

    private companion object {
        const val TAG = "GestureExecutor"
    }
}
