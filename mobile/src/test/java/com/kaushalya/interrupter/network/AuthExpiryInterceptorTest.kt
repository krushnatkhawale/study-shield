package com.kaushalya.interrupter.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Decision rules for when an API response means "your session is stale, sign in again".
 *
 * A user must be re-logged in when the backend rejects the token on an authenticated
 * call. We must NOT force a re-login when the user is browsing as a guest, when no
 * token was attached, or when the 401 came from a sign-in attempt (bad credentials
 * is a normal login error, not an expired session).
 */
class AuthExpiryInterceptorTest {

    @Test
    fun rejectedTokenOnAuthenticatedCall_forcesRelogin() {
        assertTrue(
            shouldForceRelogin(
                hasBearerToken = true,
                isGuest = false,
                isLoginRequest = false,
                code = 401
            )
        )
    }

    @Test
    fun legacyRejectionReturns403_forcesReloginToo() {
        assertTrue(
            shouldForceRelogin(
                hasBearerToken = true,
                isGuest = false,
                isLoginRequest = false,
                code = 403
            )
        )
    }

    @Test
    fun badCredentialsDuringSignIn_doesNotTriggerRelogin() {
        assertFalse(
            shouldForceRelogin(
                hasBearerToken = true,
                isGuest = false,
                isLoginRequest = true,
                code = 401
            )
        )
    }

    @Test
    fun guestSession_isNeverForcedToRelogin() {
        assertFalse(
            shouldForceRelogin(
                hasBearerToken = true,
                isGuest = true,
                isLoginRequest = false,
                code = 401
            )
        )
    }

    @Test
    fun missingToken_isNotAHostileRejection() {
        assertFalse(
            shouldForceRelogin(
                hasBearerToken = false,
                isGuest = false,
                isLoginRequest = false,
                code = 401
            )
        )
    }

    @Test
    fun serverError_isNotAnExpiredSession() {
        assertFalse(
            shouldForceRelogin(
                hasBearerToken = true,
                isGuest = false,
                isLoginRequest = false,
                code = 500
            )
        )
    }
}