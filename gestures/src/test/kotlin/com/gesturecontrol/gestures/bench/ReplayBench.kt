package com.gesturecontrol.gestures.bench

import com.gesturecontrol.gestures.Gesture
import com.gesturecontrol.gestures.GestureConfig
import com.gesturecontrol.gestures.GestureEngine
import com.gesturecontrol.gestures.HandFrame
import com.gesturecontrol.gestures.HandPose
import com.gesturecontrol.gestures.Landmark
import com.gesturecontrol.gestures.LandmarkCsv
import com.gesturecontrol.gestures.StabilityConfig
import com.gesturecontrol.gestures.ml.DenseMlp
import com.gesturecontrol.gestures.ml.PoseClassifier
import com.gesturecontrol.gestures.ml.RuleBasedPoseClassifier
import com.gesturecontrol.gestures.ml.VotingPoseClassifier
import com.gesturecontrol.gestures.pointer.PointerConfig
import com.gesturecontrol.gestures.pointer.PointerEngine
import com.gesturecontrol.gestures.pointer.PointerEvent
import java.io.File
import java.util.Locale
import kotlin.math.hypot

/**
 * Phantom-gesture bench: replays IPN Hand landmark streams (exported by `python -m gc_ml.replay_export`)
 * through the real engines and counts
 * - phantom events: any gesture / press / scroll inside segments where the person makes no app
 *   gesture (idle, talking, pointing, clicking, zooming), per minute of such footage;
 * - hits: SWIPE_LEFT on "throw left" (G05), SWIPE_RIGHT on "throw right" (G06), FIST_OPEN on
 *   "open twice" (G07), and wrong directions.
 *
 * Gesture mode = an app without zones (finger flicks on, pointer off).
 * Cursor mode = an app with zones (pointer on, finger flicks off): pinch presses and scrolls.
 *
 * Run: ./gradlew :gestures:replayBench -PbenchArgs="--split test --fps 15"
 * Options: --dir <ml/data/replay> --split train|val|test|all --fps <n> --classifier model|rules
 *          --config <name, see [configs]> --conditions a,b,... --out <report.md>
 */
fun main(args: Array<String>) {
    val opts = args.toList().chunked(2).associate { (k, v) -> k.removePrefix("--") to v }
    val root = File(opts["dir"] ?: "../ml/data/replay")
    val split = opts["split"] ?: "test"
    val fps = opts["fps"]?.toInt() ?: 30
    val classifier = opts["classifier"] ?: "model"
    dump = opts["dump"] == "true"
    val configNames = (opts["config"] ?: configs.keys.first()).let { if (it == "all-configs") configs.keys.toList() else it.split(',') }
    val conditions = opts["conditions"]?.split(',')
        ?: root.listFiles { f -> f.isDirectory }!!.map { it.name }.sortedWith(compareBy({ it != "clean" }, { it }))

    val report = StringBuilder()
    report.appendLine("# Replay bench: split=$split, fps=$fps, classifier=$classifier")
    report.appendLine()
    report.appendLine(
        "| condition | config | videos | idle min | phantom gestures/min | phantom presses/min | phantom scrolls/min | " +
            "swipe L hit | swipe R hit | wrong dir | fist-open hit | events on vertical throws |",
    )
    report.appendLine("|---|---|---|---|---|---|---|---|---|---|---|---|")
    val breakdown = StringBuilder()
    breakdown.appendLine()
    breakdown.appendLine("Phantoms per minute by activity (gestures / presses / scrolls):")
    breakdown.appendLine()
    breakdown.appendLine("| condition | config | ${ACTIVITIES.joinToString(" | ")} |")
    breakdown.appendLine("|---|---|${ACTIVITIES.joinToString("") { "---|" }}")
    for (condition in conditions) {
        val files = File(root, condition).listFiles { f -> f.extension == "csv" }?.sortedBy { it.name } ?: continue
        val videos = files.map(::loadVideo).filter { split == "all" || it.split in split.split('+') }
        if (videos.isEmpty()) continue
        for (configName in configNames) {
            val r = BenchResult()
            for (v in videos) replay(v, fps, classifier, configs.getValue(configName), r)
            report.appendLine(r.row(condition, configName, videos.size))
            breakdown.appendLine(r.activityRow(condition, configName))
            System.err.println("$condition/$configName: ${r.phantomByGesture}")
        }
    }
    report.append(breakdown)
    println(report)
    opts["out"]?.let { File(it).writeText(report.toString()) }
}

