package com.kaushalya.interrupter.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kaushalya.interrupter.data.QuizResult
import com.kaushalya.interrupter.data.QuizResultRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Loads and exposes the quiz results for a single kid, used by the kid detail page
 * (performance charts + fast-answer insight). Results are sourced from the local Room DB
 * (which mirrors the backend for the kid's rows already synced/fetched).
 */
class KidDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = QuizResultRepository.getInstance(application)

    fun resultsFor(kidName: String): Flow<List<QuizResult>> = repository.getResultsByChild(kidName)

    // Convenience state hydrated once the kid is known (used by the detail screen).
    private val _results = MutableStateFlow<List<QuizResult>>(emptyList())
    val results: StateFlow<List<QuizResult>> = _results

    private var observeJob: Job? = null

    fun observe(kidName: String) {
        if (kidName.isBlank()) return
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.getResultsByChild(kidName)
                .collect { list -> _results.value = list.sortedByDescending { it.completedAt } }
        }
    }

    /** Re-reads the kid's results from the local DB (e.g. manual refresh after a quiz). */
    fun refresh(kidName: String) = observe(kidName)
}
