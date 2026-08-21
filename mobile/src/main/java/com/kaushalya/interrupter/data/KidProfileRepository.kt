package com.kaushalya.interrupter.data

import android.content.Context
import android.util.Log
import com.kaushalya.interrupter.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class KidProfileRepository private constructor(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val kidProfileDao = database.kidProfileDao()
    private val sessionManager = SessionManager(context)

    fun getAllKids(): Flow<List<KidProfile>> = kidProfileDao.getAllKids()

    suspend fun saveKid(kid: KidProfile) {
        val finalKid = if (sessionManager.isOfflineMode) kid.copy(mode = "offline") else kid
        kidProfileDao.insertKid(finalKid)
        syncKidToBackend(finalKid)
    }

    suspend fun deleteKid(kid: KidProfile) {
        kidProfileDao.deleteKid(kid)
    }

    private suspend fun syncKidToBackend(kid: KidProfile) = withContext(Dispatchers.IO) {
        if (sessionManager.isGuest) return@withContext
        try {
            val api = RetrofitClient.getApiService()
            val request = KidRequest(
                name = kid.name,
                gender = kid.gender,
                birthYear = kid.birthYear,
                studentClass = kid.grade
            )

            val response = if (kid.remoteId != null) {
                api.updateKid(kid.remoteId, request)
            } else {
                api.addKid(request)
            }

            if (response.isSuccessful) {
                val remoteId = response.body()?.studentId
                kidProfileDao.updateSyncStatus(kid.id, 1, remoteId)
                Log.d("KidProfileRepository", "Synced kid ${kid.id} -> remote $remoteId")
            } else {
                kidProfileDao.updateSyncStatus(kid.id, 2, kid.remoteId)
                Log.w("KidProfileRepository", "Sync failed: ${response.code()}")
            }
        } catch (e: Exception) {
            kidProfileDao.updateSyncStatus(kid.id, 2, kid.remoteId)
            Log.e("KidProfileRepository", "Sync error", e)
        }
    }

    suspend fun retrySyncFailed() = withContext(Dispatchers.IO) {
        val unsynced = kidProfileDao.getUnsyncedKids()
        unsynced.forEach { kid ->
            syncKidToBackend(kid)
        }
    }

    /**
     * Pulls the account's students from the backend into the local Room cache.
     * Local-only kids are kept; server kids missing locally are inserted (deduped by remoteId).
     * Skipped in guest mode (no backend account).
     */
    suspend fun syncFromBackend() = withContext(Dispatchers.IO) {
        if (sessionManager.isGuest) return@withContext
        try {
            val api = RetrofitClient.getApiService()
            val response = api.getStudents()
            if (!response.isSuccessful) {
                Log.w(TAG, "Student sync failed: ${response.code()}")
                return@withContext
            }
            response.body()?.forEach { student ->
                val remoteId = student.studentId ?: return@forEach
                if (kidProfileDao.getByRemoteId(remoteId) == null) {
                    kidProfileDao.insertKid(
                        KidProfile(
                            name = student.name ?: DEFAULT_KID_NAME,
                            gender = student.gender ?: "",
                            birthYear = student.birthYear ?: 0,
                            grade = student.studentClass ?: "",
                            syncStatus = 1,
                            remoteId = remoteId
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Student sync error: ${e.message}")
        }
    }

    /**
     * Guarantees at least one kid profile exists after auth: pulls server kids first,
     * then creates the default "Kid1" (class "Exp") locally if the account still has none.
     * The default kid maps to hello-world/promo content until real details are provided.
     */
    suspend fun ensureDefaultKid() = withContext(Dispatchers.IO) {
        syncFromBackend()
        if (kidProfileDao.getAllKidsOnce().isEmpty()) {
            saveKid(defaultKid())
            Log.d(TAG, "Created default kid profile (Kid1 / Exp)")
        }
        refreshProfileKids()
    }

    /** Mirrors Room kid profiles into [SessionManager.profile] so kid-aware UIs stay in sync. */
    suspend fun refreshProfileKids() = withContext(Dispatchers.IO) {
        val kids = kidProfileDao.getAllKidsOnce()
        sessionManager.updateProfile {
            copy(kids = kids.map { ProfileKid(id = it.id, name = it.name, gender = it.gender.ifBlank { null }) })
        }
    }

    companion object {
        private const val TAG = "KidProfileRepository"
        const val DEFAULT_KID_NAME = "Kid1"
        const val DEFAULT_KID_GRADE = "Exp"

        fun defaultKid(): KidProfile = KidProfile(
            name = DEFAULT_KID_NAME,
            gender = "",
            birthYear = 0,
            grade = DEFAULT_KID_GRADE,
            syllabus = null
        )

        @Volatile
        private var INSTANCE: KidProfileRepository? = null

        fun getInstance(context: Context): KidProfileRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KidProfileRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
