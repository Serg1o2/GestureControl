package com.gesturecontrol.ui.sensitivity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gesturecontrol.data.SettingsRepository
import com.gesturecontrol.gestures.Sensitivity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensitivityViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {
    val sensitivity: StateFlow<Sensitivity> =
        settings.sensitivity.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Sensitivity())

    /** Saved when the slider is released: the running service rebuilds its analyzer on every change. */
    fun set(value: Sensitivity) {
        viewModelScope.launch { settings.setSensitivity(value) }
    }

    fun reset() = set(Sensitivity())
}
