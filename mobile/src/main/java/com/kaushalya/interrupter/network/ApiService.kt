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

    @POST("/api/auth/validate")
    suspend fun validateSession(): Response<ValidationResponse>

    @POST("/api/auth/signout")
    suspend fun signOut(@Body request: SignOutRequest = SignOutRequest()): Response<AuthResponse>

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
}
