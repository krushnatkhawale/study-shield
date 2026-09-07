# StudyShield Mobile — Screen Flows / Wireframes

Source of truth: `mobile/src/main/java/com/kaushalya/interrupter/` (`MainActivity.kt`, `ui/StudyScreens.kt`, `ui/auth/*`, `ui/*`). Update this doc whenever screens are added or removed — it is the reference for instructing screen changes.

## 1. Flow Map

```
Splash ──► Validating ──► Main App
   │            │
   │            └─(invalid/expired)──► Welcome
   ├─(first run)──► Feature Carousel ──► Welcome
   └─(returning)──────────────────────► Welcome

Welcome ──"Create Account"──► Sign Up ──┐
        ──"Sign In"───────► Sign In ────┤
        ──"Continue as Guest"───────────┤
                                        ▼
                            [Parent Selection overlay]
                                        │
                                        ▼
                                   Main App (drawer)
```

### Main App (drawer navigation)

```
                 ┌──────────── Drawer ────────────┐
                 │ Home · Library · Connected TVs │
                 │ Kids · Results · Quiz Setup*   │
                 │ Parents* · Settings · ProfData │
                 │ Sign Out*                      │
                 └────────────────────────────────┘
                 (* hidden in Guest mode)

 Library ─"START STUDY NOW"─┬─(no TV selected)─► Start Study ─"Next: Select Content"─► Select Content
                            └─(TV already selected)───────────────────────────────────► Select Content
```

## 2. Pre-Auth Screens

### 2.1 Splash
```
┌──────────────────────────┐
│                          │
│       StudyShield        │
│ Turn TV Ads into         │
│      Learning Time       │
│                          │
│      (2s auto-advance)   │
└──────────────────────────┘
```

### 2.2 Validating
Spinner only — stored session checked against API. Offline → toast + continue; invalid session → Welcome.

### 2.3 Feature Carousel
3 intro slides ("Answer questions right on the TV…"). Buttons: Next/Skip. Sets `hasSeenCarousel`.

### 2.4 Welcome
```
┌──────────────────────────┐
│         Welcome          │
│  [ Create Account ]      │
│  [    Sign In     ]      │
│  [Continue as Guest]     │
└──────────────────────────┘
```

### 2.5 Sign Up
Fields: Name, Email-or-Username, Password, Confirm Password. Actions: "Create Account", "Already have an account? Sign In", Back → Welcome.

### 2.6 Sign In
Fields: Email-or-Username, Password. Actions: "Sign In", Back → Welcome.

### 2.7 Parent Selection (overlay dialog)
Shown when auth responds `ParentSelectionRequired`. List of parent rows; "Add New Parent"; "Skip for now". Any choice proceeds into Main.

## 3. Main App Screens (NavHost routes)

| Route | Screen | File |
|---|---|---|
| home | Stats Dashboard | `StudyScreens.kt` |
| control | Library | `StudyScreens.kt` |
| connected_tvs | Connected TVs | `ui/TvManagementScreen.kt` |
| kids | Kids list | `ui/KidProfileScreen.kt` |
| kid_form | Add/Edit Kid | `ui/KidFormScreen.kt` |
| session_results | Results (+ detail) | `ui/SessionResultScreen.kt` |
| quiz_setup | Quiz Setup (non-guest) | `ui/quiz/QuizSetupScreen.kt` |
| parents | Parents (non-guest) | `ui/parents/ParentManagementScreen.kt` |
| settings | Settings | `StudyScreens.kt` |
| profdata | Debug data | `ui/ProfDataScreen.kt` |
| content | Select Content | `StudyScreens.kt` |
| quiz_review | Quiz Review | `ui/quiz/QuizReviewScreen.kt` |

### 3.1 Home — Stats Dashboard
Kid filter chips; cards: Study Minutes / Sessions / Correct %; Recent Activity list; one-time Exp-upgrade prompt dialog.

### 3.2 Library (Control)
```
┌──────────────────────────┐
│ 🎓 START STUDY NOW       │──► Select Content (always)
│ ─────────────────────    │
│ TV IP field              │
│ Discovered TVs list      │
│ Interruption Setup card  │
│  (mode, message, etc.)   │
│ [🚀 ACTIVATE INTERRUPTER]│──► alert (sends command to TV)
│ [🔓 EMERGENCY UNLOCK]    │──► alert (sends command to TV)
└──────────────────────────┘
```

### 3.3 Select Content (`content`)
Freemium packs in a **tabbed view — one tab per kid**; each tab shows only that kid's packs.
```
┌──────────────────────────┐
│ ← Select Content         │
│ [Aarav] [Riya]           │  ← kid tabs
│ ── Class: 4 ─────────    │
│  [Pack card] [Pack card] │
│   pack card shows attempt history:
│   "Attempted N times • last score X/Y (P%)"
│   each card has a 👁 review icon
│ [ START SESSION ]        │──► "Session Confirmed" dialog
│                          │    (STUDY_SESSION sent to TV)
└──────────────────────────┘
```

Each pack card has a **review (👁) icon** that opens `Quiz Review` (`quiz_review` route) — a listing of the quiz's questions, options, and highlighted correct answers. Reviewing does not record a result and leaves the `Select Content` screen selection untouched.

Each question in the review screen offers icon-only **feedback actions**: **up (👍) / down (👎) / report (🚩)** — no text label, just the glyphs.
- **Up ▲** → comment optional.
- **Down ▼** → category select (wrong answer / typo / offensive / other) + comment optional.
- **Report 🚩** → comment mandatory.
- A user's up/down is recorded **once per question**; tapping the same vote again clears it (`NONE`). Feedback is persisted backend-side via `PUT /api/v1/questions/{id}/feedback`, and the current state is loaded on screen open via `GET /api/v1/questions/{id}/feedback` (JWT auth, current account).

