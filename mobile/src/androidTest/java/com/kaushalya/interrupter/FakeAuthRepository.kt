package com.kaushalya.interrupter

import com.kaushalya.interrupter.data.AuthRepository
import com.kaushalya.interrupter.data.AuthResponse
import com.kaushalya.interrupter.data.ValidationResponse

class FakeAuthRepository(
    private val validateResult: Result<ValidationResponse> = Result.success(ValidationResponse(valid = true))
) : AuthRepository() {

    override suspend fun validateSession(): Result<ValidationResponse> = validateResult
}
