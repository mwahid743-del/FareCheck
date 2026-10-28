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
        val info = buildString {
            append(miles(mi)).append(" mi")
            o.minutes?.let { append(" · ").append(it.roundToInt()).append(" min") }
        }
        val chips = mutableListOf(Chip(money(perMile) + "/mi", perMile >= p.minRidePerMile))
        hourly?.let { chips += Chip(money(it) + "/h", it >= p.targetHourly) }
        return Verdict(level(ratios.min()), info, chips, o.postcode, false)
    }

    private fun delivery(o: Offer, p: Prefs): Verdict {
        val mi = o.miles
        // Deliveroo shows an offline estimate (with ~); Just Eat shows its own miles, so no miles box.
        val info = if (o.app == App.DELIVEROO) {
            if (mi == null) "~? mi" else "~" + miles(mi) + " mi"
        } else null
        if (o.ageRestricted) {
            return Verdict(Level.DECLINE, info, emptyList(), o.postcode, true)
        }
        val lvl = if (mi == null || mi <= 0.0) Level.UNKNOWN
        else level(ratio(o.price / mi, p.minDeliveryPerMile))
        return Verdict(lvl, info, emptyList(), o.postcode, false)
    }
}
