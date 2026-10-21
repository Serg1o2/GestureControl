package com.gesturecontrol.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Detects the app on screen from window state changes. Reads only package and class names,
 * never window content.
 */
@AndroidEntryPoint
class ForegroundAppService : AccessibilityService() {

    @Inject
    lateinit var tracker: ForegroundAppTracker

    /** Cache of "is this class an activity" lookups. */
    private val activityCache = HashMap<String, Boolean>()

    override fun onServiceConnected() {
        tracker.onConnectionChanged(true)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: return
        // Dialogs, toasts, keyboards and the notification shade also fire this event;
        // only real activities change the foreground app.
        if (isActivity(packageName, className)) tracker.onForegroundPackage(packageName)
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

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        tracker.onConnectionChanged(false)
        super.onDestroy()
    }
}
