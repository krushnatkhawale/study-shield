package com.kaushalya.interrupter.data

import android.content.Context
import android.util.Log
import com.kaushalya.interrupter.network.RetrofitClient
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class QuizBundle(
    val quizzes: List<StudyContent>
)

object GradeQuizMapping {
    private val gradeToFiles = mapOf(
        "Exp" to listOf("exp_promo.json"),
        "Nursery" to listOf("nursery.json"),
        "Junior KG" to listOf("junior_kg.json"),
        "Senior KG" to listOf("senior_kg.json"),
        "1" to listOf("class_1.json"),
        "2" to listOf("class_2.json"),
        "3" to listOf("class_3.json"),
        "4" to listOf("math_grade_4.json", "general_knowledge.json", "science_fun_facts.json"),
    )

    fun quizFilesForGrade(grade: String): List<String> {
        return gradeToFiles[grade] ?: emptyList()
    }
}

class QuizLoader(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Quizzes for a grade, server-first: issues a quiz bundle from the backend so content
     * can be updated per country/rollout without an app release. Falls back to the bundled
     * asset JSON when the backend is unreachable or returns nothing usable.
     */
    suspend fun loadQuizzesForGradeRemoteFirst(grade: String): List<StudyContent> {
        val remote = try {
            fetchQuizzesForGrade(grade)
        } catch (e: Exception) {
            Log.w("QuizLoader", "Remote quiz bundle unavailable, using bundled assets: ${e.message}")
            emptyList()
        }
        return remote.ifEmpty { loadQuizzesForGrade(grade) }
    }

    private suspend fun fetchQuizzesForGrade(grade: String): List<StudyContent> {
        val response = RetrofitClient.getApiService()
            .issueQuizBundle(QuizBundleRequestDto(className = grade, deviceId = deviceId()))
        if (!response.isSuccessful) return emptyList()
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
                QuizQuestion(question = text, options = optionTexts, answer = answer)
            }
            if (questions.isEmpty()) return@mapNotNull null
            StudyContent(
                type = ContentType.QUIZ,
                id = quiz.id?.toString(),
                name = quiz.title ?: "Quiz",
                category = bundle.subjects.firstOrNull(),
                questions = questions,
                grade = grade
            )
        }
        Log.d("QuizLoader", "Loaded ${mapped.size} quizzes from backend for grade $grade")
        return mapped
    }

    private fun deviceId(): String {
        val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)
        return prefs.getString("device_id", null) ?: java.util.UUID.randomUUID().toString().also {
            prefs.edit().putString("device_id", it).apply()
        }
    }

    fun loadQuizzes(): List<StudyContent> {
        val list = mutableListOf<StudyContent>()
        try {
            val files = context.assets.list("quizzes") ?: return list
            files.filter { it.endsWith(".json") }.forEach { filename ->
                list.addAll(loadQuizFile(filename))
            }
        } catch (e: Exception) {
            Log.e("QuizLoader", "Failed to load quizzes", e)
        }
        return list
    }

    fun loadQuizzesForGrade(grade: String): List<StudyContent> {
        val files = GradeQuizMapping.quizFilesForGrade(grade)
        return files.flatMap { fileName -> loadQuizFile(fileName) }
    }

    private fun loadQuizFile(fileName: String): List<StudyContent> {
        return try {
            val text = context.assets.open("quizzes/$fileName").bufferedReader().use { it.readText() }
            val element = json.parseToJsonElement(text)

            when {
                element is JsonArray -> {
                    element.jsonArray.mapNotNull { parseRichQuestion(it) }
                }
                element is JsonObject && element.containsKey("quizzes") -> {
                    val bundle = json.decodeFromString<QuizBundle>(text)
                    bundle.quizzes
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e("QuizLoader", "Failed to load $fileName: ${e.message}")
            emptyList()
        }
    }

    private fun parseRichQuestion(element: kotlinx.serialization.json.JsonElement): StudyContent? {
        val obj = element as? JsonObject ?: return null
        val questionText = obj["questionText"]?.jsonPrimitive?.content ?: return null
        data class RawOption(val id: String?, val text: String)
        val rawOptions = obj["options"]?.jsonArray?.mapNotNull {
            val o = it.jsonObject
            val text = o["text"]?.jsonPrimitive?.content ?: return@mapNotNull null
            RawOption(o["id"]?.jsonPrimitive?.content, text)
        } ?: emptyList()
        val options = rawOptions.map { it.text }
        val correctAnswers = obj["correctAnswers"]?.jsonArray?.map {
            it.jsonPrimitive.content
        } ?: emptyList()
        // Answer must be the option TEXT (what the TV compares), not the option id
        val correctId = correctAnswers.firstOrNull()
        val answer = rawOptions.firstOrNull { it.id == correctId }?.text ?: correctId ?: ""
        val subjects = obj["subjects"]?.jsonArray?.map {
            it.jsonPrimitive.content
        } ?: emptyList()
        val classes = obj["classes"]?.jsonArray?.map {
            it.jsonPrimitive.content
        } ?: emptyList()
        val resourceId = obj["resourceId"]?.jsonPrimitive?.content

        return StudyContent(
            type = ContentType.QUIZ,
            id = resourceId,
            name = questionText,
            category = subjects.firstOrNull(),
            questions = listOf(
                QuizQuestion(
                    question = questionText,
                    options = options,
                    answer = answer
                )
            ),
            grade = classes.firstOrNull()
        )
    }
}
