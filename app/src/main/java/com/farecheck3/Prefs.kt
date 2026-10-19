package com.farecheck

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("farecheck", Context.MODE_PRIVATE)

    var targetHourly: Double
        get() = sp.getFloat("hourly", 20f).toDouble()
        set(v) = sp.edit().putFloat("hourly", v.toFloat()).apply()

    var minRidePerMile: Double
        get() = sp.getFloat("rideMile", 1.2f).toDouble()
        set(v) = sp.edit().putFloat("rideMile", v.toFloat()).apply()

    var minDeliveryPerMile: Double
        get() = sp.getFloat("delMile", 2.0f).toDouble()
        set(v) = sp.edit().putFloat("delMile", v.toFloat()).apply()
}
