package com.kaushalya.interrupter.data

import android.content.Context
import android.util.Log
import com.kaushalya.interrupter.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads (seeds) the Trial-class question bank to the backend on login.
 *
 * The backend starts empty; it loads its catalog lazily via POST /api/v1/questions/load.
 * This fire-and-forget call ensures the Trial class (mapped to Nursery) always has
 * usable content so quiz-bundle fetching succeeds even before the curated bank is
 * loaded by other means. The local Trial question set mirrors the backend's
 * Nursery band (Math / English / EVS / Hindi).
 */
object TrialContentDownloader {

    private const val TAG = "TrialContentDownloader"
    private const val BOARD = "ALL"
    private const val GRADE = "Trial"
    private var lastAttemptMs = 0L
    private const val RETRY_INTERVAL_MS = 30 * 60 * 1000L // 30 min

    /**
     * Fire-and-forget seed of the Trial question bank. Safe to call on every login;
     * throttles retries internally so it only hits the network once per interval.
     */
    suspend fun ensureTrialContent(context: Context) = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("trial_content", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (prefs.getBoolean("loaded", false)) {
            return@withContext
        }
        if (now - lastAttemptMs < RETRY_INTERVAL_MS) {
            return@withContext
        }
        lastAttemptMs = now
        try {
            val items = buildTrialBank()
            val response = RetrofitClient.getApiService().loadQuestionBank(items)
            if (response.isSuccessful) {
                val body = response.body()
                Log.d(TAG, "Trial bank seeded: questionsCreated=${body?.questionsCreated ?: 0}, skipped=${body?.questionsSkipped ?: 0}")
                prefs.edit().putBoolean("loaded", true).apply()
            } else {
                Log.w(TAG, "Trial bank seed failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Trial bank seed failed: ${e.message}")
        }
    }

    private fun buildTrialBank(): List<QuestionBankLoadItem> {
        val items = mutableListOf<QuestionBankLoadItem>()
        var order = 0

        fun mcq(subject: String, text: String, correct: String, others: List<String>) {
            items += QuestionBankLoadItem(
                boardCode = BOARD,
                className = GRADE,
                subject = subject,
                questionText = text,
                questionType = "SINGLE_CHOICE",
                correctAnswer = correct,
                options = listOf(correct) + others,
                orderIndex = order++
            )
        }

        fun tf(subject: String, text: String, answer: Boolean) = mcq(subject, text, if (answer) "True" else "False",
            if (answer) listOf("False") else listOf("True"))

        // Math
        mcq("Math", "How many fingers are on one hand?", "5", listOf("4", "6", "10"))
        mcq("Math", "What is 2 + 1?", "3", listOf("2", "4", "5"))
        mcq("Math", "Which shape has three sides?", "Triangle", listOf("Circle", "Square", "Star"))
        mcq("Math", "What is 1 + 2?", "3", listOf("2", "4", "5"))
        mcq("Math", "Which number comes after 3?", "4", listOf("2", "5", "3"))
        mcq("Math", "A ball looks like which shape?", "Circle", listOf("Square", "Triangle", "Rectangle"))
        mcq("Math", "Which is the smallest number?", "1", listOf("5", "9", "7"))
        mcq("Math", "What is 1 + 1?", "2", listOf("1", "3", "4"))
        mcq("Math", "How many ears do we have?", "2", listOf("1", "3", "4"))
        mcq("Math", "Count the dots: • • •. How many?", "3", listOf("2", "4", "5"))

        // EVS
        mcq("EVS", "We smell with our…", "Nose", listOf("Eyes", "Ears", "Hands"))
        mcq("EVS", "Which animal says 'meow'?", "Cat", listOf("Dog", "Cow", "Lion"))
        mcq("EVS", "We see with our…", "Eyes", listOf("Nose", "Ears", "Feet"))
        mcq("EVS", "Which one is a fruit?", "Mango", listOf("Carrot", "Potato", "Onion"))
        mcq("EVS", "How many legs does a bird have?", "2", listOf("4", "6", "8"))
        mcq("EVS", "Which body part helps us walk?", "Legs", listOf("Ears", "Eyes", "Nose"))
        mcq("EVS", "A fish lives in…", "Water", listOf("A tree", "The sky", "A nest"))
        tf("EVS", "We should drink water every day.", true)
        tf("EVS", "The sun rises at night.", false)

        // English
        mcq("English", "Which letter comes after A?", "B", listOf("C", "Z", "A"))
        mcq("English", "'Apple' starts with which letter?", "A", listOf("B", "M", "S"))
        mcq("English", "What is the opposite of big?", "Small", listOf("Tall", "Fat", "Long"))
        mcq("English", "'Bat' rhymes with…", "Cat", listOf("Cup", "Sun", "Dog"))
        mcq("English", "'Elephant' starts with which letter?", "E", listOf("F", "A", "L"))
        mcq("English", "What is the opposite of hot?", "Cold", listOf("Warm", "Wet", "Fast"))
        tf("English", "'Ball' starts with the letter B.", true)
        mcq("English", "One cat, two…", "Cats", listOf("Cat", "Cates", "Cati"))
        mcq("English", "'Sun' starts with which letter?", "S", listOf("F", "M", "B"))
        mcq("English", "Which word names an animal?", "Lion", listOf("Red", "Jump", "Hot"))

        // Hindi (English-text placeholders per content plan D3)
        mcq("Hindi", "Red light on traffic signals means…", "Stop", listOf("Go", "Run", "Dance"))
        mcq("Hindi", "Who teaches us in school?", "Teacher", listOf("Doctor", "Postman", "Cook"))
        mcq("Hindi", "What colour is grass?", "Green", listOf("Red", "Blue", "Black"))
        mcq("Hindi", "Which vehicle flies in the sky?", "Aeroplane", listOf("Bus", "Ship", "Car"))
        mcq("Hindi", "How many colours are in a rainbow?", "7", listOf("3", "5", "10"))
        mcq("Hindi", "What colour is a banana?", "Yellow", listOf("Blue", "Purple", "Black"))
        mcq("Hindi", "Which animal lives in water?", "Fish", listOf("Cow", "Hen", "Monkey"))
        mcq("Hindi", "Who brings us letters?", "Postman", listOf("Pilot", "Chef", "Tailor"))
        mcq("Hindi", "We wear shoes on our…", "Feet", listOf("Hands", "Head", "Ears"))
        tf("Hindi", "We should brush our teeth every morning.", true)

        return items
    }
}
