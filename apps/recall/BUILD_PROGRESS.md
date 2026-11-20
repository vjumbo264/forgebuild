# PROJECT RECALL — Build Progress & Architecture Log

## Phase 1 — TaskFlow Inspection Findings

Inspected `apps/taskflow/` thoroughly before designing RECALL:

1. **Foreground Service (`DueForegroundService.kt`, `AndroidManifest.xml`)**:
   - TaskFlow declares `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` with `android:foregroundServiceType="specialUse"` and `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.
   - `startForegroundSafely()` catches `ForegroundServiceStartNotAllowedException` / platform start errors, uses `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 34+, and returns `START_STICKY`.
   - **RECALL adaptation & improvement**:
     - Reused the `specialUse` foreground service declaration, `startForegroundSafely()`, and `ContextCompat.startForegroundService` guard.
     - Removed TaskFlow's 1-second Room polling loop (`while (isActive) delay(1000)`): RECALL does NOT wake the CPU every second when the screen is off. Instead, `RecallForegroundService` only runs while RECALL is **Active** (or **Paused** with a quick-resume notification action) and stops completely (`stopSelf()`) when **Stopped**, updating its notification strictly on schedule/state events.

2. **Exact Alarm Scheduling (`ReminderScheduler.kt`, `ReminderReceiver.kt`)**:
   - TaskFlow uses `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` gated by `am.canScheduleExactAlarms()`, with `PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE`.
   - TaskFlow's `ReminderReceiver` uses `goAsync()` + `CoroutineScope(Dispatchers.IO)` and includes a stale-alarm guard on timer completion (`TimerEngine.stateOf(...) == FINISHED`).
   - **RECALL adaptation & improvement**:
     - Android Doze throttles `setExactAndAllowWhileIdle()` to ~9 minutes when the app is NOT battery-exempt and has no active foreground service. RECALL's default 5-minute chime relies on: (a) active `RecallForegroundService`, (b) explicit user-guided `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, (c) `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM`, and (d) optional `AlarmManager.setAlarmClock()` high-precision mode when exact-alarm permission is granted.
     - Single deterministic `PendingIntent` requestCode for the next global clock boundary prevents duplicate alarms; every fire cancels/replaces and recalculates from real-world `System.currentTimeMillis()`.
     - Explicit stale/missed-event guard (`MISSED_GRACE_MS = 90_000L`) + `lastFiredBoundaryMs` deduplication ensures RECALL **never** plays a backlog of missed chimes.
     - Holds a bounded `PowerManager.PARTIAL_WAKE_LOCK` across chime playback and rescheduling when triggered with the screen off.

3. **Reboot, Package Update & Time-Change Recovery (`BootReceiver.kt`)**:
   - TaskFlow handles `ACTION_BOOT_COMPLETED` via `goAsync()`.
   - **RECALL improvement**:
     - Handles `ACTION_BOOT_COMPLETED`, `ACTION_MY_PACKAGE_REPLACED`, `ACTION_TIME_CHANGED` (`TIME_SET`), `ACTION_TIMEZONE_CHANGED`, and `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`, cancelling stale alarms and recalculating the next future clock boundary without playing missed chimes.

4. **Notification Channels (`NotificationHub.kt`)**:
   - TaskFlow separates `CHANNEL_FOREGROUND` (`IMPORTANCE_LOW`, `setShowBadge(false)`) from alert channels (`IMPORTANCE_HIGH`).
   - **RECALL adaptation**:
     - `CHANNEL_FOREGROUND` (`IMPORTANCE_LOW`, silent, no badge): persistent status showing RECALL state, next scheduled chime time & type, and Pause/Resume + Stop action buttons.
     - `CHANNEL_CHIME` (`IMPORTANCE_HIGH`, notification sound disabled — `setSound(null, null)`): optional heads-up visual chime alert that never collides with RECALL's `USAGE_ALARM` audio pipeline.

5. **Audio Synthesis (`TimerSounds.kt`)**:
   - TaskFlow synthesizes PCM16 mono audio in Kotlin via `AudioTrack` using `AudioAttributes.USAGE_ALARM` + `CONTENT_TYPE_SONIFICATION` with zero bundled MP3/WAV bloat.
   - **RECALL adaptation & improvement**:
     - Ported and expanded into `RecallAudioEngine` (44.1 kHz PCM16) with 7 distinctive, sharp chimes (Crystal Bell, Double Strike, Soft Pulse, Chime Triad, Sonar Ping, Metronome Click, Westminster Cue).
     - Independent per-interval volume (`0.0f..1.0f`) applied via application-managed perceptual gain scaling + `AudioTrack.setVolume(...)` — NEVER modifies Android's system `STREAM_ALARM` volume.
     - Requests `AudioFocusRequest` (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) with `USAGE_ALARM` and releases focus in `finally`.
     - Queries `AudioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)` to surface active audio output route (speaker, wired headset, Bluetooth) cleanly in the UI without assuming simultaneous speaker+headphone playback.

6. **Permissions & Diagnostics (`Permissions.kt`, `CrashLogger.kt`, `OnboardingScreen.kt`, `SettingsScreen.kt`)**:
   - Reused TaskFlow's `PermissionWiring` extensions (`isBatteryOptimizationExempt`, `requestBatteryExemption`, `canScheduleExactAlarms`, `requestExactAlarm`) and `CrashLogger` rolling diagnostics log (expanded with `RecallDiagnostics` event entries for alarm schedule/fire/skip/recovery verification).

---

## Completed Work
- Phase 1: TaskFlow inspection completed and documented.
- Step 1: Created `apps/recall/` from `engine/`, seeded `PROMPT_HISTORY/recall.md` and `BUILD_STATE.json`.
- Step 2: Set package identity (`com.forgebuild.recall`, app name `RECALL`), fully-qualified launcher activity, and created `BUILD_PROGRESS.md`.

## Current Tasks
- Step 3: Generate RECALL adaptive icon assets via `tools/make_adaptive_icon.py`.

## Remaining Tasks
- Step 4: Record confirmation of repo signing secrets.
- Step 5: Core clock-boundary synchronization, interval model, priority resolver, persistence, and unit tests.
- Step 6: Audio synthesis & `USAGE_ALARM` engine.
- Step 7: Foreground service, exact alarm scheduler, WakeLock receiver, reboot/time-change receivers, notifications.
- Step 8: Minimal Material 3 Expressive UI (MainScreen, SettingsScreen, OnboardingScreen) + local/unit verification.
- Step 9–10: Release `recall-v1` via GitHub Actions and verify live.

## Discovered Issues & Limitations
- Note: Device/emulator hardware testing (lock-screen Doze across OEMs, wired/BT headphone routing) requires APK installation on a physical Android device; unit tests will verify all clock-boundary math, priority rules, missed-event suppression, and state transitions in CI, and the app includes a built-in Reliability & Event Log screen for on-device verification.
