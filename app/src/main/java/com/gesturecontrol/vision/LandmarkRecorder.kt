package com.gesturecontrol.vision

import com.gesturecontrol.gestures.HandFrame
import com.gesturecontrol.gestures.LandmarkCsv
import java.io.BufferedWriter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Labels offered by the training logger; names match ml/gc_ml (POSE_CLASSES, DYNAMIC_CLASSES). */
val TRAINING_LABELS = listOf(
    "NO_GESTURE",
    "OPEN_PALM", "FIST", "POINTING", "TWO_FINGERS", "THUMB_UP", "THUMB_DOWN",
    "SWIPE_LEFT", "SWIPE_RIGHT", "FINGER_LEFT", "FINGER_RIGHT", "FIST_OPEN",
)

/**
 * Writes landmark coordinates (never images) to a CSV file.
 * Format: [LandmarkCsv] plus header comments: `# expect:` (for engine replay tests),
 * `# label:` (training label, null = unlabeled engine recording) and `# aspect:` (frame width / height).
 * Not thread-safe: call [write] from the analysis thread only.
 */
class LandmarkRecorder(directory: File, val label: String? = null) {
    val file: File = File(
        directory.apply { mkdirs() },
        (label?.lowercase() ?: "gesture") + "_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".csv",
    )
    private val writer: BufferedWriter = file.bufferedWriter()
    private var startMs: Long? = null
    private var aspectWritten = false

    var frameCount = 0
        private set

    init {
        writer.appendLine("# expect: ")
        if (label != null) writer.appendLine("# label: $label")
    }

    private var closed = false

    @Synchronized
    fun write(frame: HandFrame) {
        if (closed) return
        if (!aspectWritten) {
            writer.appendLine("# aspect: ${frame.aspect}")
            writer.appendLine(LandmarkCsv.HEADER)
            aspectWritten = true
        }
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
