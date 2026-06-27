package com.drivecall.models

data class CommandResult(
    val intent: CommandIntent,
    val targetName: String? = null,
    val slotNumber: Int? = null,
    val rawText: String = "",
    val confidence: Double = 0.0,
    val matchedContact: Contact? = null
)

enum class CommandIntent {
    CALL,
    DIAL,
    PHONE,
    TIME,
    SPEED_DIAL,
    UNKNOWN
}
