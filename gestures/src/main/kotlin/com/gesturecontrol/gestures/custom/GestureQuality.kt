package com.gesturecontrol.gestures.custom

import com.gesturecontrol.gestures.Gesture
import com.gesturecontrol.gestures.GestureEngine
import com.gesturecontrol.gestures.HandFrame

/** Result of checking a freshly recorded custom gesture. */
data class QualityReport(
    /** Typical distance between repeats; becomes the template radius. */
    val radius: Float,
    /** Worst repeat: the one farthest from the others (0-based), when it is an outlier. */
    val outlierIndex: Int?,
    /** Closest other custom gesture and how many radii away it is. */
    val nearestOther: String?,
    val separation: Float?,
    /** Built-in gestures that fire while performing this gesture. */
    val builtInConflicts: Set<Gesture>,
) {
    val consistent: Boolean get() = outlierIndex == null
    val distinct: Boolean get() = separation == null || separation >= MIN_SEPARATION
    val ok: Boolean get() = consistent && distinct && builtInConflicts.isEmpty()

    companion object {
        /** Another gesture closer than this many radii is reported as a conflict. */
        const val MIN_SEPARATION = 2.0f
    }
}

object GestureQuality {
    /** Mean leave-one-out distance between repeats (robust radius), floored to avoid zero thresholds. */
    fun radius(samples: List<GestureSample>): Float {
        if (samples.size < 2) return MIN_RADIUS
        val means = samples.indices.map { i -> meanDistanceToOthers(samples, i) }
        return maxOf(means.sorted()[means.size / 2], MIN_RADIUS)
    }

    /**
     * @param recordings raw frames of each repeat (replayed through [builtInEngine] to find conflicts)
     * @param others the other custom gestures of the same profile
     */
    fun check(
        samples: List<GestureSample>,
        recordings: List<List<HandFrame>>,
        others: List<CustomTemplate>,
        builtInEngine: () -> GestureEngine,
    ): QualityReport {
        val radius = radius(samples)
        val means = samples.indices.map { meanDistanceToOthers(samples, it) }
        val worst = means.indices.maxByOrNull { means[it] }
        val outlier = worst?.takeIf { samples.size >= 3 && means[it] > radius * OUTLIER_FACTOR }

        var nearest: String? = null
        var separation: Float? = null
        for (other in others) {
            val d = samples.minOf { s -> other.samples.minOf { o -> Dtw.distance(s, o) } }
            val sep = d / maxOf(radius, other.radius)
            if (separation == null || sep < separation) {
                separation = sep
                nearest = other.name
            }
        }

        val conflicts = recordings.flatMap { frames ->
            val engine = builtInEngine()
            frames.mapNotNull { engine.process(it)?.gesture }
        }.toSet()
        return QualityReport(radius, outlier, nearest, separation, conflicts)
    }

    private fun meanDistanceToOthers(samples: List<GestureSample>, i: Int): Float =
        samples.indices.filter { it != i }.map { Dtw.distance(samples[i], samples[it]) }.average().toFloat()

    private const val MIN_RADIUS = 0.3f
    private const val OUTLIER_FACTOR = 2.0f
}
