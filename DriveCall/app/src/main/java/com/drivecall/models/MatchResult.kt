package com.drivecall.models

data class MatchResult(
    val contact: Contact,
    val confidence: Double,
    val method: MatchMethod
)

enum class MatchMethod {
    EXACT,
    ALIAS,
    FUZZY
}
