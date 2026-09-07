package com.kaushalya.interrupter.ui

import com.kaushalya.interrupter.data.InterruptionCommand
import com.kaushalya.interrupter.data.StudyContent

sealed class StudyUiState {
    object Idle : StudyUiState()
    object Loading : StudyUiState()
    data class Success(val message: String) : StudyUiState()

    /**
     * The selected TV's TTS can't speak the kid's greeting language; ask the parent whether to
     * fall back to English before sending the quiz.
     */
    data class ConfirmGreetingFallback(
        val ip: String,
        val command: InterruptionCommand,
        val content: StudyContent,
        val contentOverride: StudyContent?
    ) : StudyUiState()

    data class Error(val message: String) : StudyUiState()
}
