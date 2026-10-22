package com.gesturecontrol.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gesturecontrol.data.SelectedAppsRepository
import com.gesturecontrol.data.SettingsRepository
import com.gesturecontrol.service.GestureServiceState
import com.gesturecontrol.service.ServiceStatus
import com.gesturecontrol.vision.ClassifierKind
import com.gesturecontrol.vision.ClassifierStatus
import com.gesturecontrol.vision.PoseClassifierFactory
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
    val classifier: ClassifierKind = ClassifierKind.MODEL,
    /** What is really running; null until the camera has been bound once. */
    val classifierStatus: ClassifierStatus? = null,
    /** How many gestures have a non-default sensitivity. */
    val sensitivityChanged: Int = 0,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    serviceState: GestureServiceState,
    selectedApps: SelectedAppsRepository,
    private val settings: SettingsRepository,
    classifierFactory: PoseClassifierFactory,
) : ViewModel() {

    val state: StateFlow<MainUiState> = combine(
        serviceState.status,
        selectedApps.selectedPackages,
        settings.powerSaveEnabled,
        settings.classifierKind,
        combine(classifierFactory.status, settings.sensitivity) { classifierStatus, sensitivity -> classifierStatus to sensitivity },
    ) { status, selected, powerSave, kind, (classifierStatus, sensitivity) ->
        MainUiState(status, selected.size, powerSave, kind, classifierStatus.takeIf { it.requested == kind }, sensitivity.changedCount)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    fun setPowerSave(enabled: Boolean) {
        viewModelScope.launch { settings.setPowerSaveEnabled(enabled) }
    }

    fun setUseModel(enabled: Boolean) {
        viewModelScope.launch { settings.setClassifierKind(if (enabled) ClassifierKind.MODEL else ClassifierKind.RULES) }
    }
}
