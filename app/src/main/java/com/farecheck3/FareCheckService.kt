package com.farecheck

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FareCheckService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var overlay: OverlayController
    private lateinit var prefs: Prefs
    private var current: App? = null
    private var pending = false

    override fun onServiceConnected() {
        overlay = OverlayController(this)
        prefs = Prefs(this)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        val pkg = e?.packageName?.toString() ?: return
        val app = App.byPackage(pkg)
        if (app == null) {
            if (pkg != packageName) { current = null; overlay.hide() }
            return
        }
        current = app
        if (!pending) { // throttle: offer cards tick every second
            pending = true
            handler.postDelayed({ pending = false; process() }, 150)
        }
    }

    private fun process() {
        val app = current ?: return
        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != app.pkg) return
        val texts = ArrayList<String>()
        collect(root, texts)
        Log.d("FareCheck", "${app.name}: ${texts.joinToString(" | ").take(400)}")
        val offer = OfferParser.parse(app, texts)
        if (offer == null) overlay.hide() else overlay.show(Verdicts.evaluate(offer, prefs))
    }

    private fun collect(n: AccessibilityNodeInfo?, out: MutableList<String>) {
        if (n == null) return
        val t = n.text?.toString()?.takeIf { it.isNotBlank() }
            ?: n.contentDescription?.toString()?.takeIf { it.isNotBlank() }
        if (t != null) out.add(t)
        for (i in 0 until n.childCount) collect(n.getChild(i), out)
    }

    override fun onInterrupt() { if (::overlay.isInitialized) overlay.hide() }
    override fun onDestroy() { if (::overlay.isInitialized) overlay.hide(); super.onDestroy() }
}
