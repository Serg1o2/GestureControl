package com.gesturecontrol.gestures

import kotlin.math.sqrt

/**
 * Hand landmark in normalized "user view" coordinates:
 * x in [0, 1] grows toward the user's right (as in a selfie mirror), y in [0, 1] grows downward,
 * z is MediaPipe relative depth (wrist ~ 0, smaller = closer to the camera).
 * The vision layer is responsible for converting raw camera coordinates into this space.
 */
data class Landmark(val x: Float, val y: Float, val z: Float = 0f)

internal fun distance2d(a: Landmark, b: Landmark): Float {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return sqrt(dx * dx + dy * dy)
}

/** Indices of the 21 MediaPipe hand landmarks. */
object HandLandmarks {
    const val COUNT = 21

    const val WRIST = 0
    const val THUMB_CMC = 1
    const val THUMB_MCP = 2
    const val THUMB_IP = 3
    const val THUMB_TIP = 4
    const val INDEX_MCP = 5
    const val INDEX_PIP = 6
    const val INDEX_DIP = 7
    const val INDEX_TIP = 8
    const val MIDDLE_MCP = 9
    const val MIDDLE_PIP = 10
    const val MIDDLE_DIP = 11
    const val MIDDLE_TIP = 12
    const val RING_MCP = 13
    const val RING_PIP = 14
    const val RING_DIP = 15
    const val RING_TIP = 16
    const val PINKY_MCP = 17
    const val PINKY_PIP = 18
    const val PINKY_DIP = 19
    const val PINKY_TIP = 20

    /** Bone connections, used to draw the hand skeleton. */
    val CONNECTIONS: List<Pair<Int, Int>> = listOf(
        WRIST to THUMB_CMC, THUMB_CMC to THUMB_MCP, THUMB_MCP to THUMB_IP, THUMB_IP to THUMB_TIP,
        WRIST to INDEX_MCP, INDEX_MCP to INDEX_PIP, INDEX_PIP to INDEX_DIP, INDEX_DIP to INDEX_TIP,
        INDEX_MCP to MIDDLE_MCP, MIDDLE_MCP to MIDDLE_PIP, MIDDLE_PIP to MIDDLE_DIP, MIDDLE_DIP to MIDDLE_TIP,
        MIDDLE_MCP to RING_MCP, RING_MCP to RING_PIP, RING_PIP to RING_DIP, RING_DIP to RING_TIP,
        RING_MCP to PINKY_MCP, WRIST to PINKY_MCP, PINKY_MCP to PINKY_PIP, PINKY_PIP to PINKY_DIP,
        PINKY_DIP to PINKY_TIP,
    )
}
