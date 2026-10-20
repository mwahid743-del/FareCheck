package com.farecheck

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

class OverlayController(private val svc: AccessibilityService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private var box: LinearLayout? = null
    private var t1: TextView? = null
    private var t2: TextView? = null

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

    fun show(v: Verdict) {
        if (box == null) {
            val a = line()
            val b = line()
            val l = LinearLayout(svc).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(8), dp(12), dp(8))
                addView(a)
                addView(b)
            }
            t1 = a
            t2 = b
            box = l
            wm.addView(l, params())
        }
        t1?.text = v.line1
        t1?.textSize = v.size1
        t2?.text = v.line2
        t2?.textSize = v.size2
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