/** Named engine configurations, so that changes can be A/B tested on the same footage. */
val configs: Map<String, Pair<GestureConfig, PointerConfig>> by lazy {
    val base = GestureConfig()
    val basePointer = PointerConfig()
    val jump = StabilityConfig(maxJumpHandSizes = 1.5f)
    val jumpSettle = jump.copy(settleMs = 200)
    val frames3 = base.copy(swipe = base.swipe.copy(minFrames = 3), fingerSwipe = base.fingerSwipe.copy(minFrames = 3))
    // Hand-size units with values equal to the frame-unit defaults for a typical portrait phone frame
    // (hand = 0.15 frame heights, frame width = 0.75 heights).
    val hands = base.copy(
        swipe = base.swipe.copy(inHandSizes = true, minDistance = 1.25f, minVelocity = 4.0f),
        fingerSwipe = base.fingerSwipe.copy(inHandSizes = true, minDistance = 0.75f, minVelocity = 3.5f),
    )
    val pinch = basePointer.copy(pinchArmMs = 250, pinchMinClosedFrames = 2)
    val scrollHands = basePointer.scroll.copy(inHandSizes = true, minDistance = 0.8f, minVelocity = 2.7f, minFrames = 3)
    // Behaviour before docs/phantoms.md: every protection off. The baseline for comparisons.
    val off = StabilityConfig(maxJumpHandSizes = 0f)
    val unprotected = base.copy(
        stability = off,
        swipe = base.swipe.copy(minFrames = 2),
        fingerSwipe = base.fingerSwipe.copy(minFrames = 2),
    ) to basePointer.copy(
        stability = off,
        pinchMaxClosingMs = 0,
        scroll = basePointer.scroll.copy(minFrames = 2, confirmMs = 0),
    )
    linkedMapOf(
        "default" to (base to basePointer),
        "unprotected" to unprotected,
        "jump" to (base.copy(stability = jump) to basePointer.copy(stability = jump)),
        "jump_settle" to (base.copy(stability = jumpSettle) to basePointer.copy(stability = jumpSettle)),
        "frames3" to (frames3 to basePointer.copy(scroll = basePointer.scroll.copy(minFrames = 3))),
        "hands" to (hands to basePointer.copy(scroll = scrollHands)),
        "pinch" to (base to pinch),
        "pinch_closing300" to (base to basePointer.copy(pinchMaxClosingMs = 300)),
        "pinch_closing200" to (base to basePointer.copy(pinchMaxClosingMs = 200)),
        "pinch_closing_frames" to (base to basePointer.copy(pinchMaxClosingMs = 300, pinchMinClosedFrames = 2)),
        "scroll_confirm100" to (base to basePointer.copy(scroll = basePointer.scroll.copy(confirmMs = 100))),
        "scroll_confirm150" to (base to basePointer.copy(scroll = basePointer.scroll.copy(confirmMs = 150))),
        "scroll_hands_confirm" to (base to basePointer.copy(scroll = scrollHands.copy(confirmMs = 150))),
        "hands_jump" to (hands.copy(stability = jump) to basePointer.copy(stability = jump, scroll = scrollHands)),
        "hands_jump2" to (hands.copy(stability = StabilityConfig(maxJumpHandSizes = 2f)) to basePointer),
        "hands_jump25" to (hands.copy(stability = StabilityConfig(maxJumpHandSizes = 2.5f)) to basePointer),
        "hands_jump3" to (hands.copy(stability = StabilityConfig(maxJumpHandSizes = 3f)) to basePointer),
        "hands_jump_palm" to (
            hands.copy(stability = jump, swipe = hands.swipe.copy(poses = setOf(HandPose.OPEN_PALM))) to
                basePointer.copy(stability = jump, scroll = scrollHands)
            ),
        "hands_jump_palmfist" to (
            hands.copy(stability = jump, swipe = hands.swipe.copy(poses = setOf(HandPose.OPEN_PALM, HandPose.FIST))) to
                basePointer.copy(stability = jump, scroll = scrollHands)
            ),
        // Candidate defaults: the protections that helped on train+val without hurting hits.
        "proposed" to (
            hands.copy(
                stability = StabilityConfig(maxJumpHandSizes = 2f),
                swipe = hands.swipe.copy(minFrames = 3),
                fingerSwipe = hands.fingerSwipe.copy(minFrames = 3),
            ) to basePointer.copy(
                stability = StabilityConfig(maxJumpHandSizes = 2f),
                pinchMaxClosingMs = 300,
                scroll = scrollHands.copy(confirmMs = 150),
            )
            ),
        // The same protections without switching swipes to hand-size units.
        "proposed_frame" to (
            base.copy(
                stability = StabilityConfig(maxJumpHandSizes = 2f),
                swipe = base.swipe.copy(minFrames = 3),
                fingerSwipe = base.fingerSwipe.copy(minFrames = 3),
            ) to basePointer.copy(
                stability = StabilityConfig(maxJumpHandSizes = 2f),
                pinchMaxClosingMs = 300,
                scroll = basePointer.scroll.copy(minFrames = 3, confirmMs = 150),
            )
            ),
        "all" to (
            hands.copy(
                stability = jumpSettle,
                swipe = hands.swipe.copy(minFrames = 3),
                fingerSwipe = hands.fingerSwipe.copy(minFrames = 3),
            ) to pinch.copy(stability = jumpSettle, scroll = scrollHands)
            ),
    )
}

