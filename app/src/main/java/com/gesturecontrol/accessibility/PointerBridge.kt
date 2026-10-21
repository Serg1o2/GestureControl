package com.gesturecontrol.accessibility

import com.gesturecontrol.gestures.mapping.NormPoint
import com.gesturecontrol.gestures.mapping.NormRect
import com.gesturecontrol.gestures.mapping.TouchCommand
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What the cursor overlay should draw. */
data class OverlayState(
    val cursor: NormPoint? = null,
    val pinchClosed: Boolean = false,
    val dwellProgress: Float = 0f,
    val hoveredZone: NormRect? = null,
)

/** Short non-blocking messages shown next to the cursor. */
enum class Hint { GESTURE_CANCELLED, GESTURE_REJECTED, SECURE_WINDOW, ACTION_UNAVAILABLE, SCREEN_LOCKED }

/**
 * In-process link between [com.gesturecontrol.service.GestureService] (camera, recognition)
 * and [ForegroundAppService] (overlay, gesture injection).
 */
@Singleton
class PointerBridge @Inject constructor() {
    val overlay = MutableStateFlow(OverlayState())

    private val _commands = MutableSharedFlow<TouchCommand>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val commands: SharedFlow<TouchCommand> = _commands.asSharedFlow()

    /** Returns false when nobody (no accessibility service) is listening. */
    fun send(command: TouchCommand): Boolean =
        _commands.subscriptionCount.value > 0 && _commands.tryEmit(command)
}
