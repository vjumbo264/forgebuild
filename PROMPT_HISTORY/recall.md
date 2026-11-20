# PROMPT HISTORY — recall (append-only)

## 2026-11-20 — Operator build prompt (NEW APP)

PROJECT RECALL — Android Time Awareness App

You are an expert Android developer. Your task is to build a native Android application called RECALL.

RECALL is a time-awareness application designed to help users who struggle with time blindness, ADHD-related time awareness difficulties, and maintaining awareness of the passage of time.

This is NOT a conventional Pomodoro application. Its primary purpose is to periodically remind users that time is passing through configurable, distinctive audio signals synchronized with the actual clock.

1. CRITICAL: INSPECT TASKFLOW FIRST

I have an existing Android application called TaskFlow.

Before implementing anything, thoroughly inspect its source code, architecture, and Android background-execution mechanisms.

TaskFlow already implements mechanisms that allow it to remain operational in the background.

Your first responsibility is to identify and reuse those mechanisms in RECALL.

Specifically:

- Identify how TaskFlow maintains background execution.
- Inspect its foreground services, notification channels, receivers, WorkManager usage, AlarmManager implementation, boot receivers, permissions, battery optimization handling, and lifecycle management.
- Identify its audio playback and notification mechanisms, if any.
- Reuse proven implementations rather than independently recreating them.
- Port relevant code and dependencies into RECALL, adapting package names, resources, permissions, and application-specific logic.
- Preserve TaskFlow's existing functionality. Do not modify or break TaskFlow.
- Do not blindly copy unrelated components.
- Document which mechanisms were reused and why.

RECALL must be a separate, independently installable Android application with its own package identity, storage, settings, and notification channels.

If TaskFlow uses a mechanism that is unreliable or incompatible with RECALL's requirements, explain the issue and implement the necessary improvement.

Do not claim that background execution is reliable until it has been tested.

2. CORE FUNCTIONALITY: GLOBAL TIME SYNCHRONIZATION

This is the most important requirement.

RECALL must follow the actual system clock, NOT start counting intervals from the moment the user presses Start.

For example, if the user activates RECALL at 6:47 PM:

- 6:50 PM: Five-minute chime.
- 6:55 PM: Five-minute chime.
- 7:00 PM: Hourly chime.
- 7:05 PM: Five-minute chime.
- 7:10 PM: Five-minute chime.
- 7:15 PM: Five-minute chime.
- 7:30 PM: Thirty-minute chime.

The intervals must always remain synchronized with real-world clock boundaries.

Implementation requirements:

- Calculate upcoming events using the actual system time.
- Schedule the next event at the appropriate clock boundary.
- Recalculate after every event.
- Handle application restarts and device reboots.
- Handle system clock changes and timezone changes.
- Prevent duplicate alarms.
- Recover intelligently after missed events.
- Never play a backlog of missed chimes after the application resumes.

Use appropriate Android scheduling mechanisms, prioritizing exact AlarmManager alarms where permitted. Account for Android version-specific exact-alarm permissions and restrictions.

Do not implement this as a simple repeating timer or an infinite five-minute loop.

3. CONFIGURABLE INTERVALS

Users must be able to configure multiple independent recurring intervals.

Default configuration:

- Every 5 minutes.
- Every 30 minutes.
- Every 60 minutes.

Requirements:

- Add custom intervals.
- Edit existing intervals.
- Enable and disable individual intervals.
- Delete intervals.
- Assign a unique sound to each interval.
- Configure independent volume levels.

Each interval must align with the actual clock.

For example, a 15-minute interval should trigger at :00, :15, :30 and :45, not 15 minutes after activation.

Event priority

When multiple intervals coincide, play only the highest-priority event.

Default priority:

1. Hourly event.
2. Thirty-minute event.
3. Other custom intervals.
4. Five-minute event.

The priority should be configurable or logically resolved using interval importance.

4. AUDIO SYSTEM

Audio is a core component, not an optional feature.

I want distinctive, sharp, clearly audible notification sounds.

Requirements:

- Different sound selections for different intervals.
- Multiple built-in sound options.
- Ability to preview sounds before selecting them.
- Independent volume controls for each interval.
- Persistent user sound preferences.
- Reliable playback when the screen is off.
- Playback through the appropriate Android alarm audio pipeline.
- Proper audio focus and interruption handling.

Investigate Android AudioAttributes.USAGE_ALARM and the appropriate alarm-volume routing.

The application's volume controls must NOT change the device's actual alarm volume.

Investigate whether independent volume control can be achieved through application-managed audio gain while using the alarm audio usage category. Do not falsely represent application-level volume as a separate system volume stream.

Headphone and earpiece behavior must be tested. Do not assume Android will automatically play simultaneously through both the speaker and connected headphones.

