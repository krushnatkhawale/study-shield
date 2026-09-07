# Greeting Experience on the TV App — Exploration & Recommendation

Goal: give the kid the best possible **greeting experience** on the StudyShield TV app.
Prepared 2026-09-05 from kids-UX research (sources cited inline). The recommendation is a
start-of-quiz greeting; idle-screen and farewell variants are covered at the end.

## 1. Evidence: what makes kids' greetings work

1. **Personalization (by name) lands hard.** Children respond to the app "knowing" them —
   a greeting like "Good Morning, Cakka!" is the first screen a praised kids app shows, and
   personalized dashboards that greet each child by name are core to high-engagement kids'
   apps (LAPP greets every child by name; KidNation profiles make a shared device feel
   "theirs"). We already carry `kidName` in every quiz command — this is free to use.
   - https://www.kidnation.com/child-profiles-in-kids-apps-why-personalization-can-make-content-feel-more-age-aware/
   - https://www.red-c.co.uk/case-studies/lapp-the-library-in-your-lap/
2. **Voice matters as much as text.** Young kids read poorly or not at all; sound talks for
   them. Kids' UI guidelines: keep text minimal, use visuals + voice overs, "sound effects
   communicate without overwhelming with words" (Raw.Studio), and voice-guided apps are an
   explicit design pattern (Hellosaurus voice-over prompts; KidoXplore's "Story Reader with
   British English narration"). We already have TTS (UK English, slowed 0.8×) — reuse it for
   the greeting.
   - https://raw.studio/blog/designing-for-children/
   - https://www.mrdavenport.co/hellosaurus-app
3. **A friendly character/helper raises engagement.** Characters anchor kids' products
   (Ango, Boddle, Duolingo ABC): "a character greets the child and leads the way — turning
   an app screen into a relationship." Research-backed: avatar presence "improved learning
   outcomes and retention compared to the same lesson without one" (Gamage et al., 2018, via
   UX-of-EdTech). We have no mascot on TV yet — a simple one would multiply greeting impact.
   - https://medium.com/ux-of-edtech/characters-are-ux-making-learning-apps-kids-can-connect-with-128e0330d57c
   - https://mara.ink/case-studies/ango-kids-language-learning-app
4. **Keep it short and automatic; don't get between the kid and the quiz.** Kids lose focus
   fast. A greeting should be a 2–4 s beat that rolls into the first question, not a barrier.
   This matches the project's own recent decision (result screen auto-closes after 4 s, no
   button). Android's own guidance reserves blocking screens for first-time onboarding only.
   - https://developer.android.com/training/tv/playback/leanback/onboarding
5. **Frame it as play, praise early, use time-of-day.** "Quiz" reads like a test; "game /
   adventure" reads as play (Raw.Studio, Ango). Time-of-day ("Good morning") makes the
   greeting feel alive and costs one `Calendar` lookup. Immediate, positive feedback loop is
   a recurring kids-UX pillar (uxmag, aufaitux).

## 2. Recommended design (start-of-quiz greeting)

**When:** every quiz command starts a 3–4 s greeting, then auto-advances to Question 1.

**What the kid sees/hears (Pattern — "Hello, <Name>! Let's go!")**

- Full-screen gentle gradient (reuse the green quiz background).
- Big text: **"Hello, Rohan!"** (+ smaller line: content name or "Ready for a quick game?")
- The kid's mascot (12 per-kid avatars from Kid Profile; see §3) at the edge, waving.
  (Start-of-quiz may borrow the same mascot for continuity — see §3.)
- **TTS speaks the greeting aloud** in the UK-English voice already configured.
- Edges in with the existing `AnimatedContent` fade; auto-advance after ~3.5 s (no button).

**Rules**

| Rule | Detail |
|------|--------|
| Name | From `kidName` in the command; fallback: no name → "Hello!" only |
| Time of day | "Good morning / afternoon / evening" prefix via `java.util.Calendar` |
| Length | ≤ 4 s total; kid waits on TV, parent triggered remotely — don't stall the session |
| Repeat sessions | Shorten after the first greeting of the hour (`LockPersistenceManager`/`SharedPreferences`, e.g. only a name-less wave), so frequent quizzes don't repeat |
| Voice | Same TTS instance — auto-dictation then reads Q1 right after (nice continuity) |
| Accessibility | Text + voice together (covers non-readers and hearing-focused kids) |

## 3. How it maps to the current TV code

All in `tv/src/main/java/com/kaushalya/interrupter/MainActivity.kt`:

- `QuizSession(...)` already receives `kidName`, `textToSpeech`, `contentName`, `avatarId`.
  Add a `showGreeting` phase before question 1 (`completed == false && index == 0`), driven
  by a `LaunchedEffect` that delays ~3.5 s then flips a `greeted` flag — mirrors the result
  screen's auto-close pattern we just built (lines ~1151).
- **Live mascot (shipped):** the end-of-quiz screen (`QuizResultsScreen`) shows a code-drawn
  `LiveMascot(avatarId, …)` — blinking/eye-tracking, happy eyebrows, waving arm, gentle
  bounce. The avatar comes from the kid's profile: mobile sends `InterruptionCommand.avatarId`
  (default `"hero"`), TV forwards the `AVATAR_ID` extra through `QuizSession`. 12 variants
  (`hero, warrior, pig, panda, bear, bunny, tiger, monkey, cat, fox, dino, robot`) are defined
  by an `AvatarStyle` descriptor (colours + ear/hair/muzzle kinds) in MainActivity.kt.
