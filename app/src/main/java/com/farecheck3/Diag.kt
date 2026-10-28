package com.farecheck

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Diag {
    private fun sp(c: Context) = c.getSharedPreferences("fc_diag", Context.MODE_PRIVATE)
    fun now(): String = SimpleDateFormat("HH:mm:ss", Locale.UK).format(Date())
    fun put(c: Context, k: String, v: String, sync: Boolean = false) {
        runCatching {
            val e = sp(c).edit().putString(k, v.take(600))
            if (sync) e.commit() else e.apply()
        }
    }
    fun get(c: Context, k: String): String = runCatching { sp(c).getString(k, null) }.getOrNull() ?: "-"
    fun trace(t: Throwable): String =
        t.javaClass.simpleName + ": " + t.message + " @ " + t.stackTrace.take(3).joinToString(" < ") { it.className.substringAfterLast('.') + "." + it.methodName + ":" + it.lineNumber }
}
