package com.gesturecontrol.ui.apps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gesturecontrol.data.InstalledApp
import com.gesturecontrol.data.InstalledAppsRepository
import com.gesturecontrol.data.SelectedAppsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppItem(val app: InstalledApp, val selected: Boolean)

data class AppSelectionUiState(
    val loading: Boolean = true,
    val query: String = "",
    val items: List<AppItem> = emptyList(),
)

@HiltViewModel
class AppSelectionViewModel @Inject constructor(
    installedApps: InstalledAppsRepository,
    private val selectedApps: SelectedAppsRepository,
) : ViewModel() {

    private val apps = MutableStateFlow<List<InstalledApp>?>(null)
    private val query = MutableStateFlow("")

    val state: StateFlow<AppSelectionUiState> =
        combine(apps, selectedApps.selectedPackages, query) { all, selected, q ->
            val items = all.orEmpty()
                .filter { q.isBlank() || it.label.contains(q.trim(), ignoreCase = true) }
                .map { AppItem(it, it.packageName in selected) }
                // Selected apps first, then alphabetical (input is already sorted).
                .sortedByDescending { it.selected }
            AppSelectionUiState(loading = all == null, query = q, items = items)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSelectionUiState())

    init {
        viewModelScope.launch { apps.value = installedApps.loadLaunchableApps() }
    }

    fun setQuery(value: String) {
        query.value = value
    }

    fun setSelected(packageName: String, selected: Boolean) {
        viewModelScope.launch { selectedApps.setSelected(packageName, selected) }
    }
}
