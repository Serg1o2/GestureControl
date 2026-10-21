package com.gesturecontrol.ui.cursor

import android.Manifest
import android.app.Activity
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gesturecontrol.R
import com.gesturecontrol.gestures.mapping.Calibration
import com.gesturecontrol.gestures.mapping.NormPoint
import com.gesturecontrol.ui.rememberPermissionState

/**
 * Full-screen 3-point calibration: for each target the user points the index finger "at" it
 * and holds still for a second. Then a live test cursor shows the result.
 */
@Composable
fun CalibrationScreen(onDone: () -> Unit, viewModel: CalibrationViewModel = hiltViewModel()) {
    val camera = rememberPermissionState(Manifest.permission.CAMERA)
    val view = LocalView.current
    // Hide system bars so that the whole physical screen is available for targets.
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    if (!camera.granted) {
        LaunchedEffect(Unit) { camera.request() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.debug_no_camera_permission))
        }
        return
    }
    CalibrationContent(viewModel, onDone)
}

@Composable
private fun CalibrationContent(viewModel: CalibrationViewModel, onDone: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val view = LocalView.current
    val screen = remember {
        Point().also {
            @Suppress("DEPRECATION")
            context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(it)
        }
    }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FIT_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    DisposableEffect(lifecycleOwner) {
        val rotation = view.display?.rotation ?: 0
        val aspect = if (screen.x > 0) screen.y.toFloat() / screen.x else 2f
        viewModel.start(lifecycleOwner, previewView.surfaceProvider, rotation, aspect)
        onDispose { viewModel.stop() }
    }
    // Top-left of this box on the physical screen, to place targets at true screen positions.
    var origin by remember { mutableStateOf(Offset.Zero) }

    fun toLocal(p: NormPoint) = Offset(p.x * screen.x - origin.x, p.y * screen.y - origin.y)

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF101418))
            .onGloballyPositioned { origin = it.localToScreen(Offset.Zero) },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            when (state.phase) {
                CalibrationPhase.CAPTURE -> Calibration.TARGETS.forEachIndexed { i, target ->
                    val c = toLocal(target)
                    val active = i == state.step
                    drawCircle(if (active) Color(0xFF00E5FF) else Color(0x5500E5FF), radius = 28f, center = c, style = Stroke(5f))
                    drawCircle(if (i < state.step) Color(0xFF00BFA5) else Color.White, radius = 8f, center = c)
                    if (active && state.progress > 0f) {
                        drawArc(
                            Color(0xFF00E5FF), -90f, 360f * state.progress, false,
                            topLeft = c - Offset(44f, 44f), size = androidx.compose.ui.geometry.Size(88f, 88f),
                            style = Stroke(8f),
                        )
                    }
                }
                CalibrationPhase.TEST -> {
                    Calibration.TARGETS.forEach { drawCircle(Color(0x5500E5FF), 28f, toLocal(it), style = Stroke(4f)) }
                    state.testCursor?.let {
                        val c = toLocal(it)
                        if (state.pinchClosed) drawCircle(Color(0xCC00BFA5), 22f, c)
                        drawCircle(Color.White, 32f, c, style = Stroke(6f))
                    }
                }
                CalibrationPhase.ERROR -> Unit
            }
        }
        // Small camera preview in the middle, between the targets.
        Column(
            Modifier.align(Alignment.Center).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AndroidView(factory = { previewView }, modifier = Modifier.size(width = 150.dp, height = 200.dp))
            val text = when (state.phase) {
                CalibrationPhase.CAPTURE ->
                    if (state.pointing) stringResource(R.string.calib_instruction, state.step + 1) else stringResource(R.string.calib_no_hand)
                CalibrationPhase.TEST -> stringResource(R.string.calib_test)
                CalibrationPhase.ERROR -> stringResource(R.string.calib_error)
            }
            Text(text, color = Color.White, textAlign = TextAlign.Center)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.phase != CalibrationPhase.CAPTURE || state.step > 0) {
                    OutlinedButton(onClick = viewModel::restart) { Text(stringResource(R.string.calib_retry)) }
                }
                if (state.phase == CalibrationPhase.TEST) {
                    Button(onClick = onDone) { Text(stringResource(R.string.calib_done)) }
                } else {
                    TextButton(onClick = onDone) { Text(stringResource(R.string.calib_cancel)) }
                }
            }
        }
    }
}
