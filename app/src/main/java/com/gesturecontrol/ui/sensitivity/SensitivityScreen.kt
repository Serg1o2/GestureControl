package com.gesturecontrol.ui.sensitivity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ScrollState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gesturecontrol.R
import com.gesturecontrol.gestures.GestureConfig
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
    val sensitivity by viewModel.sensitivity.collectAsStateWithLifecycle()
    SensitivityContent(sensitivity, onBack, onChange = viewModel::set, onReset = viewModel::reset)
}

/** One adjustable gesture: how to read and write its level in [Sensitivity]. */
private class GestureRow(
    val emoji: String,
    val title: Int,
    val description: Int,
    val get: (Sensitivity) -> Int,
    val set: (Sensitivity, Int) -> Sensitivity,
)

private val ROWS = listOf(
    GestureRow("👉", R.string.sens_swipe, R.string.sens_swipe_desc, { it.swipe }, { s, l -> s.copy(swipe = l) }),
    GestureRow("☝️", R.string.sens_finger, R.string.sens_finger_desc, { it.finger }, { s, l -> s.copy(finger = l) }),
    GestureRow("✋", R.string.sens_palm, R.string.sens_palm_desc, { it.palmHold }, { s, l -> s.copy(palmHold = l) }),
    GestureRow("✊", R.string.sens_fist, R.string.sens_fist_desc, { it.fistOpen }, { s, l -> s.copy(fistOpen = l) }),
    GestureRow("🤏", R.string.sens_pinch, R.string.sens_pinch_desc, { it.pinch }, { s, l -> s.copy(pinch = l) }),
    GestureRow("✌️", R.string.sens_scroll, R.string.sens_scroll_desc, { it.scroll }, { s, l -> s.copy(scroll = l) }),
)

/** The screen without the view model (also rendered by screenshot tests). */
@Composable
fun SensitivityContent(
    sensitivity: Sensitivity,
    onBack: () -> Unit,
    onChange: (Sensitivity) -> Unit,
    onReset: () -> Unit,
    scrollState: ScrollState = rememberScrollState(),
) {
    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.sens_title_short), onBack) {
                IconButton(onClick = onReset, enabled = !sensitivity.isDefault) {
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
                SensitivityCard(row, row.get(sensitivity), detail) { onChange(row.set(sensitivity, it)) }
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
private fun SensitivityCard(row: GestureRow, level: Int, detail: String?, onLevel: (Int) -> Unit) {
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
                IconBadge(emoji = row.emoji, container = MaterialTheme.colorScheme.surfaceContainerHigh)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(row.title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(row.description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                StatusChip(stringResource(levelName(shown)), ok = shown == 0)
            }
            Slider(
                value = position,
                onValueChange = { position = it },
                onValueChangeFinished = { onLevel(position.roundToInt()) },
                valueRange = Sensitivity.MIN.toFloat()..Sensitivity.MAX.toFloat(),
                steps = Sensitivity.MAX - Sensitivity.MIN - 1,
                modifier = Modifier.padding(top = 4.dp),
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
}

private fun levelName(level: Int): Int = when {
    level <= -2 -> R.string.sens_level_min
    level == -1 -> R.string.sens_level_low
    level == 0 -> R.string.sens_level_default
    level == 1 -> R.string.sens_level_high
    else -> R.string.sens_level_max
}
