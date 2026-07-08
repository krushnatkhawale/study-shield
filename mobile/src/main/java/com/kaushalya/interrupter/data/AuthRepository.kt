package com.kaushalya.interrupter.data

import com.kaushalya.interrupter.network.RetrofitClient

open class AuthRepository {

    private val api get() = RetrofitClient.getApiService()

    suspend fun signUp(loginId: String, password: String, name: String? = null): Result<AuthResponse> {
        return try {
            val response = api.signUp(SignUpRequest(loginId, password, name))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Sign up failed: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signIn(loginId: String, password: String, parentId: String? = null): Result<AuthResponse> {
        return try {
            val response = api.signIn(SignInRequest(loginId, password, parentId))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Sign in failed: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun validateSession(): Result<ValidationResponse> {
        return try {
            val response = api.validateSession()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Session invalid"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun signOut(): Result<AuthResponse> {
        return try {
            val response = api.signOut()
            if (response.isSuccessful) {
                Result.success(response.body() ?: AuthResponse())
            } else {
                Result.failure(Exception("Sign out failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
