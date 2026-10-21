package com.gesturecontrol.gestures

data class PowerSaveConfig(
    val enabled: Boolean = true,
    /** Switch to idle mode after no hand has been seen for this long. */
    val idleAfterMs: Long = 1000,
    /** Minimum interval between analyzed frames in idle mode (100 ms = 10 fps). */
    val idleFrameIntervalMs: Long = 100,
    /** Camera frame timing jitter tolerated when comparing intervals. */
    val frameJitterMs: Long = 8,
)

/**
 * Decides which camera frames to analyze. While a hand is visible every frame is analyzed;
 * after [PowerSaveConfig.idleAfterMs] without a hand, analysis drops to ~10 fps. The first frame
 * with a hand switches back to full rate immediately.
 */
class PowerSaveThrottler(private val config: PowerSaveConfig = PowerSaveConfig()) {
    private var lastProcessedTs: Long? = null
    private var lastHandTs: Long? = null
    private var firstTs: Long? = null

    var isIdle: Boolean = false
        private set

    fun shouldProcess(timestampMs: Long): Boolean {
        if (!config.enabled || !isIdle) return true
        val last = lastProcessedTs ?: return true
        return timestampMs - last >= config.idleFrameIntervalMs - config.frameJitterMs
    }

    fun onProcessed(timestampMs: Long, handPresent: Boolean) {
        lastProcessedTs = timestampMs
        if (firstTs == null) firstTs = timestampMs
        if (handPresent) lastHandTs = timestampMs
        val reference = lastHandTs ?: firstTs!!
        isIdle = config.enabled && timestampMs - reference >= config.idleAfterMs
    }
}
