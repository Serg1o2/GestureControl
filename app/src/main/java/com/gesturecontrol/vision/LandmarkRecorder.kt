package com.gesturecontrol.vision

import com.gesturecontrol.gestures.HandFrame
import com.gesturecontrol.gestures.LandmarkCsv
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes landmark coordinates (never images) to a CSV file for building unit-test sequences.
 * Format: [LandmarkCsv]. Not thread-safe: call from the analysis thread only, except [close].
 */
class LandmarkRecorder(directory: File) {
    val file: File = File(
        directory.apply { mkdirs() },
        "gesture_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".csv",
    )
    private val writer: BufferedWriter = file.bufferedWriter()
    private var startMs: Long? = null

    var frameCount = 0
        private set

    init {
        writer.appendLine("# expect: ")
        writer.appendLine(LandmarkCsv.HEADER)
    }

    private var closed = false

    @Synchronized
    fun write(frame: HandFrame) {
        if (closed) return
        // Timestamps are rebased to 0 to keep files readable.
        val start = startMs ?: frame.timestampMs.also { startMs = it }
        writer.appendLine(LandmarkCsv.formatRow(frame.copy(timestampMs = frame.timestampMs - start)))
        frameCount++
    }

    @Synchronized
    fun close() {
        closed = true
        writer.close()
    }
}
