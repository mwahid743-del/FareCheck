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
    private var scheduled = false
    private val tick = Runnable { scheduled = false; process() }

    override fun onServiceConnected() {
        overlay = OverlayController(this)
        prefs = Prefs(this)
        Toast.makeText(this, "FareCheck is running", Toast.LENGTH_LONG).show()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val pkg = e?.packageName?.toString() ?: return
        if (App.byPackage(pkg) == null) return
        schedule(200)
    }

    private fun schedule(ms: Long) {
        if (!scheduled) {
            scheduled = true
            handler.postDelayed(tick, ms)
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
            val app = App.byPackage(r.packageName?.toString() ?: continue) ?: continue
            collect(r, byApp.getOrPut(app) { ArrayList() })
        }
        for ((app, lines) in byApp) {
            val offer = OfferParser.parse(app, lines, ::estimateMiles)
            if (offer != null) {
                Log.d("FareCheck", offer.toString())
                overlay.show(Verdicts.evaluate(offer, prefs))
                schedule(700)
                return
            }
        }
        overlay.hide()
    }

    private fun estimateMiles(codes: List<String>): Double? {
        if (codes.size < 2) return null
        var total = 0.0
        for (i in 0 until codes.size - 1) {
            val a = Postcodes.lookup(this, codes[i]) ?: return null
            val b = Postcodes.lookup(this, codes[i + 1]) ?: return null
            total += Postcodes.miles(a, b)
        }
        return total * prefs.roadFactor
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<Line>) {
        if (n == null) return
        val t = n.text?.toString()?.takeIf { it.isNotBlank() }
            ?: n.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        if (t != null) {
            val r = Rect()
            n.getBoundsInScreen(r)
            out.add(Line(t, r.height(), r.top, r.left))
        }
        for (i in 0 until n.childCount) collect(n.getChild(i), out)
    }

    override fun onInterrupt() {
        if (::overlay.isInitialized) overlay.hide()
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (::overlay.isInitialized) overlay.hide()
        super.onDestroy()
    }
}
