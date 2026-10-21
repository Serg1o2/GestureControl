package com.gesturecontrol.service

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ServiceStatus {
    /** Service is not running. */
    data object Off : ServiceStatus

    /** Running, camera off: the app on screen is not selected. */
    data object Waiting : ServiceStatus

    /** Running, camera on. */
    data class Active(val packageName: String, val appLabel: String) : ServiceStatus

    /** Running, camera handed over to the debug screen. */
    data object PausedDebug : ServiceStatus

    /** Running, camera off because the screen is off. */
    data object ScreenOff : ServiceStatus
}

/** State shared between [GestureService] and the UI. */
@Singleton
class GestureServiceState @Inject constructor() {
    val status = MutableStateFlow<ServiceStatus>(ServiceStatus.Off)

    /** The debug screen owns the camera while this is true. */
    val debugPreviewActive = MutableStateFlow(false)
}
