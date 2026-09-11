package com.kaushalya.interrupter.ui.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kaushalya.interrupter.data.AccountDataGuard
import com.kaushalya.interrupter.data.AuthRepository
import com.kaushalya.interrupter.data.AuthResponse
import com.kaushalya.interrupter.data.DeviceIdentity
import com.kaushalya.interrupter.data.KidProfileRepository
import com.kaushalya.interrupter.data.ParentSummary
import com.kaushalya.interrupter.data.ProfileData
import com.kaushalya.interrupter.data.ProfileKid
import com.kaushalya.interrupter.data.ProfileParent
import com.kaushalya.interrupter.data.ProfileTv
import com.kaushalya.interrupter.data.QuizResultRepository
import com.kaushalya.interrupter.data.SessionManager
import com.kaushalya.interrupter.data.TrialContentDownloader
import com.kaushalya.interrupter.data.UnauthorizedException
import com.kaushalya.interrupter.network.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AuthState {
    data object Idle : AuthState()
    data object Loading : AuthState()
    data class Success(val sessionId: String) : AuthState()
    data class Error(val message: String) : AuthState()
    data class ParentSelectionRequired(
        val sessionId: String,
        val parents: List<ParentSummary>
    ) : AuthState()
}

/**
 * UI state for creating an account from inside Guest mode. Kept separate from
 * [AuthState] so the guest sign-up screen can observe its own progress/errors
 * without fighting the app-wide routing signal.
 */
sealed class GuestSignUpState {
    data object Idle : GuestSignUpState()
    data object Loading : GuestSignUpState()
    data class Success(val migrated: Boolean) : GuestSignUpState()
    data class Error(val message: String) : GuestSignUpState()
}

