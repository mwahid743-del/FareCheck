package com.farecheck

import java.util.Locale
import kotlin.math.roundToInt

object Verdicts {
    private fun money(v: Double) = String.format(Locale.UK, "£%.2f", v)
    private fun miles(v: Double) = String.format(Locale.UK, "%.1f", v)
    private fun ratio(v: Double, target: Double) = if (target <= 0) 9.9 else v / target
    private fun level(worst: Double) = when {
        worst >= 1.0 -> Level.ACCEPT
        worst >= 0.9 -> Level.MAYBE
        else -> Level.DECLINE
    }

    fun evaluate(o: Offer, p: Prefs): Verdict =
        if (o.app.kind == Kind.RIDE) ride(o, p) else delivery(o, p)

    private fun ride(o: Offer, p: Prefs): Verdict {
        val mi = o.miles ?: 0.0
        val perMile = if (mi > 0) o.price / mi else 0.0
        val hourly = o.minutes?.takeIf { it > 0 }?.let { o.price / (it / 60.0) }
        val ratios = mutableListOf(ratio(perMile, p.minRidePerMile))
        if (hourly != null) ratios += ratio(hourly, p.targetHourly)
        val line1 = buildString {
            append(miles(mi)).append(" mi")
            o.minutes?.let { append("   ").append(it.roundToInt()).append(" min") }
            append("   ").append(money(o.price))
        }
        val line2 = buildString {
            append(money(perMile)).append("/mi")
            hourly?.let { append("   ").append(money(it)).append("/h") }
        }
        return Verdict(level(ratios.min()), line1, line2, 22f, 13f)
    }

    private fun delivery(o: Offer, p: Prefs): Verdict {
        val shop = o.shop ?: o.app.label
        val stopsText = o.stops?.let { if (it == 1) "1 stop" else "$it stops" }
        val mi = o.miles
        val milesText = when {
            mi == null -> "~? mi"
            o.estimated -> "~" + miles(mi) + " mi"
            else -> miles(mi) + " mi"
        }
        val line2 = listOfNotNull(money(o.price), milesText, stopsText).joinToString(" · ")
        val lvl = if (mi == null || mi <= 0.0) Level.UNKNOWN
        else level(ratio(o.price / mi, p.minDeliveryPerMile))
        return Verdict(lvl, shop, line2, 17f, 16f)
    }
}
