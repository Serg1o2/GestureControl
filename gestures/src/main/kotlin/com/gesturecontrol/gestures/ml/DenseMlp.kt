package com.gesturecontrol.gestures.ml

import com.gesturecontrol.gestures.HandPose
import kotlin.math.exp

/**
 * Pure-Kotlin evaluation of the pose MLP from weights exported by ml/gc_ml/export_vectors.py
 * (`--what weights`). Used by JVM unit tests (TFLite cannot run there) to check the model against
 * Python and to replay recorded sequences through the engines with the model in the loop.
 *
 * Text format: `classes A,B,...` then per layer `layer <in> <out> <relu|softmax>`, `<in>` lines of
 * `<out>` weights, one line of `<out>` biases.
 */
class DenseMlp private constructor(
    private val layers: List<Layer>,
    override val classes: List<HandPose>,
) : PoseModel {
    private class Layer(val w: Array<FloatArray>, val b: FloatArray, val activation: String)

    override fun predict(features: FloatArray): FloatArray {
        var x = features
        for (layer in layers) {
            val out = layer.b.copyOf()
            for (i in x.indices) {
                val xi = x[i]
                if (xi == 0f) continue
                val row = layer.w[i]
                for (j in out.indices) out[j] += xi * row[j]
            }
            when (layer.activation) {
                "relu" -> for (j in out.indices) if (out[j] < 0f) out[j] = 0f
                "softmax" -> {
                    val max = out.max()
                    var sum = 0f
                    for (j in out.indices) {
                        out[j] = exp(out[j] - max)
                        sum += out[j]
                    }
                    for (j in out.indices) out[j] /= sum
                }
            }
            x = out
        }
        return x
    }

    companion object {
        fun parse(text: String): DenseMlp {
            val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.iterator()
            val classes = lines.next().removePrefix("classes ").split(',').map(::poseForModelClass)
            val layers = mutableListOf<Layer>()
            while (lines.hasNext()) {
                val (_, nIn, nOut, act) = lines.next().split(' ')
                val w = Array(nIn.toInt()) { lines.next().split(',').map { it.toFloat() }.toFloatArray() }
                val b = lines.next().split(',').map { it.toFloat() }.toFloatArray()
                require(w.all { it.size == nOut.toInt() } && b.size == nOut.toInt())
                layers += Layer(w, b, act)
            }
            return DenseMlp(layers, classes)
        }
    }
}
