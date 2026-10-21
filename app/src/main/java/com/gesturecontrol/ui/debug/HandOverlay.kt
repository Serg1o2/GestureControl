package com.gesturecontrol.ui.debug

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.gesturecontrol.gestures.HandLandmarks
import com.gesturecontrol.gestures.Landmark

/**
 * Draws the 21 hand landmarks over a camera preview shown with FIT_CENTER scaling.
 * Landmarks are in user-view coordinates, which match the mirrored front camera preview.
 */
@Composable
fun HandOverlay(landmarks: List<Landmark>?, imageAspectRatio: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (landmarks == null || imageAspectRatio <= 0f) return@Canvas
        // Image rectangle inside the canvas (FIT_CENTER).
        val canvasAspect = size.width / size.height
        val (w, h) = if (canvasAspect > imageAspectRatio) {
            size.height * imageAspectRatio to size.height
        } else {
            size.width to size.width / imageAspectRatio
        }
        val left = (size.width - w) / 2
        val top = (size.height - h) / 2
        fun point(lm: Landmark) = Offset(left + lm.x * w, top + lm.y * h)

        for ((a, b) in HandLandmarks.CONNECTIONS) {
            drawLine(Color.White, point(landmarks[a]), point(landmarks[b]), strokeWidth = 4f)
        }
        landmarks.forEachIndexed { i, lm ->
            val color = if (i == HandLandmarks.WRIST) Color.Yellow else Color(0xFF00E5FF)
            drawCircle(color, radius = 9f, center = point(lm))
        }
    }
}
