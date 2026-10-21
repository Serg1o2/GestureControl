package com.gesturecontrol.ui.cursor

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gesturecontrol.R
import com.gesturecontrol.data.PointerSettings
import com.gesturecontrol.data.SettingsRepository
import com.gesturecontrol.gestures.pointer.PressMode
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.roundToInt

@HiltViewModel
class CursorSettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    val state: StateFlow<PointerSettings> = settings.pointerSettings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PointerSettings())

    /** For showing the dead zone in pixels. */
    val screenWidthPx: Int = context.resources.displayMetrics.widthPixels

    fun setEnabled(value: Boolean) = launch { settings.setCursorEnabled(value) }
    fun setPressMode(value: PressMode) = launch { settings.setPressMode(value) }
    fun setDeadZone(value: Float) = launch { settings.setDeadZone(value) }
    fun resetCalibration() = launch { settings.setCalibration(null) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CursorSettingsScreen(
    onBack: () -> Unit,
    onCalibrate: () -> Unit,
    viewModel: CursorSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cursor_settings_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.nav_back)) } },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.cursor_enabled), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.cursor_enabled_desc), style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.width(12.dp))
                Switch(checked = state.enabled, onCheckedChange = viewModel::setEnabled)
            }

            Text(stringResource(R.string.press_mode_title), style = MaterialTheme.typography.titleMedium)
            PressModeOption(
                selected = state.pressMode == PressMode.PINCH,
                title = stringResource(R.string.press_mode_pinch),
                description = stringResource(R.string.press_mode_pinch_desc),
                onSelect = { viewModel.setPressMode(PressMode.PINCH) },
            )
            PressModeOption(
                selected = state.pressMode == PressMode.DWELL,
                title = stringResource(R.string.press_mode_dwell),
                description = stringResource(R.string.press_mode_dwell_desc),
                onSelect = { viewModel.setPressMode(PressMode.DWELL) },
            )

            Text(
                stringResource(R.string.dead_zone_title, (state.deadZone * viewModel.screenWidthPx).roundToInt()),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(stringResource(R.string.dead_zone_desc), style = MaterialTheme.typography.bodySmall)
            Slider(
                value = state.deadZone,
                onValueChange = viewModel::setDeadZone,
                valueRange = 0f..0.02f,
                steps = 9,
            )

            Text(stringResource(R.string.calibration_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    if (state.calibration != null) R.string.calibration_status_done else R.string.calibration_status_default,
                ),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onCalibrate) { Text(stringResource(R.string.calibration_start)) }
                if (state.calibration != null) {
                    OutlinedButton(onClick = viewModel::resetCalibration) { Text(stringResource(R.string.calibration_reset)) }
                }
            }
        }
    }
}

@Composable
private fun PressModeOption(selected: Boolean, title: String, description: String, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 8.dp)) {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
