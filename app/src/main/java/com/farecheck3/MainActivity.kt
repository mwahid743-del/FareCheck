package com.farecheck

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.view.View

class MainActivity : Activity() {
    private var status: TextView? = null

    private fun refresh() {
        val on = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .contains(packageName)
        fun g(k: String) = Diag.get(this, k)
        status?.text = "STATUS\n" +
            "Enabled in system: " + on + "\n" +
            "Service created: " + g("created") + "\n" +
            "Service connected: " + g("connected") + "\n" +
            "Start error: " + g("startErr") + "\n" +
            "Last crash: " + g("crash") + "\n" +
            "Last error: " + g("err") + "\n" +
            "Apps seen: " + g("seen") + "\n" +
            "Offer: " + g("offer") + "\n" +
            "Plan: " + g("plan")
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Prefs(this)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        fun label(t: String) = root.addView(TextView(this).apply { text = t; setPadding(0, pad / 2, 0, 0) })
        fun field(t: String, v: Double): EditText {
            label(t)
            val e = EditText(this).apply {
                setText(v.toString())
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            }
            root.addView(e)
            return e
        }

        status = TextView(this).apply { textSize = 12f; setTextIsSelectable(true) }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Refresh status"
            setOnClickListener { refresh() }
        })

        val hourly = field("Target £ per hour (Uber / Bolt)", prefs.targetHourly)
        val rideMile = field("Min £ per mile (Uber / Bolt)", prefs.minRidePerMile)
        val delMile = field("Min £ per mile (Just Eat / Deliveroo)", prefs.minDeliveryPerMile)
        val road = field("Deliveroo road factor (straight-line miles x this)", prefs.roadFactor)

        root.addView(Button(this).apply {
            text = "Save"
            setOnClickListener {
                prefs.targetHourly = hourly.text.toString().toDoubleOrNull() ?: prefs.targetHourly
                prefs.minRidePerMile = rideMile.text.toString().toDoubleOrNull() ?: prefs.minRidePerMile
                prefs.minDeliveryPerMile = delMile.text.toString().toDoubleOrNull() ?: prefs.minDeliveryPerMile
                prefs.roadFactor = road.text.toString().toDoubleOrNull() ?: prefs.roadFactor
                Toast.makeText(this@MainActivity, "Saved", Toast.LENGTH_SHORT).show()
            }
        })
        root.addView(Button(this).apply {
            text = "Open accessibility settings"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        })
        root.addView(TextView(this).apply {
            setPadding(0, pad, 0, 0)
            text = "Enable FareCheck under Installed apps. If it is greyed out: Settings > Apps > FareCheck > ⋮ > " +
                "Allow restricted settings. Also set battery to Unrestricted / Never sleeping apps. " +
                "Deliveroo miles are an estimate."
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
