package com.kaushalya.interrupter

import kotlinx.serialization.Serializable

@Serializable
data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val answer: String
)

@Serializable
data class InterruptionCommand(
    val type: String,
    val message: String? = null,
    val duration: Long? = null,
    val contentName: String? = null,
    val category: String? = null,
    val questions: List<QuizQuestion>? = null,
    // Keep old fields for direct manual commands from older app versions or simple triggers
    val question: String? = null,
    val options: List<String>? = null,
    val answer: String? = null,
    val mobileIp: String? = null,
    val resultCallbackPort: Int? = null,
    // Per-kid quiz presentation configuration (Features 4/5 + configurable threshold).
    val revealReadLock: Boolean? = null,
    val autoDictation: Boolean? = null,
    val fastAnswerThresholdMs: Long? = null,
    // The kid this quiz is for; echoed back to the mobile with the result.
    val kidName: String? = null,
    // Parent-selected locale tag for the post-quiz greeting message + TTS (default: English).
    val greetingLanguage: String? = null,
    // Mascot avatar id for the completion screen (defaults to "hero").
    val avatarId: String? = null
)

@Serializable
data class QuizResultMessage(
    val score: Int,
    val totalQuestions: Int,
    val contentName: String? = null,
    val category: String? = null,
    val timeSpentSeconds: Long = 0,
    val completedAt: Long = System.currentTimeMillis(),
    val fastAnswerCount: Int = 0,
    // Echo back which kid the quiz was for so the mobile stores it under the right profile.
    val kidName: String? = null
)

/** Reply to a `TTS_CAP_CHECK` probe: the engine's speakable locale tags on this TV. */
@Serializable
data class TtsCapabilitiesMessage(
    val supportedLanguages: List<String> = emptyList()
)
