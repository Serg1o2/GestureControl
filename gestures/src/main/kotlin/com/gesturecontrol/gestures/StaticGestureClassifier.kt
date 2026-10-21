package com.gesturecontrol.gestures

import com.gesturecontrol.gestures.HandLandmarks as H

/** Classifies a single hand pose. Stage 1 only needs the open palm. */
class StaticGestureClassifier(private val config: PalmConfig) {

    fun isOpenPalm(lm: List<Landmark>): Boolean {
        val fingers = listOf(
            H.INDEX_PIP to H.INDEX_TIP,
            H.MIDDLE_PIP to H.MIDDLE_TIP,
            H.RING_PIP to H.RING_TIP,
            H.PINKY_PIP to H.PINKY_TIP,
        )
        val allFingersExtended = fingers.all { (pip, tip) -> isFingerExtended(lm, pip, tip) }
        return allFingersExtended && (!config.requireThumb || isThumbExtended(lm))
    }

    fun isFingerExtended(lm: List<Landmark>, pip: Int, tip: Int): Boolean {
        val wrist = lm[H.WRIST]
        return distance2d(wrist, lm[tip]) > distance2d(wrist, lm[pip]) * config.fingerExtensionRatio
    }

    fun isThumbExtended(lm: List<Landmark>): Boolean {
        val anchor = lm[H.PINKY_MCP]
        return distance2d(lm[H.THUMB_TIP], anchor) > distance2d(lm[H.THUMB_IP], anchor) * config.thumbExtensionRatio
    }
}
