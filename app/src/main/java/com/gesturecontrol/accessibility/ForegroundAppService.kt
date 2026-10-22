package com.gesturecontrol.accessibility

import android.content.Context
import com.gesturecontrol.ui.AppLanguage
import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.SystemClock
import android.util.Size
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import androidx.core.content.ContextCompat
import com.gesturecontrol.R
import com.gesturecontrol.gestures.mapping.TouchCommand
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Accessibility service:
 * - detects the app on screen from window state changes (package and class names only);
 * - shows the cursor overlay and performs taps / swipes / global actions requested by the gesture service;
 * - checks whether the active window is secure (Android 14+) to warn that a tap may not work.
 * It never reads window content.
 */
@AndroidEntryPoint
class ForegroundAppService : AccessibilityService() {
    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(AppLanguage.wrap(newBase))


    @Inject
    lateinit var tracker: ForegroundAppTracker

    @Inject
    lateinit var bridge: PointerBridge

    /** Cache of "is this class an activity" lookups. */
    private val activityCache = HashMap<String, Boolean>()

    private var scope: CoroutineScope? = null
    private var overlay: CursorOverlay? = null
    private var executor: GestureExecutor? = null
    private var secureProbe: SecureWindowProbe? = null
    private val lastHintAt = HashMap<Hint, Long>()

    override fun onServiceConnected() {
        tracker.onConnectionChanged(true)
        val cursorOverlay = CursorOverlay(this, ::screenSize)
        overlay = cursorOverlay
        executor = GestureExecutor(this, ::screenSize, ::showHint)
        secureProbe = SecureWindowProbe(this, ContextCompat.getMainExecutor(this))
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).apply {
            launch { bridge.overlay.collect { cursorOverlay.update(it) } }
            launch { bridge.commands.collect { executeCommand(it) } }
        }
    }

    private fun executeCommand(command: TouchCommand) {
        val probe = secureProbe
        if (probe?.isSecure == true) showHint(Hint.SECURE_WINDOW)
        // The gesture is attempted anyway: FLAG_SECURE blocks screenshots, not always input.
        executor?.execute(command)
        probe?.probe { secure -> if (secure) showHint(Hint.SECURE_WINDOW) }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return
        // Dialogs, toasts, keyboards and the notification shade also fire this event;
        // only real activities change the foreground app.
        if (isActivity(packageName, className)) {
            tracker.onForegroundPackage(packageName)
            secureProbe?.invalidate()
        }
    }

    /**
     * Packages that are not visible to us (system UI, keyboards) throw NameNotFoundException
     * and are ignored. Launcher and home apps are visible via `<queries>` in the manifest.
     */
    private fun isActivity(packageName: String, className: String): Boolean =
        activityCache.getOrPut("$packageName/$className") {
            try {
                packageManager.getActivityInfo(ComponentName(packageName, className), 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

    /** Same hint at most once per [HINT_REPEAT_MS]. */
    private fun showHint(hint: Hint) {
        val now = SystemClock.uptimeMillis()
        if (now - (lastHintAt[hint] ?: Long.MIN_VALUE / 2) < HINT_REPEAT_MS) return
        lastHintAt[hint] = now
        val text = getString(
            when (hint) {
                Hint.GESTURE_CANCELLED -> R.string.hint_gesture_cancelled
                Hint.GESTURE_REJECTED -> R.string.hint_gesture_rejected
                Hint.SECURE_WINDOW -> R.string.hint_secure_window
                Hint.ACTION_UNAVAILABLE -> R.string.hint_action_unavailable
                Hint.SCREEN_LOCKED -> R.string.hint_screen_locked
            },
        )
        overlay?.showHint(text)
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Size {
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val p = Point()
        display.getRealSize(p)
        return Size(p.x, p.y)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        scope?.cancel()
        overlay?.destroy()
        tracker.onConnectionChanged(false)
        super.onDestroy()
    }

    private companion object {
        const val HINT_REPEAT_MS = 3000L
    }
}
