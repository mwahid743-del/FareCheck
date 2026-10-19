package com.farecheck

data class Line(val text: String, val height: Int)

object OfferParser {
    private val price = Regex("""£\s?(\d+(?:\.\d{1,2})?)""")
    private val wholePrice = Regex("""^£\s?(\d+(?:\.\d{1,2})?)$""")
    private val miles = Regex("""(\d+(?:\.\d+)?)\s?(?:mi|miles?)\b""", RegexOption.IGNORE_CASE)
    private val mins = Regex("""(\d+)\s?(?:mins?|minutes?)\b""", RegexOption.IGNORE_CASE)
    private val gate = Regex("""accept|match|confirm""", RegexOption.IGNORE_CASE)
    private val skip = Regex("""holiday|today|tip|bonus|boost|surge""", RegexOption.IGNORE_CASE)

    fun parse(app: App, lines: List<Line>): Offer? {
        val all = lines.joinToString("\n") { it.text }
        if (!gate.containsMatchIn(all)) return null
        val big = lines.filter { wholePrice.matches(it.text.trim()) }.maxByOrNull { it.height }
        val p = big?.let { wholePrice.find(it.text.trim())?.groupValues?.get(1)?.toDoubleOrNull() }
            ?: lines.firstNotNullOfOrNull { l ->
                if (skip.containsMatchIn(l.text)) null
                else price.find(l.text)?.groupValues?.get(1)?.toDoubleOrNull()
            }
            ?: return null
        val m = miles.findAll(all).mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
        if (m <= 0.0) return null
        val t = mins.findAll(all).mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
        return Offer(app, p, m, if (app.kind == Kind.RIDE && t > 0) t else null)
    }
}
