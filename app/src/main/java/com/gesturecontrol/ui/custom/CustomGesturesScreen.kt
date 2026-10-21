package com.gesturecontrol.ui.custom

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gesturecontrol.R
import com.gesturecontrol.data.GLOBAL_PROFILE
import com.gesturecontrol.gestures.custom.ActionSpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomGesturesScreen(
    onBack: () -> Unit,
    onEdit: (profile: String, id: Long?) -> Unit,
    viewModel: CustomGesturesViewModel = hiltViewModel(),
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val selected by viewModel.selectedProfile.collectAsStateWithLifecycle()
    val gestures by viewModel.gestures.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbar.showSnackbar(
                context.getString(
                    when (msg) {
                        ProfileMessage.EXPORTED -> R.string.custom_exported
                        ProfileMessage.IMPORTED -> R.string.custom_imported
                        ProfileMessage.IMPORT_FAILED -> R.string.custom_import_failed
                        ProfileMessage.IMPORT_WRONG_VERSION -> R.string.custom_import_wrong_version
                        ProfileMessage.DUPLICATED -> R.string.custom_duplicated
                        ProfileMessage.RESET -> R.string.custom_reset_done
                    },
                ),
            )
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::export)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    val globalLabel = stringResource(R.string.custom_profile_global)
    fun labelOf(key: String) = if (key == GLOBAL_PROFILE) globalLabel else profiles.firstOrNull { it.key == key }?.label ?: key

    var menu by remember { mutableStateOf(false) }
    var profileMenu by remember { mutableStateOf(false) }
    var duplicateMenu by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.custom_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
                actions = {
                    Box {
                        TextButton(onClick = { menu = true }) { Text("⋮") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.custom_duplicate)) }, onClick = {
                                menu = false
                                duplicateMenu = true
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.custom_export)) }, onClick = {
                                menu = false
                                exportLauncher.launch("gestures_${labelOf(selected)}.json")
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.custom_import)) }, onClick = {
                                menu = false
                                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.custom_reset)) }, onClick = {
                                menu = false
                                confirmReset = true
                            })
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.custom_hint), style = MaterialTheme.typography.bodySmall)
            Box {
                OutlinedButton(onClick = { profileMenu = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.custom_profile, labelOf(selected)))
                }
                DropdownMenu(expanded = profileMenu, onDismissRequest = { profileMenu = false }) {
                    profiles.forEach { p ->
                        DropdownMenuItem(
                            text = { Text("${labelOf(p.key)} (${p.count})") },
                            onClick = {
                                profileMenu = false
                                viewModel.selectedProfile.value = p.key
                            },
                        )
                    }
                }
            }
            Button(onClick = { onEdit(selected, null) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.custom_record_new))
            }
            if (gestures.isEmpty()) {
                Text(stringResource(R.string.custom_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(gestures, key = { it.id }) { g ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onEdit(selected, g.id) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(g.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                ActionSpec.decode(g.action).label(viewModel::appLabel) + " · " +
                                    stringResource(R.string.custom_sensitivity_short, (g.sensitivity * 100).toInt()),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { viewModel.delete(g.id) }) { Text(stringResource(R.string.zone_delete)) }
                    }
                }
            }
        }
    }

    if (duplicateMenu) {
        AlertDialog(
            onDismissRequest = { duplicateMenu = false },
            title = { Text(stringResource(R.string.custom_duplicate_to)) },
            text = {
                Column {
                    profiles.filter { it.key != selected }.forEach { p ->
                        TextButton(onClick = {
                            duplicateMenu = false
                            viewModel.duplicateTo(p.key)
                        }) { Text(labelOf(p.key)) }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { duplicateMenu = false }) { Text(stringResource(R.string.calib_cancel)) } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.custom_reset_confirm, labelOf(selected))) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    viewModel.reset()
                }) { Text(stringResource(R.string.custom_reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.calib_cancel)) } },
        )
    }
}
