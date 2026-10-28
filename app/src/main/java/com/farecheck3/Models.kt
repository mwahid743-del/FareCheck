package com.farecheck

enum class Kind { RIDE, DELIVERY }

enum class App(val kind: Kind, val label: String) {
    UBER(Kind.RIDE, "Uber"),
    BOLT(Kind.RIDE, "Bolt"),
    DELIVEROO(Kind.DELIVERY, "Deliveroo"),
    JUST_EAT(Kind.DELIVERY, "Just Eat");

    companion object {
        fun byPackage(p: String): App? {
            val s = p.lowercase()
            val courier = s.contains("courier") || s.contains("driver") || s.contains("rider")
            return when {
                s.contains("ubercab") && s.contains("driver") -> UBER
                s.contains("mtakso") && s.contains("driver") -> BOLT
                s.contains("deliveroo") && courier -> DELIVEROO
                (s.contains("justeat") || s.contains("just_eat") || s.contains("takeaway")) && courier -> JUST_EAT
                else -> null
            }
        }
    }
}

data class Offer(
    val app: App,
    val price: Double,
    val miles: Double?,
    val minutes: Double?,
    val stops: Int?,
    val shop: String?,
    val estimated: Boolean,
    val postcode: String? = null,
    val ageRestricted: Boolean = false
)

enum class Level { ACCEPT, MAYBE, DECLINE, UNKNOWN }

data class Chip(val text: String, val good: Boolean)

data class R(val l: Int, val t: Int, val r: Int, val b: Int) {
    val w get() = r - l
    val h get() = b - t
}

/** What to show: info text (miles/time), rate chips, drop-off postcode district, age flag. */
data class Verdict(
    val level: Level,
    val info: String?,
    val chips: List<Chip>,
    val postcode: String?,
    val age: Boolean
)
