package com.farecheck

data class Line(
    val text: String,
    val height: Int,
    val top: Int,
    val left: Int,
    val right: Int = left,
    val bottom: Int = top + height
)

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
    private val fromDest = Regex("""from destination""", RegexOption.IGNORE_CASE)
    private val ageRe = Regex("""age[\s-]*restricted|alcohol\W{0,4}age\s*18|\bid\s+check\b""", RegexOption.IGNORE_CASE)

    private val padRe = Regex("""pay\)""", RegexOption.IGNORE_CASE)
    private val statRe = Regex("""\d\s?(?:mi|miles?)\b""", RegexOption.IGNORE_CASE)

    /** Last line (top to bottom) holding a postcode: the drop-off address row. */
    fun dropLine(lines: List<Line>): Line? =
        lines.sortedWith(compareBy({ it.top }, { it.left })).lastOrNull { postcode.containsMatchIn(it.text) }

    private fun dropPostcode(lines: List<Line>): String? =
        dropLine(lines)?.let { l -> postcode.findAll(l.text).lastOrNull()?.groupValues?.get(1) }

    /** The big price line the verdict price is read from. */
    fun priceLine(lines: List<Line>): Line? = lines
        .filter { val t = it.text.trim(); lead.containsMatchIn(t) && !label.containsMatchIn(t) }
        .maxByOrNull { it.height }

    /** First "7 min • 1.0 mi" row (the pickup leg): topmost row with minutes, ignoring "from destination". */
    fun routeLine(lines: List<Line>): Line? =
        lines.sortedWith(compareBy({ it.top }, { it.left })).firstOrNull {
            mins.containsMatchIn(it.text) && !fromDest.containsMatchIn(it.text) &&
                !it.text.contains('£') && !it.text.contains("est", ignoreCase = true)
        }

    /** Bolt: the "Jamal E • 4.9 ★ (14)" driver row. */
    fun ratingLine(lines: List<Line>): Line? = lines.firstOrNull { it.text.contains('★') }

    /** Bolt: the wrapped row ending in "pay)" under the price, if present. */
    fun padLine(lines: List<Line>): Line? = lines.firstOrNull { padRe.containsMatchIn(it.text) }

    /** Just Eat: the "2.6 mi · 2 stops" row. */
    fun statLine(lines: List<Line>): Line? =
        lines.filter { statRe.containsMatchIn(it.text) }
            .let { c -> c.firstOrNull { stopsRe.containsMatchIn(it.text) } ?: c.firstOrNull() }

    private fun pickPrice(lines: List<Line>): Double? {
        val big = priceLine(lines)
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
        val drop = dropPostcode(lines)

        if (app.kind == Kind.RIDE) {
            val rideText = lines.map { it.text }.filterNot { fromDest.containsMatchIn(it) }.joinToString("\n")
            val m = miles.findAll(rideText).mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
            if (m <= 0.0) return null
            val t = mins.findAll(rideText).mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
            return Offer(app, price, m, if (t > 0) t else null, null, null, false, drop)
        }

        if (app == App.JUST_EAT) {
            val mi = miles.find(all)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
            val shop = lines.firstNotNullOfOrNull { pickup.find(it.text.trim())?.groupValues?.get(1)?.trim() }
            return Offer(app, price, mi, null, stops, shop, false, drop, ageRe.containsMatchIn(all))
        }

        val sorted = lines.sortedWith(compareBy({ it.top }, { it.left }))
        val pcIdx = sorted.indices.filter { postcode.containsMatchIn(sorted[it].text) }
        val codes = pcIdx.mapNotNull { i ->
            postcode.find(sorted[i].text)?.let { it.groupValues[1] + it.groupValues[2] }
        }
        val shop = pcIdx.firstOrNull()?.let { sorted.getOrNull(it - 1)?.text?.trim() }
            ?.takeIf { it.isNotEmpty() && !postcode.containsMatchIn(it) && !lead.containsMatchIn(it) }
        val est = estimate(codes)
        return Offer(app, price, est, null, stops, shop, true, drop, ageRe.containsMatchIn(all))
    }
}
