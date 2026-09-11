package com.kaushalya.interrupter.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class QuizQuestion(
    val question: String,
    val options: List<String>,
    val answer: String,
    val id: Long? = null
)

/** Shuffle option order so the correct answer is not always the first option. */
fun QuizQuestion.shuffledOptions(): QuizQuestion {
    if (options.size < 2) return this
    if (answer !in options) return this
    val shuffled = options.shuffled()
    return if (shuffled == options) this else copy(options = shuffled)
}

@Serializable
data class InterruptionCommand(
    val type: String,
    val message: String? = null,
    val duration: Long? = null,
    val contentName: String? = null,
    val category: String? = null,
    val questions: List<QuizQuestion>? = null,
    val question: String? = null,
    val options: List<String>? = null,
    val answer: String? = null,
    val mobileIp: String? = null,
    val resultCallbackPort: Int? = null,
    // Per-kid quiz presentation configuration (Features 4/5 + configurable threshold).
    val revealReadLock: Boolean? = null,
    val autoDictation: Boolean? = null,
    val fastAnswerThresholdMs: Long? = null,
    // The kid who is taking this quiz, so the TV can show + echo it back with the result
    // (avoids relying on a potentially stale mobile-side kid selection after account switches).
    val kidName: String? = null,
    // Parent-selected locale tag for the TV's post-quiz greeting message + TTS (default: English).
    // Mirrors KidQuizConfig.greetingLanguage.
    val greetingLanguage: String? = null,
    // Which mascot avatar the TV completion screen should celebrate with (Avatars.IDS).
    val avatarId: String? = null
)

/** Per-kid quiz presentation configuration, persisted per kid and pushed to the TV on session start. */
@Serializable
data class KidQuizConfig(
    val revealReadLock: Boolean = false,
    val autoDictation: Boolean = false,
    val fastAnswerThresholdMs: Long = DEFAULT_FAST_ANSWER_THRESHOLD_MS,
    val greetingLanguage: String = DEFAULT_GREETING_LANGUAGE
) {
    companion object {
        const val DEFAULT_FAST_ANSWER_THRESHOLD_MS: Long = 1500L
        const val MIN_FAST_ANSWER_THRESHOLD_MS: Long = 0L
        const val MAX_FAST_ANSWER_THRESHOLD_MS: Long = 10000L

        /** Locale tag for the TV's post-quiz greeting; English by default. */
        const val DEFAULT_GREETING_LANGUAGE: String = "en"
    }
}

/** Greeting-message languages offered to parents for the TV's post-quiz greeting. */
object GreetingLanguages {
    val options: List<Pair<String, String>> = listOf(
        "en" to "English",
        "mr-IN" to "मराठी (Marathi)",
        "hi-IN" to "हिन्दी (Hindi)"
    )

    fun labelOf(tag: String): String = options.firstOrNull { it.first == tag }?.second ?: "English"
}

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

/** Reply to the TV's `TTS_CAP_CHECK` probe: the TV's speakable TTS locale tags. */
@Serializable
data class TtsCapabilitiesMessage(
    val supportedLanguages: List<String> = emptyList()
)

@Serializable
@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val durationMinutes: Int,
    val startTime: Long, // timestamp
    val recurrence: Recurrence = Recurrence.ONE_TIME,
    val content: StudyContent,
    val isActive: Boolean = true,
    val kidId: String? = null,
    val kidName: String? = null
)

enum class Recurrence {
    ONE_TIME, DAILY, WEEKLY
}

@Serializable
data class StudyContent(
    val type: ContentType,
    val id: String? = null,
    val name: String,
    val category: String? = null,
    val question: String? = null,
    val options: List<String>? = null,
    val answer: String? = null,
    val questions: List<QuizQuestion>? = null,
    val grade: String? = null
)

@Serializable
enum class ContentType {
    QUIZ, SUBJECT, RANDOM
}

// --- Kid Profile Models ---

@Serializable
@Entity(tableName = "kid_profiles")
data class KidProfile(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val gender: String, // "Boy", "Girl", "Other"
    val birthYear: Int,
    val dateOfBirth: Long? = null, // Optional timestamp
    val grade: String, // "class"
    val syllabus: String? = null,
    val lastModified: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0, // 0: Local, 1: Synced, 2: Modified
    val remoteId: String? = null,
    val mode: String = "online", // "online" or "offline"
    // Mascot avatar id shown on the TV completion screen after this kid's quiz.
    // See Avatars.IDS for the shared set (defaults to "hero").
    // @ColumnInfo defaultValue must match MIGRATION_12_13's ALTER ... DEFAULT 'hero'.
    @ColumnInfo(defaultValue = "'hero'")
    val avatar: String = "hero"
)

/**
 * The selectable TV completion-mascot avatars. These are friendly, inspired-by original
 * interpretations of well-known character archetypes (not brand reproductions), keyed by a
 * stable id that the TV-side LiveMascot uses to pick colours/features.
 */
