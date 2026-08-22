# StudyShield TV — Screen Flows / Wireframes

Source of truth: `tv/src/main/java/com/kaushalya/interrupter/MainActivity.kt` (single-activity, `AnimatedContent` keyed by `commandType` state) plus `TvServerService.kt`, `LockPersistenceManager.kt`, `BootReceiver.kt`. Update this doc whenever screens/states change.

## 1. How the TV receives commands

The TV has **no local navigation** — every screen is a remote-driven state:

1. `TvServerService` runs a LAN socket server; the mobile app sends commands to it.
2. Commands also arrive as Activity intents (`handleIntent`, MainActivity.kt:231): `COMMAND_TYPE`, `MESSAGE`, `DURATION`, `CONTENT_NAME`, `CATEGORY`, `MOBILE_IP`, `RESULT_CALLBACK_PORT`, `QUESTIONS_JSON`.
3. `LockPersistenceManager` re-applies an active lock after reboot; `BootReceiver` restarts the service at boot.
4. The idle screen displays device name + IP so the phone can connect.

## 2. State Map

```
                       ┌────────────┐
        app start ────►│    IDLE    │◄─ onExit / UNLOCK / timeout (moveTaskToBack)
                       │ (Ready)    │
                       └─────┬──────┘
                             │ command from mobile
      ┌──────────┬───────────┼────────────┬─────────────┐
      ▼          ▼           ▼            ▼             ▼
   BLOCK       TIMER    STUDY_SESSION    MCQ          FITB
 (lock msg) (countdown) (study pack)  (quiz Q&A)  (fill-in-blank)
                                              │
                                              ▼
                                       QUIZ RESULTS ─[Exit]──► IDLE
```

`UNLOCK` is not a screen — it clears any active state back to Idle.

## 3. States

### 3.1 Idle (`commandType == null`)
```
┌──────────────────────────────────────────────┐
│                                    ⏱ 15s ring│
│         {device name}                        │
│      Interrupter Ready! 🚀                   │
│   Connect using IP: 192.168.x.x              │
└──────────────────────────────────────────────┘
```
15-second countdown then `moveTaskToBack`. Any incoming command fades into its state.

### 3.2 BLOCK — lock screen
```
┌──────────────────────────────────────────────┐
│  (red gradient)                              │
│        Time for a break! ✋                  │
│     {custom message from mobile}             │
└──────────────────────────────────────────────┘
```

### 3.3 TIMER — timed break countdown
```
┌──────────────────────────────────────────────┐
│  See you soon! ⏳                            │
│        {countdown: DURATION}                 │
└──────────────────────────────────────────────┘
```
Returns to Idle when the timer elapses.

### 3.4 STUDY_SESSION — study content display
Full-screen study session for the pack started from mobile Select Content (`CONTENT_NAME` / `CATEGORY` shown). Ends via UNLOCK or session end → Idle.

### 3.5 MCQ — multiple-choice quiz
Renders questions from `QUESTIONS_JSON` full-screen; user answers with remote/D-pad. After all questions → Quiz Results.

### 3.6 FITB — fill in the blank quiz
Same pipeline as MCQ but text-entry answers → Quiz Results.

### 3.7 QUIZ RESULTS (`QuizResultsScreen`, MainActivity.kt:948)
```
┌──────────────────────────────────────────────┐
│            Quiz Complete! 🎉                 │
│          Score: 7 / 10                       │
│      {contentName} · {category}              │
│                                              │
│  Result reported back to mobile              │
│  ({mobileIp}:{RESULT_CALLBACK_PORT})         │
│  [ Exit ]  ──► IDLE                          │
└──────────────────────────────────────────────┘
```

### 3.8 UNLOCK (no screen)
Command-only transition: clears the active lock/quiz and returns to Idle immediately.

## 4. Edge Summary

```
IDLE ─[BLOCK cmd]──► BLOCK ─[UNLOCK]──► IDLE
IDLE ─[TIMER cmd]──► TIMER ─[timeout]──► IDLE
IDLE ─[STUDY_SESSION cmd]──► Study Session ─[UNLOCK/end]──► IDLE
IDLE ─[MCQ/FITB cmd]──► Quiz ─[all answered]──► Results ─[Exit]──► IDLE
any active state ─[UNLOCK cmd]──► IDLE
reboot with active lock ─► LockPersistenceManager re-applies previous state
```
