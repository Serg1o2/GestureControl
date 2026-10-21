package com.gesturecontrol.vision

import android.content.Context
import android.util.Log
import com.gesturecontrol.gestures.HandPose
import com.gesturecontrol.gestures.ml.LandmarkNormalizer
import com.gesturecontrol.gestures.ml.PoseModel
import com.gesturecontrol.gestures.ml.poseForModelClass
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** Metadata shipped next to the model (assets/models/<name>.json), written by ml/gc_ml/train_static.py. */
data class PoseModelMeta(
    val name: String,
    val version: String,
    val norm: String,
    val input: Int,
    val classNames: List<String>,
    val thresholds: FloatArray,
)

/**
 * TFLite pose MLP. Single-threaded: use from the analysis thread only.
 * [load] returns null (and logs why) when the model is missing or incompatible, so callers
 * fall back to the rule-based classifier.
 */
class TfLitePoseModel private constructor(
    private val interpreter: Interpreter,
    val meta: PoseModelMeta,
) : PoseModel, Closeable {
    override val classes: List<HandPose> = meta.classNames.map(::poseForModelClass)
    private val input = Array(1) { FloatArray(meta.input) }
    private val output = Array(1) { FloatArray(meta.classNames.size) }

    override fun predict(features: FloatArray): FloatArray {
        features.copyInto(input[0])
        interpreter.run(input, output)
        return output[0]
    }

    override fun close() = interpreter.close()

    companion object {
        private const val TAG = "TfLitePoseModel"
        const val DEFAULT_NAME = "pose_mlp"

        fun load(context: Context, name: String = DEFAULT_NAME): TfLitePoseModel? = try {
            val meta = readMeta(context, "models/$name.json")
            when {
                meta.norm != LandmarkNormalizer.VERSION -> {
                    Log.w(TAG, "Model normalization ${meta.norm} != app ${LandmarkNormalizer.VERSION}")
                    null
                }
                meta.input != LandmarkNormalizer.FEATURES -> {
                    Log.w(TAG, "Model input ${meta.input} != ${LandmarkNormalizer.FEATURES}")
                    null
                }
                else -> TfLitePoseModel(Interpreter(mapAsset(context, "models/$name.tflite"), Interpreter.Options().setNumThreads(1)), meta)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cannot load model $name", e)
            null
        }

        fun readMeta(context: Context, path: String): PoseModelMeta {
            val json = JSONObject(context.assets.open(path).bufferedReader().use { it.readText() })
            val classes = json.getJSONArray("classes")
            val thresholds = json.getJSONArray("thresholds")
            return PoseModelMeta(
                name = json.getString("name"),
                version = json.getString("version"),
                norm = json.getString("norm"),
                input = json.getInt("input"),
                classNames = List(classes.length()) { classes.getString(it) },
                thresholds = FloatArray(thresholds.length()) { thresholds.getDouble(it).toFloat() },
            )
        }

        private fun mapAsset(context: Context, path: String): MappedByteBuffer {
            context.assets.openFd(path).use { fd ->
                FileInputStream(fd.fileDescriptor).channel.use { channel ->
                    return channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        }
    }
}
