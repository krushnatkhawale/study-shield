# Auth/Navigation Flow – StudyShield Mobile

## App Launch Sequence

```
[1. App Launch]
     │
     ├── onCreate()
     │   ├── RetrofitClient.init()
     │   └── setContent { Theme { SplashScreen } }
     │
     ├── [2. SplashScreen]
     │   └── LaunchedEffect(Unit) { delay(2000); splashDone = true }
     │
     └── splashDone = true ──> recomposition
                │
                ▼
```

## 3. AppNavigation — Initial Screen Decision

Runs **synchronously once** via `remember`, reading from SharedPreferences:

```
remember { mutableStateOf(
    when {
        sessionManager.isLoggedIn()   → "validating"   (1)
        !sessionManager.hasSeenCarousel                (2)
                                       → "carousel"
        else                          → "welcome"      (3)
    }
) }
```

**After `screen` is set:**

| # | Scenario | Initial Screen | What triggers next |
|---|---|---|---|
| 1 | Returning signed-in user | `"validating"` | `LaunchedEffect(Unit)` calls `checkExistingSession()`, which validates with API |
| 2 | Fresh install / first launch | `"carousel"` | User clicks through 4 slides → "Get Started" → `screen = "welcome"` |
| 3 | Returning unsigned user | `"welcome"` | `WelcomeNavigation` shows `WelcomeScreen` immediately |

## 4. AppNavigation — Auth State Reactions

A single `LaunchedEffect(authState)` watches for changes:

```
LaunchedEffect(authState) {
    when (authState) {
        is AuthState.Success → screen = "main"
        is AuthState.Idle → if (screen == "main" || "validating") → screen = "welcome"
    }
}
```

```
               authState changes
                      │
            ┌─────────┴──────────┐
            ▼                    ▼
      AuthState.Success    AuthState.Idle
            │                    │
            │              ┌─────┴──────┐
            ▼              ▼            ▼
       screen="main"  screen was    screen was
                      "main" or     "carousel" or
                      "validating"  "welcome"
                           │            │
                           ▼            ▼
                     screen=      (no change,
                     "welcome"    stays "welcome")
```

## Screen States and Transitions

```
                    ┌──────────────────┐
                    │    validating    │  ═══ spinner while API validates
                    │                  │
                    │  checkExistingSession() results:
                    │    ┌─ Success     ──→ "main"
                    │    └─ Idle (invalid) ─→ "welcome"
                    └──────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│   carousel    │   │    main       │   │   welcome     │
│               │   │               │   │               │
│ 4 slides →    │   │  signOut →    │   │  signIn →     │
│ "Get Started" │   │  authState=   │   │  authState=   │
│               │   │  Idle         │   │  Success      │
│ screen=       │   │  screen=      │   │  screen=      │
│ "welcome"     │   │  "welcome"    │   │  "main"       │
└───────────────┘   └───────────────┘   └───────────────┘
```

## Carousel Path (4 slides)

```
"carousel" shown only when:
  - sessionManager.isLoggedIn() == false
  - sessionManager.hasSeenCarousel == false

Slide 1: "Welcome to StudyShield"       Button → "Next"
Slide 2: "Interactive Quizzes"          Button → "Next"
Slide 3: "Track Progress"               Button → "Next"
Slide 4: "Ready to Start?"              Button → "Get Started"

"Get Started" clicked:
  sessionManager.hasSeenCarousel = true
  screen = "welcome"                    ← triggers recomposition to WelcomeNavigation
```

## 5. WelcomeNavigation (sub-navigation)

```
WelcomeNavigation
  │
  ├── screen = "welcome"  → WelcomeScreen
  │     ├── "Create Account"  → screen = "signup"
  │     ├── "Sign In"         → screen = "signin"
  │     └── "Continue as Guest" → authViewModel.guestLogin()
  │
  ├── screen = "signup"   → SignUpScreen
  │     ├── [Submit]  → authViewModel.signUp()
  │     └── [Back]    → screen = "welcome"
  │
  └── screen = "signin"   → SignInScreen
        ├── [Submit]  → authViewModel.signIn()
        └── [Back]    → screen = "welcome"

When authState = ParentSelectionRequired:
  → ParentSelectionScreen shown as overlay
    ├── Pick parent  → authViewModel.handleParentSelection()
    ├── "Skip"       → authViewModel.handleAuthResponse(noParents)
    └── "Add New Parent" → TODO

authState becomes Success → AppNavigation LaunchedEffect catches it
                         → screen = "main" → MainScreen
```

