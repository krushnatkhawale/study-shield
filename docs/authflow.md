# Auth Flow – Sign In / Sign Up

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                      Auth Components                             │
│                                                                  │
│  UI Layer          MainActivity.kt                               │
│                    ├── AppNavigation (screen routing)             │
│                    ├── WelcomeNavigation (sub-routing)            │
│                    ├── WelcomeScreen                              │
│                    ├── SignUpScreen                               │
│                    ├── SignInScreen                               │
│                    ├── ParentSelectionScreen                      │
│                    └── FeatureCarouselScreen                      │
│                                                                  │
│  ViewModel         AuthViewModel.kt                              │
│                    ├── authState: StateFlow<AuthState>            │
│                    ├── isCheckingSession: StateFlow<Boolean>      │
│                    ├── checkExistingSession()                     │
│                    ├── signUp()                                   │
│                    ├── signIn()                                   │
│                    ├── guestLogin()                               │
│                    ├── handleAuthResponse()                       │
│                    ├── handleParentSelection()                    │
│                    └── signOut()                                  │
│                                                                  │
│  Data Layer        AuthRepository.kt                             │
│                    ├── signUp() → POST /api/auth/signup           │
│                    ├── signIn() → POST /api/auth/signin           │
│                    ├── validateSession() → GET /api/auth/validate │
│                    └── signOut() → POST /api/auth/signout         │
│                                                                  │
│                    SessionManager.kt                             │
│                    ├── SharedPreferences "auth_session"           │
│                    ├── sessionId, loginId, accountId             │
│                    ├── parentId, parentName, isGuest             │
│                    ├── hasSeenCarousel                            │
│                    ├── isLoggedIn()                               │
│                    └── clear()                                   │
│                                                                  │
│  Network           ApiService.kt (Retrofit interface)            │
│                    AuthInterceptor.kt (adds Authorization header) │
│                    RetrofitClient.kt (singleton)                  │
└─────────────────────────────────────────────────────────────────┘
```

## Navigation Decision Tree

On every app launch, `AppNavigation` makes a **single synchronous routing decision** from SharedPreferences via `remember`:

```
remember { mutableStateOf(
    when {
        sessionManager.isLoggedIn()   → "validating"
        !sessionManager.hasSeenCarousel → "carousel"
        else                          → "welcome"
    }
) }
```

### Screen States

| State | Entry Condition | What Shows |
|---|---|---|
| `"validating"` | Session exists in prefs | Spinner while API validates |
| `"carousel"` | First launch (prefs flag false) | 4-slide feature carousel |
| `"main"` | Authenticated (SignIn/SignUp/Guest) | MainScreen with drawer |
| `"welcome"` | No session, carousel seen | WelcomeScreen → SignUp or SignIn |

### Transitions

```
┌────────────┐   API returns Success    ┌────────────┐
│ validating │ ───────────────────────→ │    main     │
│            │                          │             │
│            │   API returns Idle        │  signOut   │
│            │ ───────────────────────→ │ ──────────→ │
└────────────┘                          └────────────┘
                                                   │
                                                   ↓
                                              ┌────────────┐
                                              │  welcome    │
                                              │             │
                                              │  signIn /   │
                                              │  signUp /   │
                                              │  guestLogin │
                                              │ ──────────→ │
                                              └────────────┘
                                                   │
                                                   ↓
                                              ┌────────────┐
                                              │    main     │
                                              └────────────┘
```

## Sign Up Flow

### Sequence

```
User taps "Create Account" on WelcomeScreen
  → WelcomeNavigation sets screen = "signup"
  → SignUpScreen renders (loginId, password, confirmPassword, name fields)

User fills in fields, taps "Create Account"
  → SignUpScreen validates locally:
      - password length >= 6
      - password == confirmPassword
      - loginId not blank
  → onSignUp(loginId, password, name) callback
  → AuthViewModel.signUp(loginId, password, name)

AuthViewModel.signUp():
  authState = Loading (spinner shows on button)
  viewModelScope.launch {
      val result = authRepository.signUp(loginId, password, name)
  }

AuthRepository.signUp():
  POST /api/auth/signup { loginId, password, name? }
  ├── 200/201 with body → Result.success(AuthResponse)
  └── error             → Result.failure(Exception)

On success:
  AuthViewModel.handleAuthResponse(response):
    ├── sessionManager.clear()              ← wipe stale data
    ├── sessionManager.hasSeenCarousel = true
    ├── sessionManager.sessionId = response.sessionId
    ├── sessionManager.loginId = response.loginId
    ├── sessionManager.accountId = response.accountId
    ├── sessionManager.parentId = response.parentId
    ├── sessionManager.parentName = response.parentName
    └── authState = Success(sessionId)

