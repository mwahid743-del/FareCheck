package com.farecheck

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/** One transparent, touch-through, full-screen layer. Everything (outline + boxes) is drawn from a Plan. */
class OverlayController(private val svc: AccessibilityService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private var view: PlanView? = null

    val density: Float get() = svc.resources.displayMetrics.density

    private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
    val measure = Measure { text, px ->
        measurePaint.textSize = px
        measurePaint.measureText(text)
    }

    fun screenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.currentWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
    }

    var showing = false
        private set

    fun show(plan: Plan) {
        val v = view ?: PlanView(svc, density).also {
            view = it
            wm.addView(it, params())
        }
        v.plan = plan
        v.invalidate()
        showing = true
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        showing = false
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = 1 // SHORT_EDGES
    }
}

private class PlanView(ctx: Context, private val d: Float) : View(ctx) {
    var plan: Plan? = null
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }

    override fun onDraw(c: Canvas) {
        val p = plan ?: return
        val loc = IntArray(2)
        getLocationOnScreen(loc)
        c.translate(-loc[0].toFloat(), -loc[1].toFloat())

        p.outline?.let {
            val sw = 3f * d
            stroke.strokeWidth = sw
            stroke.color = p.outlineColor
            val half = sw / 2
            c.drawRoundRect(
                RectF(it.l + half, it.t + half, it.r - half, it.b - half),
                16f * d, 16f * d, stroke
            )
        }
        for (b in p.boxes) {
            fill.color = b.bg
            val rect = RectF(b.rect.l.toFloat(), b.rect.t.toFloat(), b.rect.r.toFloat(), b.rect.b.toFloat())
            c.drawRoundRect(rect, 10f * d, 10f * d, fill)
            var y = rect.top + Layout.PAD_Y * d
            for (row in b.rows) {
                var rw = 0f
                var rh = 0f
                row.parts.forEachIndexed { i, part ->
                    text.textSize = part.px
                    if (i > 0) rw += Layout.GAP * d
                    rw += text.measureText(part.text) + if (part.chip) 2 * Layout.CHIP_PAD * d else 0f
                    rh = maxOf(rh, part.px * Layout.ROW_H)
                }
                var x = rect.left + (rect.width() - rw) / 2
                for ((i, part) in row.parts.withIndex()) {
                    text.textSize = part.px
                    if (i > 0) x += Layout.GAP * d
                    val tw = text.measureText(part.text)
                    val base = y + rh / 2 - (text.ascent() + text.descent()) / 2
                    if (part.chip) {
                        fill.color = 0x66000000
                        val ch = part.px * 1.2f
                        c.drawRoundRect(
                            RectF(x, y + rh / 2 - ch / 2, x + tw + 2 * Layout.CHIP_PAD * d, y + rh / 2 + ch / 2),
                            6f * d, 6f * d, fill
                        )
                        text.color = part.color
                        c.drawText(part.text, x + Layout.CHIP_PAD * d, base, text)
                        x += tw + 2 * Layout.CHIP_PAD * d
                    } else {
                        text.color = part.color
                        c.drawText(part.text, x, base, text)
                        x += tw
                    }
                }
                y += rh + Layout.ROW_GAP * d
            }
        }
    }
}