class AuthViewModel(
    private val sessionManager: SessionManager,
    context: Context,
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val _guestSignUpState = MutableStateFlow<GuestSignUpState>(GuestSignUpState.Idle)
    val guestSignUpState: StateFlow<GuestSignUpState> = _guestSignUpState

    private val _isCheckingSession = MutableStateFlow(false)
    val isCheckingSession: StateFlow<Boolean> = _isCheckingSession

    private val dataGuard = AccountDataGuard(context, sessionManager)
    private val kidProfileRepository = KidProfileRepository.getInstance(context)
    private val appContext: Context = context.applicationContext

    /** Seeds the Trial question bank on the backend (fire-and-forget) after ensuring a default kid. */
    private fun syncTrialContent() {
        viewModelScope.launch {
            TrialContentDownloader.ensureTrialContent(appContext)
        }
    }

    fun checkExistingSession() {
        Log.d(TAG, "checkExistingSession: start")
        _isCheckingSession.value = true
        if (!sessionManager.isLoggedIn()) {
            Log.d(TAG, "checkExistingSession: no stored session, showing welcome")
            _isCheckingSession.value = false
            return
        }
        // Guest sessions skip API validation — trust local state, but refresh the
        // short-lived guest token so quiz/quizzes keep working across app restarts.
        if (sessionManager.isGuest) {
            Log.d(TAG, "checkExistingSession: guest session found, refreshing guest token")
            viewModelScope.launch {
                refreshGuestSession()
                _isCheckingSession.value = false
                _authState.value = AuthState.Success(sessionManager.sessionId!!)
            }
            return
        }
        Log.d(TAG, "checkExistingSession: stored session found (id=${sessionManager.sessionId}), validating with server")
        viewModelScope.launch {
            val result = authRepository.validateSession()
            if (result.isSuccess) {
                val valid = result.getOrNull()?.valid
                Log.d(TAG, "checkExistingSession: validation response valid=$valid")
                if (valid == false) {
                    Log.d(TAG, "checkExistingSession: server rejected session, but trusting local session for now (offline/retry)")
                    _authState.value = AuthState.Success(sessionManager.sessionId!!)
                    kidProfileRepository.ensureDefaultKid()
                    syncTrialContent()
                } else {
                    Log.d(TAG, "checkExistingSession: session valid, navigating to home")
                    _authState.value = AuthState.Success(sessionManager.sessionId!!)
                    kidProfileRepository.ensureDefaultKid()
                    syncTrialContent()
                }
            } else {
                val cause = result.exceptionOrNull()
                if (cause is UnauthorizedException) {
                    Log.d(TAG, "checkExistingSession: server rejected stored session (401), but trusting local session for now (offline/retry)")
                    _authState.value = AuthState.Success(sessionManager.sessionId!!)
                    kidProfileRepository.ensureDefaultKid()
                    syncTrialContent()
                    _isCheckingSession.value = false
                    return@launch
                }
                Log.d(TAG, "checkExistingSession: validation failed (${cause?.javaClass?.simpleName}: ${cause?.message}), trusting local session")
                sessionManager.isOfflineMode = true
                _authState.value = AuthState.Success(sessionManager.sessionId!!)
                kidProfileRepository.ensureDefaultKid()
                syncTrialContent()
            }
            _isCheckingSession.value = false
        }
    }

    fun skipSessionValidation() {
        Log.d(TAG, "skipSessionValidation: skipping due to no network, trusting local session")
        sessionManager.isOfflineMode = true
        _isCheckingSession.value = false
        _authState.value = AuthState.Success(sessionManager.sessionId!!)
        viewModelScope.launch { kidProfileRepository.ensureDefaultKid(); syncTrialContent() }
    }

    fun signUp(loginId: String, password: String, name: String) {
        Log.d(TAG, "signUp: loginId=$loginId, name=$name")
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signUp(loginId, password, name.ifBlank { null })
            if (result.isSuccess) {
                val response = result.getOrNull()!!
                Log.d(TAG, "signUp: success, sessionId=${response.sessionId}")
                handleAuthResponse(response)
            } else {
                Log.d(TAG, "signUp: failed - ${result.exceptionOrNull()?.message}")
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Sign up failed")
            }
        }
    }

    fun signIn(loginId: String, password: String, parentId: String? = null) {
        Log.d(TAG, "signIn: loginId=$loginId, parentId=$parentId")
        viewModelScope.launch {
            _authState.value = AuthState.Loading
            val result = authRepository.signIn(loginId, password, parentId)
            if (result.isSuccess) {
                val response = result.getOrNull()!!
                Log.d(TAG, "signIn: success, sessionId=${response.sessionId}")
                handleAuthResponse(response)
            } else {
                Log.d(TAG, "signIn: failed - ${result.exceptionOrNull()?.message}")
                _authState.value = AuthState.Error(result.exceptionOrNull()?.message ?: "Sign in failed")
            }
        }
    }

    internal fun handleAuthResponse(response: AuthResponse) {
        val sessionId = response.sessionId ?: run {
            Log.d(TAG, "handleAuthResponse: no session ID in response")
            _authState.value = AuthState.Error("No session ID returned")
            return
        }
        if (response.requiresParentSelection == true && !response.parents.isNullOrEmpty()) {
            Log.d(TAG, "handleAuthResponse: parent selection required, ${response.parents.size} parents")
            // Save account info to profile before parent selection flow
            val account = response.accountId ?: response.loginId
            sessionManager.profile = ProfileData(account = account)
            _authState.value = AuthState.ParentSelectionRequired(sessionId, response.parents)
            return
        }
        Log.d(TAG, "handleAuthResponse: saving session (sessionId=$sessionId, loginId=${response.loginId})")
        // Capture the account that owned local data before it is cleared below,
        // so an owner id can still be resolved when the response lacks one.
        val priorAccount = sessionManager.profile.account
        sessionManager.clear()
        sessionManager.hasSeenCarousel = true
        sessionManager.sessionId = sessionId
        sessionManager.loginId = response.loginId
        sessionManager.accountId = response.accountId
        sessionManager.parentId = response.parentId
        sessionManager.parentName = response.parentName
        // Save profile
        val account = response.accountId ?: response.loginId
        val parents = response.parents?.map { ProfileParent(it.parentId, it.parentName) } ?: emptyList()
        sessionManager.profile = ProfileData(account = account, parents = parents)
        viewModelScope.launch {
            dataGuard.ensureOwner(response.accountId ?: response.loginId ?: priorAccount ?: OWNER_UNKNOWN)
            Log.d(TAG, "handleAuthResponse: session and profile saved, navigating to home")
            _authState.value = AuthState.Success(sessionId)
            kidProfileRepository.ensureDefaultKid()
            syncTrialContent()
        }
    }

    fun handleParentSelection(parentId: String, parentName: String) {
        Log.d(TAG, "handleParentSelection: parentId=$parentId, parentName=$parentName")
        val current = _authState.value
        if (current is AuthState.ParentSelectionRequired) {
            // Preserve account info from profile before clearing
            val existing = sessionManager.profile
            sessionManager.clear()
            sessionManager.hasSeenCarousel = true
            sessionManager.sessionId = current.sessionId
            sessionManager.parentId = parentId
            sessionManager.parentName = parentName
            val parents = current.parents.map { ProfileParent(it.parentId, it.parentName) }
            sessionManager.profile = existing.copy(parents = parents)
            viewModelScope.launch {
                dataGuard.ensureOwner(existing.account ?: OWNER_UNKNOWN)
                Log.d(TAG, "handleParentSelection: session and profile updated, navigating to home")
                _authState.value = AuthState.Success(current.sessionId)
                kidProfileRepository.ensureDefaultKid()
                syncTrialContent()
            }
        } else {
            Log.w(TAG, "handleParentSelection: called but state is ${current::class.simpleName}, ignoring")
        }
    }

    fun signOut() {
        Log.d(TAG, "signOut: starting")
        viewModelScope.launch {
            authRepository.signOut()
            sessionManager.clear()
            RetrofitClient.reset()
            _authState.value = AuthState.Idle
            Log.d(TAG, "signOut: complete, session and profile cleared")
        }
    }

    /**
     * Ends a Guest session without wiping the local app data: the quiz results,
     * study sessions, and kid profiles stay on the device so the user can still
     * create an account and have that work carried over. Returns to the Welcome
     * screen (keeps the carousel seen flag).
     */
    fun guestLogout() {
        Log.d(TAG, "guestLogout: clearing guest session only")
        viewModelScope.launch {
            sessionManager.sessionId = null
            sessionManager.isGuest = false
            sessionManager.isOfflineMode = false
            RetrofitClient.reset()
            _authState.value = AuthState.Idle
        }
    }

    /**
     * Creates a real account from inside Guest mode and carries the guest's
     * existing data (quiz results, kids, sessions) onto that account:
     *
     * 1. Signs up with the backend (new account + JWT).
     * 2. Asks the backend to move the guest account's data to the new account
     *    (same rows, same ids, no duplicates).
     * 3. Re-owns the device's local Room data to the new account so the normal
     *    account switch does not wipe it.
     * 4. Switches the session to the new account and pushes anything that had
     *    never been synced.
     *
     * The migration is best-effort: if the backend cannot be reached it still
     * completes the sign-up, but reports [GuestSignUpState.Success.migrated]=false.
     */
    fun signUpFromGuest(loginId: String, password: String, name: String) {
        Log.d(TAG, "signUpFromGuest: loginId=$loginId, name=$name")
        viewModelScope.launch {
            _guestSignUpState.value = GuestSignUpState.Loading
            val result = authRepository.signUp(loginId, password, name.ifBlank { null })
            if (result.isSuccess) {
                val response = result.getOrNull()!!
                Log.d(TAG, "signUpFromGuest: sign up ok, sessionId=${response.sessionId}, migrating guest data")

                // Interim: point the session at the new account so the migration
                // call (and the re-syncs below) carry the new account's JWT.
                sessionManager.sessionId = response.sessionId
                val migrated = try {
                    authRepository.claimGuestData(DeviceIdentity.deviceId(appContext)).isSuccess
                } catch (e: Exception) {
                    Log.w(TAG, "signUpFromGuest: guest data migration failed: ${e.message}")
                    false
                }

                // Re-own local Room data BEFORE the session switch so the owner
                // change does not trigger the account-scoped data wipe.
                dataGuard.reown(targetOwnerId(response))

                // Standard post-auth handling (saves session, validates owner = no wipe).
                handleAuthResponse(response)

                // Push anything that was never synced to the new account.
                retryPendingSyncs()

                if (migrated) {
                    Log.d(TAG, "signUpFromGuest: complete, guest data moved to new account")
                } else {
                    Log.w(TAG, "signUpFromGuest: complete but guest data migration did not run; local data kept")
                }
                _guestSignUpState.value = GuestSignUpState.Success(migrated = migrated)
            } else {
                Log.d(TAG, "signUpFromGuest: failed - ${result.exceptionOrNull()?.message}")
                _guestSignUpState.value = GuestSignUpState.Error(
                    result.exceptionOrNull()?.message ?: "Sign up failed"
                )
            }
        }
    }

    /** Resets the guest sign-up screen state (e.g. when leaving the screen). */
    fun resetGuestSignUp() {
        _guestSignUpState.value = GuestSignUpState.Idle
    }

    /** Owner id the session switch will assign; must match [handleAuthResponse]. */
    private fun targetOwnerId(response: AuthResponse): String =
        response.accountId
            ?: response.loginId
            ?: sessionManager.profile.account
            ?: OWNER_UNKNOWN

    /** Re-pushes pending local rows so guest work lands on the new account. */
    private suspend fun retryPendingSyncs() {
        runCatching { QuizResultRepository.getInstance(appContext).retrySyncFailed() }
            .onFailure { Log.w(TAG, "retryPendingSyncs: quiz result re-sync failed: ${it.message}") }
        runCatching { KidProfileRepository.getInstance(appContext).retrySyncFailed() }
            .onFailure { Log.w(TAG, "retryPendingSyncs: kid re-sync failed: ${it.message}") }
    }

    /**
     * Called when an authenticated API call reports the session token as expired
     * or rejected (401/403). Drops the token, keeps the downloaded profile data,
     * and routes back to the login flow.
     */
    fun forceReLogin() {
        Log.d(TAG, "forceReLogin: clearing expired session")
        sessionManager.sessionId = null
        sessionManager.isOfflineMode = false
        RetrofitClient.reset()
        _isCheckingSession.value = false
        _authState.value = AuthState.Idle
    }

    fun goOnline() {
        sessionManager.isOfflineMode = false
    }

    fun guestLogin() {
        Log.d(TAG, "guestLogin: requesting guest session from backend")
        viewModelScope.launch {
            sessionManager.isGuest = true
            sessionManager.hasSeenCarousel = true
            refreshGuestSession()
        }
    }

    /**
     * Requests a backend-issued anonymous session. On success the real JWT is
     * stored (handleAuthResponse clears local prefs, so guest flags are re-asserted
     * after it). On failure, falls back to a local offline guest session only when
     * there is no usable session yet — an existing guest session is kept as-is.
     */
    private suspend fun refreshGuestSession() {
        val result = authRepository.guestLogin(DeviceIdentity.deviceId(appContext))
        if (result.isSuccess) {
            val response = result.getOrNull()!!
            Log.d(TAG, "guestLogin: backend issued session (account=${response.accountId})")
            handleAuthResponse(response)
            sessionManager.isGuest = true
            sessionManager.hasSeenCarousel = true
        } else {
            Log.w(TAG, "guestLogin: backend unavailable (${result.exceptionOrNull()?.message}), using offline guest session")
            if (sessionManager.sessionId == null) {
                sessionManager.sessionId = "guest"
                sessionManager.profile = ProfileData(account = OWNER_GUEST)
                dataGuard.ensureOwner(OWNER_GUEST)
                _authState.value = AuthState.Success("guest")
                kidProfileRepository.ensureDefaultKid()
                syncTrialContent()
            }
        }
    }

    fun resetError() {
        if (_authState.value is AuthState.Error) {
            Log.d(TAG, "resetError: clearing error state")
            _authState.value = AuthState.Idle
        }
    }

    class Factory(
        private val sessionManager: SessionManager,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(sessionManager, context) as T
        }
    }

    companion object {
        private const val TAG = "AuthViewModel"
        private const val OWNER_GUEST = "guest"
        private const val OWNER_UNKNOWN = "unknown"
    }
}
