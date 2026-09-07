---
title: "Mobile Parent Config (Features 4, 5 & 6) - design + implementation"
tags: [quiz-learning, mobile, parent-config, tts, read-lock, insight]
status: active
created: 2026-09-03
---

# Mobile Parent-Configurable Quiz Countermeasures

This doc captures **Features 4, 5 & 6** decided by krushnat (2026-09-03, thread root
`44c7c3f7...`). They were planned as backlog but **implemented on 2026-09-03** after green-light.

Baseline already shipped (Options 1–3):
- Opt 1 — per-question option shuffle (mobile `QuizLoader`/`QuizQuestion.shuffledOptions()`).
- Opt 2 — TV silently captures `fastAnswerCount` (answers under the threshold, now configurable),
  sent with each result, never shown to the kid.
- Opt 3 — `fastAnswerCount` persisted mobile + backend and surfaced in mobile Results
  ("Fast answers: N") so the parent can decide whether to enable these countermeasures.

## Feature 4 — Parent-config reveal / read-lock toggles (mobile → TV)

Parent should be able to configure, per kid, whether question content should be:

- **Revealed** normally (default), or
- **Locked until read** — i.e. the child cannot answer until the question/answer text has been
  on screen long enough to plausibly be read (bytes-to-read-time calculation), OR until they
  manually confirm they've read it (reveal/read-lock).

Open decisions:
- Toggle granularity: **per-kid** at the Select Content screen (krushnat confirmed 2026-09-03;
  NOT global and NOT in the kid form). Config rides into the session-start socket command.
- Threshold source: reuse `FAST_ANSWER_THRESHOLD_MS` or a separate read-lock delay.
- Mechanism to push the toggle from mobile to TV: reuse the existing socket
  (`mobileIp:callbackPort` `InterruptionCommand`), add a new command field/type.

## Feature 6 — Kid performance insight on the Kids page

krushnat (2026-09-03): the learning signal should also be visible from the **Kids page** for the
selected kid, not only the Results screen. Today `KidItem` (`ui/KidProfileScreen.kt`) shows only
profile fields; pull each kid's recent results (same source as Results: `QuizResultRepository`) and
show a compact summary flag on the card:
- Latest quiz + % (or "no attempts yet")
- ⚠ "fast answers" flag when a recent result has `fastAnswerCount > 0`

Rich detail (per-result counts) stays on the Results screen; tap-through links the two. This is
backlog scope alongside Features 4/5.

## Feature 5 — Auto-dictation via Android TextToSpeech (TV)

When enabled, the TV should **speak** the question (and optionally the options) aloud using
Android `TextToSpeech`, so the answer isn't purely visual (addresses kids who answer fast
without reading).

Open decisions:
- Toggle to enable/disable (would ride the same parent-config mechanism as Feature 4).
- Which text is spoken: question only, or question + options.
- Replay/next behavior and speed, and whether it runs before the read-lock countdown (Feature 4).

## Implementation notes (for later)

- Mobile already receives results with `fastAnswerCount`; the config toggles live in the parent
  area of the mobile app (Settings/kid profile) and are sent to the TV at session start over the
  existing socket.
- TV `QuizResultsScreen`/`QuizSession` (`MainActivity.kt`) already has the timing plumbing to
  gate answering once a threshold is chosen.
- Android `TextToSpeech` (`android.speech.tts.TextToSpeech`) runs on TV; must be initialized
  before the session and shut down on exit.

## Implemented (2026-09-03)

All three features are implemented and building:

**Feature 4 (reveal/read-lock)**
- Initially placed on Select Content; **krushnat UX revision (2026-09-03)** moved the per-kid
  "Quiz presentation" card to the new **Kid Detail** page (tap a kid in the Kids list →
  `KidDetailScreen.kt`). Card has a **Lock answers until read** switch plus a **fast-answer
  threshold** slider (0–5s, default 1.5s).
- Config persisted per kid in `SessionManager` (`KidQuizConfig` JSON blob, key
  `kid_quiz_config_<kidId>`; `getKidQuizConfig`/`setKidQuizConfig`), no Room migration.
- Sent to the TV in `InterruptionCommand.revealReadLock`/`autoDictation`/`fastAnswerThresholdMs`
  (mobile `Models.kt` + TV `InterruptionCommand.kt`), via `TvServerService` intent extras and
  `MainActivity.handleIntent`.
- TV enforces it: `SafeQuizConfig.readLockDelayMs` = max(threshold, 1.5s); answer buttons in
  `QuizUI`/`TrueFalseUI`/`FitbUI` are disabled until the question has been on screen that long
  (with a "📖 Read the question — answers unlock in Ns" hint). `remainingReadLockMs` helper.

**Feature 5 (TTS auto-dictation)**
- `MainActivity` init a `TextToSpeech` in `onCreate` (locale default) and shuts it down in
  `onDestroy`.
- When `autoDictation` is on, `QuizSession` speaks each question in a
  `LaunchedEffect(currentIndex, …)`.

**Feature 6 (kid performance insight on Kids page)**
- `QuizResultDao.getResultsByChild(childName)` + `QuizResultRepository.getResultsByChild`.
- Kids page list is now info-only cards; tapping a kid opens **Kid Detail**
  (`KidDetailScreen.kt`, new `Screen.KidDetail` route + `KidDetailTarget`). The detail page shows
  a profile header (Edit → existing kid form), a **Performance** section with charts drawn in
  Compose Canvas (bar chart of quiz % per attempt, donut of score distribution, fast-answer
  insight), the **Quiz presentation** controls, and a **Delete profile** button (with confirm)
  at the end.

**Threshold now configurable**: replaced the hardcoded `FAST_ANSWER_THRESHOLD_MS` on the TV with
the per-kid `fastAnswerThresholdMs` sent by the parent app, clamped by `SafeQuizConfig.clampThreshold`.

**Data gap (fast-answer chart)**: only `fastAnswerCount` is stored (a count), not per-answer
response times. The fast-answer insight therefore compares threshold vs observed fast *share*;
a true "threshold vs actual time per answer" chart needs the TV to record + send per-answer
timestamps. Flagged to krushnat.

**Verification**: `:mobile:assembleDebug`, `:tv:assembleDebug` clean (JDK 21); backend
`:ss-modulith:test` green (24 tests incl. new feedback controller test).