class Segment(val startMs: Long, val endMs: Long, val label: String) {
    /** IPN classes that should produce an app event. Vertical throws (G03/G04) have no app gesture. */
    val expected: Set<Gesture>? = when (label) {
        "G05" -> setOf(Gesture.SWIPE_LEFT)
        "G06" -> setOf(Gesture.SWIPE_RIGHT)
        "G07" -> setOf(Gesture.FIST_OPEN)
        else -> null
    }
    val isVerticalThrow get() = label == "G03" || label == "G04"
    val isIdle get() = expected == null && !isVerticalThrow

    /** Kind of non-gesture activity, for the phantom breakdown. */
    val activity: String get() = when (label) {
        "D0X" -> "idle"
        "B0A", "B0B" -> "point"
        "G10", "G11" -> "zoom"
        else -> "click"
    }
}

val ACTIVITIES = listOf("idle", "point", "click", "zoom")

class Video(val name: String, val split: String, val aspect: Float, val segments: List<Segment>, val frames: List<HandFrame>)

fun loadVideo(file: File): Video {
    val text = file.readText()
    val header = text.lineSequence().takeWhile { it.startsWith("#") }.toList()
    fun value(key: String) = header.first { it.startsWith("# $key:") }.substringAfter(':').trim()
    val aspect = value("aspect").toFloat()
    val segments = header.filter { it.startsWith("# segment:") }.map {
        val (s, e, label) = it.substringAfter(':').trim().split(',')
        Segment(s.toLong(), e.toLong(), label)
    }
    val frames = LandmarkCsv.parse(text).map { HandFrame(it.timestampMs, it.landmarks, aspect) }
    return Video(value("ipn"), value("split"), aspect, segments, frames)
}

class BenchResult {
    var idleMs = 0L
    val activityMs = mutableMapOf<String, Long>()
    val gesturesByActivity = mutableMapOf<String, Int>()
    val pressesByActivity = mutableMapOf<String, Int>()
    val scrollsByActivity = mutableMapOf<String, Int>()
    var phantomGestures = 0
    var phantomPresses = 0
    var phantomScrolls = 0
    val phantomByGesture = sortedMapOf<String, Int>()
    val hits = mutableMapOf<String, Int>()
    val totals = mutableMapOf<String, Int>()
    var wrongDirection = 0
    var verticalEvents = 0
    var verticalTotal = 0

