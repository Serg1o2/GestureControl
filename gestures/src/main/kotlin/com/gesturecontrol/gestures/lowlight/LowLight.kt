package com.gesturecontrol.gestures.lowlight

import kotlin.math.ln
import kotlin.math.pow

/**
 * Brightening of dark camera frames before hand detection. Brightness is the mean Rec.601 luma in [0, 1].
 * Off by default: on simulated low light it lowers MediaPipe's hand recall (ml/gc_ml/lowlight.py,
 * docs/lowlight.md); kept for experiments on the device.
 */
data class LowLightConfig(
    val enabled: Boolean = false,
    /** Frames darker than this are brightened. */
    val darkLuma: Float = 0.30f,
    /** The gamma curve maps the frame's mean luma to this value. */
    val targetLuma: Float = 0.45f,
    /** Strongest allowed curve (smaller = brighter shadows, more amplified noise). */
    val minGamma: Float = 0.3f,
)

object LowLight {
    /**
     * 256-entry lookup table out = in^g with g chosen so that [luma] maps to [targetLuma]; never darkens.
     * Twin of `gamma_lut` in ml/gc_ml/lowlight.py (same rounding), checked by LowLightTest.
     */
    fun gammaLut(luma: Float, targetLuma: Float, minGamma: Float, maxGamma: Float = 1f): IntArray {
        val l = luma.coerceIn(1e-3f, 0.999f).toDouble()
        val g = (ln(targetLuma.toDouble()) / ln(l)).coerceIn(minGamma.toDouble(), maxGamma.toDouble())
        return IntArray(256) { i -> ((i / 255.0).pow(g) * 255.0 + 0.5).toInt() }
    }

    /** Rec.601 luma of a packed ARGB pixel, in [0, 255]. */
    fun luma(argb: Int): Float =
        0.299f * ((argb shr 16) and 0xFF) + 0.587f * ((argb shr 8) and 0xFF) + 0.114f * (argb and 0xFF)

    /**
     * Mean luma in [0, 1] over every [step]-th pixel of every [step]-th row of packed ARGB [pixels]
     * (width [width]); the same 4x grid as `mean_luma` in lowlight.py.
     */
    fun meanLuma(pixels: IntArray, width: Int, height: Int, step: Int = 4): Float {
        var sum = 0.0
        var n = 0
        var y = 0
        while (y < height) {
            var i = y * width
            val end = i + width
            while (i < end) {
                sum += luma(pixels[i])
                n++
                i += step
            }
            y += step
        }
        return if (n == 0) 0f else (sum / n / 255.0).toFloat()
    }

    /** Applies [lut] to the R, G and B channels of packed ARGB [pixels] in place. */
    fun applyLut(pixels: IntArray, lut: IntArray) {
        for (i in pixels.indices) {
            val p = pixels[i]
            pixels[i] = (p and 0xFF000000.toInt()) or
                (lut[(p shr 16) and 0xFF] shl 16) or
                (lut[(p shr 8) and 0xFF] shl 8) or
                lut[p and 0xFF]
        }
    }
}

/**
 * Auto-exposure frame-rate range for the analysis camera. Allowing the camera to slow down to
 * [minFps] in the dark doubles the exposure time per frame (less sensor noise, see docs/lowlight.md);
 * in good light auto-exposure still runs at [maxFps].
 */
object AeFpsRange {
    /**
     * Picks from the device's ranges (lower, upper) the one that tops out at [maxFps] and goes lowest
     * without dropping under [minFps]. Null = keep the camera's default (nothing suitable).
     */
    fun choose(available: List<Pair<Int, Int>>, minFps: Int = 15, maxFps: Int = 30): Pair<Int, Int>? =
        available.filter { (lo, hi) -> hi == maxFps && lo >= minFps && lo < hi }.minByOrNull { it.first }
}