- The idle screen (`else ->` branch, ~line 427) could show a lighter greeting ("Hi! Ready
  when you are 🚀") for when the kid walks past the TV.

## 4. Variants worth considering (with trade-offs)

| Variant | Pros | Cons | Verdict |
|---------|------|------|---------|
| **A. Personalized voice+visual greeting (recommended)** | Feels personal, works for non-readers, cheap to build | Needs the auto-dictation voice; name required | ✅ Do first |
| **B. Add a real mascot character** | Long-term engagement/return driver (strongest lever in kids' apps) | Artwork/asset cost, "doesn't look good" risk (illo rejected) — source real illustration | ✅ shipped: code-drawn `LiveMascot` on the completion screen with **12 selectable per-kid avatars** (Kid Profile → "Celebration Mascot") |
| **C. Time-of-day variants** | Alive/contextual | Trivial | ✅ bundle with A |
| **D. First-launch onboarding screen** | Guides parents/kids once | Blocking, unnecessary for remote-driven app | ❌ skip (not appropriate here) |

## 5. Suggested copy set

- "Good morning, Rohan! Ready for a quick game? 🌟"
- "Hi Rohan! Let's play!" / "Hello again, Rohan!"
- End beat (optionally spoken): "You did great, Rohan!" (ties into the existing
  `QuizResultsScreen` messages and the upcoming 4 s auto-close).

## 6. Sources

- KidNation — child profiles & personalization: https://www.kidnation.com/child-profiles-in-kids-apps-why-personalization-can-make-content-feel-more-age-aware/
- Raw.Studio — designing for children: https://raw.studio/blog/designing-for-children/
- Red C Mobile — LAPP (name personalization): https://www.red-c.co.uk/case-studies/lapp-the-library-in-your-lap/
- UX of EdTech / C. Tan — characters as UX (research citations): https://medium.com/ux-of-edtech/characters-are-ux-making-learning-apps-kids-can-connect-with-128e0330d57c
- Mara — Ango kids language app: https://mara.ink/case-studies/ango-kids-language-learning-app
- Android TV — first-time onboarding guidance: https://developer.android.com/training/tv/playback/leanback/onboarding
- Hellosaurus: https://www.mrdavenport.co/hellosaurus-app
- UX Magazine — UX design for children: https://uxmag.com/articles/ux-design-for-children-how-to-create-a-product-children-will-love
- Aparna K S — UI/UX design for children: https://www.aufaitux.com/blog/ui-ux-designing-for-children/

## 7. Status — what was actually built (2026-09-05)

krushnat clarified the greeting belongs at the **completion** moment (not quiz start), with the
family choosing the language from the mobile app (English default). Implemented end-to-end:

- **Mobile:** `KidQuizConfig.greetingLanguage` (default `"en"`) persisted per kid in
  `SessionManager`; parent picker ("Greeting message language") added to
  `QuizPresentationConfigCard` (StudyScreens); sent to the TV via
  `InterruptionCommand.greetingLanguage` in `StudyViewModel.startSession`.
- **TV:** `TvServerService` forwards `GREETING_LANGUAGE` intent extra → `MainActivity` state →
  `QuizSession` → new `QuizResultsScreen` (MainActivity.kt:1093):
  - Live, code-drawn mascot (`LiveMascot`): blinking + wandering eyes, waving hand, gentle bounce.
  - Message chosen per language in `CompletionMessages` (en / mr-IN / hi-IN today; English fallback),
    personalised with the kid's name.
  - Spoken via TTS in the parent-chosen language (`configureKidTts` re-applies locale per command),
    result sent to mobile immediately, **no buttons**, auto-closes after 4 s.
- Build verified: `./gradlew :tv:assembleDebug :mobile:assembleDebug` → BUILD SUCCESSFUL.

Open items: more languages on request (need script review); the kid-profile language could later
default the picker.

## 8. TV support verification (2026-09-05)

A parent can pick a language the TV's TTS engine can't speak, so the mobile app now verifies:

- **Picker-time (mobile):** after selecting a non-English language, the app probes the family TV
  (`SessionManager.lastTvIp` — the last TV a session was started on) with a new `TTS_CAP_CHECK`
  command; the TV answers via the existing JSON-line callback channel with its speakable locale
  tags. The app then shows a green "Your TV can speak {lang}." or a warning
  "Your TV can't speak {lang} yet — it will fall back to English", with a one-tap "Back to English".
- **Launch-time:** before sending a quiz whose greeting language is not English, `StudyViewModel`
  probes the target TV; if unsupported it pauses with a dialog — "Use English instead" /
  "Start anyway". This is the authoritative check for the exact TV (covers TVs that were off when
  the language was picked and scheduled/other flows).
- Transport: `StudyRepository.probeTtsLanguages(ip)` (mobile) ⇄ `TvServerService.respondTtsCapabilities`
  (TV); capabilities cached in `TtsCapabilities` (TV) and refreshed on TTS init.
  `TTS_CAP_CHECK` is never persisted as a lock and never shows UI.
- **Offline / no TV (offline-tolerant):** the language setting is always saved to the kid config the
  moment the parent picks it — connectivity never blocks saving. Verification is best-effort and
  non-blocking: no Wi-Fi (mobile not on LAN) ⇒ fail-fast "NOT_ON_WIFI" notice; TV unreachable ⇒
  "Couldn't reach your TV (setting still saved)"; both with a one-tap "Back to English". The probe
  and command sockets use explicit 2 s connect + 4 s reply timeouts so the UI never stalls. When the
  app later connects to the TV, the greeting is checked again at quiz start and English is the
  automatic fallback on the TV. This matches krushnat's requirement (2026-09-05): "just save the
  setting on mobile and it will execute when it connects to the TV with default settings."