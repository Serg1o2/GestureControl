package com.gesturecontrol.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Holds the package of the app currently on screen, as reported by [ForegroundAppService]. */
@Singleton
class ForegroundAppTracker @Inject constructor() {
    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

    private val _connected = MutableStateFlow(false)

    /** Whether the accessibility service is currently running. */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    internal fun onForegroundPackage(packageName: String) {
        _foregroundPackage.value = packageName
    }

    internal fun onConnectionChanged(connected: Boolean) {
        _connected.value = connected
        if (!connected) _foregroundPackage.value = null
    }

    companion object {
        /** Whether the user has enabled our service in the system accessibility settings. */
        fun isServiceEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val ours = ComponentName(context, ForegroundAppService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
        }
    }
}
