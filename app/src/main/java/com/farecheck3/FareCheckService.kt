package com.farecheck

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class FareCheckService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlay: OverlayController
    private lateinit var prefs: Prefs
    private var pending = false

    override fun onServiceConnected() {
        overlay = OverlayController(this)
        prefs = Prefs(this)
        Toast.makeText(this, "FareCheck is running", Toast.LENGTH_LONG).show()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.packageName?.toString() == packageName) return
        if (!pending) {
            pending = true
            handler.postDelayed({ pending = false; process() }, 200)
        }
    }

    private fun process() {
        val roots = ArrayList<AccessibilityNodeInfo>()
        val ws = windows
        if (ws.isNullOrEmpty()) {
            rootInActiveWindow?.let { roots.add(it) }
        } else {
            for (w in ws) w.root?.let { roots.add(it) }
        }
        val byApp = LinkedHashMap<App, MutableList<Line>>()
        for (r in roots) {
            val pkg = r.packageName?.toString() ?: continue
            val app = App.byPackage(pkg) ?: continue
            collect(r, byApp.getOrPut(app) { ArrayList() })
        }
        for ((app, lines) in byApp) {
            Log.d("FareCheck", "${app.name}: ${lines.joinToString(" | ") { it.text }.take(400)}")
            val offer = OfferParser.parse(app, lines)
            if (offer != null) {
                overlay.show(Verdicts.evaluate(offer, prefs))
                return
            }
        }
        overlay.hide()
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<Line>) {
        if (n == null) return
        val t = n.text?.toString()?.takeIf { it.isNotBlank() }
            ?: n.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        if (t != null) {
            val r = Rect()
            n.getBoundsInScreen(r)
            out.add(Line(t, r.height()))
        }
        for (i in 0 until n.childCount) collect(n.getChild(i), out)
    }

    override fun onInterrupt() { if (::overlay.isInitialized) overlay.hide() }
    override fun onDestroy() { if (::overlay.isInitialized) overlay.hide(); super.onDestroy() }
}
