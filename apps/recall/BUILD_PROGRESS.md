# PROJECT RECALL — Build Progress & Architecture Log

## Phase 1 — TaskFlow Inspection Findings

Inspected `apps/taskflow/` thoroughly before designing RECALL:

1. **Foreground Service (`DueForegroundService.kt`, `AndroidManifest.xml`)**:
   - TaskFlow declares `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE` with `android:foregroundServiceType="specialUse"` and `PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.
   - `startForegroundSafely()` catches `ForegroundServiceStartNotAllowedException` / platform start errors, uses `ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE` on API 34+, and returns `START_STICKY`.
   - **RECALL adaptation & improvement**:
     - Reused the `specialUse` foreground service declaration, `startForegroundSafely()`, and `ContextCompat.startForegroundService` guard in `RecallForegroundService`.
     - Removed TaskFlow's 1-second polling loop (`while (isActive) delay(1000)`): RECALL performs ZERO background polling when the screen is off. `RecallForegroundService` runs only while RECALL is **Active** or **Paused**, stops itself (`stopSelf()`) when **Stopped**, and updates its notification strictly on schedule/state events.

2. **Exact Alarm Scheduling (`ReminderScheduler.kt`, `ReminderReceiver.kt`)**:
   - TaskFlow uses `AlarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` gated by `am.canScheduleExactAlarms()`, with `PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE`.
   - TaskFlow's `ReminderReceiver` uses `goAsync()` + `CoroutineScope(Dispatchers.IO)` and a stale-alarm guard.
   - **RECALL adaptation & improvement**:
     - `RecallScheduler` arms a single deterministic `PendingIntent` (`REQUEST_CODE_BOUNDARY = 9001`) for the earliest upcoming wall-clock boundary computed by `ClockSyncEngine.nextGlobalEvent(...)`, cancelling any prior alarm first so duplicates cannot occur.
     - Supports `setExactAndAllowWhileIdle(RTC_WAKEUP, ...)` and an optional high-precision `setAlarmClock(...)` mode for OEM devices with aggressive Doze throttling.
     - `RecallAlarmReceiver` acquires a bounded `PowerManager.PARTIAL_WAKE_LOCK` (15s timeout, released in `finally`) across audio playback and rescheduling.
     - `ClockSyncEngine.evaluateAlarmTrigger(...)` enforces `EARLY_TOLERANCE_MS = 2_000L`, `MISSED_GRACE_MS = 90_000L`, and `lastFiredBoundaryMs` deduplication so missed events NEVER replay a backlog of chimes.

3. **Reboot, Package Update & Time-Change Recovery (`BootReceiver.kt`)**:
   - TaskFlow handles `ACTION_BOOT_COMPLETED` via `goAsync()`.
   - **RECALL improvement (`RecallSystemReceiver`)**:
     - Handles `ACTION_BOOT_COMPLETED`, `ACTION_MY_PACKAGE_REPLACED`, `ACTION_TIME_SET`, `ACTION_TIMEZONE_CHANGED`, and `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
     - Resets `lastFiredBoundaryMs` on manual time/timezone changes so clock rollbacks reschedule cleanly without false duplicate suppression.

4. **Notification Channels (`NotificationHub.kt`)**:
   - TaskFlow separates `CHANNEL_FOREGROUND` (`IMPORTANCE_LOW`, no badge) from alert channels (`IMPORTANCE_HIGH`).
   - **RECALL adaptation (`RecallNotificationHub`)**:
     - `recall_service` (`IMPORTANCE_LOW`, silent, no badge): persistent status showing Active/Paused state, next scheduled chime time & sound, and Pause/Resume/Stop actions.
     - `recall_chime_alerts` (`IMPORTANCE_HIGH`, `setSound(null, null)`, vibration disabled): optional visual heads-up alerts that never collide with `RecallAudioEngine`'s `USAGE_ALARM` audio.

