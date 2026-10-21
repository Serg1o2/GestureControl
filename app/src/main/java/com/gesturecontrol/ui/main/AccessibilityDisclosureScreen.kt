package com.gesturecontrol.ui.main

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gesturecontrol.R

/**
 * Prominent disclosure shown before sending the user to the system accessibility settings:
 * what the service does, what it does not do, then an explicit choice.
 */
@Composable
fun AccessibilityDisclosureScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    Scaffold { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.disclosure_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.disclosure_intro))

            Text(stringResource(R.string.disclosure_does_title), style = MaterialTheme.typography.titleMedium)
            Bullet(stringResource(R.string.disclosure_does_app))
            Bullet(stringResource(R.string.disclosure_does_cursor))
            Bullet(stringResource(R.string.disclosure_does_gestures))
            Bullet(stringResource(R.string.disclosure_does_secure))

            Text(stringResource(R.string.disclosure_doesnt_title), style = MaterialTheme.typography.titleMedium)
            Bullet(stringResource(R.string.disclosure_doesnt_read))
            Bullet(stringResource(R.string.disclosure_doesnt_store))

            Text(
                stringResource(R.string.perm_accessibility_restricted_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.disclosure_continue)) }
            OutlinedButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.disclosure_cancel))
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("•")
        Text(text)
    }
}
