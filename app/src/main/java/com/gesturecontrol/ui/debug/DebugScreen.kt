package com.gesturecontrol.ui.debug

import android.Manifest
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
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
import com.gesturecontrol.ui.label
import com.gesturecontrol.ui.rememberPermissionState
import com.gesturecontrol.vision.TRAINING_LABELS

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(onBack: () -> Unit, viewModel: DebugViewModel = hiltViewModel()) {
    val camera = rememberPermissionState(Manifest.permission.CAMERA)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.debug_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (camera.granted) {
                DebugContent(viewModel)
            } else {
                LaunchedEffect(Unit) { camera.request() }
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.debug_no_camera_permission))
                    Button(onClick = camera::request) { Text(stringResource(R.string.action_grant)) }
                }
            }
        }
    }
}

@Composable
private fun DebugContent(viewModel: DebugViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val rotation = LocalView.current.display?.rotation ?: 0
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(lifecycleOwner) {
        viewModel.startCamera(lifecycleOwner, previewView.surfaceProvider, rotation)
        onDispose { viewModel.stopCamera() }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            HandOverlay(state.landmarks, state.aspectRatio, Modifier.fillMaxSize())
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.debug_state, state.engineState.label()), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.debug_fps, state.fps) +
                    if (state.powerSaving) " · " + stringResource(R.string.debug_power_save_on) else "",
            )
            Text(stringResource(R.string.debug_last_gesture, state.lastGesture?.label() ?: stringResource(R.string.debug_none)))
            val classifier by viewModel.classifierStatus.collectAsStateWithLifecycle()
            Text(
                stringResource(R.string.debug_classifier, classifier.active.name, classifier.modelVersion ?: stringResource(R.string.debug_none)),
                style = MaterialTheme.typography.bodySmall,
            )
            val pointer = state.pointer
            Text(
                stringResource(
                    R.string.debug_pointer,
                    pointer?.pose?.name ?: stringResource(R.string.debug_none),
                    pointer?.pinchRatio?.let { "%.2f".format(it) } ?: stringResource(R.string.debug_none),
                    pointer?.cursor?.let { "%.2f, %.2f".format(it.x, it.y) } ?: stringResource(R.string.debug_none),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.debug_last_pointer_event, state.lastPointerEvent?.toString() ?: stringResource(R.string.debug_none)),
                style = MaterialTheme.typography.bodySmall,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = state.executeActions, onCheckedChange = viewModel::setExecuteActions)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.debug_execute_actions))
            }
            val frames = state.recordingFrames
            var labelMenu by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { labelMenu = true }, enabled = frames == null) {
                    Text(stringResource(R.string.debug_training_label, state.trainingLabel ?: stringResource(R.string.debug_label_none)))
                }
                DropdownMenu(expanded = labelMenu, onDismissRequest = { labelMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.debug_label_none)) },
                        onClick = {
                            labelMenu = false
                            viewModel.setTrainingLabel(null)
                        },
                    )
                    TRAINING_LABELS.forEach { label ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                labelMenu = false
                                viewModel.setTrainingLabel(label)
                            },
                        )
                    }
                }
            }
            OutlinedButton(onClick = viewModel::toggleRecording) {
                Text(
                    if (frames == null) {
                        stringResource(R.string.debug_record_start)
                    } else {
                        stringResource(R.string.debug_record_stop, frames)
                    },
                )
            }
            state.savedFile?.let {
                Text(stringResource(R.string.debug_record_saved, it), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
