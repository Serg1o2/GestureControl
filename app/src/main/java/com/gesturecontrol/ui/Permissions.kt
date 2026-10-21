package com.gesturecontrol.ui

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

class PermissionState(granted: Boolean) {
    var granted by mutableStateOf(granted)
        internal set
    internal var requester: () -> Unit = {}

    fun request() = requester()
}

/** Runtime permission state that is refreshed whenever the screen resumes. */
@Composable
fun rememberPermissionState(permission: String): PermissionState {
    val context = LocalContext.current
    val state = remember(permission) { PermissionState(context.hasPermission(permission)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.granted = granted
    }
    state.requester = { launcher.launch(permission) }
    LifecycleResumeEffect(permission) {
        state.granted = context.hasPermission(permission)
        onPauseOrDispose { }
    }
    return state
}
