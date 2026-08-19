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
    val resultCallbackPort: Int? = null
)

@Serializable
data class QuizResultMessage(
    val score: Int,
    val totalQuestions: Int,
    val contentName: String? = null,
    val category: String? = null,
    val timeSpentSeconds: Long = 0,
    val completedAt: Long = System.currentTimeMillis()
)
