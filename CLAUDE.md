# CLAUDE.md

GestureControl: Android app that controls the phone with hand gestures seen by the front camera. On-device only (MediaPipe Hand Landmarker). See README.md for user-facing docs (Russian).

## Build environment

- JDK 17: `C:\Users\vladk\dev\jdk-17.0.20.1+1` (system `java` on PATH may be Java 8 — always set `JAVA_HOME`).
- Android SDK: `C:\Users\vladk\dev\android-sdk` (platform 35, build-tools 35.0.0). `local.properties` uses forward slashes.
- In Git Bash: `export JAVA_HOME="/c/Users/vladk/dev/jdk-17.0.20.1+1"; export PATH="$JAVA_HOME/bin:$PATH"`.

## Commands

```bash
./gradlew assembleDebug        # build APK (downloads the hand model into app/src/main/assets on first run)
./gradlew :gestures:test       # gesture logic unit tests (pure JVM, fast)
./gradlew :app:lintDebug       # Android lint; must have no errors
./gradlew installDebug         # install on a connected device
```

After each change: `assembleDebug` and `:gestures:test` must pass before committing.

## Architecture

- `:gestures` — **pure Kotlin/JVM module, no Android dependencies allowed.** All gesture and coordinate logic lives here so it can be unit-tested on recorded landmark sequences:
  - root package: `GestureEngine` (media gestures state machine), `SwipeDetector`, `StaticGestureClassifier` (poses + pinch ratio), `OneEuroFilter`, `PowerSaveThrottler`, `LandmarkCsv`, `GestureConfig`;
  - `mapping/`: `ScreenMapper` + `Calibration` (3-point affine frame→screen), `CalibrationCapture`, `Zone`/`ZoneAction`/`ZoneSet` (hit test), `CommandPlanner` (press/scroll → `TouchCommand`), `NormPoint`/`NormRect`;
  - `pointer/`: `PointerEngine` (cursor, pinch/dwell press, two-finger scroll), `CursorSmoother` (One Euro + dead zone), `ScrollDetector`, `PointerConfig`.
- `:app` packages:
  - `camera/` CameraX binding (front camera, ImageAnalysis RGBA, optional Preview). The camera provider is shared: unbind only own use cases, never `unbindAll()`.
  - `vision/` MediaPipe wrapper (VIDEO mode, CPU) and `HandAnalyzer` (rotate + mirror to user-view coordinates → landmarks → `GestureEngine` + optional `PointerEngine`). Everything runs on the single analysis thread.
  - `actions/` media keys via `AudioManager.dispatchMediaKeyEvent`, fixed gesture→action map in `ActionDispatcher`.
  - `service/` `GestureService` camera foreground service + shared `GestureServiceState`. Routes pointer output to `PointerBridge`; zones and calibration are updated live, other pointer settings rebind the camera.
  - `accessibility/` `ForegroundAppService`: foreground app (activities only) → `ForegroundAppTracker`; `CursorOverlay` (TYPE_ACCESSIBILITY_OVERLAY, non-touchable, attached only when needed); `GestureExecutor` (`dispatchGesture` with cancel/reject → hints, global actions); `SecureWindowProbe` (FLAG_SECURE via `takeScreenshotOfWindow`, API 34+, buffer closed unread); `PointerBridge` (in-process link to the service).
  - `data/` Room v2 (`selected_apps`, `zones`, auto-migration 1→2, schemas in `app/schemas`), DataStore settings incl. `PointerSettings` and calibration, installed app list.
  - `ui/` Compose: main, app selection, debug (preview + overlay + pointer info + CSV recorder), accessibility disclosure, zone list/editor, cursor settings, calibration wizard.
- DI: Hilt. Async: coroutines/Flow.

## Key rules

- Landmark coordinates in `:gestures` are "user view": upright, mirrored; x grows toward the user's right. The conversion happens only in `HandAnalyzer`. Screen coordinates are normalized [0, 1] of the real (full) screen; pixels appear only in `GestureExecutor`/`CursorOverlay`.
- Every threshold goes into `GestureConfig` / `PointerConfig` / `PowerSaveConfig`, never hard-coded in detectors. Never use landmark z as a trigger.
- Coordinate mapping, zones and hit testing stay pure and unit-tested.
- Android 14+: the camera FGS may only be started from a visible screen (`GestureService.start` from the main screen). The service is `START_NOT_STICKY` and never restarts itself from the background; it binds/unbinds the camera instead. In-app camera screens (debug, calibration) set `GestureServiceState.debugPreviewActive` so the service releases the camera.
- Accessibility failures (cancelled/rejected gesture, secure window, locked screen) show a rate-limited hint next to the cursor; never crash.
- Privacy: frames are never stored or sent. No network: `INTERNET`/`ACCESS_NETWORK_STATE` are removed in the manifest (MediaPipe adds them). The recorder writes landmark coordinates only. Keep `docs/privacy.md` and the disclosure screen in sync with any data or capability change.
- Code comments in English. All UI strings in `res/values/strings.xml` (English, default) and `res/values-ru/strings.xml` (Russian).
- Gesture tests: add real recordings to `gestures/src/test/resources/sequences/` with a `# expect: GESTURE,...` line; `RecordedSequenceTest` replays all of them.
- Small commits with clear messages; commit only after build and tests are green.

## Scope

- Stage 1 (done): swipe left/right, open palm hold, media actions, foreground service, app selection, accessibility-based gating, power saving.
- Stage 2 (done): cursor overlay, pinch/dwell press, per-app screen zones with actions, two-finger scroll, 3-point calibration, secure-window handling, accessibility disclosure. One zone set and one calibration per app (portrait), by the owner's decision.
- Stage 3 (not started): custom gestures and model training. Do not add unless asked.
- Personal, non-commercial app: Google Play policy constraints are not a blocker.
