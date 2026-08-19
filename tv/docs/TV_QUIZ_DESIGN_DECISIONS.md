# TV Quiz Player — Design Decisions

Recorded for future agents working on the TV module (`tv/`).

## Screen Layout Rules

The TV app runs on **fixed-screen Android TV devices** (typically 1080p, 1920×1080).
All composable UIs MUST follow these constraints:

1. **No vertical scrolling.** Every screen must fit within a single viewport.
   Use `Arrangement.SpaceBetween` on Columns to distribute content across
   the available height. Never use `Arrangement.Center` with unconstrained
   children — it pushes content off-screen when totals exceed viewport height.

2. **Fixed-size buttons.** Use `Modifier.weight(1f)` for horizontal distribution
   within a Row. Use fixed `height()` (not `heightIn()`) for vertical sizing.
   Typical button heights: 100dp (MCQ options), 140dp (True/False), 70dp (CLEAR).

3. **Reduced font sizes.** TV is viewed from 2–4 meters. Question text:
   48/38/30/24sp (by length). Option text: 32/26/22/18sp (by length).
   The old 72sp question / 42sp option fonts caused overflow.

4. **Consistent button appearance.** All option buttons within a quiz type
   must be identical in size. Use `weight(1f)` + fixed height, never
   fixed pixel widths like `width(460.dp)`.

5. **D-pad navigation.** Every interactive element must be focusable.
   Use `focusRequester` on the first option in each quiz type.
   Focus scale animation: 1.08f (subtle, not 1.15f which was too large).

## PAUSE / Exit Flow

- **No on-screen PAUSE button.** The play/pause key on the TV remote
  toggles quiz pause via `onKeyDown()`. A `PauseOverlay` covers the
  screen when paused.

- **Exit confirmation:** Double-press play/pause during a quiz to exit.
  First press shows "PAUSED" overlay. Second press within 3 seconds
  triggers `exitApp()`. If the user resumes or waits, the exit window
  resets.

- **No PIN entry on TV.** PIN is set on the mobile app side. The TV
  trusts commands received via TCP from the paired mobile device.

## Quiz Lifecycle

1. Mobile app sends `MCQ`/`FITB` command via TCP to port 8888
2. `TvServerService` receives, persists via `LockPersistenceManager`,
   launches `MainActivity` with Intent extras
3. `QuizSession` composable renders questions with progress bar
4. User answers all questions → `QuizResultsScreen` shows score
5. User taps "Done" → `exitApp()` → `moveTaskToBack(true)` + state reset
6. `TvServerService` continues running (foreground service, survives reboots)

## Command Types

| Type | Screen | Auto-exit |
|------|--------|-----------|
| `MCQ` | Quiz (MCQ or True/False) | No — waits for completion |
| `FITB` | Fill-in-the-blank quiz | No — waits for completion |
| `BLOCK` | "Time for a break!" | After duration seconds |
| `TIMER` | Countdown message | After duration seconds |
| `STUDY_SESSION` | Study reminder | After duration minutes |
| `UNLOCK` | Immediately exits | Instant |

## Network Architecture

- TV runs a TCP `ServerSocket` on port 8888 (foreground service)
- Mobile app connects and sends one JSON line per command
- No authentication on TCP — relies on local WiFi network trust
- TV also registers via mDNS for auto-discovery by mobile app
- Commands are atomic (one line of JSON), no streaming

## File Locations

| File | Purpose |
|------|---------|
| `tv/.../MainActivity.kt` | All Compose UI (quiz, pause, results, study session) |
| `tv/.../TvServerService.kt` | TCP server, command dispatch, NSD registration |
| `tv/.../LockPersistenceManager.kt` | SharedPreferences wrapper for command persistence |
| `tv/.../BootReceiver.kt` | Starts TvServerService on device boot |
| `tv/.../InterruptionCommand.kt` | Serializable command model (duplicated in mobile) |

## Key Gotchas

- `InterruptionCommand` is **duplicated** between mobile and TV modules.
  Changes to the command format must be made in both places.
- `TvServerService` has **no socket read timeout** — a stalled client
  blocks the server loop. Consider adding `setSoTimeout()`.
- The app never calls `finish()` — it only calls `moveTaskToBack(true)`.
  The service keeps running independently.
- The "Parental PIN" switch in mobile settings is a **UI placeholder**
  with no backing logic.
