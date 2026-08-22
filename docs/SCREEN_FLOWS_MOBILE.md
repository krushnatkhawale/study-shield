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
| study_start | Start Study Now | `StudyScreens.kt` |
| content | Select Content | `StudyScreens.kt` |

### 3.1 Home — Stats Dashboard
Kid filter chips; cards: Study Minutes / Sessions / Correct %; Recent Activity list; one-time Exp-upgrade prompt dialog.

### 3.2 Library (Control)
```
┌──────────────────────────┐
│ 🎓 START STUDY NOW       │──► Start Study / Select Content
│ ─────────────────────    │
│ TV IP field              │
│ Discovered TVs list      │
│ Interruption Setup card  │
│  (mode, message, etc.)   │
│ [🚀 ACTIVATE INTERRUPTER]│──► alert (sends command to TV)
│ [🔓 EMERGENCY UNLOCK]    │──► alert (sends command to TV)
└──────────────────────────┘
```

### 3.3 Start Study Now (`study_start`)
Shown only when **no TV is selected** (if a TV is already selected, Library goes straight to Select Content).
```
┌──────────────────────────┐
│ ← Select Kid chips (>1)  │
│ Select Target TV         │
│  [TV card] [TV card] …   │
│  [ Connect Now (rescan)] │
│ [Next: Select Content]   │──► Select Content (needs TV selected)
└──────────────────────────┘
```

### 3.4 Select Content (`content`)
Freemium packs segregated per kid by class.
```
┌──────────────────────────┐
│ ← Select Content         │
│ ─ Aarav • Class: 4 ───   │
│  [Pack card] [Pack card] │
│ ─ Riya • Class: 6 ────   │
│  [Pack card]             │
│ [ START SESSION ]        │──► "Session Confirmed" dialog
│                          │    (STUDY_SESSION sent to TV)
└──────────────────────────┘
```
Empty states: no kid profiles / no packs for a grade.

### 3.5 Connected TVs
Scan Now button, discovered TV list, Remember toggle.

### 3.6 Kids & Kid Form
Kids: profile rows (name, grade); empty state "Click + to add your first child." Kid Form: add/edit fields, "Save Profile".

### 3.7 Results
Session Results list → Result Details; Edit Kid entry.

### 3.8 Quiz Setup (non-guest)
Per-kid quiz configuration; prompts to add a kid first if none exist.

### 3.9 Parents (non-guest)
Parent list; "Add Parent" dialog (Name required); Retry on failure.

### 3.10 Settings
TV connection selection + General settings (Parental PIN, Auto-Discovery).

### 3.11 ProfData (dev/debug)
Internal data inspection screen.

## 4. Edge Summary

```
Library ─[START STUDY NOW]──► study_start (if no TV) | content (if TV selected)
study_start ─[Next: Select Content]──► content
content ─[START SESSION]──► session confirmed on TV (dialog, stays on screen)
kids/kid row ─► kid_form ─[Save]──► back
results ─[tap]──► result detail ─[back]──► results
drawer item ─► target route (popUpTo home)
Sign Out ─► welcome
```
