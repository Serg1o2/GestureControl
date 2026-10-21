package com.gesturecontrol.gestures.ml

import com.gesturecontrol.gestures.HandPose
import com.gesturecontrol.gestures.Landmark
import com.gesturecontrol.gestures.PalmConfig
import com.gesturecontrol.gestures.StaticGestureClassifier

data class PoseResult(val pose: HandPose, val confidence: Float)

/** Classifies the static hand pose of one frame. Implementations may keep state (smoothing). */
interface PoseClassifier {
    /** [aspect] = width / height of the frame the landmarks are normalized to. */
    fun classify(landmarks: List<Landmark>, aspect: Float): PoseResult

    /** Forget any temporal state (hand lost). */
    fun reset() = Unit
}

/** The hand-written geometric rules of stages 1-2. */
class RuleBasedPoseClassifier(config: PalmConfig = PalmConfig()) : PoseClassifier {
    private val rules = StaticGestureClassifier(config)
    override fun classify(landmarks: List<Landmark>, aspect: Float) = PoseResult(rules.classify(landmarks), 1f)
}

/** Raw per-frame model output: probabilities over the model's classes. */
interface PoseModel {
    /** Pose for each model output index (from the model metadata). */
    val classes: List<HandPose>

    /** [features] are [LandmarkNormalizer] output; returns probabilities. */
    fun predict(features: FloatArray): FloatArray
}

/**
 * Model-based classifier with output smoothing:
 * - a class counts only if its probability reaches its own threshold (otherwise OTHER);
 * - the reported pose is the majority over the last [window] frames (ties: most recent wins).
 */
class VotingPoseClassifier(
    private val model: PoseModel,
    private val thresholds: FloatArray,
    private val window: Int = 5,
) : PoseClassifier {
    private val features = FloatArray(LandmarkNormalizer.FEATURES)
    private val history = ArrayDeque<HandPose>()

    override fun classify(landmarks: List<Landmark>, aspect: Float): PoseResult {
        val probs = model.predict(LandmarkNormalizer.normalize(landmarks, aspect, features))
        var best = 0
        for (i in probs.indices) if (probs[i] > probs[best]) best = i
        val raw = if (probs[best] >= thresholds.getOrElse(best) { 0.5f }) model.classes[best] else HandPose.OTHER
        history.addLast(raw)
        while (history.size > window) history.removeFirst()
        val voted = vote()
        val confidence = if (voted == model.classes[best]) probs[best] else 0f
        return PoseResult(voted, confidence)
    }

    override fun reset() = history.clear()

    private fun vote(): HandPose {
        val counts = HashMap<HandPose, Int>()
        for (p in history) counts[p] = (counts[p] ?: 0) + 1
        val max = counts.values.max()
        // Most recent pose among the most frequent ones.
        return history.last { counts[it] == max }
    }
}

/** Maps the model's class names (from metadata) to [HandPose]; unknown names become OTHER. */
fun poseForModelClass(name: String): HandPose = runCatching { HandPose.valueOf(name) }.getOrDefault(HandPose.OTHER)
