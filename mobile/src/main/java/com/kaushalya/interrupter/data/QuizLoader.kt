package com.kaushalya.interrupter.data

import android.content.Context
import android.util.Log
import com.kaushalya.interrupter.network.RetrofitClient
import kotlinx.serialization.Serializable

@Serializable
data class QuizBundle(
    val quizzes: List<StudyContent>
)

class QuizLoader(private val context: Context) {

    /**
     * Quizzes for a grade, server-first: issues a quiz bundle from the backend so content
     * can be updated per country/rollout without an app release. Returns empty if the
     * backend is unreachable or has nothing for the grade yet.
     */
    suspend fun loadQuizzesForGradeRemoteFirst(grade: String): List<StudyContent> {
        return try {
            fetchQuizzesForGrade(grade)
        } catch (e: Exception) {
            Log.w("QuizLoader", "Remote quiz bundle unavailable: ${e.message}")
            emptyList()
        }
    }

    private suspend fun fetchQuizzesForGrade(grade: String): List<StudyContent> {
        val requestDto = QuizBundleRequestDto(className = grade, deviceId = DeviceIdentity.deviceId(context))
        var response = RetrofitClient.getApiService().issueQuizBundle(requestDto)

        // Guest self-healing: on 401 the stored token may be the literal "guest" string
        // (offline fallback from a cold-start timeout) or an expired JWT.  Re-mint a
        // real guest token and retry once.
        if (response.code() == 401) {
            val sm = RetrofitClient.sessionManager
            if (sm != null && sm.isGuest) {
                Log.w("QuizLoader", "Quiz bundle 401 for guest — refreshing token")
                val freshResult = AuthRepository().guestLogin(DeviceIdentity.deviceId(context))
                if (freshResult.isSuccess) {
                    val freshToken = freshResult.getOrNull()?.sessionId
                    if (freshToken != null) {
                        sm.sessionId = freshToken
                        response = RetrofitClient.getApiService().issueQuizBundle(requestDto)
                    }
                } else {
                    Log.w("QuizLoader", "Guest token refresh failed: ${freshResult.exceptionOrNull()?.message}")
                }
            }
        }

        if (!response.isSuccessful) {
            Log.w("QuizLoader", "Quiz bundle request failed: HTTP ${response.code()} for grade $grade")
            return emptyList()
        }
        val bundle = response.body() ?: return emptyList()
        val mapped = bundle.quizzes.mapNotNull { quiz ->
            val questions = quiz.questions.mapNotNull { q ->
                val text = q.questionText ?: return@mapNotNull null
                val optionTexts = q.options.mapNotNull { it.text }
                if (optionTexts.isEmpty()) return@mapNotNull null
                val correctId = q.correctAnswers.firstOrNull()
                val answer = q.options.firstOrNull { it.id == correctId }?.text
                    ?: correctId
                    ?: ""
                QuizQuestion(question = text, options = optionTexts, answer = answer, id = q.id).shuffledOptions()
            }
            if (questions.isEmpty()) return@mapNotNull null
            val subject = quiz.title?.split("·")?.firstOrNull()?.trim()
                ?: quiz.contentPackName?.removePrefix("Freemium ")?.trim()
                ?: bundle.subjects.firstOrNull()
            StudyContent(
                type = ContentType.QUIZ,
                id = quiz.id?.toString(),
                name = quiz.title ?: "Quiz",
                category = subject,
                questions = questions,
                grade = grade
            )
        }
        Log.d("QuizLoader", "Loaded ${mapped.size} quizzes from backend for grade $grade")
        return mapped
    }
}
