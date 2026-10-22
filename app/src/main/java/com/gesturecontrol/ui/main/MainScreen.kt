package com.gesturecontrol.ui.main

import android.Manifest
import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gesturecontrol.R
import com.gesturecontrol.accessibility.ForegroundAppTracker
import com.gesturecontrol.service.GestureNotifications
import com.gesturecontrol.service.GestureService
import com.gesturecontrol.service.ServiceStatus
import com.gesturecontrol.ui.AppLanguage
import com.gesturecontrol.ui.components.IconBadge
import com.gesturecontrol.ui.components.NavRow
import com.gesturecontrol.ui.components.SectionCard
import com.gesturecontrol.ui.components.StatusChip
import com.gesturecontrol.ui.components.SwitchRow
import com.gesturecontrol.ui.rememberPermissionState
import com.gesturecontrol.ui.theme.LocalBrand
import com.gesturecontrol.vision.ClassifierKind
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    onOpenApps: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenAccessibilityDisclosure: () -> Unit,
    onOpenZones: () -> Unit,
    onOpenCursorSettings: () -> Unit,
    onOpenCustom: () -> Unit,
    onOpenSensitivity: () -> Unit,
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
    val ready = camera.granted && accessibilityEnabled
    MainContent(
        state = state,
        statusText = GestureNotifications.statusText(context, state.status),
        cameraGranted = camera.granted,
        notificationsGranted = notifications?.granted,
        accessibilityEnabled = accessibilityEnabled,
        snackbar = snackbar,
        onToggle = { enable ->
            if (!enable) {
                GestureService.stop(context)
            } else if (ready) {
                if (notifications?.granted == false) notifications.request()
                GestureService.start(context)
            } else {
                scope.launch { snackbar.showSnackbar(requirementsMessage) }
            }
        },
        onRequestCamera = camera::request,
        onRequestNotifications = { notifications?.request() },
        onOpenAccessibilityDisclosure = onOpenAccessibilityDisclosure,
        onOpenApps = onOpenApps,
        onOpenZones = onOpenZones,
        onOpenCursorSettings = onOpenCursorSettings,
        onOpenCustom = onOpenCustom,
        onOpenDebug = onOpenDebug,
        onOpenSensitivity = onOpenSensitivity,
        onPowerSaveChange = viewModel::setPowerSave,
        onUseModelChange = viewModel::setUseModel,
    )
}

