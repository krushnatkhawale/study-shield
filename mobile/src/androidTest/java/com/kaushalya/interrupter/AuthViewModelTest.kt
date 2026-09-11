package com.kaushalya.interrupter

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaushalya.interrupter.data.AuthResponse
import com.kaushalya.interrupter.data.ClaimGuestDataResponse
import com.kaushalya.interrupter.data.SessionManager
import com.kaushalya.interrupter.data.UnauthorizedException
import com.kaushalya.interrupter.data.ValidationResponse
import com.kaushalya.interrupter.ui.auth.AuthState
import com.kaushalya.interrupter.ui.auth.AuthViewModel
import com.kaushalya.interrupter.ui.auth.GuestSignUpState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class AuthViewModelTest {

    private lateinit var sessionManager: SessionManager
    private lateinit var ctx: android.content.Context
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        ctx.getSharedPreferences("auth_session", android.content.Context.MODE_PRIVATE).edit().clear().apply()
        sessionManager = SessionManager(ctx)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun checkExistingSession_returns_Success_when_session_stored() = runTest(testDispatcher) {
        sessionManager.sessionId = "stored-session"

        val vm = AuthViewModel(sessionManager, ctx, FakeAuthRepository(
            validateResult = Result.success(ValidationResponse(valid = true))
        ))
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Success)
        assertEquals("stored-session", (vm.authState.value as AuthState.Success).sessionId)
    }

    @Test
    fun checkExistingSession_returns_Success_when_valid_field_absent() = runTest(testDispatcher) {
        sessionManager.sessionId = "stored-session"

        val vm = AuthViewModel(sessionManager, ctx, FakeAuthRepository(
            validateResult = Result.success(ValidationResponse(valid = null))
        ))
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Success)
    }

    @Test
    fun checkExistingSession_clears_session_when_valid_explicitly_false() = runTest(testDispatcher) {
        sessionManager.sessionId = "stored-session"

        val vm = AuthViewModel(sessionManager, ctx, FakeAuthRepository(
            validateResult = Result.success(ValidationResponse(valid = false))
        ))
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Idle)
        assertNull(sessionManager.sessionId)
    }

    @Test
    fun checkExistingSession_returns_Success_on_ConnectException() = runTest(testDispatcher) {
        sessionManager.sessionId = "stored-session"

        val vm = AuthViewModel(sessionManager, ctx, FakeAuthRepository(
            validateResult = Result.failure(ConnectException("Connection refused"))
        ))
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Success)
        assertEquals("stored-session", sessionManager.sessionId)
    }

    @Test
    fun checkExistingSession_forcesRelogin_when_token_rejected() = runTest(testDispatcher) {
        sessionManager.sessionId = "expired-session"

        val vm = AuthViewModel(sessionManager, ctx, FakeAuthRepository(
            validateResult = Result.failure(UnauthorizedException("Session expired"))
        ))
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Idle)
        assertNull(sessionManager.sessionId)
    }

    @Test
    fun checkExistingSession_returns_Success_on_SocketTimeout() = runTest(testDispatcher) {
        sessionManager.sessionId = "stored-session"

        val vm = AuthViewModel(sessionManager, ctx, FakeAuthRepository(
            validateResult = Result.failure(SocketTimeoutException("timeout"))
        ))
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Success)
    }

    @Test
    fun checkExistingSession_returns_Success_on_UnknownHost() = runTest(testDispatcher) {
        sessionManager.sessionId = "stored-session"

        val vm = AuthViewModel(sessionManager, ctx, FakeAuthRepository(
            validateResult = Result.failure(UnknownHostException("no such host"))
        ))
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Success)
    }

    @Test
    fun checkExistingSession_returns_Success_on_non_network_error() = runTest(testDispatcher) {
        sessionManager.sessionId = "stored-session"

        val vm = AuthViewModel(sessionManager, ctx, FakeAuthRepository(
            validateResult = Result.failure(Exception("Some other error"))
        ))
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Success)
        assertEquals("stored-session", sessionManager.sessionId)
    }

    @Test
    fun checkExistingSession_skips_validation_for_guest() = runTest(testDispatcher) {
        sessionManager.sessionId = "guest"
        sessionManager.isGuest = true

        val vm = AuthViewModel(sessionManager, ctx, FakeAuthRepository(
            validateResult = Result.failure(Exception("should not be called"))
        ))
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Success)
        assertEquals("guest", (vm.authState.value as AuthState.Success).sessionId)
    }

    @Test
    fun checkExistingSession_shows_welcome_when_no_session() = runTest(testDispatcher) {
        sessionManager.clear()

        val vm = AuthViewModel(sessionManager, ctx)
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Idle)
    }

    @Test
    fun guestLogout_clears_guest_session_but_keeps_carousel_and_local_data() = runTest(testDispatcher) {
        sessionManager.sessionId = "guest-session"
        sessionManager.isGuest = true
        sessionManager.hasSeenCarousel = true

        val vm = AuthViewModel(sessionManager, ctx)
        vm.guestLogout()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(sessionManager.sessionId)
        assertFalse(sessionManager.isGuest)
        assertTrue(sessionManager.hasSeenCarousel)
        assertTrue(vm.authState.value is AuthState.Idle)
    }

    @Test
    fun signUpFromGuest_migrates_guest_data_and_switches_to_new_account() = runTest(testDispatcher) {
        sessionManager.sessionId = "guest-session"
        sessionManager.isGuest = true

        val vm = AuthViewModel(sessionManager, ctx, FakeAuthRepository(
            signUpResult = Result.success(
                AuthResponse(
                    sessionId = "new-account-session",
                    loginId = "newuser@test.test",
                    accountId = "acc-42",
                    parentName = "New User"
                )
            ),
            claimResult = Result.success(
                ClaimGuestDataResponse(success = true, resultsMoved = 3)
            )
        ))
        vm.signUpFromGuest("newuser@test.test", "password123", "New User")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.guestSignUpState.value
        assertTrue("expected Success but was $state", state is GuestSignUpState.Success)
        assertEquals(true, (state as GuestSignUpState.Success).migrated)

        // Session now owns the new account and guest mode is off.
        assertEquals("new-account-session", sessionManager.sessionId)
        assertEquals("acc-42", sessionManager.accountId)
        assertFalse(sessionManager.isGuest)
        assertTrue(vm.authState.value is AuthState.Success)
    }

    @Test
    fun signUpFromGuest_still_completes_when_migration_fails() = runTest(testDispatcher) {
        sessionManager.sessionId = "guest-session"
        sessionManager.isGuest = true

        val vm = AuthViewModel(sessionManager, ctx, FakeAuthRepository(
            signUpResult = Result.success(
                AuthResponse(
                    sessionId = "new-account-session",
                    loginId = "newuser@test.test",
                    accountId = "acc-42"
                )
            ),
            claimResult = Result.failure(Exception("backend down"))
        ))
        vm.signUpFromGuest("newuser@test.test", "password123", "New User")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = vm.guestSignUpState.value
        assertTrue("expected Success but was $state", state is GuestSignUpState.Success)
        assertEquals(false, (state as GuestSignUpState.Success).migrated)
        assertEquals("new-account-session", sessionManager.sessionId)
        assertFalse(sessionManager.isGuest)
    }

    @Test
    fun signUpFromGuest_surfaces_error_when_sign_up_fails() = runTest(testDispatcher) {
        sessionManager.sessionId = "guest-session"
        sessionManager.isGuest = true

        val vm = AuthViewModel(sessionManager, ctx, FakeAuthRepository(
            signUpResult = Result.failure(Exception("Email already registered"))
        ))
        vm.signUpFromGuest("taken@test.test", "password123", "Parent")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.guestSignUpState.value is GuestSignUpState.Error)
        // The guest session is untouched when sign-up fails.
        assertTrue(sessionManager.isGuest)
        assertEquals("guest-session", sessionManager.sessionId)
    }

    @Test
    fun handleAuthResponse_saves_session_to_prefs_across_instances() = runTest(testDispatcher) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        sessionManager.sessionId = null

        val vm = AuthViewModel(sessionManager, ctx)
        vm.handleAuthResponse(
            com.kaushalya.interrupter.data.AuthResponse(
                sessionId = "session-from-signin",
                loginId = "testuser",
                parentName = "Parent",
                accountId = "acc-1"
            )
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Success)
        assertEquals("session-from-signin", sessionManager.sessionId)

        val sm2 = SessionManager(ctx)
        assertEquals("session-from-signin", sm2.sessionId)
        assertEquals("testuser", sm2.loginId)
        assertEquals("Parent", sm2.parentName)
    }
}
