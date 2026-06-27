package com.drivecall.utilities

import com.drivecall.models.CommandIntent
import com.drivecall.models.CommandResult

object CommandParser {

    private val callPatterns = listOf(
        Regex("^(?:call|dial|phone|ring)\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("^(?:call|dial|phone|ring)\\s+(?:to\\s+)?(.+)$", RegexOption.IGNORE_CASE),
    )

    private val actionCommands = mapOf<String, CommandIntent>(
        "navigate" to CommandIntent.UNKNOWN,
        "open" to CommandIntent.UNKNOWN,
        "message" to CommandIntent.UNKNOWN,
        "send" to CommandIntent.UNKNOWN,
        "play" to CommandIntent.UNKNOWN,
        "stop" to CommandIntent.UNKNOWN,
        "bluetooth" to CommandIntent.UNKNOWN,
        "wifi" to CommandIntent.UNKNOWN
    )

    fun parse(rawText: String): CommandResult {
        val trimmed = rawText.trim()
        Logger.speech("Parsing command: '$trimmed'")

        for (pattern in callPatterns) {
            val matchResult = pattern.find(trimmed)
            if (matchResult != null) {
                val target = matchResult.groupValues[1].trim()
                return CommandResult(
                    intent = CommandIntent.CALL,
                    targetName = target,
                    rawText = trimmed
                )
            }
        }

        return CommandResult(
            intent = CommandIntent.UNKNOWN,
            rawText = trimmed
        )
    }

    fun isCallCommand(text: String): Boolean {
        return callPatterns.any { it.containsMatchIn(text.trim()) }
    }

    fun extractContactName(text: String): String? {
        for (pattern in callPatterns) {
            val matchResult = pattern.find(text.trim())
            if (matchResult != null) {
                return matchResult.groupValues[1].trim()
            }
        }
        return null
    }
}
