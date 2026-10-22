package com.gesturecontrol.gestures.pointer

import com.gesturecontrol.gestures.HandFrame
import com.gesturecontrol.gestures.HandLandmarks as H
import com.gesturecontrol.gestures.HandPose
import com.gesturecontrol.gestures.Landmark
import com.gesturecontrol.gestures.PalmConfig
import com.gesturecontrol.gestures.StaticGestureClassifier
import com.gesturecontrol.gestures.TrackingGuard
import com.gesturecontrol.gestures.distance2d
import com.gesturecontrol.gestures.isotropicHandSize
import com.gesturecontrol.gestures.mapping.NormPoint
import com.gesturecontrol.gestures.mapping.ScreenMapper
import com.gesturecontrol.gestures.mapping.VerticalDirection
import com.gesturecontrol.gestures.mapping.Zone
import com.gesturecontrol.gestures.mapping.ZoneSet
import com.gesturecontrol.gestures.ml.PoseClassifier
import com.gesturecontrol.gestures.ml.RuleBasedPoseClassifier

sealed interface PointerEvent {
    /** A press at [at] (normalized screen); [zone] is the zone under it, if any. */
    data class Press(val at: NormPoint, val zone: Zone?) : PointerEvent
    data class Scroll(val direction: VerticalDirection) : PointerEvent
}

/** Per-frame pointer state for drawing the overlay, plus at most one event. */
data class PointerOutput(
    /** Cursor in normalized screen coordinates, or null when the cursor is hidden. */
    val cursor: NormPoint?,
    val pose: HandPose?,
    /** Thumb-index distance in hand sizes, for the debug screen. */
    val pinchRatio: Float?,
    val pinchClosed: Boolean,
    val hoveredZone: Zone?,
    /** Dwell progress in [0, 1] for the hovered zone in DWELL mode. */
    val dwellProgress: Float,
    val event: PointerEvent?,
) {
    companion object {
        val HIDDEN = PointerOutput(null, null, null, false, null, 0f, null)
    }
}

/**
 * Cursor-mode state machine. Pure function of the frame sequence (feed frames in order).
 *
 * - POINTING pose shows the cursor at the index fingertip, mapped by [mapper] and smoothed.
 * - PINCH mode: thumb + index pinch presses; the cursor freezes while the pinch closes, and the
 *   press lands where the cursor was just before, so the finger motion of the pinch does not move it.
 * - DWELL mode: holding the cursor inside a zone for [PointerConfig.dwellMs] presses that zone.
 * - TWO_FINGERS pose hides the cursor; vertical strokes emit scroll steps.
 * - Stability: a palm jump restarts tracking, and nothing presses or scrolls during the settle time.
 * Depth (z) is never used.
 */
