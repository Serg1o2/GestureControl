package com.gesturecontrol

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gesturecontrol.gestures.ml.LandmarkNormalizer
import com.gesturecontrol.vision.TfLitePoseModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * On-device check that the shipped TFLite model gives the same probabilities as Python
 * (vectors from `python -m gc_ml.export_vectors --what model`) and fits the latency budget.
 * Run: ./gradlew connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class PoseModelParityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testAssets = InstrumentationRegistry.getInstrumentation().context.assets

    @Test
    fun outputsMatchPythonAndLatencyIsUnderBudget() {
        val model = TfLitePoseModel.load(context)
        assertNotNull("model failed to load", model)
        model!!
        val rows = testAssets.open("pose_vectors.csv").bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
        val n = LandmarkNormalizer.FEATURES
        var maxDiff = 0f
        var argmaxAgree = 0
        for (row in rows) {
            val v = row.split(',').map { it.toFloat() }.toFloatArray()
            val expected = v.copyOfRange(n, v.size)
            val actual = model.predict(v.copyOfRange(0, n)).copyOf()
            for (i in expected.indices) maxDiff = maxOf(maxDiff, abs(expected[i] - actual[i]))
            if (expected.indices.maxBy { expected[it] } == actual.indices.maxBy { actual[it] }) argmaxAgree++
        }
        assertEquals("all argmax equal", rows.size, argmaxAgree)
        assertTrue("max |Δp| = $maxDiff", maxDiff < 1e-3f)

        // Latency: normalization + inference, after warm-up.
        val features = rows.first().split(',').take(n).map { it.toFloat() }.toFloatArray()
        repeat(50) { model.predict(features) }
        val runs = 1000
        val start = SystemClock.elapsedRealtimeNanos()
        repeat(runs) { model.predict(features) }
        val perCallMs = (SystemClock.elapsedRealtimeNanos() - start) / 1e6 / runs
        Log.i("PoseModelParityTest", "inference: %.4f ms per call, max |Δp| = %.2e".format(perCallMs, maxDiff))
        assertTrue("inference $perCallMs ms exceeds the 5 ms budget", perCallMs < 5.0)
        model.close()
    }
}
