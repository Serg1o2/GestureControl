package com.gesturecontrol.ui.sensitivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gesturecontrol.data.SettingsRepository
import com.gesturecontrol.gestures.GestureToggles
import com.gesturecontrol.gestures.Sensitivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GestureSettingsState(val sensitivity: Sensitivity = Sensitivity(), val toggles: GestureToggles = GestureToggles())

@HiltViewModel
class SensitivityViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<GestureSettingsState> =
        combine(settings.sensitivity, settings.gestureToggles, ::GestureSettingsState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GestureSettingsState())

    /** Saved when the slider is released: the running service rebuilds its analyzer on every change. */
    fun setSensitivity(value: Sensitivity) {
        viewModelScope.launch { settings.setSensitivity(value) }
    }

    fun setToggles(value: GestureToggles) {
        viewModelScope.launch { settings.setGestureToggles(value) }
    }

    /** Standard sensitivity and every gesture switched on. */
    fun reset() {
        viewModelScope.launch {
            settings.setSensitivity(Sensitivity())
            settings.setGestureToggles(GestureToggles())
        }
    }
}
