package com.kaushalya.interrupter.network

import com.kaushalya.interrupter.data.*
import kotlinx.serialization.json.JsonObject
import retrofit2.Response
import retrofit2.http.*

interface ApiService {

    // Auth
    @POST("/api/auth/signup")
    suspend fun signUp(@Body request: SignUpRequest): Response<AuthResponse>

    @POST("/api/auth/signin")
    suspend fun signIn(@Body request: SignInRequest): Response<AuthResponse>

    @POST("/api/auth/guest")
    suspend fun guestAuth(@Body request: GuestAuthRequest): Response<AuthResponse>

    @POST("/api/auth/validate")
    suspend fun validateSession(): Response<ValidationResponse>

    @POST("/api/auth/signout")
    suspend fun signOut(@Body request: SignOutRequest = SignOutRequest()): Response<AuthResponse>

    // Guest data migration (transfers a guest account's data to a registered account)
    @POST("/api/migrate/guest-data")
    suspend fun claimGuestData(@Body request: ClaimGuestDataRequest): Response<ClaimGuestDataResponse>

    // Parents
    @GET("/api/parents")
    suspend fun listParents(): Response<List<ParentResponse>>

    @POST("/api/parents")
    suspend fun addParent(@Body request: ParentRequest): Response<ParentResponse>

    @PUT("/api/parents/me")
    suspend fun updateMyName(@Body request: ParentRequest): Response<ParentResponse>

    @DELETE("/api/parents/{id}")
    suspend fun deleteParent(@Path("id") id: String): Response<Unit>

    // Students
    @GET("/api/students")
    suspend fun getStudents(): Response<List<KidResponse>>

    @POST("/api/students")
    suspend fun addKid(@Body request: KidRequest): Response<KidResponse>

    @PUT("/api/students/{id}")
    suspend fun updateKid(
        @Path("id") id: String,
        @Body request: KidRequest
    ): Response<KidResponse>

    // Config
    @GET("/api/config/classes")
    suspend fun getClassConfig(): Response<JsonObject>

    // Quiz Results
    @POST("/api/quiz-results")
    suspend fun saveQuizResult(@Body request: QuizResultRequest): Response<QuizResultResponse>

    @GET("/api/quiz-results")
    suspend fun listQuizResults(): Response<List<QuizResultListItem>>

    // Class Grades (from content.class_grades)
    @GET("/api/v1/class-grades")
    suspend fun getClassGrades(): Response<List<ClassGradeDto>>

    // Quiz Bundles (server-issued quizzes)
    @POST("/api/v1/quiz-bundles")
    suspend fun issueQuizBundle(@Body request: QuizBundleRequestDto): Response<QuizBundleResponseDto>

    // Question Feedback (review)
    @PUT("/api/v1/questions/{id}/feedback")
    suspend fun submitQuestionFeedback(
        @Path("id") id: Long,
        @Body request: QuestionFeedbackRequest
    ): Response<QuestionFeedbackResponse>

    @GET("/api/v1/questions/{id}/feedback")
    suspend fun getQuestionFeedback(@Path("id") id: Long): Response<QuestionFeedbackResponse>

    // Question Bank Load (seed content on demand)
    @POST("/api/v1/questions/load")
    suspend fun loadQuestionBank(@Body items: List<QuestionBankLoadItem>): Response<QuestionBankLoadResponseDto>
}
