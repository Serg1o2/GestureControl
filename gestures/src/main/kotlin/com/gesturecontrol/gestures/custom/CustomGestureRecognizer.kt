package com.gesturecontrol.gestures.custom

import com.gesturecontrol.gestures.HandFrame

/**
 * A user-defined gesture: 3-5 recorded samples plus its own tuning.
 * @param radius typical distance between the recorded repeats (computed by [GestureQuality.radius]).
 * @param sensitivity 0..1; higher = fires more easily (threshold from 1.2x to 2.4x the radius).
 */
data class CustomTemplate(
    val id: Long,
    val name: String,
    val samples: List<GestureSample>,
    val radius: Float,
    val sensitivity: Float = 0.5f,
    val cooldownMs: Long = 1000,
) {
    val threshold: Float get() = radius * (1.2f + 1.2f * sensitivity.coerceIn(0f, 1f))
}

data class CustomMatch(
    val template: CustomTemplate,
    val distance: Float,
    /** 1 at a perfect match, 0 at the threshold, negative beyond it. */
    val confidence: Float,
)

/**
 * Online recognizer: keeps the last few seconds of frames and, on each frame, compares the most
 * recent window (at 0.8x, 1x, 1.25x each sample's duration) to every template by DTW.
 * A template fires when its best distance is under its threshold, its cooldown has passed and it
 * was re-armed (distance went above 1.5x the threshold since the last firing).
 */
class CustomGestureRecognizer(templates: List<CustomTemplate>, private val evaluateEveryMs: Long = 50) {
    @Volatile
    var templates: List<CustomTemplate> = templates

    private val history = ArrayDeque<FeatureFrame>()
    private val lastFired = HashMap<Long, Long>()
    private val armed = HashMap<Long, Boolean>()
    private var lastEvalTs = Long.MIN_VALUE / 2

    /** Best match of the last evaluation (for the "Test" indicator), fired or not. */
    var lastBest: CustomMatch? = null
        private set

    fun process(frame: HandFrame): CustomMatch? {
        val f = SampleBuilder.featureFrame(frame)
        if (f == null) {
            if (history.isNotEmpty() && frame.timestampMs - history.last().t > HAND_LOST_MS) history.clear()
            return null
        }
        history.addLast(f)
        while (f.t - history.first().t > HISTORY_MS) history.removeFirst()
        if (f.t - lastEvalTs < evaluateEveryMs) return null
        lastEvalTs = f.t

        var fired: CustomMatch? = null
        var best: CustomMatch? = null
        for (template in templates) {
            val d = bestDistance(template, f.t) ?: continue
            val match = CustomMatch(template, d, 1f - d / template.threshold)
            if (best == null || match.confidence > best.confidence) best = match
            if (d > template.threshold * REARM_FACTOR) armed[template.id] = true
            val ready = armed[template.id] != false &&
                f.t - (lastFired[template.id] ?: Long.MIN_VALUE / 2) >= template.cooldownMs
            if (d <= template.threshold && ready && (fired == null || match.confidence > fired.confidence)) fired = match
        }
        lastBest = best
        fired?.let {
            lastFired[it.template.id] = f.t
            armed[it.template.id] = false
        }
        return fired
    }

    fun reset() {
        history.clear()
        lastBest = null
    }

    private fun bestDistance(template: CustomTemplate, now: Long): Float? {
        var best: Float? = null
        for (sample in template.samples) {
            for (scale in SCALES) {
                val span = (sample.durationMs * scale).toLong()
                if (history.isEmpty() || now - history.first().t < span * 0.9f) continue
                val window = history.filter { now - it.t <= span }
                val observed = SampleBuilder.build(window) ?: continue
                val d = Dtw.distance(observed, sample)
                if (best == null || d < best) best = d
            }
        }
        return best
    }

    private companion object {
        val SCALES = floatArrayOf(0.8f, 1f, 1.25f)
        const val HISTORY_MS = 4000L
        const val HAND_LOST_MS = 300L
        const val REARM_FACTOR = 1.5f
    }
}
