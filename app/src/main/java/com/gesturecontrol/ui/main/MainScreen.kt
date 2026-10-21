package com.gesturecontrol.ui.main

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gesturecontrol.R
import com.gesturecontrol.ui.rememberPermissionState

@Composable
fun MainScreen(onOpenApps: () -> Unit, onOpenDebug: () -> Unit) {
    val camera = rememberPermissionState(Manifest.permission.CAMERA)
    Scaffold { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.perm_camera))
            if (!camera.granted) {
                Button(onClick = camera::request) { Text(stringResource(R.string.action_grant)) }
            }
            OutlinedButton(onClick = onOpenDebug) { Text(stringResource(R.string.main_debug_open)) }
        }
    }
}
