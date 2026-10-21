package com.gesturecontrol.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class OneEuroFilterTest {

    @Test
    fun constantSignalIsUnchanged() {
        val f = OneEuroFilter(1.5f, 0.5f, 1f)
        repeat(30) { i -> assertEquals(0.5f, f.filter(0.5f, i * 33L), 1e-6f) }
    }

    @Test
    fun jitterIsReduced() {
        val f = OneEuroFilter(1.5f, 0.5f, 1f)
        val rnd = Random(1)
        var rawVar = 0f
        var filteredVar = 0f
        for (i in 0 until 300) {
            val raw = 0.5f + (rnd.nextFloat() - 0.5f) * 0.02f
            val out = f.filter(raw, i * 33L)
            if (i > 30) {
                rawVar += (raw - 0.5f) * (raw - 0.5f)
                filteredVar += (out - 0.5f) * (out - 0.5f)
            }
        }
        assertTrue("filtered=$filteredVar raw=$rawVar", filteredVar < rawVar * 0.5f)
    }

    @Test
    fun fastMotionIsFollowedWithSmallLag() {
        val f = OneEuroFilter(1.5f, 0.5f, 1f)
        var out = 0f
        // Linear motion of 1.5 units/s for 300 ms.
        for (i in 0..9) out = f.filter(i * 0.05f, i * 33L)
        assertTrue("lag too large: ${0.45f - out}", abs(0.45f - out) < 0.1f)
    }

    @Test
    fun resetForgetsHistory() {
        val f = OneEuroFilter(1.5f, 0.5f, 1f)
        f.filter(0f, 0)
        f.filter(0f, 33)
        f.reset()
        assertEquals(1f, f.filter(1f, 66), 1e-6f)
    }
}