/** The main screen without the view model and permission plumbing (also rendered by screenshot tests). */
@Composable
fun MainContent(
    state: MainUiState,
    statusText: String,
    cameraGranted: Boolean,
    notificationsGranted: Boolean?,
    accessibilityEnabled: Boolean,
    snackbar: SnackbarHostState,
    onToggle: (Boolean) -> Unit,
    onRequestCamera: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenAccessibilityDisclosure: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenZones: () -> Unit,
    onOpenCursorSettings: () -> Unit,
    onOpenCustom: () -> Unit,
    onOpenDebug: () -> Unit,
    onOpenSensitivity: () -> Unit,
    onPowerSaveChange: (Boolean) -> Unit,
    onUseModelChange: (Boolean) -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    val ready = cameraGranted && accessibilityEnabled
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header()

            // Main switch. Starting here (a visible screen) satisfies the Android 14+ camera FGS rule.
            val running = state.status != ServiceStatus.Off
            HeroCard(running = running, status = if (running) statusText else null, ready = ready, onToggle = onToggle)

            val steps = listOfNotNull(cameraGranted, notificationsGranted, accessibilityEnabled)
            val done = steps.count { it }
            val allDone = done == steps.size
            var setupExpanded by rememberSaveable(allDone) { mutableStateOf(!allDone) }
            SectionCard(
                title = stringResource(R.string.main_setup_section),
                icon = Icons.Rounded.Done,
                trailing = { StatusChip(stringResource(R.string.setup_progress, done, steps.size), allDone) },
            ) {
                if (allDone) {
                    // Everything granted: one line instead of the checklist, which can still be opened.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = LocalBrand.current.ok)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.setup_all_done), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = { setupExpanded = !setupExpanded }) {
                            Text(stringResource(if (setupExpanded) R.string.action_hide else R.string.action_show))
                        }
                    }
                }
                AnimatedVisibility(visible = setupExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        LinearProgressIndicator(
                            progress = { done.toFloat() / steps.size },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                            strokeCap = StrokeCap.Round,
                            drawStopIndicator = {},
                        )
                        SetupRow(
                            icon = Icons.Rounded.Face,
                            title = stringResource(R.string.perm_camera),
                            description = stringResource(R.string.perm_camera_desc),
                            done = cameraGranted,
                            doneLabel = stringResource(R.string.status_granted),
                            actionLabel = stringResource(R.string.action_grant),
                            onAction = onRequestCamera,
                        )
                        if (notificationsGranted != null) {
                            SetupRow(
                                icon = Icons.Rounded.Notifications,
                                title = stringResource(R.string.perm_notifications),
                                description = stringResource(R.string.perm_notifications_desc),
                                done = notificationsGranted,
                                doneLabel = stringResource(R.string.status_granted),
                                actionLabel = stringResource(R.string.action_grant),
                                onAction = onRequestNotifications,
                            )
                        }
                        SetupRow(
                            icon = Icons.Rounded.Person,
                            title = stringResource(R.string.perm_accessibility),
                            description = stringResource(R.string.perm_accessibility_desc) +
                                if (accessibilityEnabled) "" else "\n" + stringResource(R.string.perm_accessibility_restricted_hint),
                            done = accessibilityEnabled,
                            doneLabel = stringResource(R.string.status_enabled),
                            actionLabel = stringResource(R.string.action_open_settings),
                            onAction = onOpenAccessibilityDisclosure,
                        )
                    }
                }
            }

            SectionCard(
                title = stringResource(R.string.main_apps_section),
                icon = Icons.AutoMirrored.Rounded.List,
                trailing = {
                    if (state.selectedCount > 0) StatusChip(stringResource(R.string.main_apps_selected, state.selectedCount), ok = true)
                },
            ) {
                Text(
                    stringResource(if (state.selectedCount > 0) R.string.main_apps_desc else R.string.main_apps_none_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onOpenApps, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.main_apps_choose))
                }
            }

            SectionCard(title = stringResource(R.string.main_gestures_section), icon = Icons.Rounded.ThumbUp) {
                val on = state.toggles
                val tiles = listOf(
                    GestureTileData("👉", R.string.gesture_swipe_right_name, R.string.action_next_track, on.swipe),
                    GestureTileData("👈", R.string.gesture_swipe_left_name, R.string.action_prev_track, on.swipe),
                    GestureTileData("✋", R.string.gesture_palm_name, R.string.action_play_pause, on.palmHold),
                    GestureTileData("✊", R.string.gesture_fist_open_name, R.string.action_play_pause, on.fistOpen),
                    GestureTileData("☝️", R.string.gesture_finger_name, R.string.action_screen_swipe, on.finger),
                    GestureTileData("🔊", R.string.gesture_volume_name, R.string.action_volume, on.volume),
                )
                tiles.forEach {
                    GestureTile(it, Modifier.fillMaxWidth())
                }
                Text(
                    stringResource(R.string.gestures_finger_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard(title = stringResource(R.string.main_cursor_section), icon = Icons.Rounded.Place) {
                Text(
                    stringResource(R.string.main_cursor_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NavRow(stringResource(R.string.main_zones_open), stringResource(R.string.main_zones_desc), onOpenZones, icon = Icons.Rounded.Edit)
                NavRow(
                    stringResource(R.string.cursor_settings_title),
                    stringResource(R.string.cursor_settings_desc),
                    onOpenCursorSettings,
                    emoji = "🎯",
                )
            }

            SectionCard(title = stringResource(R.string.settings_section), icon = Icons.Rounded.Settings) {
                val off = state.toggles.disabledCount
                val changed = state.sensitivityChanged
                NavRow(
                    stringResource(R.string.sens_title),
                    if (off == 0 && changed == 0) {
                        stringResource(R.string.sens_open_desc_default)
                    } else {
                        listOfNotNull(
                            if (off > 0) stringResource(R.string.sens_open_disabled, off) else null,
                            if (changed > 0) stringResource(R.string.sens_open_changed, changed) else null,
                        ).joinToString(" · ")
                    },
                    onOpenSensitivity,
                    emoji = "🎚️",
                )
                SwitchRow(
                    title = stringResource(R.string.power_save_title),
                    description = stringResource(R.string.power_save_desc),
                    checked = state.powerSave,
                    onCheckedChange = onPowerSaveChange,
                    emoji = "🔋",
                )
                val status = state.classifierStatus
                SwitchRow(
                    title = stringResource(R.string.classifier_title),
                    description = when {
                        state.classifier == ClassifierKind.RULES -> stringResource(R.string.classifier_rules)
                        status == null -> stringResource(R.string.classifier_model_pending)
                        status.active == ClassifierKind.MODEL -> stringResource(R.string.classifier_model_active, status.modelVersion.orEmpty())
                        else -> stringResource(R.string.classifier_model_fallback)
                    },
                    checked = state.classifier == ClassifierKind.MODEL,
                    onCheckedChange = onUseModelChange,
                    emoji = "🧠",
                )
                LanguageRow()
            }

            SectionCard(title = stringResource(R.string.tools_section), icon = Icons.Rounded.Build) {
                NavRow(stringResource(R.string.main_custom_open), stringResource(R.string.main_custom_desc), onOpenCustom, icon = Icons.Rounded.Star)
                NavRow(stringResource(R.string.main_debug_open), stringResource(R.string.main_debug_desc), onOpenDebug, icon = Icons.Rounded.Build)
            }

            PrivacyFooter()
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        IconBadge(emoji = "🖐", size = 48)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.main_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Gradient status card with the main switch; the dot pulses while gestures are active. */
@Composable
private fun HeroCard(running: Boolean, status: String?, ready: Boolean, onToggle: (Boolean) -> Unit) {
    val brand = LocalBrand.current
    val pulse = rememberInfiniteTransition(label = "pulse")
    val dotAlpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "dot",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (running) brand.heroOn else brand.heroOff)
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (running) "✋" else "💤", style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(if (running) R.string.main_hero_on_title else R.string.main_hero_off_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = brand.onHero,
                    )
                    Text(
                        stringResource(R.string.main_toggle_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = brand.onHero.copy(alpha = 0.8f),
                    )
                }
                Switch(
                    checked = running,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color.White.copy(alpha = 0.35f),
                        checkedBorderColor = Color.Transparent,
                        uncheckedThumbColor = Color.White.copy(alpha = 0.9f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
                        uncheckedBorderColor = Color.White.copy(alpha = 0.5f),
                    ),
                )
            }
            Surface(color = Color.White.copy(alpha = 0.14f), shape = MaterialTheme.shapes.small, contentColor = brand.onHero) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .alpha(if (running) dotAlpha else 1f)
                            .clip(CircleShape)
                            .background(if (running) Color(0xFF7CFFB2) else if (ready) Color.White.copy(alpha = 0.6f) else brand.warn),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        status ?: stringResource(if (ready) R.string.main_hero_hint_ready else R.string.main_hero_hint_missing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SetupRow(
    icon: ImageVector,
    title: String,
    description: String,
    done: Boolean,
    doneLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val brand = LocalBrand.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                IconBadge(
                    icon = icon,
                    container = if (done) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    content = if (done) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                )
                Icon(
                    if (done) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = if (done) brand.ok else brand.warn,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (done) {
                Spacer(Modifier.width(8.dp))
                Text(doneLabel, style = MaterialTheme.typography.labelLarge, color = brand.ok)
            }
        }
        // The action goes under the text, so long descriptions keep the full width.
        if (!done) {
            FilledTonalButton(onClick = onAction, modifier = Modifier.align(Alignment.End).padding(top = 4.dp)) {
                Text(actionLabel)
            }
        }
    }
}

private data class GestureTileData(val emoji: String, val name: Int, val action: Int, val on: Boolean = true)

@Composable
private fun GestureTile(tile: GestureTileData, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.small) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp).alpha(if (tile.on) 1f else 0.45f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(emoji = tile.emoji, container = LocalBrand.current.card)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(tile.name), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(tile.action),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!tile.on) StatusChip(stringResource(R.string.gesture_off_chip), ok = false)
        }
    }
}

/** Interface language: Russian (default) or English; switching recreates the activity. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LanguageRow() {
    val context = LocalContext.current
    val current = remember { AppLanguage.current(context) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                emoji = "🌐",
                container = MaterialTheme.colorScheme.surfaceContainerHigh,
                content = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.language_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val options = listOf(
            AppLanguage.Lang.RU to stringResource(R.string.lang_ru),
            AppLanguage.Lang.EN to stringResource(R.string.lang_en),
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, (lang, label) ->
                SegmentedButton(
                    selected = current == lang,
                    onClick = { (context as? Activity)?.let { AppLanguage.set(it, lang) } },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) { Text(label) }
            }
        }
    }
}

@Composable
private fun PrivacyFooter() {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.main_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
    Text(
        stringResource(R.string.version_label, version),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    )
}
