package com.farecheck

enum class Kind { RIDE, DELIVERY }

// Package names: verify on your phone (adb shell dumpsys window | grep mCurrentFocus)
enum class App(val pkg: String, val kind: Kind) {
    UBER("com.ubercab.driver", Kind.RIDE),
    BOLT("ee.mtakso.driver", Kind.RIDE),
    DELIVEROO("com.deliveroo.driverapp", Kind.DELIVERY),
    JUST_EAT("com.justeat.courier", Kind.DELIVERY);

    companion object {
        fun byPackage(p: String): App? = values().firstOrNull { it.pkg == p }
    }
}

data class Offer(val app: App, val price: Double, val miles: Double, val minutes: Double?)

enum class Level { ACCEPT, MAYBE, DECLINE }

data class Verdict(val level: Level, val text: String)
