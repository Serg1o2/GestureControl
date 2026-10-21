package com.gesturecontrol.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.Size
import android.view.View
import android.view.WindowManager
import com.gesturecontrol.gestures.mapping.NormPoint

/**
 * Full-screen, non-touchable accessibility overlay that draws the cursor, the hovered zone,
 * dwell progress and short hints. The window is attached only while there is something to show.
 */
class CursorOverlay(private val context: Context, private val screenSize: () -> Size) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val view = OverlayView(context)
    private var attached = false

    private var state = OverlayState()
    private var hintText: String? = null
    private var hintUntil = 0L

    fun update(newState: OverlayState) {
        state = newState
        refresh()
    }

    fun showHint(text: String) {
        hintText = text
        hintUntil = SystemClock.uptimeMillis() + HINT_MS
        refresh()
        view.postDelayed({ refresh() }, HINT_MS + 50)
    }

    fun destroy() = detach()

    private fun refresh() {
        val needed = state.cursor != null || hintVisible()
        if (needed) attach() else detach()
        if (attached) view.invalidate()
    }

    private fun hintVisible() = hintText != null && SystemClock.uptimeMillis() < hintUntil

    @SuppressLint("RtlHardcoded")
    private fun attach() {
        if (attached) return
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                fitInsetsTypes = 0
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            title = "GestureControl cursor"
        }
        runCatching { windowManager.addView(view, params) }.onSuccess { attached = true }
    }

    private fun detach() {
        if (!attached) return
        runCatching { windowManager.removeView(view) }
        attached = false
    }

    private inner class OverlayView(context: Context) : View(context) {
        private val density = resources.displayMetrics.density
        private val location = IntArray(2)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3 * density
            color = Color.WHITE
        }
        private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6 * density
            color = 0x88000000.toInt()
        }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCC00BFA5.toInt() }
        private val progress = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4 * density
            strokeCap = Paint.Cap.ROUND
            color = 0xFF00E5FF.toInt()
        }
        private val zoneFill = Paint().apply { color = 0x3300E5FF }
        private val zoneStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2 * density
            color = 0xAA00E5FF.toInt()
        }
        private val hintBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xDD212121.toInt() }
        private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14 * density
        }
        private val arc = RectF()

        /** Normalized screen point to this view's coordinates. */
        private fun toView(p: NormPoint): Pair<Float, Float> {
            val size = screenSize()
            getLocationOnScreen(location)
            return p.x * size.width - location[0] to p.y * size.height - location[1]
        }

        override fun onDraw(canvas: Canvas) {
            state.hoveredZone?.let { zone ->
                val (l, t) = toView(NormPoint(zone.left, zone.top))
                val (r, b) = toView(NormPoint(zone.right, zone.bottom))
                canvas.drawRect(l, t, r, b, zoneFill)
                canvas.drawRect(l, t, r, b, zoneStroke)
            }
            val cursor = state.cursor
            var anchorX = width / 2f
            var anchorY = height * 0.85f
            if (cursor != null) {
                val (x, y) = toView(cursor)
                anchorX = x
                anchorY = y
                val radius = CURSOR_DP * density
                if (state.pinchClosed) canvas.drawCircle(x, y, radius * 0.7f, fill)
                canvas.drawCircle(x, y, radius, outline)
                canvas.drawCircle(x, y, radius, ring)
                if (state.dwellProgress > 0f) {
                    val r = radius + 6 * density
                    arc.set(x - r, y - r, x + r, y + r)
                    canvas.drawArc(arc, -90f, 360f * state.dwellProgress, false, progress)
                }
            }
            val text = hintText
            if (text != null && hintVisible()) {
                val pad = 10 * density
                val w = hintPaint.measureText(text) + 2 * pad
                val h = hintPaint.textSize + 2 * pad
                val left = (anchorX - w / 2).coerceIn(pad, width - w - pad)
                val top = (anchorY + CURSOR_DP * density + pad).let { if (it + h > height) anchorY - h - 3 * pad else it }
                canvas.drawRoundRect(left, top, left + w, top + h, h / 2, h / 2, hintBackground)
                canvas.drawText(text, left + pad, top + pad + hintPaint.textSize * 0.85f, hintPaint)
            }
        }
    }

    private companion object {
        const val CURSOR_DP = 14f
        const val HINT_MS = 2000L
    }
}
