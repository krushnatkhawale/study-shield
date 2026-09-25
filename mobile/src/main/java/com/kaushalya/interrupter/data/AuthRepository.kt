package com.kaushalya.interrupter.data

import com.kaushalya.interrupter.network.RetrofitClient
import kotlinx.coroutines.withTimeout

open class AuthRepository {

    companion object {
        const val VALIDATE_TIMEOUT_MS = 4_000L
    }

    private val api get() = RetrofitClient.getApiService()

    open suspend fun signUp(loginId: String, password: String, name: String? = null): Result<AuthResponse> {
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

    /** Requests a backend-issued anonymous session tied to this installation's deviceId. */
    suspend fun guestLogin(deviceId: String): Result<AuthResponse> {
        return try {
            val response = api.guestAuth(GuestAuthRequest(deviceId))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Guest login failed: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Moves the guest account's data (quiz results, kids, attempts) tied to this
     * deviceId over to the currently-signed-in account. Requires the calling
     * session to hold a real JWT for the target account.
     */
    open suspend fun claimGuestData(deviceId: String): Result<ClaimGuestDataResponse> {
        return try {
            val response = api.claimGuestData(ClaimGuestDataRequest(deviceId))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorBody = response.errorBody()?.string() ?: "Unknown error"
                Result.failure(Exception("Guest data migration failed: $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    open suspend fun validateSession(): Result<ValidationResponse> {
        return try {
            // Session validation must never block startup — fall back to offline
            // mode quickly if the backend is slow or unreachable.
            val response = withTimeout(VALIDATE_TIMEOUT_MS) {
                api.validateSession()
            }
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else if (response.code() == 401 || response.code() == 403) {
                Result.failure(UnauthorizedException("Session expired"))
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
