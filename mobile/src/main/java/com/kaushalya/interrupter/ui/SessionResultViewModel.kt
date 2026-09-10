package com.kaushalya.interrupter.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaushalya.interrupter.R
import com.kaushalya.interrupter.data.KidProfile
import com.kaushalya.interrupter.data.KidProfileRepository
import com.kaushalya.interrupter.data.QuizResult
import com.kaushalya.interrupter.data.QuizResultRepository
import com.kaushalya.interrupter.data.SessionManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SessionResultViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = QuizResultRepository.getInstance(application)
    private val kidRepository = KidProfileRepository.getInstance(application)
    private val sessionManager = SessionManager(application)

    val recentResults: StateFlow<List<QuizResult>> = repository.getRecentResults(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val kidProfiles: StateFlow<List<KidProfile>> = kidRepository.getAllKids()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The Trial-grade kid that just completed its first test and should be offered a
     * profile update to unlock class/syllabus based tests. Null when there is nobody
     * to prompt (no Trial kid, no results for one, or already handled for that kid).
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

    fun refresh() {
        fetchResultsFromBackend()
        retrySync()
    }

    fun fetchResultsFromBackend() {
        viewModelScope.launch {
            try {
                val added = repository.syncFromBackend()
                Log.d("SessionResultVM", "Backend fetch: added $added results")
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
                _syncState.value = SyncState.Success(getApplication<Application>().getString(R.string.results_synced))
            } catch (e: Exception) {
                _syncState.value = SyncState.Error(getApplication<Application>().getString(R.string.sync_failed, e.message))
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
            ratio >= 0.8 -> getApplication<Application>().getString(R.string.result_msg_great)
            ratio >= 0.5 -> getApplication<Application>().getString(R.string.result_msg_good)
            else -> getApplication<Application>().getString(R.string.result_msg_practice)
        }
    }
}

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Success(val message: String) : SyncState()
    data class Error(val message: String) : SyncState()
}