    fun row(condition: String, config: String, videos: Int): String {
        val min = idleMs / 60000.0
        fun rate(n: Int) = fmt(n / min)
        fun hit(label: String) = "${hits[label] ?: 0}/${totals[label] ?: 0}"
        return "| $condition | $config | $videos | ${fmt(min)} | ${rate(phantomGestures)} | ${rate(phantomPresses)} | " +
            "${rate(phantomScrolls)} | ${hit("G05")} | ${hit("G06")} | $wrongDirection | ${hit("G07")} | $verticalEvents/$verticalTotal |"
    }

    /** Phantoms per minute of each activity: gestures / presses / scrolls. */
    fun activityRow(condition: String, config: String): String {
        val cells = ACTIVITIES.joinToString(" | ") { a ->
            val min = (activityMs[a] ?: 0L) / 60000.0
            fun rate(m: Map<String, Int>) = fmt((m[a] ?: 0) / min)
            "${rate(gesturesByActivity)} / ${rate(pressesByActivity)} / ${rate(scrollsByActivity)}"
        }
        return "| $condition | $config | $cells |"
    }

    private fun fmt(v: Double) = String.format(Locale.US, "%.2f", v)
}

/** Grace period after a gesture segment: the event may fire slightly after the annotated end. */
private const val LATE_MS = 400L

private fun replay(video: Video, fps: Int, classifierName: String, config: Pair<GestureConfig, PointerConfig>, r: BenchResult) {
    val frames = subsample(video.frames, fps)
    val (gestureConfig, pointerConfig) = config

    // Gesture mode.
    val engine = GestureEngine(gestureConfig, classifier(classifierName, gestureConfig))
    val gestureEvents = frames.mapNotNull { engine.process(it) }
    // Cursor mode: the pointer engine owns pointing; finger flicks are off.
    val pointer = PointerEngine(pointerConfig, poseClassifier = classifier(classifierName, gestureConfig))
    val pointerEvents = frames.mapNotNull { f -> pointer.process(f).event?.let { f.timestampMs to it } }

    val fired = mutableMapOf<Segment, MutableList<Gesture>>()
    for (e in gestureEvents) {
        val seg = attribute(video.segments, e.timestampMs) ?: continue
        fired.getOrPut(seg) { mutableListOf() } += e.gesture
        if (seg.isIdle && dump) System.err.println("PHANTOM ${video.name} ${seg.label} ${e.gesture} ${context(frames, e.timestampMs)}")
        if (seg.isIdle) {
            r.phantomGestures++
            r.phantomByGesture.merge(e.gesture.name, 1, Int::plus)
            r.gesturesByActivity.merge(seg.activity, 1, Int::plus)
        }
    }
    for ((t, e) in pointerEvents) {
        val seg = attribute(video.segments, t) ?: continue
        if (!seg.isIdle) continue
        if (dump) System.err.println("PHANTOM ${video.name} ${seg.label} ${e::class.simpleName} ${context(frames, t)}")
        when (e) {
            is PointerEvent.Press -> {
                r.phantomPresses++
                r.pressesByActivity.merge(seg.activity, 1, Int::plus)
            }
            is PointerEvent.Scroll -> {
                r.phantomScrolls++
                r.scrollsByActivity.merge(seg.activity, 1, Int::plus)
            }
        }
    }
    for (seg in video.segments) {
        if (seg.isIdle) {
            r.idleMs += seg.endMs - seg.startMs
            r.activityMs.merge(seg.activity, seg.endMs - seg.startMs, Long::plus)
        }
        val events = fired[seg].orEmpty()
        if (seg.isVerticalThrow) {
            r.verticalTotal++
            if (events.isNotEmpty()) r.verticalEvents++
        }
        val expected = seg.expected ?: continue
        r.totals.merge(seg.label, 1, Int::plus)
        if (events.any { it in expected }) r.hits.merge(seg.label, 1, Int::plus)
        val opposite = when (seg.label) {
            "G05" -> Gesture.SWIPE_RIGHT
            "G06" -> Gesture.SWIPE_LEFT
            else -> null
        }
        if (opposite != null && opposite in events) r.wrongDirection++
    }
}

