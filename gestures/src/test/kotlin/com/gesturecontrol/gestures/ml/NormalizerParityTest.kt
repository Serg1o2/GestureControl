package com.gesturecontrol.gestures.ml

import com.gesturecontrol.gestures.Landmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * LandmarkNormalizer must match ml/gc_ml/normalize.py exactly. The vectors are exported by
 * `python -m gc_ml.export_vectors --what norm` (real HaGRID hands plus their mirror images).
 */
class NormalizerParityTest {
    @Test
    fun matchesPythonNormalizer() {
        val text = javaClass.classLoader.getResource("ml/norm_vectors.csv")!!.readText()
        val rows = text.lineSequence().filter { it.isNotBlank() && !it.startsWith("#") }.toList()
        assertTrue("no vectors", rows.size >= 50)
        var maxDiff = 0f
        for (row in rows) {
            val v = row.split(',').map { it.toFloat() }
            val aspect = v[0]
            val landmarks = List(21) { i -> Landmark(v[1 + i * 2], v[2 + i * 2]) }
            val expected = v.subList(43, 85)
            val actual = LandmarkNormalizer.normalize(landmarks, aspect)
            for (i in expected.indices) maxDiff = maxOf(maxDiff, abs(expected[i] - actual[i]))
        }
        assertEquals("max |kotlin - python|", 0f, maxDiff, 1e-4f)
    }
}
