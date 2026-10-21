package com.gesturecontrol.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Replays every CSV in src/test/resources/sequences through the engine.
 * Each file declares its expected events in a comment line, e.g. `# expect: SWIPE_RIGHT,SWIPE_LEFT`
 * (`# expect:` with nothing after it means "no events"). Recordings made with the debug screen
 * can be dropped into that folder as-is after adding the expect line.
 */
class RecordedSequenceTest {

    @Test
    fun csvRoundTrip() {
        val frames = SyntheticHand.Sequence()
            .hold(SyntheticHand.Pose.OPEN_PALM, 0.5f, 0.7f, 100)
            .noHand(66)
            .build()
        val csv = (listOf(LandmarkCsv.HEADER) + frames.map(LandmarkCsv::formatRow)).joinToString("\n")
        assertEquals(frames, LandmarkCsv.parse(csv))
    }

    @Test
    fun recordedSequencesProduceExpectedGestures() {
        val dir = File(javaClass.classLoader.getResource("sequences")!!.toURI())
        val files = dir.listFiles { f -> f.extension == "csv" }!!.sortedBy { it.name }
        assertTrue("no sequences found", files.isNotEmpty())
        for (file in files) {
            val text = file.readText()
            val expectLine = text.lineSequence().firstOrNull { it.startsWith("# expect:") }
                ?: error("${file.name}: missing '# expect:' line")
            val expected = expectLine.removePrefix("# expect:").split(',')
                .map { it.trim() }.filter { it.isNotEmpty() }.map { Gesture.valueOf(it) }
            val actual = GestureEngine().run(LandmarkCsv.parse(text)).map { it.gesture }
            assertEquals(file.name, expected, actual)
        }
    }
}
