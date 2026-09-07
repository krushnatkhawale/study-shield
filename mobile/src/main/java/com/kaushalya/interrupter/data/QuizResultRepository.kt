package com.kaushalya.interrupter.data

import android.content.Context
import android.util.Log
import com.kaushalya.interrupter.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class QuizResultRepository private constructor(context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val quizResultDao = database.quizResultDao()
    private val sessionManager = SessionManager(context)

    fun getRecentResults(limit: Int = 10): Flow<List<QuizResult>> =
        quizResultDao.getRecentResults(limit)

    fun getAllResults(): Flow<List<QuizResult>> =
        quizResultDao.getAllResults()

    fun getResultsByChild(childName: String): Flow<List<QuizResult>> =
        quizResultDao.getResultsByChild(childName)

    suspend fun saveResult(result: QuizResult) {
        val finalResult = if (sessionManager.isOfflineMode) result.copy(mode = "offline") else result
        quizResultDao.insertResult(finalResult)
        syncResult(finalResult)
    }

    /**
     * Inserts a result that already exists on the backend (e.g. one pulled back by a fetch).
     * These rows must NOT be pushed to the server again — re-syncing them would create
     * duplicate results that inflate attempt counts on every refresh.
     */
    suspend fun insertFromBackend(result: QuizResult) {
        quizResultDao.insertResult(result)
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
                completedAt = result.completedAt,
                fastAnswerCount = result.fastAnswerCount
            )
            val response = api.saveQuizResult(request)
            if (response.isSuccessful) {
                val backendId = response.body()?.resultId?.toLongOrNull()
                if (backendId != null) {
                    quizResultDao.updateSyncStatusAndBackendId(result.id, 1, backendId)
                } else {
                    quizResultDao.updateSyncStatus(result.id, 1)
                }
                // Offline-attempt rows are disposable once the server has acknowledged them:
                // their authoritative copy lives on the backend and is pulled back on the next
                // fetch (inserted without re-syncing), so keeping the local offline copy would
                // just clutter the device and risk double counting.
                if (result.mode == "offline") {
                    quizResultDao.deleteResult(result)
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

    /**
     * Pulls results from the backend and inserts any rows not yet present locally.
     * Returns how many rows were newly inserted (e.g. for a manual refresh prompt).
     */
    suspend fun syncFromBackend(): Int = withContext(Dispatchers.IO) {
        try {
            val api = RetrofitClient.getApiService()
            val response = api.listQuizResults()
            if (!response.isSuccessful) return@withContext 0
            val items = response.body() ?: emptyList()
            var inserted = 0
            items.forEach { item ->
                val backendId = item.id ?: return@forEach
                if (getByBackendId(backendId) == null) {
                    val completedAt = try {
                        LocalDateTime.parse(
                            item.completedAt,
                            DateTimeFormatter.ISO_LOCAL_DATE_TIME
                        ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } catch (_: Exception) {
                        System.currentTimeMillis()
                    }
                    insertFromBackend(
                        QuizResult(
                            childName = item.childName ?: "Quiz",
                            score = item.score ?: 0,
                            totalQuestions = item.totalQuestions ?: 0,
                            timeSpentSeconds = item.timeSpentSeconds ?: 0,
                            contentName = item.contentName,
                            category = item.category,
                            completedAt = completedAt,
                            syncStatus = 1,
                            backendId = backendId,
                            fastAnswerCount = item.fastAnswerCount ?: 0
                        )
                    )
                    inserted++
                }
            }
            Log.d("QuizResultRepository", "Backend sync: ${items.size} listed, $inserted added")
            inserted
        } catch (e: Exception) {
            Log.e("QuizResultRepository", "Backend sync failed", e)
            0
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
