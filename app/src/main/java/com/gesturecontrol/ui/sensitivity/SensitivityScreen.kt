package com.gesturecontrol.ui.sensitivity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gesturecontrol.R
import com.gesturecontrol.gestures.GestureConfig
import com.gesturecontrol.gestures.GestureToggles
import com.gesturecontrol.gestures.Sensitivity
import com.gesturecontrol.gestures.SensitivityMapping
import com.gesturecontrol.ui.components.AppTopBar
import com.gesturecontrol.ui.components.IconBadge
import com.gesturecontrol.ui.components.StatusChip
import com.gesturecontrol.ui.theme.LocalBrand
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SensitivityScreen(onBack: () -> Unit, viewModel: SensitivityViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SensitivityContent(
        sensitivity = state.sensitivity,
        toggles = state.toggles,
        onBack = onBack,
        onChange = viewModel::setSensitivity,
        onToggle = viewModel::setToggles,
        onReset = viewModel::reset,
    )
}

/** One main gesture: how to read and write its level and its switch. */
private class GestureRow(
    val emoji: String,
    val title: Int,
    val description: Int,
    val level: (Sensitivity) -> Int,
    val withLevel: (Sensitivity, Int) -> Sensitivity,
    val on: (GestureToggles) -> Boolean,
    val withOn: (GestureToggles, Boolean) -> GestureToggles,
)

private val ROWS = listOf(
    GestureRow(
        "👉", R.string.sens_swipe, R.string.sens_swipe_desc,
        { it.swipe }, { s, l -> s.copy(swipe = l) }, { it.swipe }, { t, v -> t.copy(swipe = v) },
    ),
    GestureRow(
        "☝️", R.string.sens_finger, R.string.sens_finger_desc,
        { it.finger }, { s, l -> s.copy(finger = l) }, { it.finger }, { t, v -> t.copy(finger = v) },
    ),
    GestureRow(
        "✋", R.string.sens_palm, R.string.sens_palm_desc,
        { it.palmHold }, { s, l -> s.copy(palmHold = l) }, { it.palmHold }, { t, v -> t.copy(palmHold = v) },
    ),
    GestureRow(
        "✊", R.string.sens_fist, R.string.sens_fist_desc,
        { it.fistOpen }, { s, l -> s.copy(fistOpen = l) }, { it.fistOpen }, { t, v -> t.copy(fistOpen = v) },
    ),
    GestureRow(
        "🤏", R.string.sens_pinch, R.string.sens_pinch_desc,
        { it.pinch }, { s, l -> s.copy(pinch = l) }, { it.pinch }, { t, v -> t.copy(pinch = v) },
    ),
    GestureRow(
        "✌️", R.string.sens_scroll, R.string.sens_scroll_desc,
        { it.scroll }, { s, l -> s.copy(scroll = l) }, { it.scroll }, { t, v -> t.copy(scroll = v) },
    ),
)

/** The screen without the view model (also rendered by screenshot tests). */
@Composable
fun SensitivityContent(
    sensitivity: Sensitivity,
    toggles: GestureToggles,
    onBack: () -> Unit,
    onChange: (Sensitivity) -> Unit,
    onToggle: (GestureToggles) -> Unit,
    onReset: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.sens_title_short), onBack) {
                IconButton(onClick = onReset, enabled = !sensitivity.isDefault || toggles.disabledCount > 0) {
                    Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.sens_reset))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        stringResource(R.string.sens_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            ROWS.forEach { row ->
                val detail = if (row.title == R.string.sens_palm) {
                    val ms = SensitivityMapping.apply(GestureConfig(), sensitivity).palm.holdMs
                    stringResource(R.string.sens_palm_value, String.format(Locale.getDefault(), "%.1f", ms / 1000f))
                } else {
                    null
                }
                GestureCard(
                    row = row,
                    level = row.level(sensitivity),
                    on = row.on(toggles),
                    detail = detail,
                    onLevel = { onChange(row.withLevel(sensitivity, it)) },
                    onOn = { onToggle(row.withOn(toggles, it)) },
                )
            }
            Text(
                stringResource(R.string.sens_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun GestureCard(row: GestureRow, level: Int, on: Boolean, detail: String?, onLevel: (Int) -> Unit, onOn: (Boolean) -> Unit) {
    // The slider moves freely; the level is saved when it is released.
    var position by remember(level) { mutableFloatStateOf(level.toFloat()) }
    val shown = position.roundToInt()
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = LocalBrand.current.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).alpha(if (on) 1f else 0.5f), verticalAlignment = Alignment.CenterVertically) {
                    IconBadge(emoji = row.emoji, container = MaterialTheme.colorScheme.surfaceContainerHigh)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(row.title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(row.description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = on, onCheckedChange = onOn)
            }
            AnimatedVisibility(visible = on) {
                Column(Modifier.padding(top = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.sens_level_label),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f),
                        )
                        StatusChip(stringResource(levelName(shown)), ok = shown == 0)
                    }
                    Slider(
                        value = position,
                        onValueChange = { position = it },
                        onValueChangeFinished = { onLevel(position.roundToInt()) },
                        valueRange = Sensitivity.MIN.toFloat()..Sensitivity.MAX.toFloat(),
                        steps = Sensitivity.MAX - Sensitivity.MIN - 1,
                    )
                    Row {
                        Text(
                            stringResource(R.string.sens_less),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            stringResource(R.string.sens_more),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (detail != null) {
                        Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (!on) {
                Text(
                    stringResource(R.string.gesture_off_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalBrand.current.warn,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

private fun levelName(level: Int): Int = when {
    level <= -2 -> R.string.sens_level_min
    level == -1 -> R.string.sens_level_low
    level == 0 -> R.string.sens_level_default
    level == 1 -> R.string.sens_level_high
    else -> R.string.sens_level_max
}
