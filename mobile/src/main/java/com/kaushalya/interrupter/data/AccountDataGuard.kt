package com.kaushalya.interrupter.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tracks which account owns the locally cached Room data and wipes
 * account-scoped tables when the owner changes (account switch).
 *
 * The owner id lives in its own prefs file so it survives [SessionManager.clear].
 * Wi-Fi/TV pairing tables are device-scoped hardware state, not user data,
 * and are intentionally left untouched.
 */
class AccountDataGuard(context: Context, private val sessionManager: SessionManager) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val database = AppDatabase.getDatabase(context.applicationContext)

    suspend fun ensureOwner(ownerId: String) = withContext(Dispatchers.IO) {
        val stored = prefs.getString(KEY_OWNER, null)
        if (stored == ownerId) return@withContext

        Log.w(TAG, "Local data owner changed ($stored -> $ownerId), wiping account-scoped data")
        database.quizResultDao().deleteAll()
        database.studySessionDao().deleteAll()
        database.kidProfileDao().deleteAll()
        sessionManager.selectedKidId = null
        prefs.edit().putString(KEY_OWNER, ownerId).apply()
    }

    companion object {
        private const val TAG = "AccountDataGuard"
        private const val PREFS_NAME = "data_owner"
        private const val KEY_OWNER = "owner_id"
    }
}