**Offline + sync behavior (all API calls):**
- Every network call has a **7-second timeout** (`OkHttpClient.callTimeout/connectTimeout/readTimeout/writeTimeout = 7s`), so the app stays responsive when the backend is slow or gone.
- With no backend the app keeps working as-is: pack content is cache/asset-first (`PackCache` + bundled quizzes), and mutations degrade gracefully.
- Feedback submits are **queued offline**: if the API can't be reached, the action is stored in Room (`pending_feedback`) and flushed automatically via `ConnectivityObserver` when connectivity returns (`FeedbackRepository.retrySyncFailed`). Quiz results and kid profiles follow the same pending-sync pattern.
- **Quiz results are never re-sent just by opening the app.** Results are only pushed to the server when (a) they were captured offline and are queued for sync, or (b) a quiz finishes on the TV and the mobile record is acknowledged. Rows pulled back from the backend on fetch are inserted locally **without** re-syncing (`QuizResultRepository.insertFromBackend`), and offline-mode rows are deleted locally once the server has acknowledged them — so attempt counts stay correct instead of climbing on every restart.
Empty states: no kid profiles / no packs for a grade (a kid tab with no packs shows an inline "No packs for <kid>" message).

Pack loading is **cache-first** (`data/PackCache.kt`): packs are stored per logged-in user + grade in app-private files; the backend is only fetched on the first download or cache miss, and cache hits are logged (`PackCache: Cache hit ... skipping backend fetch`). Attempt counts and last scores come from the local `quiz_results` Room table, matched by kid name + pack name.

### 3.4 Connected TVs
Scan Now button, discovered TV list, Remember toggle.

### 3.5 Kids & Kid Form
Kids: profile rows (name, grade); empty state "Click + to add your first child." Kid Form: add/edit fields, "Save Profile".

### 3.6 Results
Session Results list → Result Details; Edit Kid entry.

### 3.7 Quiz Setup (non-guest)
Per-kid quiz configuration; prompts to add a kid first if none exist.

### 3.8 Parents (non-guest)
Parent list; "Add Parent" dialog (Name required); Retry on failure.

### 3.9 Settings
TV connection selection + General settings (Parental PIN, Auto-Discovery).

### 3.10 ProfData (dev/debug)
Internal data inspection screen.

## 4. Edge Summary

```
Library ─[START STUDY NOW]──► content (always)
content ─[START SESSION]──► session confirmed on TV (dialog, stays on screen)
content ─[review icon]──► quiz_review ─[back]──► content
kids/kid row ─[tap]──► kid_detail ─[Edit]──► kid_form ─[Save]──► back
kids/kid row ─[+]──► kid_form ─[Save]──► back
kid_detail ─[Delete]──► (confirm) ─► back to kids
results ─[tap]──► result detail ─[back]──► results
drawer item ─► target route (popUpTo home)
Sign Out ─► welcome
```

## 5. Quiz Learning Countermeasures

### Implemented (2026-09-03)
- **Fast-answer detection**: TV silently captures `fastAnswerCount` (answers under the threshold),
  sent with every quiz result, never shown to the kid.
- **Kid Detail page (new)** — krushnat UX (2026-09-03): the **Kids** list is now clean info-only
  cards (no per-row edit/delete buttons); tapping a kid opens a full-screen **Kid Detail** page
  (`KidDetailScreen.kt`) that contains:
  - Profile summary header with an Edit action (→ existing kid form).
  - **Performance section** (charts drawn with Compose Canvas, no new dependency): a bar chart of
    quiz % across attempts, a donut of score distribution (Great ≥80 / Good 50–79 / Needs practice
    <50), and a **fast-answer insight** comparing the configured threshold vs the latest attempt's
    fast-answer share.
  - **Quiz presentation** controls (moved here from Select Content).
  - **Delete profile** button (error-color) at the very end with a confirmation dialog.
- **Configurable threshold + Features 4/5**: the per-kid "Quiz presentation" card
  (`QuizPresentationConfigCard`, moved from Select Content to Kid Detail) with:
  - **Lock answers until read** (reveal/read-lock) — switch
  - **Read questions aloud (TTS)** — switch
  - **Fast-answer threshold** slider (0–5s, default 1.5s)
  Persisted per kid via `SessionManager.getKidQuizConfig/setKidQuizConfig` and pushed to the TV in
  the `InterruptionCommand` (`revealReadLock`, `autoDictation`, `fastAnswerThresholdMs`).
- **Feature 6 — kid performance insight**: the **Kids page** now shows on each kid card the latest
  quiz % and a ⚠ "N fast" flag when any recent result has `fastAnswerCount > 0` (via
  `QuizResultDao.getResultsByChild`).
- Results screen already flags "Fast answers: N" in the list + detail.

### Note — threshold-vs-actual chart data
The TV currently stores only a fast-answer **count**, not per-answer response times. So the
fast-answer insight on Kid Detail compares the configured threshold against the observed fast
*share*. A true "threshold vs actual time per answer" chart requires the TV to record and send
per-answer timestamps (not stored today) — flagged to krushnat.

### Backlog
Previously-deferred parent-config items (reveal/read-lock, TTS) are now implemented as above. See
`docs/PLANS/MOBILE_PARENT_CONFIG_BACKLOG.md` for the design rationale.
