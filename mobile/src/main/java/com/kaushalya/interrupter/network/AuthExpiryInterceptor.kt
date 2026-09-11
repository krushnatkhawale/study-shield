package com.kaushalya.interrupter.network

import com.kaushalya.interrupter.data.SessionManager
import com.kaushalya.interrupter.data.ToastHelper
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Detects when the backend rejects our stored session token (401 from the
 * authentication entry point, or the historically used 403) on an authenticated
 * call and forces a re-login instead of silently using the stale token forever.
 *
 * Runs inside [RetrofitClient] after [AuthInterceptor], so the request it sees
 * already carries the candidate bearer token. Skips the login endpoints — a 401
 * there means bad credentials, not an expired session.
 */
class AuthExpiryInterceptor(
    private val sessionManager: SessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val hasBearerToken = request.header("Authorization")?.startsWith("Bearer ") == true
        val isLoginRequest = request.url.toString().contains("/api/auth/signin")
            || request.url.toString().contains("/api/auth/signup")

        if (shouldForceRelogin(
                hasBearerToken = hasBearerToken,
                isGuest = sessionManager.isGuest,
                isLoginRequest = isLoginRequest,
                code = response.code,
                url = request.url.toString()
            )
            && sessionManager.sessionId != null
        ) {
            android.util.Log.w(TAG, "Auth response ${response.code}; clearing expired session")
            ToastHelper.show("Session expired. Please sign in again.")
            sessionManager.sessionId = null
            AuthEvents.notifySessionExpired()
        }

        return response
    }

    companion object {
        private const val TAG = "AuthExpiryInterceptor"
    }
}

internal fun shouldForceRelogin(
    hasBearerToken: Boolean,
    isGuest: Boolean,
    isLoginRequest: Boolean,
    code: Int,
    url: String
): Boolean = !isGuest && hasBearerToken && !isLoginRequest && !url.contains("/api/auth/validate") && (code == 401 || code == 403)
