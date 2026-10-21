package com.gesturecontrol.gestures

/**
 * Plain CSV format for recorded landmark sequences (shared by the app recorder and unit tests).
 * Header: t_ms,x0,y0,z0,...,x20,y20,z20. A row with only t_ms means "no hand".
 * Lines starting with '#' are comments.
 */
object LandmarkCsv {
    val HEADER: String = buildString {
        append("t_ms")
        for (i in 0 until HandLandmarks.COUNT) append(",x$i,y$i,z$i")
    }

    fun formatRow(frame: HandFrame): String = buildString {
        append(frame.timestampMs)
        frame.landmarks?.forEach { append(',').append(it.x).append(',').append(it.y).append(',').append(it.z) }
    }

    fun parse(text: String): List<HandFrame> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("t_ms") }
        .map { line ->
            val parts = line.split(',')
            val t = parts[0].toLong()
            if (parts.size == 1) {
                HandFrame(t, null)
            } else {
                require(parts.size == 1 + HandLandmarks.COUNT * 3) { "Bad row: $line" }
                val lm = (0 until HandLandmarks.COUNT).map { i ->
                    Landmark(parts[1 + i * 3].toFloat(), parts[2 + i * 3].toFloat(), parts[3 + i * 3].toFloat())
                }
                HandFrame(t, lm)
            }
        }
        .toList()
}
