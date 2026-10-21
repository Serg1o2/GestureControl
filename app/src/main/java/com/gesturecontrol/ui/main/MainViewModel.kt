package com.gesturecontrol.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gesturecontrol.data.SelectedAppsRepository
import com.gesturecontrol.data.SettingsRepository
import com.gesturecontrol.service.GestureServiceState
import com.gesturecontrol.service.ServiceStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val status: ServiceStatus = ServiceStatus.Off,
    val selectedCount: Int = 0,
    val powerSave: Boolean = true,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    serviceState: GestureServiceState,
    selectedApps: SelectedAppsRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val state: StateFlow<MainUiState> = combine(
        serviceState.status,
        selectedApps.selectedPackages,
        settings.powerSaveEnabled,
    ) { status, selected, powerSave ->
        MainUiState(status, selected.size, powerSave)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun setPowerSave(enabled: Boolean) {
        viewModelScope.launch { settings.setPowerSaveEnabled(enabled) }
    }
}
