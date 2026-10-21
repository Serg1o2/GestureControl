package com.gesturecontrol.vision

import android.content.Context
import android.graphics.Bitmap
import com.gesturecontrol.gestures.Landmark
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.io.Closeable

/**
 * Thin wrapper over MediaPipe Hand Landmarker in VIDEO mode (synchronous, one hand).
 * Must be used from a single thread.
 */
class HandLandmarkerEngine(context: Context, delegate: Delegate = Delegate.CPU) : Closeable {

    private val landmarker: HandLandmarker = HandLandmarker.createFromOptions(
        context,
        HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath(MODEL_ASSET)
                    .setDelegate(delegate)
                    .build(),
            )
            .setRunningMode(RunningMode.VIDEO)
            .setNumHands(1)
            .setMinHandDetectionConfidence(MIN_CONFIDENCE)
            .setMinHandPresenceConfidence(MIN_CONFIDENCE)
            .setMinTrackingConfidence(MIN_CONFIDENCE)
            .build(),
    )

    private var lastTimestampMs = Long.MIN_VALUE / 2

    /**
     * Detects a hand on an upright, already mirrored bitmap.
     * @return 21 normalized landmarks in the bitmap's coordinates, or null when no hand is found.
     */
    fun detect(bitmap: Bitmap, timestampMs: Long): List<Landmark>? {
        // MediaPipe rejects non-increasing timestamps; this instance outlives camera rebinds.
        val ts = maxOf(timestampMs, lastTimestampMs + 1)
        lastTimestampMs = ts
        val result = landmarker.detectForVideo(BitmapImageBuilder(bitmap).build(), ts)
        val hand = result.landmarks().firstOrNull() ?: return null
        return hand.map { Landmark(it.x(), it.y(), it.z()) }
    }

    override fun close() = landmarker.close()

    companion object {
        const val MODEL_ASSET = "hand_landmarker.task"
        private const val MIN_CONFIDENCE = 0.5f
    }
}
