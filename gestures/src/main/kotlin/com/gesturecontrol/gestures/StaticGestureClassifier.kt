package com.gesturecontrol.gestures

import com.gesturecontrol.gestures.HandLandmarks as H

/** Static hand poses. Order of checks matters: see [StaticGestureClassifier.classify]. */
enum class HandPose {
    OPEN_PALM,

    /** Index finger extended, middle/ring/pinky folded (thumb free). Drives the cursor. */
    POINTING,

    /** Index and middle extended, ring and pinky folded (thumb free). Scrolls. */
    TWO_FINGERS,
    OTHER,
}

/** Classifies a single hand pose from 2D landmark geometry (z is never used). */
class StaticGestureClassifier(private val config: PalmConfig) {

    fun classify(lm: List<Landmark>): HandPose {
        val index = isFingerExtended(lm, H.INDEX_PIP, H.INDEX_TIP)
        val middle = isFingerExtended(lm, H.MIDDLE_PIP, H.MIDDLE_TIP)
        val ring = isFingerExtended(lm, H.RING_PIP, H.RING_TIP)
        val pinky = isFingerExtended(lm, H.PINKY_PIP, H.PINKY_TIP)
        return when {
            index && middle && ring && pinky && (!config.requireThumb || isThumbExtended(lm)) -> HandPose.OPEN_PALM
            index && !middle && !ring && !pinky -> HandPose.POINTING
            index && middle && !ring && !pinky -> HandPose.TWO_FINGERS
            else -> HandPose.OTHER
        }
    }

    fun isOpenPalm(lm: List<Landmark>): Boolean = classify(lm) == HandPose.OPEN_PALM

    fun isFingerExtended(lm: List<Landmark>, pip: Int, tip: Int): Boolean {
        val wrist = lm[H.WRIST]
        return distance2d(wrist, lm[tip]) > distance2d(wrist, lm[pip]) * config.fingerExtensionRatio
    }

    fun isThumbExtended(lm: List<Landmark>): Boolean {
        val anchor = lm[H.PINKY_MCP]
        return distance2d(lm[H.THUMB_TIP], anchor) > distance2d(lm[H.THUMB_IP], anchor) * config.thumbExtensionRatio
    }

    companion object {
        /** Reference hand size: wrist to middle finger MCP. Stable across poses. */
        fun handSize(lm: List<Landmark>): Float = distance2d(lm[H.WRIST], lm[H.MIDDLE_MCP])

        /** Thumb tip to index tip distance in hand sizes; scale invariant, 2D only. */
        fun pinchRatio(lm: List<Landmark>): Float {
            val size = handSize(lm)
            return if (size <= 1e-6f) Float.MAX_VALUE else distance2d(lm[H.THUMB_TIP], lm[H.INDEX_TIP]) / size
        }
    }
}
