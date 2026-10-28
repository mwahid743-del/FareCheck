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

    override fun onCreate() {
        super.onCreate()
        Diag.put(this, "created", Diag.now())
    }

    override fun onServiceConnected() {
        try {
            overlay = OverlayController(this)
            prefs = Prefs(this)
            Diag.put(this, "connected", Diag.now())
            Toast.makeText(this, "FareCheck is running", Toast.LENGTH_LONG).show()
        } catch (t: Throwable) {
            Diag.put(this, "startErr", Diag.now() + " " + Diag.trace(t))
            Toast.makeText(this, "FareCheck start error: " + t.javaClass.simpleName + ": " + t.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (!::overlay.isInitialized || !::prefs.isInitialized) return
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

    private class Win(val app: App, val rect: Bounds, val lines: MutableList<Line> = ArrayList())

    private var lastErr = 0L

    private fun process() {
        try {
            processInner()
        } catch (t: Throwable) {
            Log.e("FareCheck", "process failed", t)
            Diag.put(this, "err", Diag.now() + " " + Diag.trace(t))
            runCatching { overlay.hide() }
            val now = System.currentTimeMillis()
            if (now - lastErr > 10000) {
                lastErr = now
                Toast.makeText(this, "FareCheck error: " + t.javaClass.simpleName + ": " + t.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun processInner() {
        val wins = ArrayList<Win>()
        val ws = windows
        if (ws.isNullOrEmpty()) {
            rootInActiveWindow?.let { r ->
                val app = App.byPackage(r.packageName?.toString() ?: "")
                if (app != null) {
                    val rc = Rect(); r.getBoundsInScreen(rc)
                    wins.add(Win(app, Bounds(rc.left, rc.top, rc.right, rc.bottom)).also { collect(r, it.lines) })
                }
            }
        } else {
            for (w in ws) {
                val r = w.root ?: continue
                val app = App.byPackage(r.packageName?.toString() ?: continue) ?: continue
                val rc = Rect(); w.getBoundsInScreen(rc)
                wins.add(Win(app, Bounds(rc.left, rc.top, rc.right, rc.bottom)).also { collect(r, it.lines) })
            }
        }
        val byApp = LinkedHashMap<App, MutableList<Line>>()
        for (w in wins) byApp.getOrPut(w.app) { ArrayList() }.addAll(w.lines)
        Diag.put(this, "seen", Diag.now() + " " + byApp.entries.joinToString { it.key.label + ":" + it.value.size })
        val (sw, sh) = overlay.screenSize()
        for ((app, lines) in byApp) {
            val offer = OfferParser.parse(app, lines, ::estimateMiles) ?: continue
            Log.d("FareCheck", offer.toString())
            Diag.put(this, "offer", Diag.now() + " " + offer.toString())
            val verdict = Verdicts.evaluate(offer, prefs)
            // the window that holds the offer card (the one containing the price row)
            val pl = OfferParser.priceLine(lines)
            val win = wins.firstOrNull { it.app == app && pl != null && it.lines.contains(pl) }
            val cardLines = win?.lines ?: lines
            val plan = Layout.plan(app, verdict, cardLines, win?.rect, sw, sh, overlay.density, overlay.measure)
            Diag.put(this, "plan", Diag.now() + " boxes=" + (plan?.boxes?.size ?: -1))
            if (plan != null) {
                overlay.show(plan)
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
            out.add(Line(t, r.height(), r.top, r.left, r.right, r.bottom))
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
