package com.kaushalya.interrupter.ui

sealed class StudyUiState {
    object Idle : StudyUiState()
    object Loading : StudyUiState()
    data class Success(val message: String) : StudyUiState()
    data class Error(val message: String) : StudyUiState()
}
