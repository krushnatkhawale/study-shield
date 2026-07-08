package com.kaushalya.interrupter.network

import com.kaushalya.interrupter.data.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionManager: SessionManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val sessionId = sessionManager.sessionId
        val request = if (sessionId != null) {
            chain.request().newBuilder()
                .addHeader("Authorization", "Bearer $sessionId")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