AppNavigation LaunchedEffect(authState, isCheckingSession) fires:
  authState is Success → screen = "main"
  → MainScreen renders

On failure:
  authState = Error(message)
  → SignUpScreen shows error text
```

### SharedPreferences After Sign Up

| Key | Value |
|---|---|
| `session_id` | From API response |
| `login_id` | From API response |
| `account_id` | From API response |
| `parent_id` | From API response |
| `parent_name` | From API response |
| `is_guest` | `false` |
| `has_seen_carousel` | `true` |

## Sign In Flow

### Sequence

```
User taps "Sign In" on WelcomeScreen
  → WelcomeNavigation sets screen = "signin"
  → SignInScreen renders (loginId, password fields)

User fills in fields, taps "Sign In"
  → SignInScreen validates:
      - loginId not blank
      - password not blank
  → onSignIn(loginId, password) callback
  → AuthViewModel.signIn(loginId, password)

AuthViewModel.signIn():
  authState = Loading (spinner shows on button)
  viewModelScope.launch {
      val result = authRepository.signIn(loginId, password)
  }

AuthRepository.signIn():
  POST /api/auth/signin { loginId, password }
  ├── 200/201 with body → Result.success(AuthResponse)
  └── error             → Result.failure(Exception)

On success → same handleAuthResponse() as Sign Up
On failure → authState = Error(message)
```

### Parent Selection (Optional)

If the API response has `requiresParentSelection = true` and a non-empty `parents` list:

```
handleAuthResponse:
  authState = ParentSelectionRequired(sessionId, parents)
  → ParentSelectionScreen renders

User picks a parent:
  AuthViewModel.handleParentSelection(parentId, parentName):
    sessionManager.clear()
    sessionManager.hasSeenCarousel = true
    sessionManager.sessionId = current.sessionId
    sessionManager.parentId = parentId
    sessionManager.parentName = parentName
    authState = Success(sessionId)
    → screen = "main"

User taps "Skip":
  handleAuthResponse(AuthResponse(sessionId, requiresParentSelection=false))
    → cleans up stale data, saves sessionId
    → authState = Success
    → screen = "main"
```

## Guest Login

```
User taps "Continue as Guest" on WelcomeScreen
  → authViewModel.guestLogin()

AuthViewModel.guestLogin():
  sessionManager.isGuest = true
  sessionManager.hasSeenCarousel = true
  sessionManager.sessionId = "guest"
  authState = Success("guest")

AppNavigation LaunchedEffect:
  authState is Success → screen = "main"
  → MainScreen renders (guest mode, limited access)
```

## Session Persistence & Restoration

### Next Launch After Sign In/Sign Up

```
AppNavigation initial routing:
  isLoggedIn() = true (sessionId is in prefs)
  → screen = "validating"

LaunchedEffect(Unit):
  authViewModel.checkExistingSession()

checkExistingSession():
  ├── isGuest = true  → authState = Success("guest"), skip API
  └── has real session:
      GET /api/auth/validate
      ├── 200 + valid = true/null  → authState = Success
      ├── 200 + valid = false       → clear, authState = Idle
      └── Any error (network, 400, 500) → authState = Success (trust local)

AppNavigation LaunchedEffect:
  authState is Success → screen = "main"
  authState is Idle → screen = "welcome" (session expired)
```

### Validation Error Handling

All API errors during session validation **trust the local session**. The session is only cleared if:

1. Server explicitly returns `200 OK` with `"valid": false` in the response body
2. User explicitly signs out

This prevents transient API failures (network blips, server 400/500, bad header format) from logging the user out.

## Sign Out

```
User taps "Sign Out" in drawer
  → AuthViewModel.signOut()

AuthViewModel.signOut():
  viewModelScope.launch {
    authRepository.signOut()    // POST /api/auth/signout (best effort)
    sessionManager.clear()       // wipe all SharedPreferences
    RetrofitClient.reset()       // clear Retrofit instance
    authState = Idle
  }

AppNavigation LaunchedEffect:
  authState is Idle, screen = "main"
  → screen = "welcome"
  → WelcomeScreen renders

Next launch after sign out:
  isLoggedIn() = false
  hasSeenCarousel = false (was wiped by clear())
  → screen = "carousel" (first-time experience again)
