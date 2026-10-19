package com.farecheck

/**
 * Generic text-based parser. Takes all on-screen text, requires an accept-style
 * button plus a £ price and at least one distance. Sums every distance/time found
 * (pickup + trip). Tune per app using `adb logcat -s FareCheck`.
 */
object OfferParser {
    private val price = Regex("""£\s?(\d+(?:\.\d{1,2})?)""")
    private val miles = Regex("""(\d+(?:\.\d+)?)\s?(?:mi|miles?)\b""", RegexOption.IGNORE_CASE)
    private val mins = Regex("""(\d+)\s?(?:mins?|minutes?)\b""", RegexOption.IGNORE_CASE)
    private val gate = Regex("""accept|match|confirm""", RegexOption.IGNORE_CASE)

    fun parse(app: App, texts: List<String>): Offer? {
        val all = texts.joinToString("\n")
        if (!gate.containsMatchIn(all)) return null
        val p = price.find(all)?.groupValues?.get(1)?.toDoubleOrNull() ?: return null
        val m = miles.findAll(all).mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
        if (m <= 0.0) return null
        val t = mins.findAll(all).mapNotNull { it.groupValues[1].toDoubleOrNull() }.sum()
        return Offer(app, p, m, if (app.kind == Kind.RIDE && t > 0) t else null)
    }
}
