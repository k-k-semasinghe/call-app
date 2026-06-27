package com.drivecall.utilities

import com.drivecall.models.CommandIntent
import com.drivecall.models.CommandResult

object CommandParser {

    private val callPatterns = listOf(
        Regex("^(?:call|dial|phone|ring)\\s+(.+)$", RegexOption.IGNORE_CASE),
        Regex("^(?:call|dial|phone|ring)\\s+(?:to\\s+)?(.+)$", RegexOption.IGNORE_CASE),
    )

    private val speedDialPatterns = listOf(
        Regex("^(?:call|dial|phone|ring)\\s+(?:speed\\s+)?(?:dial\\s+)?(?:number|slot)\\s+(\\w+)$", RegexOption.IGNORE_CASE),
        Regex("^(?:call|dial|phone|ring)\\s+speed\\s+dial\\s+(\\w+)$", RegexOption.IGNORE_CASE),
        Regex("^speed\\s+dial(?:\\s+number)?\\s+(\\w+)$", RegexOption.IGNORE_CASE),
        Regex("^(?:number|slot)\\s+(\\w+)$", RegexOption.IGNORE_CASE),
    )

    val numberWordMap = mapOf(
        "one" to 1, "first" to 1, "1" to 1,
        "two" to 2, "second" to 2, "2" to 2,
        "three" to 3, "third" to 3, "3" to 3,
        "four" to 4, "fourth" to 4, "4" to 4,
        "five" to 5, "fifth" to 5, "5" to 5,
        "six" to 6, "sixth" to 6, "6" to 6,
        "seven" to 7, "seventh" to 7, "7" to 7,
        "eight" to 8, "eighth" to 8, "8" to 8,
        "nine" to 9, "ninth" to 9, "9" to 9,
    )

    private val timePatterns = listOf(
        Regex("^(?:what(?:'s|\\s+is)\\s+(?:the\\s+)?)?(?:current\\s+)?time(?:\\s+is\\s+it)?$", RegexOption.IGNORE_CASE),
        Regex("^tell\\s+me\\s+(?:the\\s+)?(?:current\\s+)?time$", RegexOption.IGNORE_CASE),
        Regex("^time(?:\\s+please)?$", RegexOption.IGNORE_CASE),
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

        for (pattern in speedDialPatterns) {
            val matchResult = pattern.find(trimmed)
            if (matchResult != null) {
                val word = matchResult.groupValues[1].trim().lowercase()
                val slot = numberWordMap[word]
                if (slot != null && slot in 1..9) {
                    return CommandResult(
                        intent = CommandIntent.SPEED_DIAL,
                        slotNumber = slot,
                        rawText = trimmed
                    )
                }
            }
        }

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

        for (pattern in timePatterns) {
            if (pattern.matches(trimmed)) {
                return CommandResult(
                    intent = CommandIntent.TIME,
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