object Avatars {
    data class Avatar(val id: String, val label: String)

    val ALL: List<Avatar> = listOf(
        Avatar("hero", "Hero Kid"),
        Avatar("warrior", "Brave Warrior"),
        Avatar("pig", "Pink Piggy"),
        Avatar("panda", "Panda"),
        Avatar("bear", "Teddy Bear"),
        Avatar("bunny", "Bunny"),
        Avatar("tiger", "Tiger Cub"),
        Avatar("monkey", "Mischievous Monkey"),
        Avatar("cat", "Cute Kitty"),
        Avatar("fox", "Clever Fox"),
        Avatar("dino", "Little Dino"),
        Avatar("robot", "Friendly Robot")
    )

    fun labelOf(id: String?): String = ALL.firstOrNull { it.id == id }?.label ?: ALL.first().label

    fun isValid(id: String?): Boolean = ALL.any { it.id == id }
}

// --- Smart TV Connection History Models ---

@Serializable
@Entity(
    tableName = "wifi_networks",
    indices = [Index(value = ["ssid"], unique = true)]
)
data class WifiNetwork(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val ssid: String,
    val bssid: String? = null,
    val lastConnected: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0, // 0: Local, 1: Synced, 2: Modified
    val remoteId: String? = null
)

@Serializable
@Entity(
    tableName = "connected_tvs",
    foreignKeys = [
        ForeignKey(
            entity = WifiNetwork::class,
            parentColumns = ["id"],
            childColumns = ["networkId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["networkId"])]
)
data class ConnectedTV(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val networkId: String,
    val name: String,
    val ipAddress: String,
    val macAddress: String? = null,
    val deviceInfo: String? = null,
    val lastConnected: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val syncStatus: Int = 0,
    val remoteId: String? = null
)

// --- API DTOs ---

@Serializable
data class SignUpRequest(
    val loginId: String,
    val password: String,
    val name: String? = null
)

@Serializable
data class SignInRequest(
    val loginId: String,
    val password: String,
    val parentId: String? = null
)

@Serializable
data class SignOutRequest(
    val sessionId: String? = null
)

@Serializable
data class GuestAuthRequest(
    val deviceId: String? = null
)

@Serializable
data class AuthResponse(
    val accountId: String? = null,
    val loginId: String? = null,
    val sessionId: String? = null,
    val parentId: String? = null,
    val parentName: String? = null,
    val requiresParentSelection: Boolean? = null,
    val parents: List<ParentSummary>? = null,
    val message: String? = null,
    val errorCode: String? = null,
    val timestamp: Long? = null
)

@Serializable
data class ParentSummary(
    val parentId: String,
    val parentName: String
)

@Serializable
data class ValidationResponse(
    val accountId: String? = null,
    val loginId: String? = null,
    val parentId: String? = null,
    val parentName: String? = null,
    val message: String? = null,
    val errorCode: String? = null,
    val timestamp: Long? = null,
    val valid: Boolean? = null
)

@Serializable
data class ParentResponse(
    val parentId: String,
    val name: String
)

@Serializable
data class ParentRequest(
    val name: String,
    val gender: String? = null,
    val relation: String? = null,
    val type: String? = "ACCOUNT_HOLDER"
)

@Serializable
data class KidRequest(
    val name: String,
    val gender: String? = null,
    val birthYear: Int? = null,
    val studentClass: String? = null
)

@Serializable
data class KidResponse(
    val studentId: String? = null,
    val accountId: String? = null,
    val name: String? = null,
    val gender: String? = null,
    val birthYear: Int? = null,
    val studentClass: String? = null
)

// --- Quiz Result Models ---

@Serializable
@Entity(tableName = "quiz_results")
data class QuizResult(
    @PrimaryKey
    val id: String = java.util.UUID.randomUUID().toString(),
    val childName: String,
    val score: Int,
    val totalQuestions: Int,
    val timeSpentSeconds: Long,
    val contentName: String? = null,
    val category: String? = null,
    val completedAt: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0, // 0: Local, 1: Synced, 2: SyncFailed
    val backendId: Long? = null, // Backend-assigned ID for dedup on fetch
    val mode: String = "online", // "online" or "offline"
    val fastAnswerCount: Int = 0 // Answers given suspiciously fast (parent-side signal only)
)

@Serializable
data class QuizResultRequest(
    val childName: String,
    val score: Int,
    val totalQuestions: Int,
    val timeSpentSeconds: Long,
    val contentName: String? = null,
    val category: String? = null,
    val completedAt: Long,
    val fastAnswerCount: Int = 0
)

@Serializable
data class QuizResultResponse(
    val resultId: String? = null,
    val message: String? = null,
    val errorCode: String? = null
)

@Serializable
data class QuizResultListItem(
    val id: Long? = null,
    val childName: String? = null,
    val score: Int? = null,
    val totalQuestions: Int? = null,
    val timeSpentSeconds: Long? = null,
    val contentName: String? = null,
    val category: String? = null,
    val completedAt: String? = null,
    val createdAt: String? = null,
    val fastAnswerCount: Int? = null
)

@Serializable
data class ClassGradeDto(
    val id: Long? = null,
    val name: String? = null,
    val description: String? = null,
    val boardId: Long? = null,
    val boardName: String? = null
)

// --- Application Profile ---

@Serializable
data class ProfileData(
    val account: String? = null,
    val parents: List<ProfileParent> = emptyList(),
    val kids: List<ProfileKid> = emptyList(),
    val tvHistory: List<ProfileTv> = emptyList(),
    val defaultParentId: String? = null,
    val defaultParentName: String? = null
)

@Serializable
data class ProfileParent(
    val parentId: String,
    val parentName: String,
    val gender: String? = null,
    val relation: String? = null
)

@Serializable
data class ProfileKid(
    val id: String,
    val name: String,
    val gender: String? = null
)

@Serializable
data class ProfileTv(
    val id: String,
    val name: String,
    val ipAddress: String
)

class Converters {
    @TypeConverter
    fun fromContent(content: StudyContent): String = Json.encodeToString(content)
    @TypeConverter
    fun toContent(content: String): StudyContent = Json.decodeFromString(content)

    @TypeConverter
    fun fromRecurrence(recurrence: Recurrence): String = recurrence.name
    @TypeConverter
    fun toRecurrence(recurrence: String): Recurrence = Recurrence.valueOf(recurrence)
}

// --- Quiz Bundle (server-issued quizzes) ---

@Serializable
data class QuizBundleRequestDto(
    val className: String,
    val boardCode: String? = null,
    val language: String? = null,
    val childId: Long? = null,
    val deviceId: String? = null,
    val userId: Long? = null,
    val allowPartial: Boolean = true
)

@Serializable
data class QuizBundleOptionDto(
    val id: String? = null,
    val text: String? = null,
    val imageUrl: String? = null
)

@Serializable
data class QuizBundleQuestionDto(
    val id: Long? = null,
    val resourceId: String? = null,
    val questionText: String? = null,
    val questionType: String? = null,
    val options: List<QuizBundleOptionDto> = emptyList(),
    val correctAnswers: List<String> = emptyList(),
    val explanation: String? = null
)

@Serializable
data class QuizBundleQuizDto(
    val id: Long? = null,
    val title: String? = null,
    val description: String? = null,
    val contentPackName: String? = null,
    val quizType: String? = null,
    val contentTier: String? = null,
    val questions: List<QuizBundleQuestionDto> = emptyList()
)

@Serializable
data class QuizBundleResponseDto(
    val id: Long? = null,
    val className: String? = null,
    val subjects: List<String> = emptyList(),
    val quizCount: Int? = null,
    val quizzes: List<QuizBundleQuizDto> = emptyList()
)

// --- Question feedback (review) ---

@Serializable
data class QuestionFeedbackRequest(
    val vote: String,
    val downCategory: String? = null,
    val comment: String? = null,
    val report: Boolean = false
)

@Serializable
data class QuestionFeedbackResponse(
    val id: Long? = null,
    val questionId: Long? = null,
    val vote: String = "NONE",
    val downCategory: String? = null,
    val reported: Boolean = false,
    val comment: String? = null
)

// --- Pending question feedback (offline queue) ---
// Opaque server question id (backend ID, not the local question resource id). Kept as a
// plain Long; there is no FK to a local quiz table because feedback is keyed by the remote id.

@Entity(tableName = "pending_feedback")
data class PendingFeedback(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val questionId: Long,
    val vote: String,
    val downCategory: String? = null,
    val comment: String? = null,
    val report: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

// --- Question bank load (POST /api/v1/questions/load) ---
// Each item carries board/class/subject metadata so the backend can auto-create
// the full Board -> ClassGrade -> Subject -> ContentPack -> Quiz -> Question chain.

@Serializable
data class QuestionBankLoadItem(
    val boardCode: String,
    val className: String,
    val age: Int? = null,
    val subject: String,
    val questionText: String,
    val questionType: String? = null, // SINGLE_CHOICE / MULTIPLE_CHOICE / TRUE_FALSE / FITB; derived by backend if absent
    val correctAnswer: String,
    val options: List<String> = emptyList(),
    val orderIndex: Int? = null
)

@Serializable
data class QuestionBankLoadResponseDto(
    val boardsCreated: Int = 0,
    val classGradesCreated: Int = 0,
    val subjectsCreated: Int = 0,
    val contentPacksCreated: Int = 0,
    val quizzesCreated: Int = 0,
    val questionsCreated: Int = 0,
    val questionsSkipped: Int = 0
)
