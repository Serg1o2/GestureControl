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

- `:gestures` — **pure Kotlin/JVM module, no Android dependencies allowed.** All gesture logic lives here so it can be unit-tested on recorded landmark sequences: `GestureEngine` (state machine), `SwipeDetector`, `StaticGestureClassifier`, `OneEuroFilter`, `PowerSaveThrottler`, `LandmarkCsv`, `GestureConfig`.
- `:app` packages:
  - `camera/` CameraX binding (front camera, ImageAnalysis RGBA, optional Preview). The camera provider is shared: unbind only own use cases, never `unbindAll()`.
  - `vision/` MediaPipe wrapper (VIDEO mode, CPU) and `HandAnalyzer` (rotate + mirror to user-view coordinates → landmarks → engine). Everything runs on the single analysis thread.
  - `actions/` `Action` interface, media keys via `AudioManager.dispatchMediaKeyEvent`, fixed gesture→action map in `ActionDispatcher`.
  - `service/` `GestureService` camera foreground service + shared `GestureServiceState`.
  - `accessibility/` `ForegroundAppService` reports the foreground app (activities only) to `ForegroundAppTracker`.
  - `data/` Room (`selected_apps`), DataStore settings, installed app list.
  - `ui/` Compose screens: main, app selection, debug (preview + 21-point overlay + CSV recorder).
- DI: Hilt. Async: coroutines/Flow.

## Key rules

- Landmark coordinates in `:gestures` are "user view": upright, mirrored; x grows toward the user's right. The conversion happens only in `HandAnalyzer`.
- Every gesture threshold (distance, velocity, time) goes into `GestureConfig` / `PowerSaveConfig`, never hard-coded in detectors.
- Android 14+: the camera FGS may only be started from a visible screen (`GestureService.start` from the main screen). The service is `START_NOT_STICKY` and never restarts itself from the background; it binds/unbinds the camera instead.
- Privacy: frames are never stored or sent. No network: `INTERNET`/`ACCESS_NETWORK_STATE` are removed in the manifest (MediaPipe adds them). The recorder writes landmark coordinates only. Keep `docs/privacy.md` in sync with any data change.
- Code comments in English. All UI strings in `res/values/strings.xml` (English, default) and `res/values-ru/strings.xml` (Russian).
- Gesture tests: add real recordings to `gestures/src/test/resources/sequences/` with a `# expect: GESTURE,...` line; `RecordedSequenceTest` replays all of them.
- Small commits with clear messages.

## Scope

Stage 1 (done): swipe left/right, open palm hold, media actions, foreground service, app selection, accessibility-based gating, power saving. Do not add stage-2 features (tap zones / dispatchGesture, custom gestures, gesture assignment UI) unless asked.
