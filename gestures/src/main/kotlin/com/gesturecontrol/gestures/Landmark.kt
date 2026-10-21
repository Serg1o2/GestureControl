package com.gesturecontrol.gestures

/** Normalized hand landmark: x/y in [0, 1] of the frame, z relative depth (wrist ~ 0). */
data class Landmark(val x: Float, val y: Float, val z: Float = 0f)
