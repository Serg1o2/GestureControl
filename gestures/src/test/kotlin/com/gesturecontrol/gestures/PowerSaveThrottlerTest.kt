package com.gesturecontrol.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerSaveThrottlerTest {

    /** Simulates a 30 fps camera; [handAt] says whether a hand is visible at a time. Returns processed timestamps. */
    private fun simulate(
        throttler: PowerSaveThrottler,
        fromMs: Long,
        toMs: Long,
        handAt: (Long) -> Boolean,
    ): List<Long> {
        val processed = mutableListOf<Long>()
        var t = fromMs
        while (t < toMs) {
            if (throttler.shouldProcess(t)) {
                throttler.onProcessed(t, handAt(t))
                processed += t
            }
            t += 33
        }
        return processed
    }

    @Test
    fun fullRateWhileHandVisible() {
        val throttler = PowerSaveThrottler()
        val processed = simulate(throttler, 0, 3000) { true }
        assertEquals(91, processed.size)
        assertFalse(throttler.isIdle)
    }

    @Test
    fun dropsToTenFpsWithoutHand() {
        val throttler = PowerSaveThrottler()
        simulate(throttler, 0, 1100) { false }
        assertTrue(throttler.isIdle)
        val idle = simulate(throttler, 1100, 3100) { false }
        // 2 s at ~10 fps (camera frames are 33 ms apart, so effectively every 3rd frame).
        assertTrue("processed ${idle.size}", idle.size in 18..21)
    }

    @Test
    fun handAppearanceRestoresFullRate() {
        val throttler = PowerSaveThrottler()
        simulate(throttler, 0, 2000) { false }
        assertTrue(throttler.isIdle)
        val processed = simulate(throttler, 2000, 3000) { true }
        assertFalse(throttler.isIdle)
        // At most one idle interval is lost before full rate resumes.
        assertTrue("processed ${processed.size}", processed.size >= 27)
    }

    @Test
    fun disabledNeverThrottles() {
        val throttler = PowerSaveThrottler(PowerSaveConfig(enabled = false))
        val processed = simulate(throttler, 0, 3000) { false }
        assertEquals(91, processed.size)
        assertFalse(throttler.isIdle)
    }
}
