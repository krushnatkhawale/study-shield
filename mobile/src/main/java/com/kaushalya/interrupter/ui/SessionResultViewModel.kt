package com.kaushalya.interrupter.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaushalya.interrupter.data.KidProfile
import com.kaushalya.interrupter.data.KidProfileRepository
import com.kaushalya.interrupter.data.QuizResult
import com.kaushalya.interrupter.data.QuizResultRepository
import com.kaushalya.interrupter.data.SessionManager
import com.kaushalya.interrupter.network.RetrofitClient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SessionResultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = QuizResultRepository.getInstance(application)
    private val kidRepository = KidProfileRepository.getInstance(application)
    private val sessionManager = SessionManager(application)

    val recentResults: StateFlow<List<QuizResult>> = repository.getRecentResults(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val kidProfiles: StateFlow<List<KidProfile>> = kidRepository.getAllKids()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The Exp-grade kid that just completed its first test and should be offered a
     * profile update to unlock class/syllabus based tests. Null when there is nobody
     * to prompt (no Exp kid, no results for one, or already handled for that kid).
     */
    val expUpgradeKid: StateFlow<KidProfile?> =
        combine(recentResults, kidProfiles) { results, kids ->
            val handled = sessionManager.expPromptHandledKidIds
            kids.filter { it.grade.equals(KidProfileRepository.DEFAULT_KID_GRADE, ignoreCase = true) }
                .firstOrNull { kid ->
                    kid.id !in handled && results.any { it.childName == kid.name }
                }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Marks the prompt as shown for this kid so it is not repeated. */
    fun markExpPromptHandled(kid: KidProfile) {
        sessionManager.expPromptHandledKidIds = sessionManager.expPromptHandledKidIds + kid.id
    }

    private val _selectedResult = MutableStateFlow<QuizResult?>(null)
    val selectedResult: StateFlow<QuizResult?> = _selectedResult

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState

    init {
        fetchResultsFromBackend()
    }

    fun fetchResultsFromBackend() {
        viewModelScope.launch {
            try {
                val api = RetrofitClient.getApiService()
                val response = api.listQuizResults()
                if (response.isSuccessful) {
                    val backendResults = response.body() ?: emptyList()
                    Log.d("SessionResultVM", "Fetched ${backendResults.size} results from backend")
                    backendResults.forEach { item ->
                        val backendId = item.id ?: return@forEach
                        val existing = repository.getByBackendId(backendId)
                        if (existing == null) {
                            val completedAt = try {
                                LocalDateTime.parse(item.completedAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            } catch (_: Exception) { System.currentTimeMillis() }

                            repository.saveResult(QuizResult(
                                childName = item.childName ?: "Quiz",
                                score = item.score ?: 0,
                                totalQuestions = item.totalQuestions ?: 0,
                                timeSpentSeconds = item.timeSpentSeconds ?: 0,
                                contentName = item.contentName,
                                category = item.category,
                                completedAt = completedAt,
                                syncStatus = 1,
                                backendId = backendId
                            ))
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("SessionResultVM", "Failed to fetch from backend", e)
            }
        }
    }

    fun selectResult(result: QuizResult) {
        _selectedResult.value = result
    }

    fun clearSelection() {
        _selectedResult.value = null
    }

    fun retrySync() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            try {
                repository.retrySyncFailed()
                _syncState.value = SyncState.Success("Results synced")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("Sync failed: ${e.message}")
            }
        }
    }

    fun saveResult(
        childName: String,
        score: Int,
        totalQuestions: Int,
        timeSpentSeconds: Long,
        contentName: String? = null,
        category: String? = null
    ) {
        viewModelScope.launch {
            val result = QuizResult(
                childName = childName,
                score = score,
                totalQuestions = totalQuestions,
                timeSpentSeconds = timeSpentSeconds,
                contentName = contentName,
                category = category
            )
            repository.saveResult(result)
        }
    }

    fun getMessageForScore(score: Int, total: Int): String {
        val ratio = if (total > 0) score.toDouble() / total else 0.0
        return when {
            ratio >= 0.8 -> "Great effort! Keep it up!"
            ratio >= 0.5 -> "Good try! A bit more practice will help."
            else -> "Needs a bit more practice. Don't give up!"
        }
    }
}

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}
