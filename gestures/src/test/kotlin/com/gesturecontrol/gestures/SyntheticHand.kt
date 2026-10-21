package com.gesturecontrol.gestures

import kotlin.random.Random

/** Generates plausible synthetic hand landmarks and frame sequences for tests. */
object SyntheticHand {
    // Offsets from the wrist for an upright open palm (y grows downward), hand height ~0.26.
    private val OPEN_PALM = listOf(
        0f to 0f,
        -0.04f to -0.03f, -0.07f to -0.06f, -0.09f to -0.09f, -0.11f to -0.12f,
        -0.035f to -0.13f, -0.04f to -0.18f, -0.042f to -0.21f, -0.045f to -0.24f,
        0f to -0.135f, 0f to -0.19f, 0f to -0.225f, 0f to -0.26f,
        0.03f to -0.125f, 0.035f to -0.175f, 0.038f to -0.205f, 0.04f to -0.235f,
        0.055f to -0.11f, 0.065f to -0.145f, 0.07f to -0.17f, 0.075f to -0.19f,
    )

    // Fist: fingertips curled back toward the palm, thumb folded across.
    private val FIST = listOf(
        0f to 0f,
        -0.04f to -0.03f, -0.06f to -0.06f, -0.04f to -0.09f, -0.015f to -0.10f,
        -0.035f to -0.13f, -0.04f to -0.15f, -0.035f to -0.12f, -0.03f to -0.10f,
        0f to -0.135f, 0f to -0.155f, 0f to -0.125f, 0f to -0.105f,
        0.03f to -0.125f, 0.035f to -0.145f, 0.033f to -0.115f, 0.03f to -0.098f,
        0.055f to -0.11f, 0.06f to -0.125f, 0.058f to -0.10f, 0.055f to -0.088f,
    )

    enum class Pose { OPEN_PALM, FIST }

    /** Landmarks with the wrist at (wx, wy). */
    fun pose(pose: Pose, wx: Float, wy: Float, scale: Float = 1f, noise: Float = 0f, rnd: Random = Random(0)): List<Landmark> {
        val offsets = if (pose == Pose.OPEN_PALM) OPEN_PALM else FIST
        return offsets.map { (dx, dy) ->
            Landmark(
                x = wx + dx * scale + if (noise > 0f) (rnd.nextFloat() - 0.5f) * 2 * noise else 0f,
                y = wy + dy * scale + if (noise > 0f) (rnd.nextFloat() - 0.5f) * 2 * noise else 0f,
            )
        }
    }

    /** Builder for frame sequences at a fixed frame rate. */
    class Sequence(private val fps: Int = 30, private val noise: Float = 0.002f, seed: Int = 42) {
        private val rnd = Random(seed)
        private val frames = mutableListOf<HandFrame>()
        var t = 0L
            private set
        private val stepMs get() = 1000L / fps

        /** Holds a static pose for [durationMs]. */
        fun hold(pose: Pose, x: Float, y: Float, durationMs: Long) = apply {
            val end = t + durationMs
            while (t < end) {
                frames += HandFrame(t, pose(pose, x, y, noise = noise, rnd = rnd))
                t += stepMs
            }
        }

        /** Moves the wrist linearly from (x0, y0) to (x1, y1) during [durationMs]. */
        fun move(pose: Pose, x0: Float, y0: Float, x1: Float, y1: Float, durationMs: Long) = apply {
            val start = t
            val end = t + durationMs
            while (t <= end) {
                val k = (t - start).toFloat() / durationMs
                frames += HandFrame(t, pose(pose, x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, noise = noise, rnd = rnd))
                t += stepMs
            }
        }

        fun noHand(durationMs: Long) = apply {
            val end = t + durationMs
            while (t < end) {
                frames += HandFrame(t, null)
                t += stepMs
            }
        }

        fun build(): List<HandFrame> = frames.toList()
    }
}

fun GestureEngine.run(frames: List<HandFrame>): List<GestureEvent> = frames.mapNotNull { process(it) }