```

## AuthState Machine

```
                  ┌─────────┐
         start → │   Idle   │ ←─────────────┐
                  └────┬────┘               │
                       │ signIn/signUp      │ signOut
                       ↓                    │
                  ┌─────────┐               │
                  │ Loading  │              │
                  └────┬────┘               │
                       │ API completes      │
                       ↓                    │
             ┌─────────┴──────────┐        │
             │                    │         │
        needs parent?         error        │
             │                    │         │
             ↓                    ↓         │
    ┌────────────────┐    ┌─────────┐       │
    │ParentSelection │    │  Error  │       │
    │  Required      │    └─────────┘       │
    └───────┬────────┘           │          │
            │ parent selected    │ resetError│
            │ or skipped         ↓          │
            ↓              ┌─────────┐      │
            │              │   Idle  │──────┘
            │              └─────────┘
            ↓
     ┌─────────┐
     │ Success  │ ──────────→ screen = "main"
     └─────────┘
```

## Logcat Tags & Filtering

```bash
adb logcat -s MainActivity AuthViewModel SessionManager
```

Key log lines:

**Sign Up success:**
```
AuthViewModel: signUp: loginId=testuser, name=Test
AuthViewModel: signUp: success, sessionId=abc-123
AuthViewModel: handleAuthResponse: saving session (sessionId=abc-123)
SessionManager: clear: wiping all SharedPreferences
SessionManager: set sessionId -> abc-123
SessionManager: set loginId -> testuser
SessionManager: set hasSeenCarousel -> true
AuthViewModel: handleAuthResponse: session saved, navigating to home
MainActivity: AppNavigation: navigating to main
```

**Sign In success:**
```
AuthViewModel: signIn: loginId=testuser, parentId=null
AuthViewModel: signIn: success, sessionId=abc-123
AuthViewModel: handleAuthResponse: saving session (sessionId=abc-123)
...
MainActivity: AppNavigation: navigating to main
```

**Session restored on next launch:**
```
MainActivity: AppNavigation: screen=validating authState=Idle isCheckingSession=true
MainActivity: AppNavigation: stored session found, validating
AuthViewModel: checkExistingSession: stored session found, validating with server
AuthViewModel: checkExistingSession: session valid, navigating to home
MainActivity: AppNavigation: effect fired authState=Success isCheckingSession=false screen=validating
MainActivity: AppNavigation: navigating to main
```

**Session restored (server error, trusting local):**
```
AuthViewModel: checkExistingSession: validation failed (Exception: Session invalid), trusting local session
MainActivity: AppNavigation: effect fired authState=Success isCheckingSession=false screen=validating
MainActivity: AppNavigation: navigating to main
```

**Guest login:**
```
AuthViewModel: guestLogin: setting guest mode
SessionManager: set isGuest -> true
SessionManager: set hasSeenCarousel -> true
SessionManager: set sessionId -> guest
MainActivity: AppNavigation: effect fired authState=Success isCheckingSession=true screen=welcome
MainActivity: AppNavigation: navigating to main
```

**Sign Out:**
```
AuthViewModel: signOut: starting
AuthViewModel: signOut: complete, session cleared
MainActivity: AppNavigation: effect fired authState=Idle isCheckingSession=false screen=main
MainActivity: AppNavigation: navigating to welcome
```

## Data Models

### API Request/Response

```kotlin
// Requests
data class SignUpRequest(val loginId: String, val password: String, val name: String?)
data class SignInRequest(val loginId: String, val password: String, val parentId: String?)

// Response
data class AuthResponse(
    val sessionId: String?,
    val loginId: String?,
    val accountId: String?,
    val parentId: String?,
    val parentName: String?,
    val requiresParentSelection: Boolean?,
    val parents: List<ParentSummary>?
)

data class ValidationResponse(
    val valid: Boolean?
)

data class ParentSummary(
    val parentId: String,
    val parentName: String
)
```

### SessionManager SharedPreferences

```
File: auth_session.xml (MODE_PRIVATE)

session_id    → String?  (null = not logged in)
login_id      → String?
account_id    → String?
parent_id     → String?
parent_name   → String?
is_guest      → Boolean  (default false)
has_seen_carousel → Boolean (default false)
```

## Key Files

| File | Purpose |
|---|---|
| `MainActivity.kt` | AppNavigation routing, WelcomeNavigation sub-routing |
| `AuthViewModel.kt` | Auth state machine, API orchestration |
| `AuthRepository.kt` | REST API calls (signUp, signIn, validate, signOut) |
| `SessionManager.kt` | SharedPreferences persistence layer |
| `AuthInterceptor.kt` | OkHttp interceptor, adds `Authorization: <sessionId>` header |
| `RetrofitClient.kt` | Retrofit singleton with kotlinx.serialization |
| `ApiService.kt` | Retrofit interface (endpoint definitions) |
| `WelcomeScreen.kt` | Welcome screen (Create Account / Sign In / Guest) |
| `SignUpScreen.kt` | Sign up form with validation |
| `SignInScreen.kt` | Sign in form with validation |
| `ParentSelectionScreen.kt` | Parent selection dialog |
| `FeatureCarouselScreen.kt` | First-launch feature carousel |
