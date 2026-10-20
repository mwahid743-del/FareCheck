package com.farecheck

data class Line(val text: String, val height: Int, val top: Int, val left: Int)

object OfferParser {
    private val lead = Regex("""^£\s?(\d+(?:\.\d{1,2})?)""")
    private val label = Regex("""^£\s?\d+(?:\.\d{1,2})?\s*(?:est|entitlement|holiday|toll|tip|bonus|boost|surge|today|\+)""", RegexOption.IGNORE_CASE)
    private val anyPrice = Regex("""£\s?(\d+(?:\.\d{1,2})?)""")
    private val skip = Regex("""holiday|today|toll|tip|bonus|boost|surge""", RegexOption.IGNORE_CASE)
    private val miles = Regex("""(\d+(?:\.\d+)?)\s?(?:mi|miles?)\b""", RegexOption.IGNORE_CASE)
    private val mins = Regex("""(\d+)\s?(?:mins?|minutes?)\b""", RegexOption.IGNORE_CASE)
    private val stopsRe = Regex("""(\d+)\s*stops?\b""", RegexOption.IGNORE_CASE)
    private val postcode = Regex("""\b([A-Z]{1,2}\d[A-Z\d]?)\s?(\d[A-Z]{2})\b""")
    private val pickup = Regex("""^\d+\s+pickups?:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val gate = Regex("""accept|match|confirm""", RegexOption.IGNORE_CASE)

    private fun pickPrice(lines: List<Line>): Double? {
        val big = lines
            .filter { val t = it.text.trim(); lead.containsMatchIn(t) && !label.containsMatchIn(t) }
            .maxByOrNull { it.height }
        big?.let { lead.find(it.text.trim())?.groupValues?.get(1)?.toDoubleOrNull() }?.let { return it }
        return lines.firstNotNullOfOrNull { l ->
            if (skip.containsMatchIn(l.text)) null
            else anyPrice.find(l.text)?.groupValues?.get(1)?.toDoubleOrNull()
        }
    }

    fun parse(app: App, lines: List<Line>, estimate: (List<String>) -> Double?): Offer? {
        val all = lines.joinToString("\n") { it.text }
        if (!gate.containsMatchIn(all)) return null
        val price = pickPrice(lines) ?: return null
        val stops = stopsRe.find(all)?.groupValues?.get(1)?.toIntOrNull()

        if (app.kind == Kind.RIDE) {
            val m = miles.findAll(all).mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
            if (m <= 0.0) return null
            val t = mins.findAll(all).mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
            return Offer(app, price, m, if (t > 0) t else null, null, null, false)
        }

        if (app == App.JUST_EAT) {
            val mi = miles.find(all)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
            val shop = lines.firstNotNullOfOrNull { pickup.find(it.text.trim())?.groupValues?.get(1)?.trim() }
            return Offer(app, price, mi, null, stops, shop, false)
        }

        val sorted = lines.sortedWith(compareBy({ it.top }, { it.left }))
        val pcIdx = sorted.indices.filter { postcode.containsMatchIn(sorted[it].text) }
        val codes = pcIdx.mapNotNull { i ->
            postcode.find(sorted[i].text)?.let { it.groupValues[1] + it.groupValues[2] }
        }
        val shop = pcIdx.firstOrNull()?.let { sorted.getOrNull(it - 1)?.text?.trim() }
            ?.takeIf { it.isNotEmpty() && !postcode.containsMatchIn(it) && !lead.containsMatchIn(it) }
        val est = estimate(codes)
        return Offer(app, price, est, null, stops, shop, true)
    }
}
