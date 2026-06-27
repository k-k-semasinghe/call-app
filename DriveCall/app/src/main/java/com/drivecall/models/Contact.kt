package com.drivecall.models

data class Contact(
    val id: Long,
    val name: String,
    val normalizedName: String,
    val phoneNumber: String,
    val aliases: List<String> = emptyList()
)
