package com.gesturecontrol.vision

import android.content.Context
import com.gesturecontrol.gestures.ml.PinchDetector
import com.gesturecontrol.gestures.ml.PoseClassifier
import com.gesturecontrol.gestures.ml.RuleBasedPoseClassifier
import com.gesturecontrol.gestures.ml.VotingPoseClassifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Which pose classifier the engines use. */
enum class ClassifierKind { RULES, MODEL }

/** What is actually running (the model may be unavailable and fall back to rules). */
data class ClassifierStatus(val requested: ClassifierKind, val active: ClassifierKind, val modelVersion: String?)

/**
 * A set of pose classifiers for one analyzer: separate smoothing state per engine, one model; plus the
 * separate pinch model for the volume slider (loaded with either classifier kind).
 */
class PoseClassifiers(
    val forGestures: PoseClassifier,
    val forPointer: PoseClassifier,
    private val model: TfLitePoseModel?,
    private val pinchModel: TfLitePoseModel? = null,
) {
    val pinch: PinchDetector? = pinchModel?.let { PinchDetector(it, it.meta.thresholds.getOrElse(1) { 0.5f }) }

    /** Release the models on the analysis thread after the camera is unbound. */
    fun close() {
        model?.close()
        pinchModel?.close()
    }
}

@Singleton
class PoseClassifierFactory @Inject constructor(@ApplicationContext private val context: Context) {
    private val _status = MutableStateFlow(ClassifierStatus(ClassifierKind.RULES, ClassifierKind.RULES, null))
    val status: StateFlow<ClassifierStatus> = _status.asStateFlow()

    /** Falls back to rules when the model cannot be loaded. */
    fun create(kind: ClassifierKind): PoseClassifiers {
        val model = if (kind == ClassifierKind.MODEL) TfLitePoseModel.load(context) else null
        val pinch = TfLitePoseModel.load(context, PINCH_MODEL)
        _status.value = ClassifierStatus(kind, if (model != null) ClassifierKind.MODEL else ClassifierKind.RULES, model?.meta?.version)
        if (model == null) return PoseClassifiers(RuleBasedPoseClassifier(), RuleBasedPoseClassifier(), null, pinch)
        val thresholds = model.meta.thresholds
        return PoseClassifiers(
            forGestures = VotingPoseClassifier(model, thresholds),
            forPointer = VotingPoseClassifier(model, thresholds),
            model = model,
            pinchModel = pinch,
        )
    }

    private companion object {
        const val PINCH_MODEL = "pinch_mlp"
    }
}
