package com.gesturecontrol.gestures.mapping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenMapperTest {
    private fun assertPoint(expected: NormPoint, actual: NormPoint, eps: Float = 1e-4f) {
        assertEquals("x", expected.x, actual.x, eps)
        assertEquals("y", expected.y, actual.y, eps)
    }

    @Test
    fun defaultRegionStretchesToScreen() {
        val m = ScreenMapper.fromRegion(NormRect(0.2f, 0.2f, 0.8f, 0.8f))
        assertPoint(NormPoint(0f, 0f), m.map(NormPoint(0.2f, 0.2f)))
        assertPoint(NormPoint(0.5f, 0.5f), m.map(NormPoint(0.5f, 0.5f)))
        assertPoint(NormPoint(1f, 1f), m.map(NormPoint(0.8f, 0.8f)))
    }

    @Test
    fun pointsOutsideRegionAreClamped() {
        val m = ScreenMapper.fromRegion()
        assertPoint(NormPoint(0f, 1f), m.map(NormPoint(0.05f, 0.99f)))
    }

    @Test
    fun calibrationHitsTargetsExactly() {
        val frame = listOf(NormPoint(0.3f, 0.25f), NormPoint(0.7f, 0.3f), NormPoint(0.52f, 0.75f))
        val m = Calibration.solve(frame)
        assertNotNull(m)
        m!!
        frame.zip(Calibration.TARGETS).forEach { (f, s) -> assertPoint(s, m.map(f)) }
    }

    @Test
    fun calibrationRecoversKnownAffineTransform() {
        val truth = ScreenMapper(1.8f, 0.1f, -0.3f, -0.05f, 2.1f, -0.4f)
        val frame = listOf(NormPoint(0.25f, 0.3f), NormPoint(0.7f, 0.28f), NormPoint(0.45f, 0.7f))
        val screen = frame.map { truth.mapRaw(it) }
        val m = Calibration.solve(frame, screen)!!
        val probe = NormPoint(0.5f, 0.5f)
        assertPoint(truth.mapRaw(probe), m.mapRaw(probe))
    }

    @Test
    fun movingFingerToUserRightMovesCursorRight() {
        // User-view coordinates are mirrored upstream: a hand moving to the user's right has growing x.
        val m = Calibration.solve(
            listOf(NormPoint(0.3f, 0.3f), NormPoint(0.7f, 0.3f), NormPoint(0.5f, 0.7f)),
        )!!
        val left = m.map(NormPoint(0.4f, 0.5f))
        val right = m.map(NormPoint(0.6f, 0.5f))
        assertTrue("cursor must move right: $left -> $right", right.x > left.x)
    }

    @Test
    fun degenerateCalibrationIsRejected() {
        val collinear = listOf(NormPoint(0.3f, 0.3f), NormPoint(0.5f, 0.5f), NormPoint(0.7f, 0.7f))
        val tooSmall = listOf(NormPoint(0.5f, 0.5f), NormPoint(0.52f, 0.5f), NormPoint(0.51f, 0.53f))
        assertNull(Calibration.solve(collinear))
        assertNull(Calibration.solve(tooSmall))
    }

    @Test
    fun serializationRoundTrip() {
        val m = ScreenMapper(1.5f, 0.01f, -0.25f, 0.02f, 1.7f, -0.33f)
        assertEquals(m, ScreenMapper.parse(m.serialize()))
        assertNull(ScreenMapper.parse("1,2,3"))
    }

    @Test
    fun rectOffsetStaysOnScreenAndKeepsSize() {
        val r = NormRect(0.1f, 0.8f, 0.3f, 0.9f)
        val moved = r.offsetWithin(-0.5f, 0.5f)
        assertEquals(0f, moved.left, 1e-6f)
        assertEquals(1f, moved.bottom, 1e-6f)
        assertEquals(r.width, moved.width, 1e-6f)
        assertEquals(r.height, moved.height, 1e-6f)
        val inside = r.offsetWithin(0.2f, -0.3f)
        assertEquals(NormPoint(0.3f, 0.5f).x, inside.left, 1e-6f)
        assertEquals(0.5f, inside.top, 1e-6f)
    }

    @Test
    fun rectFromCornersNormalizesAndContains() {
        val r = NormRect.fromCorners(NormPoint(0.8f, 0.6f), NormPoint(0.2f, 1.3f))
        assertEquals(NormRect(0.2f, 0.6f, 0.8f, 1f), r)
        assertTrue(NormPoint(0.5f, 0.8f) in r)
        assertTrue(NormPoint(0.2f, 1f) in r)
    }
}
