package com.farecheck

import java.util.Locale

object Verdicts {
    fun evaluate(o: Offer, p: Prefs): Verdict {
        val perMile = o.price / o.miles
        val hourly = o.minutes?.let { o.price / (it / 60.0) }
        val minMile = if (o.app.kind == Kind.RIDE) p.minRidePerMile else p.minDeliveryPerMile

        val ratios = mutableListOf(ratio(perMile, minMile))
        if (hourly != null) ratios += ratio(hourly, p.targetHourly)
        val worst = ratios.min()

        val level = when {
            worst >= 1.0 -> Level.ACCEPT
            worst >= 0.9 -> Level.MAYBE
            else -> Level.DECLINE
        }
        val head = when (level) { Level.ACCEPT -> "ACCEPT"; Level.MAYBE -> "BORDERLINE"; Level.DECLINE -> "DECLINE" }
        val miLine = String.format(Locale.UK, "£%.2f/mi", perMile)
        val text = if (hourly != null)
            String.format(Locale.UK, "%s · %s · £%.2f/h", head, miLine, hourly)
        else "$head · $miLine"
        return Verdict(level, text)
    }

    private fun ratio(v: Double, target: Double) = if (target <= 0) 9.9 else v / target
}
