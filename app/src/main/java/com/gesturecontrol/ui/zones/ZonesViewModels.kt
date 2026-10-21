package com.gesturecontrol.ui.zones

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gesturecontrol.data.InstalledAppsRepository
import com.gesturecontrol.data.SelectedAppsRepository
import com.gesturecontrol.data.ZoneRepository
import com.gesturecontrol.gestures.mapping.NormRect
import com.gesturecontrol.gestures.mapping.Zone
import com.gesturecontrol.gestures.mapping.ZoneAction
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppZonesItem(val packageName: String, val label: String, val zoneCount: Int)

@HiltViewModel
class ZoneAppsViewModel @Inject constructor(
    selectedApps: SelectedAppsRepository,
    zones: ZoneRepository,
    installedApps: InstalledAppsRepository,
) : ViewModel() {
    val items: StateFlow<List<AppZonesItem>?> =
        combine(selectedApps.selectedPackages, zones.zoneCounts) { selected, counts ->
            selected.map { AppZonesItem(it, installedApps.labelOf(it), counts[it] ?: 0) }.sortedBy { it.label.lowercase() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@HiltViewModel
class ZoneEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ZoneRepository,
    installedApps: InstalledAppsRepository,
    @ApplicationContext context: Context,
) : ViewModel() {
    val packageName: String = checkNotNull(savedStateHandle[ARG_PACKAGE])
    val appLabel: String = installedApps.labelOf(packageName)

    /** Screen height / width, so the mock has the phone's proportions. */
    val screenAspect: Float = run {
        val p = Point()
        @Suppress("DEPRECATION")
        context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.getRealSize(p)
        // Zones are stored for the portrait screen, which is how music players are used.
        if (p.x > 0 && p.y > 0) maxOf(p.x, p.y).toFloat() / minOf(p.x, p.y) else 2f
    }

    val zones: StateFlow<List<Zone>> = repository.zonesFor(packageName)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedId = MutableStateFlow<Long?>(null)

    fun select(id: Long?) {
        selectedId.value = id
    }

    fun add(rect: NormRect) {
        viewModelScope.launch { selectedId.value = repository.add(packageName, rect, ZoneAction.Tap) }
    }

    fun move(zone: Zone, rect: NormRect) {
        viewModelScope.launch { repository.update(packageName, zone.copy(rect = rect)) }
    }

    fun setAction(zone: Zone, action: ZoneAction) {
        viewModelScope.launch { repository.update(packageName, zone.copy(action = action)) }
    }

    fun delete(zone: Zone) {
        viewModelScope.launch {
            repository.delete(zone.id)
            if (selectedId.value == zone.id) selectedId.value = null
        }
    }

    companion object {
        const val ARG_PACKAGE = "pkg"
    }
}
