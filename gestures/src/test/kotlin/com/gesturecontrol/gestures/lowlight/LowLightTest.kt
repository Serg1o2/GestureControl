package com.gesturecontrol.gestures.lowlight

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class LowLightTest {
    private val probe = intArrayOf(0, 1, 5, 10, 32, 64, 128, 200, 255)

    /** Reference values from `gamma_lut` in ml/gc_ml/lowlight.py (target 0.45, gamma 0.3..1). */
    @Test
    fun gammaLutMatchesPython() {
        val expected = mapOf(
            0.05f to intArrayOf(0, 48, 78, 97, 137, 168, 207, 237, 255),
            0.18f to intArrayOf(0, 19, 41, 56, 97, 134, 185, 228, 255),
            0.29f to intArrayOf(0, 7, 20, 32, 67, 105, 163, 218, 255),
            0.6f to intArrayOf(0, 1, 5, 10, 32, 64, 128, 200, 255),
        )
        for ((luma, values) in expected) {
            val lut = LowLight.gammaLut(luma, targetLuma = 0.45f, minGamma = 0.3f)
            assertArrayEquals("luma $luma", values, probe.map { lut[it] }.toIntArray())
        }
    }

    @Test
    fun brightFramesAreNeverDarkened() {
        val lut = LowLight.gammaLut(0.8f, targetLuma = 0.45f, minGamma = 0.3f)
        for (i in 0..255) assertEquals(i, lut[i])
    }

    @Test
    fun lutKeepsAlphaAndMapsEachChannel() {
        val lut = IntArray(256) { 255 - it }
        val pixels = intArrayOf(0x80102030.toInt())
        LowLight.applyLut(pixels, lut)
        assertEquals(0x80EFDFCF.toInt(), pixels[0])
    }

    @Test
    fun meanLumaOfGrey() {
        val grey = IntArray(16 * 8) { 0xFF404040.toInt() }
        assertEquals(64f / 255f, LowLight.meanLuma(grey, 16, 8), 1e-3f)
    }
}

class AeFpsRangeTest {
    @Test
    fun picksTheLowestRangeUpToThirtyWithoutGoingUnderFifteen() {
        val ranges = listOf(7 to 30, 15 to 30, 20 to 30, 30 to 30, 15 to 15, 24 to 24)
        assertEquals(15 to 30, AeFpsRange.choose(ranges))
    }

    @Test
    fun keepsTheDefaultWhenNothingFits() {
        assertEquals(null, AeFpsRange.choose(listOf(7 to 30, 30 to 30, 15 to 15)))
    }
}
