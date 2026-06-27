package com.drivecall.utilities

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class SpeedDialEntry(
    val slot: Int,
    val name: String,
    val phoneNumber: String
)

class SpeedDialManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("speed_dial", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SLOTS = "slots"
        const val MAX_SLOTS = 9
    }

    fun getEntry(slot: Int): SpeedDialEntry? {
        val all = getAll()
        return all.find { it.slot == slot }
    }

    fun getAll(): List<SpeedDialEntry> {
        val json = prefs.getString(KEY_SLOTS, null) ?: return emptyList()
        val entries = mutableListOf<SpeedDialEntry>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                entries.add(
                    SpeedDialEntry(
                        slot = obj.getInt("slot"),
                        name = obj.getString("name"),
                        phoneNumber = obj.getString("phone")
                    )
                )
            }
        } catch (_: Exception) {}
        return entries.sortedBy { it.slot }
    }

    fun setEntry(slot: Int, name: String, phoneNumber: String) {
        val entries = getAll().toMutableList()
        entries.removeAll { it.slot == slot }
        entries.add(SpeedDialEntry(slot, name, phoneNumber))
        saveAll(entries)
    }

    fun removeEntry(slot: Int) {
        val entries = getAll().toMutableList()
        entries.removeAll { it.slot == slot }
        saveAll(entries)
    }

    fun isSlotOccupied(slot: Int): Boolean {
        return getEntry(slot) != null
    }

    private fun saveAll(entries: List<SpeedDialEntry>) {
        val arr = JSONArray()
        for (e in entries) {
            val obj = JSONObject()
            obj.put("slot", e.slot)
            obj.put("name", e.name)
            obj.put("phone", e.phoneNumber)
            arr.put(obj)
        }
        prefs.edit().putString(KEY_SLOTS, arr.toString()).apply()
    }
}
