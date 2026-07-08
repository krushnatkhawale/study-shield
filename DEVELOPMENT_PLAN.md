# Interrupter Project - Development Plan

**Current Status:** Implementing Feature 5: Parental Presets

## Feature 1: Audible Feedback System (COMPLETED)
- [x] Initialize `ToneGenerator` in `MainActivity`.
- [x] Play beeps for lock start, wrong answers, and success.

## Feature 2: Persistent Lock (COMPLETED)
- [x] Create `LockPersistenceManager` to handle SharedPreferences.
- [x] Updated `TvServerService` to restore locks on reboot.

## Feature 3: Network Auto-Discovery (NSD) (COMPLETED)
- [x] TV: Register `_interrupter._tcp` service.
- [x] Mobile: Implement robust discovery logic with resolution queue and MulticastLock.
- [x] Mobile: UI Spinner for device selection.

## Feature 4: [Onboarding] Add Splash Screen & Carousel (COMPLETED)
- [x] StudyShield branding implemented in Jetpack Compose.
- [x] Auto-advance logic (2.5 seconds).
- [x] 3-page Onboarding carousel with smooth transitions.
- [x] Persistent `isFirstLaunch` flag management.

## Feature 5: Parental Presets (CANCELLED)
- feature is cancelled, revert changes related to this.
