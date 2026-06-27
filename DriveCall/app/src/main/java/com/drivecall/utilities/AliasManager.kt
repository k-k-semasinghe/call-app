package com.drivecall.utilities

object AliasManager {
    private val defaultAliases = mapOf(
        "aththa" to listOf("tatta", "thaththa", "father", "dad", "papa", "appachchi"),
        "amma" to listOf("ama", "ammi", "mother", "mom", "mummy", "mama"),
        "nangi" to listOf("nangi", "sister", "akkai"),
        "aiya" to listOf("ayya", "aiyaa", "brother", "bro", "malli", "malliya"),
        "saman ayya" to listOf("saman aiya", "saman bro", "saman"),
        "chathura" to listOf("chathura bro", "chathu"),
        "nuwan" to listOf("nuwan bro", "nuwa"),
        "kusal" to listOf("kus", "kusal bro"),
        "kasun" to listOf("kasun bro", "kas"),
        "nimal" to listOf("nimal bro", "nim")
    )

    fun getAliases(name: String): List<String> {
        val normalized = normalizeForAlias(name)
        val direct = defaultAliases[normalized] ?: emptyList()
        val reverseAliases = defaultAliases.entries
            .filter { it.value.any { alias -> alias == normalized } }
            .map { it.key }
        return (direct + reverseAliases + listOf(normalized)).distinct()
    }

    private fun normalizeForAlias(name: String): String {
        return name.trim().lowercase()
    }

    fun addCustomAlias(to: String, alias: String) {
    }
}
