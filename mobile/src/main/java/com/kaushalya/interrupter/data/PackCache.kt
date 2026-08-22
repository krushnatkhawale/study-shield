package com.kaushalya.interrupter.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

/**
 * Per-user local cache of freemium quiz packs, so the Select Content screen does not
 * hit the backend every time. Cache is keyed by logged-in user (or "guest") + grade,
 * stored in app-private files as serialized StudyContent lists.
 */
class PackCache(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    fun userKey(loginId: String?): String =
        loginId?.takeIf { it.isNotBlank() } ?: "guest"

    private fun cacheFile(user: String, grade: String): java.io.File {
        val dir = java.io.File(context.filesDir, "pack_cache").apply { mkdirs() }
        val safeGrade = MessageDigest.getInstance("MD5")
            .digest(grade.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)
        return java.io.File(dir, "${user}_$safeGrade.json")
    }

    /**
     * Returns cached packs for [user]/[grade] if present; logs that the download was skipped.
     */
    fun get(user: String, grade: String): List<StudyContent>? {
        val file = cacheFile(user, grade)
        if (!file.exists()) return null
        return try {
            val packs = json.decodeFromString<List<StudyContent>>(file.readText())
            Log.d(TAG, "Cache hit for user=$user grade=$grade (${packs.size} packs) — skipping backend fetch")
            packs
        } catch (e: Exception) {
            Log.w(TAG, "Corrupt pack cache for $user/$grade, refetching: ${e.message}")
            file.delete()
            null
        }
    }

    fun put(user: String, grade: String, packs: List<StudyContent>) {
        try {
            cacheFile(user, grade).writeText(json.encodeToString(packs))
            Log.d(TAG, "Cached ${packs.size} packs for user=$user grade=$grade (first download)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write pack cache for $user/$grade: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PackCache"
    }
}
