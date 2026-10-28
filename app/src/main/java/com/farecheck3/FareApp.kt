package com.farecheck

import android.app.Application

class FareApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { th, e ->
            Diag.put(this, "crash", Diag.now() + " " + Diag.trace(e) + (e.cause?.let { " CAUSE " + Diag.trace(it) } ?: ""), true)
            old?.uncaughtException(th, e)
        }
    }
}
