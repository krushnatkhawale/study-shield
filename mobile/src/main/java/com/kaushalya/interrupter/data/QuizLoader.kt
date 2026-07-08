package com.kaushalya.interrupter.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class QuizBundle(
    val quizzes: List<StudyContent>
)

class QuizLoader(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    fun loadQuizzes(): List<StudyContent> {
        val list = mutableListOf<StudyContent>()
        try {
            val files = context.assets.list("quizzes") ?: return list
            files.filter { it.endsWith(".json") }.forEach { filename ->
                try {
                    val text = context.assets.open("quizzes/$filename").bufferedReader().use { it.readText() }
                    val bundle = json.decodeFromString<QuizBundle>(text)
                    list.addAll(bundle.quizzes)
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        return list
    }
}
