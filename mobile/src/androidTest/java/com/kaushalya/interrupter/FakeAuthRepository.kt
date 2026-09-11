package com.kaushalya.interrupter

import com.kaushalya.interrupter.data.AuthRepository
import com.kaushalya.interrupter.data.AuthResponse
import com.kaushalya.interrupter.data.ClaimGuestDataResponse
import com.kaushalya.interrupter.data.ValidationResponse

class FakeAuthRepository(
    private val validateResult: Result<ValidationResponse> = Result.success(ValidationResponse(valid = true)),
    private val signUpResult: Result<AuthResponse> = Result.success(
        AuthResponse(
            sessionId = "new-account-session",
            loginId = "newuser@test.test",
            accountId = "acc-42",
            parentName = "New User"
        )
    ),
    private val claimResult: Result<ClaimGuestDataResponse> = Result.success(
        ClaimGuestDataResponse(success = true, resultsMoved = 2)
    )
) : AuthRepository() {

    override suspend fun validateSession(): Result<ValidationResponse> = validateResult

    override suspend fun signUp(
        loginId: String,
        password: String,
        name: String?
    ): Result<AuthResponse> = signUpResult

    override suspend fun claimGuestData(deviceId: String): Result<ClaimGuestDataResponse> = claimResult
}