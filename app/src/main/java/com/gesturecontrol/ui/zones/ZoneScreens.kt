package com.gesturecontrol.ui.zones

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gesturecontrol.R
import com.gesturecontrol.ui.components.AppTopBar
import com.gesturecontrol.gestures.mapping.NormPoint
import com.gesturecontrol.gestures.mapping.NormRect
import com.gesturecontrol.gestures.mapping.Zone
import com.gesturecontrol.gestures.mapping.ZoneAction
import com.gesturecontrol.gestures.mapping.ZoneSet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneAppsScreen(
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    viewModel: ZoneAppsViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            AppTopBar(stringResource(R.string.zones_title), onBack = onBack)
        },
    ) { padding ->
        val list = items
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                stringResource(R.string.zones_list_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
            when {
                list == null -> Unit
                list.isEmpty() -> Text(stringResource(R.string.zones_no_selected_apps), Modifier.padding(16.dp))
                else -> LazyColumn {
                    items(list, key = { it.packageName }) { item ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenEditor(item.packageName) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        ) {
                            Text(item.label, Modifier.weight(1f))
                            Text(stringResource(R.string.zones_count, item.zoneCount), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ZoneEditorScreen(onBack: () -> Unit, viewModel: ZoneEditorViewModel = hiltViewModel()) {
    val zones by viewModel.zones.collectAsStateWithLifecycle()
    val selectedId by viewModel.selectedId.collectAsStateWithLifecycle()
    val selected = zones.firstOrNull { it.id == selectedId }
    Scaffold(
        topBar = {
            AppTopBar(viewModel.appLabel, onBack = onBack)
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.zone_editor_hint), style = MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                ScreenMock(
                    zones = zones,
                    selected = selected,
                    aspect = viewModel.screenAspect,
                    onSelect = viewModel::select,
                    onCreate = viewModel::add,
                    onMove = viewModel::move,
                )
            }
            ZoneActionsPanel(
                zone = selected,
                onAction = { action -> selected?.let { viewModel.setAction(it, action) } },
                onDelete = { selected?.let(viewModel::delete) },
            )
        }
    }
}

@Composable
private fun ZoneActionsPanel(zone: Zone?, onAction: (ZoneAction) -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (zone == null) {
            Text(stringResource(R.string.zone_none_selected), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Row
        }
        var menu by remember { mutableStateOf(false) }
        Box(Modifier.weight(1f)) {
            OutlinedButton(onClick = { menu = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.zone_action, stringResource(zone.action.labelRes())))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                ZONE_ACTIONS.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(action.labelRes())) },
                        onClick = {
                            menu = false
                            onAction(action)
                        },
                    )
                }
            }
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.zone_delete)) }
    }
}

/**
 * Phone-shaped canvas: drag on empty space draws a new zone, tap selects a zone,
 * dragging the selected zone moves it.
 */
@Composable
private fun ScreenMock(
    zones: List<Zone>,
    selected: Zone?,
    aspect: Float,
    onSelect: (Long?) -> Unit,
    onCreate: (NormRect) -> Unit,
    onMove: (Zone, NormRect) -> Unit,
) {
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStart by remember { mutableStateOf<NormPoint?>(null) }
    var dragNow by remember { mutableStateOf<NormPoint?>(null) }
    var moving by remember { mutableStateOf<Zone?>(null) }
    val currentZones by rememberUpdatedState(zones)
    val currentSelected by rememberUpdatedState(selected)
    val labels = zones.associate { it.id to stringResource(it.action.labelRes()) }
    val textMeasurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme

    fun toNorm(o: Offset) = NormPoint(
        (o.x / boxSize.width.coerceAtLeast(1)).coerceIn(0f, 1f),
        (o.y / boxSize.height.coerceAtLeast(1)).coerceIn(0f, 1f),
    )

    Box(
        Modifier
            .aspectRatio(1f / aspect)
            .border(3.dp, colors.outline, MaterialTheme.shapes.medium)
            .background(colors.surfaceVariant, MaterialTheme.shapes.medium)
            .onSizeChanged { boxSize = it }
            .pointerInput(Unit) {
                detectTapGestures { o -> onSelect(ZoneSet(currentZones).hitTest(toNorm(o))?.id) }
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { o ->
                        val p = toNorm(o)
                        moving = currentSelected?.takeIf { p in it.rect }
                        dragStart = p
                        dragNow = p
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        dragNow = toNorm(change.position)
                    },
                    onDragEnd = {
                        val a = dragStart
                        val b = dragNow
                        val zone = moving
                        if (a != null && b != null) {
                            if (zone != null) {
                                onMove(zone, zone.rect.offsetWithin(b.x - a.x, b.y - a.y))
                            } else {
                                val rect = NormRect.fromCorners(a, b)
                                if (rect.width >= MIN_ZONE && rect.height >= MIN_ZONE) onCreate(rect)
                            }
                        }
                        dragStart = null
                        dragNow = null
                        moving = null
                    },
                    onDragCancel = {
                        dragStart = null
                        dragNow = null
                        moving = null
                    },
                )
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            fun rectOf(r: NormRect) =
                Offset(r.left * size.width, r.top * size.height) to Size(r.width * size.width, r.height * size.height)

            // Thirds grid to help placing zones.
            for (i in 1..2) {
                drawLine(colors.outlineVariant, Offset(size.width * i / 3f, 0f), Offset(size.width * i / 3f, size.height))
                drawLine(colors.outlineVariant, Offset(0f, size.height * i / 3f), Offset(size.width, size.height * i / 3f))
            }
            val a = dragStart
            val b = dragNow
            zones.forEachIndexed { index, zone ->
                val shown = if (zone == moving && a != null && b != null) {
                    zone.rect.offsetWithin(b.x - a.x, b.y - a.y)
                } else {
                    zone.rect
                }
                val (topLeft, rectSize) = rectOf(shown)
                val isSelected = zone.id == selected?.id
                drawRect(if (isSelected) colors.primary.copy(alpha = 0.35f) else colors.tertiary.copy(alpha = 0.2f), topLeft, rectSize)
                drawRect(if (isSelected) colors.primary else colors.tertiary, topLeft, rectSize, style = Stroke(if (isSelected) 6f else 3f))
                drawText(
                    textMeasurer,
                    "${index + 1}. ${labels[zone.id].orEmpty()}",
                    topLeft + Offset(8f, 6f),
                    style = TextStyle(fontSize = 11.sp, color = colors.onSurface),
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                    size = Size((rectSize.width - 8f).coerceAtLeast(1f), (rectSize.height - 6f).coerceAtLeast(1f)),
                )
            }
            if (moving == null && a != null && b != null) {
                val (topLeft, rectSize) = rectOf(NormRect.fromCorners(a, b))
                drawRect(colors.primary, topLeft, rectSize, style = Stroke(4f))
            }
        }
    }
}

private const val MIN_ZONE = 0.03f