class PointerEngine(
    private val config: PointerConfig = PointerConfig(),
    /** Screen height / width, so that distances are measured as on screen. */
    screenAspect: Float = 2f,
    /** Pose source (rules or the ML model); pinch geometry always uses the rules. */
    private val poseClassifier: PoseClassifier = RuleBasedPoseClassifier(),
) {
    /** Can be replaced at any time (calibration changes). */
    @Volatile
    var mapper: ScreenMapper = ScreenMapper.fromRegion()

    /** Zones of the app on screen. */
    @Volatile
    var zones: ZoneSet = ZoneSet.EMPTY

    /** When false the engine is idle (e.g. apps without zones, where pointing flicks switch tracks). */
    @Volatile
    var active: Boolean = true

    private val classifier = StaticGestureClassifier(PalmConfig())
    private val smoother = CursorSmoother(config.filter, config.deadZone, screenAspect)
    private val scroll = ScrollDetector(config.scroll)
    private val history = ArrayDeque<Pair<Long, NormPoint>>()
    private val guard = TrackingGuard(config.stability, config.handLostToleranceMs)

    private var lastHandTs: Long? = null
    private var cursorActive = false
    private var cursorSinceTs = Long.MIN_VALUE / 2
    private var lastPointerTs = Long.MIN_VALUE / 2
    private var cursor: NormPoint? = null
    private var lastPressTs = Long.MIN_VALUE / 2

    private var pinchOnsetTs: Long? = null
    private var pinchClosedSince: Long? = null
    private var pinchClosedFrames = 0
    /** Last time the thumb and index were open (ratio above pinchOpen) while the cursor was shown. */
    private var pinchLastOpenTs = Long.MIN_VALUE / 2
    private var pinchFired = false
    private var frozenCursor: NormPoint? = null

    private var dwellZoneId: Long? = null
    private var dwellStartTs = 0L
    private var dwellFired = false

    fun process(frame: HandFrame): PointerOutput {
        if (!active) {
            if (cursorActive || lastHandTs != null) reset()
            return PointerOutput.HIDDEN
        }
        val t = frame.timestampMs
        val lm = frame.landmarks
        if (lm == null) {
            if (guard.onNoHand(t)) resetTracking()
            return if (cursorActive) output(null, null, null) else PointerOutput.HIDDEN
        }
        if (guard.onHand(t, lm, frame.aspect)) resetTracking()
        lastHandTs = t
        val settled = guard.settled(t)

        val pose = poseClassifier.classify(lm, frame.aspect).pose
        val ratio = StaticGestureClassifier.pinchRatio(lm)

        if (pose == HandPose.TWO_FINGERS) {
            hideCursor()
            if (!settled) {
                scroll.resetStroke()
                return PointerOutput(null, pose, ratio, false, null, 0f, null)
            }
            val tipA = lm[H.INDEX_TIP]
            val tipB = lm[H.MIDDLE_TIP]
            val scale = if (config.scroll.inHandSizes) isotropicHandSize(lm, frame.aspect) else 1f
            val direction = scroll.update(t, (tipA.x + tipB.x) / 2, (tipA.y + tipB.y) / 2, scale)
            return PointerOutput(null, pose, ratio, false, null, 0f, direction?.let { PointerEvent.Scroll(it) })
        }
        scroll.resetStroke()

        val pinchCandidate = othersFolded(lm) && indexReach(lm) >= config.pinchMinIndexReach &&
            ratio < config.pinchOpen
        val keepAlive = pose == HandPose.POINTING || (cursorActive && pinchCandidate)
        if (keepAlive) {
            lastPointerTs = t
            if (!cursorActive) cursorSinceTs = t
            cursorActive = true
        } else if (cursorActive && t - lastPointerTs > config.cursorHideDelayMs) {
            hideCursor()
        }
        if (!cursorActive) return PointerOutput(null, pose, ratio, false, null, 0f, null)

        // During the hide grace period the last cursor is kept: the fingertip may not be the anchor.
        if (keepAlive) {
            val target = mapper.map(NormPoint(lm[H.INDEX_TIP].x, lm[H.INDEX_TIP].y))
            val smoothed = smoother.update(target, t)
            cursor = smoothed
            history.addLast(t to smoothed)
            while (t - history.first().first > HISTORY_MS) history.removeFirst()
        }

        // A press needs settled tracking and a cursor shown for the arming time.
        val armed = settled && t - cursorSinceTs >= config.pinchArmMs
        val event = when (config.pressMode) {
            PressMode.PINCH -> updatePinch(t, pinchCandidate && armed, ratio)
            PressMode.DWELL -> null
        }
        val display = frozenCursor ?: cursor
        val dwellEvent = if (config.pressMode == PressMode.DWELL && display != null && settled) updateDwell(t, display) else null
        return output(pose, ratio, event ?: dwellEvent)
    }

    fun reset() {
        resetTracking()
        guard.reset()
    }

    private fun resetTracking() {
        hideCursor()
        lastHandTs = null
        scroll.resetStroke()
        poseClassifier.reset()
    }

    private fun updatePinch(t: Long, candidate: Boolean, ratio: Float): PointerEvent? {
        if (ratio >= config.pinchOpen) pinchLastOpenTs = t
        if (!candidate) {
            resetPinch()
            return null
        }
        if (pinchOnsetTs == null) {
            pinchOnsetTs = t
            frozenCursor = cursorAt(t - config.pressRollbackMs)
        }
        if (ratio >= config.pinchClose) {
            pinchClosedSince = null
            pinchClosedFrames = 0
            return null
        }
        val since = pinchClosedSince ?: t.also { pinchClosedSince = it }
        pinchClosedFrames++
        if (pinchFired || t - since < config.pinchMinClosedMs || pinchClosedFrames < config.pinchMinClosedFrames ||
            t - lastPressTs < config.pressCooldownMs
        ) {
            return null
        }
        if (config.pinchMaxClosingMs > 0 && since - pinchLastOpenTs > config.pinchMaxClosingMs) {
            pinchFired = true // closed too slowly or was never open: wait for the fingers to open again
            return null
        }
        pinchFired = true
        lastPressTs = t
        val at = frozenCursor ?: cursor ?: return null
        return PointerEvent.Press(at, zones.hitTest(at))
    }

    private fun updateDwell(t: Long, at: NormPoint): PointerEvent? {
        val zone = zones.hitTest(at)
        if (zone == null) {
            resetDwell()
            return null
        }
        if (zone.id != dwellZoneId) {
            dwellZoneId = zone.id
            dwellStartTs = t
            dwellFired = false
        }
        if (dwellFired || t - dwellStartTs < config.dwellMs || t - lastPressTs < config.pressCooldownMs) return null
        dwellFired = true
        lastPressTs = t
        return PointerEvent.Press(zone.rect.center, zone)
    }

    private fun output(pose: HandPose?, ratio: Float?, event: PointerEvent?): PointerOutput {
        val display = frozenCursor ?: cursor
        val hovered = display?.let { zones.hitTest(it) }
        val progress = if (config.pressMode == PressMode.DWELL && hovered != null && hovered.id == dwellZoneId) {
            if (dwellFired) 1f else ((lastHandTs ?: dwellStartTs) - dwellStartTs).toFloat() / config.dwellMs
        } else {
            0f
        }
        return PointerOutput(
            cursor = display,
            pose = pose,
            pinchRatio = ratio,
            pinchClosed = pinchClosedSince != null || pinchFired,
            hoveredZone = hovered,
            dwellProgress = progress.coerceIn(0f, 1f),
            event = event,
        )
    }

    /** Smoothed cursor at time [t] or the oldest one we still have. */
    private fun cursorAt(t: Long): NormPoint? =
        history.lastOrNull { it.first <= t }?.second ?: history.firstOrNull()?.second

    private fun othersFolded(lm: List<Landmark>) =
        !classifier.isFingerExtended(lm, H.MIDDLE_PIP, H.MIDDLE_TIP) &&
            !classifier.isFingerExtended(lm, H.RING_PIP, H.RING_TIP) &&
            !classifier.isFingerExtended(lm, H.PINKY_PIP, H.PINKY_TIP)

    private fun indexReach(lm: List<Landmark>): Float {
        val size = StaticGestureClassifier.handSize(lm)
        return if (size <= 1e-6f) 0f else distance2d(lm[H.WRIST], lm[H.INDEX_TIP]) / size
    }

    private fun hideCursor() {
        pinchLastOpenTs = Long.MIN_VALUE / 2
        cursorActive = false
        cursor = null
        smoother.reset()
        history.clear()
        resetPinch()
        resetDwell()
    }

    private fun resetPinch() {
        pinchOnsetTs = null
        pinchClosedSince = null
        pinchClosedFrames = 0
        pinchFired = false
        frozenCursor = null
    }

    private fun resetDwell() {
        dwellZoneId = null
        dwellFired = false
    }

    private companion object {
        const val HISTORY_MS = 1000L
    }
}
