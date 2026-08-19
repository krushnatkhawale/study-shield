package com.kaushalya.interrupter.data

import android.content.Context
import android.util.Log
import com.kaushalya.interrupter.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class QuizResultRepository private constructor(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val quizResultDao = database.quizResultDao()

    fun getRecentResults(limit: Int = 10): Flow<List<QuizResult>> =
        quizResultDao.getRecentResults(limit)

    fun getAllResults(): Flow<List<QuizResult>> =
        quizResultDao.getAllResults()

    suspend fun saveResult(result: QuizResult) {
        quizResultDao.insertResult(result)
        syncResult(result)
    }

    suspend fun getResultById(id: String): QuizResult? =
        quizResultDao.getResultById(id)

    suspend fun getByBackendId(backendId: Long): QuizResult? =
        quizResultDao.getByBackendId(backendId)

    private suspend fun syncResult(result: QuizResult) = withContext(Dispatchers.IO) {
        try {
            val api = RetrofitClient.getApiService()
            val request = QuizResultRequest(
                childName = result.childName,
                score = result.score,
                totalQuestions = result.totalQuestions,
                timeSpentSeconds = result.timeSpentSeconds,
                contentName = result.contentName,
                category = result.category,
                completedAt = result.completedAt
            )
            val response = api.saveQuizResult(request)
            if (response.isSuccessful) {
                val backendId = response.body()?.resultId?.toLongOrNull()
                if (backendId != null) {
                    quizResultDao.updateSyncStatusAndBackendId(result.id, 1, backendId)
                } else {
                    quizResultDao.updateSyncStatus(result.id, 1)
                }
                Log.d("QuizResultRepository", "Synced result ${result.id}")
            } else {
                quizResultDao.updateSyncStatus(result.id, 2)
                Log.w("QuizResultRepository", "Sync failed: ${response.code()}")
            }
        } catch (e: Exception) {
            quizResultDao.updateSyncStatus(result.id, 2)
            Log.e("QuizResultRepository", "Sync error", e)
        }
    }

    suspend fun retrySyncFailed() = withContext(Dispatchers.IO) {
        val unsynced = quizResultDao.getUnsyncedResults()
        unsynced.forEach { result ->
            syncResult(result)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: QuizResultRepository? = null

        fun getInstance(context: Context): QuizResultRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: QuizResultRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