/** Set by --dump true: print every phantom with its context to stderr. */
var dump = false

/**
 * Context of an event for diagnosis: time since the hand (re)appeared after a gap > 150 ms, the
 * largest frame-to-frame palm jump in the last 500 ms (hand sizes), frames missing in that window.
 */
private fun context(frames: List<HandFrame>, t: Long): String {
    val idx = frames.indexOfLast { it.timestampMs <= t }
    var onset = idx
    while (onset > 0) {
        val prev = frames[onset - 1]
        if (prev.landmarks == null && frames[onset].timestampMs - lastSeenBefore(frames, onset) > 150) break
        onset--
    }
    var maxJump = 0f
    var missing = 0
    var prev: HandFrame? = null
    for (f in frames.subList(0, idx + 1).takeLastWhile { t - it.timestampMs <= 500 }) {
        val lm = f.landmarks
        if (lm == null) {
            missing++
            continue
        }
        val p = prev?.landmarks
        if (p != null) {
            val size = hypot((lm[9].x - lm[0].x) * f.aspect, lm[9].y - lm[0].y).coerceAtLeast(1e-6f)
            val (ax, ay) = palm(lm, f.aspect)
            val (bx, by) = palm(p, f.aspect)
            maxJump = maxOf(maxJump, hypot(ax - bx, ay - by) / size)
        }
        prev = f
    }
    return "t=$t sinceOnset=${t - frames[onset.coerceAtLeast(0)].timestampMs} maxJump=${"%.2f".format(Locale.US, maxJump)} missing500=$missing"
}

private fun lastSeenBefore(frames: List<HandFrame>, i: Int): Long =
    (i - 1 downTo 0).firstOrNull { frames[it].landmarks != null }?.let { frames[it].timestampMs } ?: Long.MIN_VALUE / 2

private fun palm(lm: List<Landmark>, aspect: Float): Pair<Float, Float> {
    val idx = intArrayOf(0, 5, 9, 13, 17)
    return idx.map { lm[it].x * aspect }.average().toFloat() to idx.map { lm[it].y }.average().toFloat()
}

/** Gesture segments own events up to [LATE_MS] after their end. */
private fun attribute(segments: List<Segment>, t: Long): Segment? {
    segments.firstOrNull { it.expected != null && t >= it.startMs && t <= it.endMs + LATE_MS }?.let { return it }
    return segments.firstOrNull { t >= it.startMs && t < it.endMs }
}

/** Keeps the first frame of every 1/fps slot (simulates a slower camera, e.g. in the dark). */
fun subsample(frames: List<HandFrame>, fps: Int): List<HandFrame> {
    if (fps >= 30) return frames
    var lastSlot = Long.MIN_VALUE
    return frames.filter { f ->
        val slot = f.timestampMs * fps / 1000
        (slot != lastSlot).also { lastSlot = slot }
    }
}

private val model by lazy {
    DenseMlp.parse(ClassLoader.getSystemResource("ml/pose_mlp_weights.txt")!!.readText())
}

/** Same thresholds as the app's model metadata. */
private val thresholds by lazy {
    val json = File("../app/src/main/assets/models/pose_mlp.json").readText()
    Regex("\"thresholds\"\\s*:\\s*\\[([^]]*)]").find(json)!!.groupValues[1]
        .split(',').map { it.trim().toFloat() }.toFloatArray()
}

private fun classifier(name: String, config: GestureConfig): PoseClassifier = when (name) {
    "rules" -> RuleBasedPoseClassifier(config.palm)
    else -> VotingPoseClassifier(model, thresholds)
}
