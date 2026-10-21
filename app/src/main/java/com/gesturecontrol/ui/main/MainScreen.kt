package com.gesturecontrol.ui.main

import android.Manifest
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gesturecontrol.R
import com.gesturecontrol.accessibility.ForegroundAppTracker
import com.gesturecontrol.service.GestureNotifications
import com.gesturecontrol.service.GestureService
import com.gesturecontrol.service.ServiceStatus
import com.gesturecontrol.ui.rememberPermissionState
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    onOpenApps: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAccessibilityDisclosure: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val camera = rememberPermissionState(Manifest.permission.CAMERA)
    val notifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }
    var accessibilityEnabled by remember { mutableStateOf(ForegroundAppTracker.isServiceEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        accessibilityEnabled = ForegroundAppTracker.isServiceEnabled(context)
        onPauseOrDispose { }
    }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val requirementsMessage = stringResource(R.string.main_requirements_missing)

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)

            // Main switch. Starting here (a visible screen) satisfies the Android 14+ camera FGS rule.
            Card(Modifier.fillMaxWidth()) {
                val running = state.status != ServiceStatus.Off
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.main_toggle_label), style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (running) GestureNotifications.statusText(context, state.status) else stringResource(R.string.main_toggle_off),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Switch(
                        checked = running,
                        onCheckedChange = { enable ->
                            if (!enable) {
                                GestureService.stop(context)
                            } else if (camera.granted && accessibilityEnabled) {
                                if (notifications?.granted == false) notifications.request()
                                GestureService.start(context)
                            } else {
                                scope.launch { snackbar.showSnackbar(requirementsMessage) }
                            }
                        },
                    )
                }
            }

            SectionTitle(stringResource(R.string.main_setup_section))
            SetupRow(
                title = stringResource(R.string.perm_camera),
                description = stringResource(R.string.perm_camera_desc),
                done = camera.granted,
                doneLabel = stringResource(R.string.status_granted),
                actionLabel = stringResource(R.string.action_grant),
                onAction = camera::request,
            )
            if (notifications != null) {
                SetupRow(
                    title = stringResource(R.string.perm_notifications),
                    description = stringResource(R.string.perm_notifications_desc),
                    done = notifications.granted,
                    doneLabel = stringResource(R.string.status_granted),
                    actionLabel = stringResource(R.string.action_grant),
                    onAction = notifications::request,
                )
            }
            SetupRow(
                title = stringResource(R.string.perm_accessibility),
                description = stringResource(R.string.perm_accessibility_desc) +
                    if (accessibilityEnabled) "" else "\n" + stringResource(R.string.perm_accessibility_restricted_hint),
                done = accessibilityEnabled,
                doneLabel = stringResource(R.string.status_enabled),
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = onOpenAccessibilityDisclosure,
            )

            SectionTitle(stringResource(R.string.main_apps_section))
            Text(
                if (state.selectedCount > 0) {
                    stringResource(R.string.main_apps_selected, state.selectedCount)
                } else {
                    stringResource(R.string.main_apps_none_hint)
                },
            )
            OutlinedButton(onClick = onOpenApps) { Text(stringResource(R.string.main_apps_choose)) }

            SectionTitle(stringResource(R.string.main_gestures_section))
            Text(stringResource(R.string.gesture_swipe_right_desc))
            Text(stringResource(R.string.gesture_swipe_left_desc))
            Text(stringResource(R.string.gesture_palm_desc))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.power_save_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.power_save_desc), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = state.powerSave, onCheckedChange = viewModel::setPowerSave)
            }

            OutlinedButton(onClick = onOpenDebug) { Text(stringResource(R.string.main_debug_open)) }

            Text(
                stringResource(R.string.main_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun SetupRow(
    title: String,
    description: String,
    done: Boolean,
    doneLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.width(12.dp))
        if (done) {
            Text(doneLabel, color = MaterialTheme.colorScheme.primary)
        } else {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}
