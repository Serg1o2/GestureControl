package com.gesturecontrol.gestures

/**
 * One analyzed camera frame.
 * @param timestampMs monotonic timestamp of the frame.
 * @param landmarks 21 landmarks of the detected hand, or null when no hand is in the frame.
 * @param aspect width / height of the image the landmarks are normalized to
 *   (default: portrait 480x640 analysis frames).
 * @param brightness mean luma of the camera frame in [0, 1] before any brightening, null if unknown.
 */
data class HandFrame(
    val timestampMs: Long,
    val landmarks: List<Landmark>?,
    val aspect: Float = DEFAULT_ASPECT,
    val brightness: Float? = null,
) {
    init {
        require(landmarks == null || landmarks.size == HandLandmarks.COUNT) {
            "Expected ${HandLandmarks.COUNT} landmarks, got ${landmarks?.size}"
        }
    }

    val hasHand: Boolean get() = landmarks != null

    companion object {
        const val DEFAULT_ASPECT = 0.75f
    }
}
