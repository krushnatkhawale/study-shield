package com.kaushalya.interrupter.data

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
    val mode: String = "online" // "online" or "offline"
)

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
    val mode: String = "online" // "online" or "offline"
)

@Serializable
data class QuizResultRequest(
    val childName: String,
    val score: Int,
    val totalQuestions: Int,
    val timeSpentSeconds: Long,
    val contentName: String? = null,
    val category: String? = null,
    val completedAt: Long
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
    val createdAt: String? = null
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
