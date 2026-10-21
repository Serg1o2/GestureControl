package com.gesturecontrol.gestures.ml

import com.gesturecontrol.gestures.HandLandmarks
import com.gesturecontrol.gestures.Landmark
import kotlin.math.sqrt

/**
 * Kotlin twin of ml/gc_ml/normalize.py ("norm-v1"). Must produce identical features;
 * NormalizerParityTest checks it against vectors exported by Python.
 *
 * 1. Isotropic coordinates: X = x * aspect (width / height), Y = y.
 * 2. Origin at the wrist.
 * 3. Divide by the wrist -> middle MCP distance.
 * 4. Mirror X when the palm triangle (index MCP, pinky MCP around the wrist) is clockwise,
 *    so left / right hands and mirrored cameras share one chirality. Rotation is kept.
 */
object LandmarkNormalizer {
    const val VERSION = "norm-v1"
    const val FEATURES = HandLandmarks.COUNT * 2
    private const val EPS = 1e-6f

    fun normalize(landmarks: List<Landmark>, aspect: Float, out: FloatArray = FloatArray(FEATURES)): FloatArray {
        require(landmarks.size == HandLandmarks.COUNT)
        val wx = landmarks[HandLandmarks.WRIST].x * aspect
        val wy = landmarks[HandLandmarks.WRIST].y

        fun isoX(i: Int) = landmarks[i].x * aspect - wx
        fun isoY(i: Int) = landmarks[i].y - wy

        val mx = isoX(HandLandmarks.MIDDLE_MCP)
        val my = isoY(HandLandmarks.MIDDLE_MCP)
        val size = maxOf(sqrt(mx * mx + my * my), EPS)

        val ax = isoX(HandLandmarks.INDEX_MCP)
        val ay = isoY(HandLandmarks.INDEX_MCP)
        val bx = isoX(HandLandmarks.PINKY_MCP)
        val by = isoY(HandLandmarks.PINKY_MCP)
        val sign = if (ax * by - ay * bx < 0f) -1f else 1f

        for (i in 0 until HandLandmarks.COUNT) {
            out[i * 2] = isoX(i) / size * sign
            out[i * 2 + 1] = isoY(i) / size
        }
        return out
    }
}
