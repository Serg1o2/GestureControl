package com.gesturecontrol.ui.custom

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gesturecontrol.R
import com.gesturecontrol.gestures.custom.ActionSpec
import com.gesturecontrol.ui.label
import com.gesturecontrol.ui.rememberPermissionState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GestureEditorScreen(onDone: () -> Unit, viewModel: GestureEditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val camera = rememberPermissionState(Manifest.permission.CAMERA)
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.custom_editor_title)) },
                navigationIcon = { TextButton(onClick = onDone) { Text(stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        if (!camera.granted) {
            LaunchedEffect(Unit) { camera.request() }
            Text(stringResource(R.string.debug_no_camera_permission), Modifier.padding(padding).padding(16.dp))
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CameraPreview(viewModel, state)
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.custom_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            RecordingSection(viewModel, state)
            state.quality?.let { QualitySection(it) }
            ActionPicker(state.action, viewModel)
            Text(stringResource(R.string.custom_sensitivity, (state.sensitivity * 100).roundToInt()))
            Slider(value = state.sensitivity, onValueChange = viewModel::setSensitivity, valueRange = 0f..1f)
            Text(stringResource(R.string.custom_cooldown, state.cooldownMs / 1000f))
            Slider(
                value = state.cooldownMs.toFloat(),
                onValueChange = { viewModel.setCooldown((it / 100).roundToInt() * 100L) },
                valueRange = 300f..3000f,
            )
            TestSection(viewModel, state)
            Button(onClick = viewModel::save, enabled = state.canSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.custom_save))
            }
        }
    }
}

@Composable
private fun CameraPreview(viewModel: GestureEditorViewModel, state: EditorUiState) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val rotation = LocalView.current.display?.rotation ?: 0
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(owner) {
        viewModel.startCamera(owner, previewView.surfaceProvider, rotation)
        onDispose { viewModel.stopCamera() }
    }
    Box(Modifier.fillMaxWidth().height(220.dp).background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        when (val phase = state.phase) {
            is RecordPhase.Countdown -> Text("${phase.seconds}", style = MaterialTheme.typography.displayLarge, color = Color.White)
            is RecordPhase.Recording -> Text(stringResource(R.string.custom_recording), color = Color.Red, style = MaterialTheme.typography.titleLarge)
            RecordPhase.Idle -> if (!state.handVisible) Text(stringResource(R.string.calib_no_hand), color = Color.White)
        }
    }
}

@Composable
private fun RecordingSection(viewModel: GestureEditorViewModel, state: EditorUiState) {
    Text(
        stringResource(R.string.custom_repeats, state.repeats, EditorUiState.MIN_REPEATS, EditorUiState.MAX_REPEATS) +
            if (state.storedSamples && state.repeats == 0) "\n" + stringResource(R.string.custom_stored_samples) else "",
    )
    val phase = state.phase
    if (phase is RecordPhase.Recording) LinearProgressIndicator(progress = { phase.progress }, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = viewModel::recordRepeat,
            enabled = phase == RecordPhase.Idle && state.repeats < EditorUiState.MAX_REPEATS,
        ) { Text(stringResource(R.string.custom_record_repeat)) }
        OutlinedButton(onClick = viewModel::removeLastRepeat, enabled = phase == RecordPhase.Idle && state.repeats > 0) {
            Text(stringResource(R.string.custom_remove_repeat))
        }
    }
    Text(stringResource(R.string.custom_record_hint), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun QualitySection(q: com.gesturecontrol.gestures.custom.QualityReport) {
    val warn = MaterialTheme.colorScheme.error
    if (q.ok) {
        Text(stringResource(R.string.custom_quality_ok), color = MaterialTheme.colorScheme.primary)
        return
    }
    q.outlierIndex?.let { Text(stringResource(R.string.custom_quality_outlier, it + 1), color = warn) }
    if (!q.distinct) {
        Text(stringResource(R.string.custom_quality_similar, q.nearestOther.orEmpty(), q.separation ?: 0f), color = warn)
    }
    if (q.builtInConflicts.isNotEmpty()) {
        Text(
            stringResource(R.string.custom_quality_builtin, q.builtInConflicts.map { it.label() }.joinToString()),
            color = warn,
        )
    }
}

@Composable
private fun ActionPicker(action: ActionSpec, viewModel: GestureEditorViewModel) {
    var menu by remember { mutableStateOf(false) }
    var appMenu by remember { mutableStateOf(false) }
    var taskerText by remember { mutableStateOf((action as? ActionSpec.TaskerIntent)?.action.orEmpty()) }
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    Box {
        OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.zone_action, action.label(viewModel::appLabel)))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            SIMPLE_ACTIONS.forEach { spec ->
                DropdownMenuItem(text = { Text(spec.label()) }, onClick = {
                    menu = false
                    viewModel.setAction(spec)
                })
            }
            DropdownMenuItem(text = { Text(stringResource(R.string.spec_launch_pick)) }, onClick = {
                menu = false
                appMenu = true
            })
            DropdownMenuItem(text = { Text(stringResource(R.string.spec_tasker_pick)) }, onClick = {
                menu = false
                viewModel.setAction(ActionSpec.TaskerIntent(taskerText.ifBlank { "gesturecontrol.CUSTOM" }))
            })
        }
        DropdownMenu(expanded = appMenu, onDismissRequest = { appMenu = false }) {
            apps.forEach { app ->
                DropdownMenuItem(text = { Text(app.label) }, onClick = {
                    appMenu = false
                    viewModel.setAction(ActionSpec.LaunchApp(app.packageName))
                })
            }
        }
    }
    if (action is ActionSpec.TaskerIntent) {
        OutlinedTextField(
            value = taskerText,
            onValueChange = {
                taskerText = it
                if (it.isNotBlank()) viewModel.setAction(ActionSpec.TaskerIntent(it.trim()))
            },
            label = { Text(stringResource(R.string.spec_tasker_action)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (action is ActionSpec.Brightness) {
        Text(stringResource(R.string.spec_brightness_permission), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TestSection(viewModel: GestureEditorViewModel, state: EditorUiState) {
    OutlinedButton(
        onClick = viewModel::toggleTest,
        enabled = state.repeats >= EditorUiState.MIN_REPEATS || state.storedSamples,
    ) { Text(stringResource(if (state.testing) R.string.custom_test_stop else R.string.custom_test)) }
    if (state.testing) {
        val confidence = (state.testConfidence ?: -1f).coerceIn(0f, 1f)
        val justFired = System.currentTimeMillis() - state.testFiredAt < 800
        Text(
            if (justFired) stringResource(R.string.custom_test_fired) else stringResource(R.string.custom_test_confidence, (confidence * 100).roundToInt()),
            color = if (justFired) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
        LinearProgressIndicator(progress = { confidence }, modifier = Modifier.fillMaxWidth())
    }
}
