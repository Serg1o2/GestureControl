package com.gesturecontrol.gestures.ml

import com.gesturecontrol.gestures.HandPose
import com.gesturecontrol.gestures.Landmark

/**
 * The separate pinch model (assets/models/pinch_mlp, trained by ml/gc_ml/train_pinch.py): thumb and index
 * tips touching with any position of the other fingers ("ok" included). Used only by the volume slider,
 * so it cannot change the other gestures. Single-threaded, like the pose model.
 */
class PinchDetector(private val model: PoseModel, val threshold: Float) {
    private val index = model.classes.indexOf(HandPose.PINCH)
    private val features = FloatArray(LandmarkNormalizer.FEATURES)

    init {
        require(index >= 0) { "pinch model has no PINCH class: ${model.classes}" }
    }

    fun probability(landmarks: List<Landmark>, aspect: Float): Float =
        model.predict(LandmarkNormalizer.normalize(landmarks, aspect, features))[index]

    fun isPinch(landmarks: List<Landmark>, aspect: Float): Boolean = probability(landmarks, aspect) >= threshold
}
