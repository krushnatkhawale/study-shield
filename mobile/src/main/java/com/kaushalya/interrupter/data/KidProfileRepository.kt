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

    fun getAllKids(): Flow<List<KidProfile>> = kidProfileDao.getAllKids()

    suspend fun saveKid(kid: KidProfile) {
        kidProfileDao.insertKid(kid)
        syncKidToBackend(kid)
    }

    suspend fun deleteKid(kid: KidProfile) {
        kidProfileDao.deleteKid(kid)
    }

    private suspend fun syncKidToBackend(kid: KidProfile) = withContext(Dispatchers.IO) {
        try {
            val api = RetrofitClient.getApiService()
            val request = StudentRequest(
                name = kid.name,
                gender = kid.gender,
                birthYear = kid.birthYear,
                studentClass = kid.grade
            )

            val response = if (kid.remoteId != null) {
                api.updateStudent(kid.remoteId, request)
            } else {
                api.addStudent(request)
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

    companion object {
        @Volatile
        private var INSTANCE: KidProfileRepository? = null

        fun getInstance(context: Context): KidProfileRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: KidProfileRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