Provide a test button for every sound and a global test function.

The sounds should be distinguishable even without looking at the phone.

5. BACKGROUND RELIABILITY

RECALL must function when:

- The application is minimized.
- The screen is locked.
- The phone is idle.
- The user switches to another application.
- The application process is terminated by Android.
- The device is restarted.

Reuse TaskFlow's proven background architecture wherever appropriate.

Requirements:

- Persistent foreground notification when necessary.
- Appropriate foreground service type and permissions.
- Alarm scheduling and rescheduling.
- Boot-completed receiver.
- Time-change and timezone-change handling.
- Notification permission handling.
- Exact alarm permission handling.
- Battery optimization guidance where necessary.

Do not use unnecessary background services.

Prioritize battery efficiency and Android platform compliance.

Test on actual Android devices, particularly devices with aggressive background process management.

6. USER INTERFACE

Keep V1 deliberately minimal.

The main screen should display:

- Application name: RECALL.
- Current local time.
- Whether the system is active.
- Time remaining until the next chime.
- The type of upcoming chime.
- Enabled intervals.
- Start, pause, resume and stop controls.
- Sound settings access.

The interface should be clean, modern, minimal and easy to understand.

Avoid unnecessary navigation, dashboards, productivity statistics, complicated animations, and excessive settings.

The user should understand the application's state immediately upon opening it.

Settings can be accessed through a simple button or icon on the main screen.

7. NOTIFICATIONS

Implement a persistent notification showing that RECALL is active.

The notification should:

- Indicate that time awareness is running.
- Show the next scheduled chime where practical.
- Provide pause and resume actions.
- Avoid generating unnecessary notification clutter.

Optional heads-up notifications can accompany audio events.

Do not implement an always-visible floating window in V1. Keep the architecture extensible enough to support one later.

8. ARCHITECTURE AND CODE QUALITY

- Build a native Android application.
- Prefer Kotlin.
- Follow TaskFlow's existing architecture where sensible.
- Keep RECALL independent from TaskFlow at runtime.
- Separate scheduling, audio playback, settings, and UI logic.
- Persist user configurations.
- Handle Android lifecycle events correctly.
- Avoid unnecessary dependencies.
- Include meaningful error handling and logging.

Do not create a superficial prototype that only works while the application is open.

9. TESTING REQUIREMENTS

Test the following scenarios:

1. Activate RECALL at 6:47 PM and verify the first event occurs at 6:50 PM.
2. Activate RECALL at 6:55 PM and verify the next event is the hourly chime at 7:00 PM.
3. Verify the 30-minute event overrides the five-minute event at coinciding timestamps.
4. Verify custom intervals remain synchronized with the clock.
5. Lock the screen and verify scheduled sounds play.
6. Minimize the application and verify scheduled sounds play.
7. Restart the device and verify scheduling is restored.
8. Change the device's system time and verify scheduling is recalculated.
9. Disconnect and reconnect headphones and test audio routing.
10. Change RECALL's volume and verify the device's actual alarm volume remains unchanged.
11. Pause and resume the application and verify scheduling behaves correctly.
12. Verify that missed events do not produce multiple delayed sounds.

10. DEVELOPMENT WORKFLOW

Work in this order:

Phase 1 — TaskFlow inspection

- Inspect the entire relevant TaskFlow architecture.
- Identify reusable background and notification mechanisms.
- Report the findings and implementation plan.

Phase 2 — Core application

- Create RECALL as an independent application.
- Implement global clock synchronization.
- Implement configurable interval scheduling.

Phase 3 — Audio

- Implement distinctive sounds.
- Implement separate application-level volume settings.
- Integrate the Android alarm audio pipeline.

Phase 4 — Background execution

- Port and adapt TaskFlow's relevant background mechanisms.
- Implement recovery, reboot handling, and scheduling restoration.

Phase 5 — Interface

- Build the minimal main screen and settings interface.

Phase 6 — Testing

- Test the requirements above.
- Fix discovered issues.
- Document limitations and permissions.

Do not stop after creating the interface. The scheduling and background reliability are the actual product.

Maintain a BUILD_PROGRESS.md file documenting completed work, current tasks, discovered issues, and remaining tasks.

FINAL PRODUCT PRINCIPLE

RECALL should not demand the user's attention.

It should quietly monitor the passage of time and periodically bring the user back to awareness.

The experience should be simple:

Time passes. RECALL reminds you. You regain awareness.

Build that experience reliably.

## 2026-11-20 — Build agent note

Slug 'recall' chosen (short kebab-case name matching PROJECT RECALL; verified absent in apps/). Copied engine/ full content into apps/recall/ as starting point and seeded BUILD_STATE.json.
