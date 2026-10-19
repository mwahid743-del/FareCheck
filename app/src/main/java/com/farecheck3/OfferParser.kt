package com.farecheck

data class Line(val text: String, val height: Int)

object OfferParser {
    private val lead = Regex("""^£\s?(\d+(?:\.\d{1,2})?)""")
    private val label = Regex("""^£\s?\d+(?:\.\d{1,2})?\s*(?:est|entitlement|holiday|toll|tip|bonus|boost|surge|today|\+)""", RegexOption.IGNORE_CASE)
    private val anyPrice = Regex("""£\s?(\d+(?:\.\d{1,2})?)""")
    private val skip = Regex("""holiday|today|toll|tip|bonus|boost|surge""", RegexOption.IGNORE_CASE)
    private val miles = Regex("""(\d+(?:\.\d+)?)\s?(?:mi|miles?)\b""", RegexOption.IGNORE_CASE)
    private val mins = Regex("""(\d+)\s?(?:mins?|minutes?)\b""", RegexOption.IGNORE_CASE)
    private val gate = Regex("""accept|match|confirm""", RegexOption.IGNORE_CASE)

    fun parse(app: App, lines: List<Line>): Offer? {
        val all = lines.joinToString("\n") { it.text }
        if (!gate.containsMatchIn(all)) return null
        val p = lines
            .filter { val t = it.text.trim(); lead.containsMatchIn(t) && !label.containsMatchIn(t) }
            .maxByOrNull { it.height }
            ?.let { lead.find(it.text.trim())?.groupValues?.get(1)?.toDoubleOrNull() }
            ?: lines.firstNotNullOfOrNull { l ->
                if (skip.containsMatchIn(l.text)) null
                else anyPrice.find(l.text)?.groupValues?.get(1)?.toDoubleOrNull()
            }
            ?: return null
        val m = miles.findAll(all).mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
        if (m <= 0.0) return null
        val t = mins.findAll(all).mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
        return Offer(app, p, m, if (app.kind == Kind.RIDE && t > 0) t else null)
    }
}