## 6. MainScreen (authenticated)

```
MainScreen
  ├── Full access for signed-in users
  ├── Limited access for guest mode
  │     (drawer shows "Guest", hides Students/Quiz Setup/Parents)
  └── Sign Out → authViewModel.signOut()
                  → sessionManager.clear()
                  → authState = Idle
                  → screen = "welcome"
```

## Guest Login

```
guestLogin():
  sessionManager.isGuest = true
  sessionManager.hasSeenCarousel = true
  sessionManager.sessionId = "guest"
  authState = Success("guest")

  → AppNavigation LaunchedEffect: Success → screen = "main"

  Next launch:
    sessionManager.isLoggedIn() = true (sessionId = "guest")
    → screen = "validating"
    → checkExistingSession():
        isGuest = true → skip API → authState = Success("guest")
    → screen = "main"
```

## Sign Out

```
signOut():
  sessionManager.clear()     → wipes sessionId, isGuest, hasSeenCarousel, etc.
  authState = Idle

  → AppNavigation LaunchedEffect:
      authState = Idle, screen = "main"
      → screen = "welcome"

  Next launch:
    isLoggedIn() = false
    hasSeenCarousel = false (was wiped by clear())
    → screen = "carousel" (first time again)

  ⚠️  On sign out, hasSeenCarousel is also cleared.
     Next launch shows carousel again.
     This is expected — carousel is a one-time onboarding per device,
     independent of auth state.
```

## handleAuthResponse (signup/signin success)

```
handleAuthResponse(response):
  sessionManager.clear()              ← wipe any stale data
  sessionManager.hasSeenCarousel = true
  sessionManager.sessionId = response.sessionId
  sessionManager.loginId = response.loginId
  sessionManager.accountId = response.accountId
  sessionManager.parentId = response.parentId
  sessionManager.parentName = response.parentName
  authState = Success(sessionId)

  → AppNavigation: screen = "main"
```

## Key State Changes

| Action | SharedPreferences | authState | screen |
|---|---|---|---|
| Fresh install | `sessionId=null, hasSeenCarousel=false` | `Idle` | `"carousel"` |
| "Get Started" clicked | `hasSeenCarousel=true` | `Idle` | `"welcome"` |
| Sign In submitted | `sessionId="abc", hasSeenCarousel=true` | `Success("abc")` | `"main"` |
| Guest Login | `sessionId="guest", isGuest=true, hasSeenCarousel=true` | `Success("guest")` | `"main"` |
| Sign Out | all cleared | `Idle` | `"welcome"` |
| Next launch (signed in) | `sessionId="abc", hasSeenCarousel=true` | → validates → `Success` | `"main"` |
| Next launch (guest) | `sessionId="guest", isGuest=true` | → skips API → `Success` | `"main"` |

## Logcat Filter

```bash
adb logcat -s MainActivity AuthViewModel SessionManager
```

Key log lines to trace the flow:

```
MainActivity: AppNavigation: screen=carousel authState=Idle
MainActivity: AppNavigation: carousel finished
MainActivity: AppNavigation: screen=welcome authState=Idle
AuthViewModel: signIn: loginId=testuser
AuthViewModel: handleAuthResponse: saving session (sessionId=abc-123)
AuthViewModel: handleAuthResponse: session saved, navigating to home
MainActivity: AppNavigation: authState changed to Success
MainActivity: AppNavigation: navigating to main
```

```
MainActivity: AppNavigation: screen=validating authState=Idle
AuthViewModel: checkExistingSession: stored session found, validating with server
AuthViewModel: checkExistingSession: session valid, navigating to home
MainActivity: AppNavigation: authState changed to Success
MainActivity: AppNavigation: navigating to main
```
