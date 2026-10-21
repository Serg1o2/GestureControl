package com.gesturecontrol.gestures.custom

/**
 * Text encoding of gesture samples for storage and JSON export:
 * samples separated by ';', each "durationMs|v,v,v..." with steps*dims values (row-major).
 * Values are rounded to 4 decimals (far below detector noise).
 */
object SampleCodec {
    fun encode(samples: List<GestureSample>): String = samples.joinToString(";") { s ->
        s.durationMs.toString() + "|" + s.steps.joinToString(",") { step -> step.joinToString(",") { "%.4f".format(java.util.Locale.ROOT, it) } }
    }

    fun decode(text: String): List<GestureSample> {
        if (text.isBlank()) return emptyList()
        return text.split(';').map { part ->
            val duration = part.substringBefore('|').toLong()
            val values = part.substringAfter('|').split(',').map { it.toFloat() }
            require(values.size == SampleBuilder.POINTS * SampleBuilder.DIMS) { "Bad sample size ${values.size}" }
            val steps = Array(SampleBuilder.POINTS) { i ->
                FloatArray(SampleBuilder.DIMS) { d -> values[i * SampleBuilder.DIMS + d] }
            }
            GestureSample(steps, duration)
        }
    }
}
