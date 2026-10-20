package com.farecheck

import android.content.Context
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Postcodes {
    private const val REC = 15
    private var data: ByteArray? = null

    private fun load(ctx: Context): ByteArray? {
        data?.let { return it }
        return try {
            ctx.assets.open("postcodes.bin").use { it.readBytes() }.also { data = it }
        } catch (e: Exception) {
            null
        }
    }

    private fun cmp(d: ByteArray, off: Int, key: ByteArray, len: Int): Int {
        for (i in 0 until len) {
            val a = d[off + i].toInt() and 0xFF
            val b = key[i].toInt() and 0xFF
            if (a != b) return a - b
        }
        return 0
    }

    private fun lowerBound(d: ByteArray, key: ByteArray, len: Int): Int {
        var lo = 0
        var hi = d.size / REC
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (cmp(d, mid * REC, key, len) < 0) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun readInt(d: ByteArray, o: Int): Int =
        ((d[o].toInt() and 0xFF) shl 24) or ((d[o + 1].toInt() and 0xFF) shl 16) or
            ((d[o + 2].toInt() and 0xFF) shl 8) or (d[o + 3].toInt() and 0xFF)

    private fun average(d: ByteArray, prefix: String): DoubleArray? {
        if (prefix.length < 2) return null
        val key = prefix.toByteArray(Charsets.US_ASCII)
        val total = d.size / REC
        var i = lowerBound(d, key, key.size)
        var lat = 0.0
        var lon = 0.0
        var n = 0
        while (i < total && cmp(d, i * REC, key, key.size) == 0) {
            lat += readInt(d, i * REC + 7) / 1e6
            lon += readInt(d, i * REC + 11) / 1e6
            n++
            i++
        }
        return if (n == 0) null else doubleArrayOf(lat / n, lon / n)
    }

    fun lookup(ctx: Context, code: String): DoubleArray? {
        val d = load(ctx) ?: return null
        val full = code.padEnd(7, ' ').toByteArray(Charsets.US_ASCII)
        val i = lowerBound(d, full, 7)
        if (i < d.size / REC && cmp(d, i * REC, full, 7) == 0) {
            return doubleArrayOf(readInt(d, i * REC + 7) / 1e6, readInt(d, i * REC + 11) / 1e6)
        }
        if (code.length < 5) return null
        return average(d, code.dropLast(2)) ?: average(d, code.dropLast(3))
    }

    fun miles(a: DoubleArray, b: DoubleArray): Double {
        val r = 3958.8
        val p1 = Math.toRadians(a[0])
        val p2 = Math.toRadians(b[0])
        val dl = Math.toRadians(b[1] - a[1])
        val h = sin((p2 - p1) / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * asin(sqrt(h))
    }
}
