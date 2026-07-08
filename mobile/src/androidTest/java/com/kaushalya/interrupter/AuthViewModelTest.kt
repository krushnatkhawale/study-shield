package com.kaushalya.interrupter

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kaushalya.interrupter.data.SessionManager
import com.kaushalya.interrupter.data.ValidationResponse
import com.kaushalya.interrupter.ui.auth.AuthState
import com.kaushalya.interrupter.ui.auth.AuthViewModel
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
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
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

        val vm = AuthViewModel(sessionManager, FakeAuthRepository(
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

        val vm = AuthViewModel(sessionManager, FakeAuthRepository(
            validateResult = Result.success(ValidationResponse(valid = null))
        ))
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Success)
    }

    @Test
    fun checkExistingSession_clears_session_when_valid_explicitly_false() = runTest(testDispatcher) {
        sessionManager.sessionId = "stored-session"

        val vm = AuthViewModel(sessionManager, FakeAuthRepository(
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

        val vm = AuthViewModel(sessionManager, FakeAuthRepository(
            validateResult = Result.failure(ConnectException("Connection refused"))
        ))
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Success)
        assertEquals("stored-session", sessionManager.sessionId)
    }

    @Test
    fun checkExistingSession_returns_Success_on_SocketTimeout() = runTest(testDispatcher) {
        sessionManager.sessionId = "stored-session"

        val vm = AuthViewModel(sessionManager, FakeAuthRepository(
            validateResult = Result.failure(SocketTimeoutException("timeout"))
        ))
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Success)
    }

    @Test
    fun checkExistingSession_returns_Success_on_UnknownHost() = runTest(testDispatcher) {
        sessionManager.sessionId = "stored-session"

        val vm = AuthViewModel(sessionManager, FakeAuthRepository(
            validateResult = Result.failure(UnknownHostException("no such host"))
        ))
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Success)
    }

    @Test
    fun checkExistingSession_returns_Success_on_non_network_error() = runTest(testDispatcher) {
        sessionManager.sessionId = "stored-session"

        val vm = AuthViewModel(sessionManager, FakeAuthRepository(
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

        val vm = AuthViewModel(sessionManager, FakeAuthRepository(
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

        val vm = AuthViewModel(sessionManager)
        vm.checkExistingSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm.authState.value is AuthState.Idle)
    }

    @Test
    fun handleAuthResponse_saves_session_to_prefs_across_instances() = runTest(testDispatcher) {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        sessionManager.sessionId = null

        val vm = AuthViewModel(sessionManager)
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
