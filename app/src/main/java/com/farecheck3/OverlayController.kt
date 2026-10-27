package com.farecheck

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class OverlayController(private val svc: AccessibilityService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private var box: LinearLayout? = null
    private var t1: TextView? = null
    private var t2: TextView? = null
    private var chipRow: LinearLayout? = null
    private var t3: TextView? = null

    var showing = false
        private set

    private fun dp(v: Int) = (v * svc.resources.displayMetrics.density).toInt()

    private fun line(): TextView = TextView(svc).apply {
        setTextColor(Color.WHITE)
        setTypeface(null, Typeface.BOLD)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun fit(t: TextView, text: String, maxSp: Float, reserve: Int) {
        t.text = text
        var sp = maxSp
        t.textSize = sp
        val avail = svc.resources.displayMetrics.widthPixels - dp(28) - reserve
        while (sp > 12f && t.paint.measureText(text) > avail) {
            sp -= 1f
            t.textSize = sp
        }
    }

    private fun chip(c: Chip, size: Float): TextView = TextView(svc).apply {
        text = c.text
        textSize = size
        setTypeface(null, Typeface.BOLD)
        setTextColor(if (c.good) 0xFF7CFFA0.toInt() else 0xFFFF9A94.toInt())
        setPadding(dp(8), dp(1), dp(8), dp(1))
        background = GradientDrawable().apply {
            setColor(0x66000000)
            cornerRadius = dp(6).toFloat()
        }
    }

    fun show(v: Verdict) {
        if (box == null) {
            val a = line()
            val b = line()
            val row = LinearLayout(svc).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            b.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val c = TextView(svc).apply {
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                textSize = 18f
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.END or Gravity.CENTER_VERTICAL
                )
            }
            val second = FrameLayout(svc).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(row)
                addView(b)
                addView(c)
            }
            val l = LinearLayout(svc).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(6), dp(8))
                addView(a)
                addView(second)
            }
            t1 = a
            t2 = b
            chipRow = row
            t3 = c
            box = l
            wm.addView(l, params())
        }
        val hasPc = !v.postcode.isNullOrBlank()
        var reserve = 0
        t3?.let {
            if (hasPc) {
                it.text = v.postcode
                it.textSize = 18f
                it.visibility = View.VISIBLE
                reserve = 2 * (it.paint.measureText(v.postcode.orEmpty()) + dp(8)).toInt()
            } else {
                it.visibility = View.GONE
            }
        }
        t1?.let { fit(it, v.line1, v.size1, 0) }
        if (v.chips.isNotEmpty()) {
            t2?.visibility = View.GONE
            chipRow?.let { row ->
                row.removeAllViews()
                row.visibility = View.VISIBLE
                v.chips.forEachIndexed { i, ch ->
                    val tv = chip(ch, v.size2)
                    if (i > 0) {
                        tv.layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { leftMargin = dp(12) }
                    }
                    row.addView(tv)
                }
            }
        } else {
            chipRow?.visibility = View.GONE
            chipRow?.removeAllViews()
            t2?.visibility = View.VISIBLE
            t2?.let { fit(it, v.line2, v.size2, reserve) }
        }
        box?.setBackgroundColor(
            when (v.level) {
                Level.ACCEPT -> 0xEE2E7D32.toInt()
                Level.MAYBE -> 0xEEEF6C00.toInt()
                Level.DECLINE -> 0xEEC62828.toInt()
                Level.UNKNOWN -> 0xEE455A64.toInt()
            }
        )
        showing = true
    }

    fun hide() {
        box?.let { runCatching { wm.removeView(it) } }
        box = null
        t1 = null
        t2 = null
        chipRow = null
        t3 = null
        showing = false
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP
        y = dp(48)
    }
}
