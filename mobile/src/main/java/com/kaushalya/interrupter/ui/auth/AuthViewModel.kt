package com.kaushalya.interrupter.ui.auth

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kaushalya.interrupter.data.AccountDataGuard
import com.kaushalya.interrupter.data.AuthRepository
import com.kaushalya.interrupter.data.AuthResponse
import com.kaushalya.interrupter.data.KidProfileRepository
import com.kaushalya.interrupter.data.ParentSummary
import com.kaushalya.interrupter.data.ProfileData
import com.kaushalya.interrupter.data.ProfileKid
import com.kaushalya.interrupter.data.ProfileParent
import com.kaushalya.interrupter.data.ProfileTv
import com.kaushalya.interrupter.data.SessionManager
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

class AuthViewModel(
    private val sessionManager: SessionManager,
    context: Context,
    private val authRepository: AuthRepository = AuthRepository()
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    private val _isCheckingSession = MutableStateFlow(true)
    val isCheckingSession: StateFlow<Boolean> = _isCheckingSession

    private val dataGuard = AccountDataGuard(context, sessionManager)
    private val kidProfileRepository = KidProfileRepository.getInstance(context)

    fun checkExistingSession() {
        Log.d(TAG, "checkExistingSession: start")
        if (!sessionManager.isLoggedIn()) {
            Log.d(TAG, "checkExistingSession: no stored session, showing welcome")
            _isCheckingSession.value = false
            return
        }
        // Guest sessions skip API validation — trust local state
        if (sessionManager.isGuest) {
            Log.d(TAG, "checkExistingSession: guest session found, skipping API validation")
            _isCheckingSession.value = false
            _authState.value = AuthState.Success(sessionManager.sessionId!!)
            return
        }
        Log.d(TAG, "checkExistingSession: stored session found (id=${sessionManager.sessionId}), validating with server")
        viewModelScope.launch {
            val result = authRepository.validateSession()
            if (result.isSuccess) {
                val valid = result.getOrNull()?.valid
                Log.d(TAG, "checkExistingSession: validation response valid=$valid")
                if (valid == false) {
                    Log.d(TAG, "checkExistingSession: server rejected session, clearing")
                    sessionManager.clear()
                    _authState.value = AuthState.Idle
                } else {
                    Log.d(TAG, "checkExistingSession: session valid, navigating to home")
                    _authState.value = AuthState.Success(sessionManager.sessionId!!)
                    kidProfileRepository.ensureDefaultKid()
                }
            } else {
                val cause = result.exceptionOrNull()
                Log.d(TAG, "checkExistingSession: validation failed (${cause?.javaClass?.simpleName}: ${cause?.message}), trusting local session")
                sessionManager.isOfflineMode = true
                _authState.value = AuthState.Success(sessionManager.sessionId!!)
                kidProfileRepository.ensureDefaultKid()
            }
            _isCheckingSession.value = false
        }
    }

    fun skipSessionValidation() {
        Log.d(TAG, "skipSessionValidation: skipping due to no network, trusting local session")
        sessionManager.isOfflineMode = true
        _isCheckingSession.value = false
        _authState.value = AuthState.Success(sessionManager.sessionId!!)
        viewModelScope.launch { kidProfileRepository.ensureDefaultKid() }
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

    fun goOnline() {
        sessionManager.isOfflineMode = false
    }

    fun guestLogin() {
        Log.d(TAG, "guestLogin: setting guest mode")
        viewModelScope.launch {
            sessionManager.isGuest = true
            sessionManager.hasSeenCarousel = true
            sessionManager.sessionId = "guest"
            sessionManager.profile = ProfileData(account = "guest")
            dataGuard.ensureOwner(OWNER_GUEST)
            _authState.value = AuthState.Success("guest")
            kidProfileRepository.ensureDefaultKid()
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
