package com.kaushalya.interrupter.data

import android.content.Context
import android.util.Log
import com.kaushalya.interrupter.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Submits question review feedback (up / down / report + comment) and keeps a
 * durable offline queue in Room. When the backend is unreachable (offline, or a
 * busy/slow API), the feedback is saved as [PendingFeedback] and flushed later by
 * [retrySyncFailed] once connectivity is restored — so the app keeps working with
 * no backend and still syncs when the API comes back up.
 */
class FeedbackRepository private constructor(context: Context) {

    private val database = AppDatabase.getDatabase(context)
    private val pendingFeedbackDao = database.pendingFeedbackDao()

    /**
     * Tries to persist a feedback action immediately; on any failure (offline,
     * timeout, server error) it is queued locally for later sync. Best-effort —
     * never throws to the caller.
     */
    suspend fun submit(
        questionId: Long,
        vote: String,
        downCategory: String?,
        comment: String?,
        report: Boolean
    ) = withContext(Dispatchers.IO) {
        try {
            val api = RetrofitClient.getApiService()
            val response = api.submitQuestionFeedback(
                id = questionId,
                request = QuestionFeedbackRequest(
                    vote = vote,
                    downCategory = downCategory,
                    comment = comment,
                    report = report
                )
            )
            if (!response.isSuccessful) {
                Log.w(TAG, "Feedback sync failed (HTTP ${response.code()}); queuing offline")
                queueForLater(questionId, vote, downCategory, comment, report)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Feedback sync error; queuing offline: ${e.message}")
            queueForLater(questionId, vote, downCategory, comment, report)
        }
    }

    private suspend fun queueForLater(
        questionId: Long,
        vote: String,
        downCategory: String?,
        comment: String?,
        report: Boolean
    ) {
        pendingFeedbackDao.insert(
            PendingFeedback(
                questionId = questionId,
                vote = vote,
                downCategory = downCategory,
                comment = comment,
                report = report
            )
        )
    }

    /** Flushes queued feedback to the backend, removing each row once accepted. */
    suspend fun retrySyncFailed() = withContext(Dispatchers.IO) {
        val pending = pendingFeedbackDao.getAllPending()
        if (pending.isEmpty()) return@withContext
        Log.d(TAG, "Flushing ${pending.size} queued feedback item(s)")
        pending.forEach { item ->
            try {
                val api = RetrofitClient.getApiService()
                val response = api.submitQuestionFeedback(
                    id = item.questionId,
                    request = QuestionFeedbackRequest(
                        vote = item.vote,
                        downCategory = item.downCategory,
                        comment = item.comment,
                        report = item.report
                    )
                )
                if (response.isSuccessful) {
                    pendingFeedbackDao.delete(item.id)
                    Log.d(TAG, "Flushed feedback for question ${item.questionId}")
                } else {
                    Log.w(TAG, "Retry failed for question ${item.questionId}: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Retry error for question ${item.questionId}: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "FeedbackRepository"

        @Volatile
        private var INSTANCE: FeedbackRepository? = null

        fun getInstance(context: Context): FeedbackRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FeedbackRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
