package com.gesturecontrol.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityWindowInfo
import androidx.annotation.RequiresApi
import java.util.concurrent.Executor

/**
 * Detects whether the active app window is protected with FLAG_SECURE (Android 14+).
 *
 * There is no public API to read another app's window flags, but a window screenshot of a secure
 * window fails with ERROR_TAKE_SCREENSHOT_SECURE_WINDOW. On success the image buffer is closed
 * immediately without being read: no pixels are ever looked at or stored.
 */
class SecureWindowProbe(private val service: AccessibilityService, private val executor: Executor) {
    private var lastProbeMs = 0L
    private var lastWindowId = -1

    /** Last known result for the active window: true = secure, null = unknown. */
    @Volatile
    var isSecure: Boolean? = null
        private set

    /** Probes at most once per [MIN_INTERVAL_MS] (the system rate-limits screenshots). */
    fun probe(onResult: (Boolean) -> Unit = {}) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val now = SystemClock.uptimeMillis()
        val window = activeAppWindow() ?: return
        if (window.id == lastWindowId && now - lastProbeMs < MIN_INTERVAL_MS) return
        if (window.id != lastWindowId) isSecure = null
        lastProbeMs = now
        lastWindowId = window.id
        probeApi34(window.id, onResult)
    }

    fun invalidate() {
        isSecure = null
        lastWindowId = -1
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun probeApi34(windowId: Int, onResult: (Boolean) -> Unit) {
        service.takeScreenshotOfWindow(
            windowId,
            executor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    result.hardwareBuffer.close()
                    isSecure = false
                    onResult(false)
                }

                override fun onFailure(errorCode: Int) {
                    if (errorCode == AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW) {
                        isSecure = true
                        onResult(true)
                    }
                    // Other errors (rate limit, window gone) leave the state unknown.
                }
            },
        )
    }

    private fun activeAppWindow(): AccessibilityWindowInfo? = runCatching {
        service.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isActive }
            ?: service.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && it.isFocused }
    }.getOrNull()

    private companion object {
        const val MIN_INTERVAL_MS = 1500L
    }
}
