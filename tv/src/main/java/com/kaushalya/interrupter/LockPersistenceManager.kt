package com.kaushalya.interrupter

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LockPersistenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("lock_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun saveLockCommand(command: InterruptionCommand) {
        val serialized = json.encodeToString(command)
        prefs.edit().putString("saved_command", serialized).apply()
    }

    fun getSavedLockCommand(): InterruptionCommand? {
        val serialized = prefs.getString("saved_command", null) ?: return null
        return try {
            json.decodeFromString<InterruptionCommand>(serialized)
        } catch (e: Exception) {
            null
        }
    }

    fun clearLockCommand() {
        prefs.edit().remove("saved_command").apply()
    }
}