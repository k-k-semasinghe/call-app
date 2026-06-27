package com.drivecall.fuzzy

import com.drivecall.models.Contact
import com.drivecall.models.MatchMethod
import com.drivecall.models.MatchResult
import com.drivecall.utilities.AliasManager
import com.drivecall.utilities.Logger
import kotlin.math.max
import kotlin.math.min

object FuzzySearchHelper {

    private const val HIGH_CONFIDENCE_THRESHOLD = 0.90
    private const val MEDIUM_CONFIDENCE_THRESHOLD = 0.60

    fun findBestMatch(
        query: String,
        contacts: List<Contact>,
        topN: Int = 3
    ): List<MatchResult> {
        val normalizedQuery = normalizeText(query)
        if (normalizedQuery.isBlank()) return emptyList()

        Logger.matching("Searching for: '$query' (normalized: '$normalizedQuery')")

        val candidates = mutableListOf<MatchResult>()

        val queryWords = normalizedQuery.split("\\s+".toRegex()).filter { it.isNotBlank() }

        for (contact in contacts) {
            val contactNorm = contact.normalizedName

            val exactScore = calculateExactMatchScore(normalizedQuery, contactNorm)
            if (exactScore > 0.0) {
                candidates.add(MatchResult(contact, exactScore, MatchMethod.EXACT))
                continue
            }

            val aliasScore = calculateAliasScore(normalizedQuery, contact)
            if (aliasScore > 0.0) {
                candidates.add(MatchResult(contact, aliasScore, MatchMethod.ALIAS))
                continue
            }

            val fuzzyScore = calculateFuzzyScore(normalizedQuery, contactNorm, queryWords)
            if (fuzzyScore >= MEDIUM_CONFIDENCE_THRESHOLD) {
                candidates.add(MatchResult(contact, fuzzyScore, MatchMethod.FUZZY))
            }
        }

        candidates.sortByDescending { it.confidence }
        val results = candidates.take(topN)

        Logger.matching("Top matches for '$query': ${
            results.joinToString { "${it.contact.name} (${(it.confidence * 100).toInt()}%)" }
        }")

        return results
    }

    private fun calculateExactMatchScore(query: String, contactName: String): Double {
        if (query == contactName) return 1.0
        val contactParts = contactName.split("\\s+".toRegex())
        val queryParts = query.split("\\s+".toRegex())

        if (queryParts.size == 1 && contactParts.size > 1) {
            if (contactParts.any { it == query }) return 0.85
        }
        return 0.0
    }

    private fun calculateAliasScore(query: String, contact: Contact): Double {
        val allAliases = contact.aliases + AliasManager.getAliases(contact.name)
        for (alias in allAliases) {
            val normalizedAlias = normalizeText(alias)
            if (query == normalizedAlias) return 0.95
            val aliasParts = normalizedAlias.split("\\s+".toRegex())
            val queryParts = query.split("\\s+".toRegex())
            if (aliasParts.any { it == query }) return 0.85
            if (queryParts.any { it == alias }) return 0.80
        }
        return 0.0
    }

    private fun calculateFuzzyScore(
        query: String,
        contactName: String,
        queryWords: List<String>
    ): Double {
        val levScore = 1.0 - normalizedLevenshteinDistance(query, contactName)

        val contactParts = contactName.split("\\s+".toRegex())
        var maxWordScore = 0.0
        for (qWord in queryWords) {
            for (cWord in contactParts) {
                val wordLevScore = 1.0 - normalizedLevenshteinDistance(qWord, cWord)
                if (wordLevScore > maxWordScore) maxWordScore = wordLevScore
            }
        }

        val prefixScore = if (contactName.startsWith(query)) 0.3 else 0.0
        val containsScore = if (contactName.contains(query)) 0.25 else 0.0

        return max(levScore, maxWordScore) + prefixScore + containsScore
    }

    private fun normalizeText(text: String): String {
        return text.trim()
            .lowercase()
            .replace(Regex("[\\s]+"), " ")
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
    }

    private fun normalizedLevenshteinDistance(s1: String, s2: String): Double {
        if (s1.isEmpty()) return if (s2.isEmpty()) 0.0 else 1.0
        if (s2.isEmpty()) return 1.0

        val maxLen = max(s1.length, s2.length).toDouble()
        val distance = levenshteinDistance(s1, s2).toDouble()
        return distance / maxLen
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    fun classifyConfidence(confidence: Double): ConfidenceLevel {
        return when {
            confidence >= HIGH_CONFIDENCE_THRESHOLD -> ConfidenceLevel.HIGH
            confidence >= MEDIUM_CONFIDENCE_THRESHOLD -> ConfidenceLevel.MEDIUM
            else -> ConfidenceLevel.LOW
        }
    }
}

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}
