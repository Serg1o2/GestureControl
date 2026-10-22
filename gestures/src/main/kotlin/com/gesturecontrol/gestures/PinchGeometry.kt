package com.gesturecontrol.gestures

import com.gesturecontrol.gestures.HandLandmarks as H

/**
 * Pinch geometry shared by the cursor press ([com.gesturecontrol.gestures.pointer.PointerEngine]) and the
 * volume slider ([VolumeSlider]): 2D only, scale invariant (distances in hand sizes).
 */
object PinchGeometry {
    private val fingers = StaticGestureClassifier(PalmConfig())

    /** Thumb tip to index tip distance in hand sizes. */
    fun ratio(lm: List<Landmark>): Float = StaticGestureClassifier.pinchRatio(lm)

    /** Middle, ring and little fingers folded: a pinch, not an open hand touching thumb and index. */
    fun othersFolded(lm: List<Landmark>): Boolean =
        !fingers.isFingerExtended(lm, H.MIDDLE_PIP, H.MIDDLE_TIP) &&
            !fingers.isFingerExtended(lm, H.RING_PIP, H.RING_TIP) &&
            !fingers.isFingerExtended(lm, H.PINKY_PIP, H.PINKY_TIP)

    /** Index tip to wrist distance in hand sizes; small for a fist, where thumb and index also touch. */
    fun indexReach(lm: List<Landmark>): Float {
        val size = StaticGestureClassifier.handSize(lm)
        return if (size <= 1e-6f) 0f else distance2d(lm[H.WRIST], lm[H.INDEX_TIP]) / size
    }

    /** A hand that can pinch: other fingers folded and the index reaching out (not a fist). */
    fun isPinchHand(lm: List<Landmark>, minIndexReach: Float): Boolean =
        othersFolded(lm) && indexReach(lm) >= minIndexReach
}
