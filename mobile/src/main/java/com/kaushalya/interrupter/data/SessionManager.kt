package com.kaushalya.interrupter.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONObject

class SessionManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("auth_session", Context.MODE_PRIVATE)

    private val profileJson = Json { ignoreUnknownKeys = true }

    private fun metadataBlob(): JSONObject {
        val raw = prefs.getString(KEY_METADATA, null)
        if (raw != null) {
            return try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
        }
        // Migration: build blob from old individual keys
        val blob = JSONObject()
        prefs.getString(KEY_SESSION_ID, null)?.let { blob.put("session_id", it) }
        prefs.getString(KEY_LOGIN_ID, null)?.let { blob.put("login_id", it) }
        prefs.getString(KEY_ACCOUNT_ID, null)?.let { blob.put("account_id", it) }
        if (blob.length() > 0) {
            prefs.edit()
                .putString(KEY_METADATA, blob.toString())
                .remove(KEY_SESSION_ID)
                .remove(KEY_LOGIN_ID)
                .remove(KEY_ACCOUNT_ID)
                .apply()
        }
        return blob
    }

    private fun saveMetadata(blob: JSONObject) {
        val value = blob.toString()
        if (value == "{}") {
            prefs.edit().remove(KEY_METADATA).apply()
        } else {
            prefs.edit().putString(KEY_METADATA, value).apply()
        }
    }

    var sessionId: String?
        get() {
            val blob = metadataBlob()
            val v = if (blob.has("session_id")) blob.getString("session_id") else null
            Log.d(TAG, "get sessionId -> $v")
            return v
        }
        set(value) {
            Log.d(TAG, "set sessionId -> $value")
            val blob = metadataBlob()
            if (value != null) blob.put("session_id", value) else blob.remove("session_id")
            saveMetadata(blob)
        }

    var loginId: String?
        get() {
            val blob = metadataBlob()
            return if (blob.has("login_id")) blob.getString("login_id") else null
        }
        set(value) {
            Log.d(TAG, "set loginId -> $value")
            val blob = metadataBlob()
            if (value != null) blob.put("login_id", value) else blob.remove("login_id")
            saveMetadata(blob)
        }

    var accountId: String?
        get() {
            val blob = metadataBlob()
            return if (blob.has("account_id")) blob.getString("account_id") else null
        }
        set(value) {
            Log.d(TAG, "set accountId -> $value")
            val blob = metadataBlob()
            if (value != null) blob.put("account_id", value) else blob.remove("account_id")
            saveMetadata(blob)
        }

    var parentId: String?
        get() = migrateParentFields().defaultParentId
        set(value) {
            Log.d(TAG, "set parentId -> $value")
            profile = profile.copy(defaultParentId = value)
        }

    var parentName: String?
        get() = migrateParentFields().defaultParentName
        set(value) {
            Log.d(TAG, "set parentName -> $value")
            profile = profile.copy(defaultParentName = value)
        }

    private fun migrateParentFields(): ProfileData {
        val oldId = prefs.getString(KEY_PARENT_ID, null)
        val oldName = prefs.getString(KEY_PARENT_NAME, null)
        if (oldId == null && oldName == null) return profile
        val current = profile
        val updated = current.copy(
            defaultParentId = current.defaultParentId ?: oldId,
            defaultParentName = current.defaultParentName ?: oldName
        )
        if (updated != current) {
            profile = updated
        }
        prefs.edit()
            .remove(KEY_PARENT_ID)
            .remove(KEY_PARENT_NAME)
            .apply()
        return updated
    }

    var isGuest: Boolean
        get() = prefs.getBoolean(KEY_IS_GUEST, false)
        set(value) {
            Log.d(TAG, "set isGuest -> $value")
            prefs.edit().putBoolean(KEY_IS_GUEST, value).apply()
        }

    var isOfflineMode: Boolean
        get() = prefs.getBoolean(KEY_IS_OFFLINE, false)
        set(value) {
            Log.d(TAG, "set isOfflineMode -> $value")
            prefs.edit().putBoolean(KEY_IS_OFFLINE, value).apply()
        }

    var hasSeenCarousel: Boolean
        get() = prefs.getBoolean(KEY_SEEN_CAROUSEL, false)
        set(value) {
            Log.d(TAG, "set hasSeenCarousel -> $value")
            prefs.edit().putBoolean(KEY_SEEN_CAROUSEL, value).apply()
        }

    var selectedKidId: String?
        get() = prefs.getString(KEY_SELECTED_KID, null)
        set(value) {
            Log.d(TAG, "set selectedKidId -> $value")
            prefs.edit().putString(KEY_SELECTED_KID, value).apply()
        }

    /**
     * Kid profile ids for which the "update kid info to unlock specialized tests"
     * prompt has already been shown (one offer per kid until its profile is updated).
     */
    var expPromptHandledKidIds: Set<String>
        get() = prefs.getStringSet(KEY_EXP_PROMPT_HANDLED, emptySet()) ?: emptySet()
        set(value) {
            prefs.edit().putStringSet(KEY_EXP_PROMPT_HANDLED, value).apply()
        }

    val selectedKidName: String?
        get() {
            val kidId = selectedKidId ?: return null
            return profile.kids.find { it.id == kidId }?.name
        }

    fun isLoggedIn(): Boolean {
        val loggedIn = sessionId != null
        Log.d(TAG, "isLoggedIn -> $loggedIn (sessionId=${sessionId})")
        return loggedIn
    }

    fun clear() {
        Log.d(TAG, "clear: wiping all SharedPreferences")
        prefs.edit().clear().apply()
    }

    // --- Profile ---

    var profile: ProfileData
        get() {
            val raw = prefs.getString(KEY_PROFILE, null) ?: return ProfileData()
            return try {
                profileJson.decodeFromString(raw)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decode profile: ${e.message}")
                ProfileData()
            }
        }
        set(value) {
            Log.d(TAG, "saving profile: account=${value.account}, parents=${value.parents.size}, kids=${value.kids.size}, tvs=${value.tvHistory.size}")
            prefs.edit().putString(KEY_PROFILE, profileJson.encodeToString(value)).apply()
        }

    fun updateProfile(block: ProfileData.() -> ProfileData) {
        profile = profile.block()
    }

    companion object {
        private const val TAG = "SessionManager"
        private const val KEY_METADATA = "session_metadata"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_LOGIN_ID = "login_id"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_PARENT_ID = "parent_id"
        private const val KEY_PARENT_NAME = "parent_name"
        private const val KEY_IS_GUEST = "is_guest"
        private const val KEY_IS_OFFLINE = "is_offline"
        private const val KEY_SEEN_CAROUSEL = "seen_carousel"
        private const val KEY_SELECTED_KID = "selected_kid_id"
        private const val KEY_EXP_PROMPT_HANDLED = "exp_prompt_handled_kids"
        private const val KEY_PROFILE = "app_profile"
    }
}
