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
    private var current: App? = null
    private var pending = false

    override fun onServiceConnected() {
        overlay = OverlayController(this)
        prefs = Prefs(this)
        Toast.makeText(this, "FareCheck is running", Toast.LENGTH_LONG).show()
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val pkg = e?.packageName?.toString() ?: return
        val app = App.byPackage(pkg)
        if (app == null) {
            if (pkg != packageName) { current = null; overlay.hide() }
            return
        }
        current = app
        if (!pending) {
            pending = true
            handler.postDelayed({ pending = false; process() }, 150)
        }
    }

    private fun findRoot(app: App): AccessibilityNodeInfo? {
        rootInActiveWindow?.let { if (it.packageName?.toString() == app.pkg) return it }
        return windows.firstNotNullOfOrNull { w ->
            w.root?.takeIf { it.packageName?.toString() == app.pkg }
        }
    }

    private fun process() {
        val app = current ?: return
        val root = findRoot(app) ?: return
        val lines = ArrayList<Line>()
        collect(root, lines)
        Log.d("FareCheck", "${app.name}: ${lines.joinToString(" | ") { it.text }.take(400)}")
        if (lines.isEmpty()) {
            overlay.show(Verdict(Level.MAYBE, "FareCheck: ${app.name} text hidden"))
            return
        }
        val offer = OfferParser.parse(app, lines)
        if (offer == null) overlay.hide() else overlay.show(Verdicts.evaluate(offer, prefs))
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
