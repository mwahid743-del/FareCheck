package com.farecheck

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

class OverlayController(private val svc: AccessibilityService) {
    private val wm = svc.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private fun dp(v: Int) = (v * svc.resources.displayMetrics.density).toInt()

    fun show(v: Verdict) {
        val tv = view ?: TextView(svc).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }.also {
            view = it
            wm.addView(it, params())
        }
        tv.text = v.text
        tv.setBackgroundColor(
            when (v.level) {
                Level.ACCEPT -> 0xEE2E7D32.toInt()
                Level.MAYBE -> 0xEEEF6C00.toInt()
                Level.DECLINE -> 0xEEC62828.toInt()
            }
        )
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
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
