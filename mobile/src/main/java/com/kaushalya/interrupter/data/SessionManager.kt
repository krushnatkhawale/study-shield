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

    /**
     * Parent-facing app language (SS-EXP-03): BCP-47 tag for the resource locale used by the
     * mobile app chrome ("en", "hi", "mr"). Null = never chosen → English fallback. This is a
     * **different** setting from [KidQuizConfig.greetingLanguage] (the per-kid TV greeting).
     */
    var appLocale: String?
        get() = prefs.getString(KEY_APP_LOCALE, null)
        set(value) {
            Log.d(TAG, "set appLocale -> $value")
            if (value == null) {
                prefs.edit().remove(KEY_APP_LOCALE).apply()
            } else {
                prefs.edit().putString(KEY_APP_LOCALE, value).apply()
            }
        }

    /** SS-EXP-07: Optional TTS narration for the first-run stepper screens. */
    var speakSetupSteps: Boolean
        get() = prefs.getBoolean(KEY_SPEAK_SETUP_STEPS, false)
        set(value) {
            Log.d(TAG, "set speakSetupSteps -> $value")
            prefs.edit().putBoolean(KEY_SPEAK_SETUP_STEPS, value).apply()
        }

    var hasCompletedFirstQuiz: Boolean
        get() = prefs.getBoolean(KEY_COMPLETED_FIRST_QUIZ, false)
        set(value) {
            Log.d(TAG, "set hasCompletedFirstQuiz -> $value")
            prefs.edit().putBoolean(KEY_COMPLETED_FIRST_QUIZ, value).apply()
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

    private fun kidConfigKey(kidId: String) = "$KEY_KID_CONFIG_PREFIX$kidId"

    /** Per-kid quiz presentation config (Features 4/5 switchable threshold). Defaults when unset. */
    fun getKidQuizConfig(kidId: String): KidQuizConfig {
        val raw = prefs.getString(kidConfigKey(kidId), null) ?: return KidQuizConfig()
        return try {
            profileJson.decodeFromString(raw)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode kid config: ${e.message}")
            KidQuizConfig()
        }
    }

    fun setKidQuizConfig(kidId: String, config: KidQuizConfig) {
        prefs.edit().putString(kidConfigKey(kidId), profileJson.encodeToString(config)).apply()
    }

    /** IP of the TV the family most recently launched a session on (used for TTS checks). */
    var lastTvIp: String?
        get() = prefs.getString(KEY_LAST_TV_IP, null)
        set(value) {
            prefs.edit().putString(KEY_LAST_TV_IP, value).apply()
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
        private const val KEY_APP_LOCALE = "app_locale"
        private const val KEY_SPEAK_SETUP_STEPS = "speak_setup_steps"
        private const val KEY_COMPLETED_FIRST_QUIZ = "completed_first_quiz"
        private const val KEY_SELECTED_KID = "selected_kid_id"
        private const val KEY_EXP_PROMPT_HANDLED = "exp_prompt_handled_kids"
        private const val KEY_KID_CONFIG_PREFIX = "kid_quiz_config_"
        private const val KEY_LAST_TV_IP = "last_tv_ip"
        private const val KEY_PROFILE = "app_profile"
    }
}
