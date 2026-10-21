package com.gesturecontrol.gestures.ml

import com.gesturecontrol.gestures.GestureEngine
import com.gesturecontrol.gestures.GestureConfig
import com.gesturecontrol.gestures.HandPose
import com.gesturecontrol.gestures.LandmarkCsv
import com.gesturecontrol.gestures.SyntheticHand
import com.gesturecontrol.gestures.SyntheticHand.Pose
import com.gesturecontrol.gestures.run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * The trained pose model on the JVM (weights exported by `python -m gc_ml.export_vectors --what weights`):
 * same outputs as Keras, sensible on canonical poses, and the engine with the model in the loop
 * produces the expected gestures on the recorded sequences.
 */
class PoseModelJvmTest {
    private val model = DenseMlp.parse(javaClass.classLoader.getResource("ml/pose_mlp_weights.txt")!!.readText())

    @Test
    fun matchesKerasOutputs() {
        val rows = javaClass.classLoader.getResource("ml/pose_mlp_vectors.csv")!!.readText()
            .lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
        assertTrue(rows.size >= 100)
        var maxDiff = 0f
        for (row in rows) {
            val v = row.split(',').map { it.toFloat() }.toFloatArray()
            val expected = v.copyOfRange(LandmarkNormalizer.FEATURES, v.size)
            val actual = model.predict(v.copyOfRange(0, LandmarkNormalizer.FEATURES))
            for (i in expected.indices) maxDiff = maxOf(maxDiff, abs(expected[i] - actual[i]))
        }
        assertEquals("max |kotlin - keras|", 0f, maxDiff, 1e-4f)
    }

    @Test
    fun recognizesCanonicalPoses() {
        fun top(p: Pose): HandPose {
            val probs = model.predict(LandmarkNormalizer.normalize(SyntheticHand.pose(p, 0.5f, 0.7f), 0.75f))
            return model.classes[probs.indices.maxBy { probs[it] }]
        }
        assertEquals(HandPose.OPEN_PALM, top(Pose.OPEN_PALM))
        assertEquals(HandPose.FIST, top(Pose.FIST))
        assertEquals(HandPose.POINTING, top(Pose.POINTING))
        assertEquals(HandPose.TWO_FINGERS, top(Pose.TWO_FINGERS))
    }

    @Test
    fun engineWithModelReplaysRecordedSequences() {
        val dir = File(javaClass.classLoader.getResource("sequences")!!.toURI())
        for (file in dir.listFiles { f -> f.extension == "csv" }!!.sortedBy { it.name }) {
            val text = file.readText()
            val expected = text.lineSequence().first { it.startsWith("# expect:") }
                .removePrefix("# expect:").split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val classifier = VotingPoseClassifier(model, floatArrayOf(0f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f))
            val actual = GestureEngine(GestureConfig(), classifier).run(LandmarkCsv.parse(text)).map { it.gesture.name }
            assertEquals(file.name, expected, actual)
        }
    }
}