5. **Audio Synthesis (`TimerSounds.kt`)**:
   - TaskFlow synthesizes PCM16 audio via `AudioTrack` (`USAGE_ALARM`, `CONTENT_TYPE_SONIFICATION`) with zero bundled MP3/WAV assets.
   - **RECALL adaptation & improvement (`RecallSoundSynth`, `RecallAudioEngine`)**:
     - Upgraded to 44.1 kHz mono PCM16 synthesis with 7 distinctive chimes: Crystal Bell, Double Strike, Chime Triad, Sonar Ping, Woodblock Tap, Warm Pulse, and Four-Note Cue.
     - Independent per-interval volume (`0f..1f`) uses application-managed perceptual gain scaling (`volume.pow(1.6f)`) inside the PCM buffer — NEVER changes the device's system Alarm volume stream.
     - Requests `AudioFocusRequest` (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, `USAGE_ALARM`) and abandons focus in `finally`.
     - Exposes connected output devices (`AudioManager.GET_DEVICES_OUTPUTS`) in Settings alongside per-sound preview and Global Test controls.

6. **Permissions & Diagnostics (`Permissions.kt`, `CrashLogger.kt`, `OnboardingScreen.kt`, `SettingsScreen.kt`)**:
   - Reused `PermissionWiring` (`isBatteryOptimizationExempt`, `requestBatteryExemption`, `canScheduleExactAlarms`, `requestExactAlarm`) and adapted `CrashLogger` into `RecallDiagnostics` (uncaught crash handler + structured scheduling/recovery event log).

---

## Completed Work
- **Step 1–2**: Created `apps/recall/` from `engine/`, configured package identity (`com.forgebuild.recall`, app name `RECALL`), documented Phase 1 findings.
- **Step 3**: Generated geometric clock-chime adaptive icon layers + monochrome themed layer (`#293556` background).
- **Step 4**: Verified repo-level signing secrets exist on `vjumbo264/forgebuild`.
- **Step 5**: Implemented `RecallModels`, `RecallJsonCodec`, `ClockSyncEngine`, `RecallRepository`, `ClockSyncEngineTest` (10 JVM tests), and `recall-tests.yml` CI workflow.
- **Step 6**: Implemented `RecallSoundSynth`, `RecallAudioEngine`, `RecallDiagnostics`, and `RecallSoundSynthTest`.
- **Step 7**: Implemented `RecallNotificationHub`, `RecallScheduler`, `RecallController`, `RecallForegroundService`, `RecallAlarmReceiver`, `RecallActionReceiver`, `RecallSystemReceiver`, `RecallApp`, and `AndroidManifest.xml`.
- **Step 8**: Implemented Material 3 Expressive UI (`MainScreen`, `SettingsScreen`, `OnboardingScreen`, `RecallViewModel`, `MainActivity`, bundled Material Symbols in `EngineIcons`).

## Remaining Tasks
- **Step 9**: Verify CI unit tests + debug APK pass, dispatch `release.yml` (`app_path=apps/recall`), verify `recall-v1` release + APK asset live.
- **Step 10**: Set `build_complete: true` in `apps/recall/BUILD_STATE.json`.

## Verification Coverage & Device Testing Notes
- **Automated JVM Unit Tests** (`ClockSyncEngineTest`, `RecallSoundSynthTest`):
  - Verify Scenarios 1–4 (6:47 -> 6:50, 6:55 -> 7:00 Hourly override, 7:30 30m override, custom 15m clock alignment), missed-event backlog suppression, duplicate/early guards, paused/stopped gating, non-hour timezone offsets (`Asia/Kolkata`), JSON codec + repository CRUD, and 7-sound waveform synthesis + perceptual gain scaling.
- **On-Device Physical Testing Checklist** (requires physical Android device with APK installed):
  - Screen locked / Doze delivery across OEM power managers (Xiaomi/Samsung/Pixel — enable Battery Exemption & High-Precision Alarm Clock mode in Settings if OEM delays Doze alarms).
  - Wired / Bluetooth headphone plug/unplug routing verification via Settings -> Audio Pipeline & Routing -> Global Test.
  - Device reboot (`BOOT_COMPLETED`) and manual time/timezone changes via Settings -> Event & Diagnostics Log.
