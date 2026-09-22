# PROMPT HISTORY — taskflow

## 2026-09-22 — Operator instruction (new app build)
Build "TaskFlow" (working title) — native Android to-do app with AI agent, Kotlin + Jetpack Compose, Material 3 Expressive.
Core: Room-backed Task model (title, fractional-rank priority, optional fixed time, recurrence none/daily/weekly/monthly/yearly, free-text info field, nullable parent for infinitely nestable sub-tasks, completion state, timestamps). Fractional/lexo-rank priority (insert between neighbors, background rebalance), drag-and-drop reorder, always sorted priority-first. Fixed-time tasks: AlarmManager exact alarms + foreground service with honest explanation (no unkillable background promise), due-now pinning + distinct styling, system + in-app alerts. Sub-tasks unlimited nesting, recursive UI. Info field hidden behind eye icon (read-only quick-peek bottom sheet; edited via edit screen). Full edit screen for all fields. AI agent via Gemini Flash Lite, multiple API keys with failover (encrypted on-device storage), mic FAB chat/voice panel, function-calling tools mirroring all task CRUD + relational ops, open-ended instruction inference, confirmations in chat + system notification. Per-type notification toggles (due, overdue, daily agenda, API-keys-failed, agent confirmation, approaching deadline [off], recurring instance [off]). Screens: main list, task detail/edit, AI chat panel, settings (keys, notifications, permissions, theme), onboarding permission flow. M3 Expressive discipline throughout. Non-goals: no device admin, no unkillable-background promises, keys local-only.
Process: slug chosen by AI (chose "taskflow"), engine copied as starting point, one feature per commit/push, release via release.yml app_path=apps/taskflow, tag taskflow-v1, verify release live.

## 2026-09-22 — Revision Pass 2 (Fixes + New Requirements)

# TaskFlow — Revision Pass 2 (Fixes + New Requirements)

This is a follow-up pass on an existing app. Below is the full current intended spec, with fixes and new requirements from testing folded in. Treat this as the target state to build toward — fix what's broken, add what's missing, and do the full UI redo described below.

## Core Data Model
- **Task**: title, priority rank (see Priority System), duration (see Duration — mandatory), fixed time (optional datetime), recurrence rule (optional), info/notes (free text, hidden by default — see Task Info), parent task ID (nullable, for sub-tasks — infinitely nestable), completion state, completed-at timestamp, created/updated timestamps.
- **Recurrence**: none | daily | weekly (specific weekdays) | monthly | yearly. Recurring instances regenerate automatically after completion or at day rollover.

## Duration (NEW — mandatory field)
- Every task must have a duration (how long it will take) set before it can be created — this is a required field, not optional.
- The app tracks total time already allocated for the current day. When creating or editing a task's time/duration, if the remaining time left in the day is less than the task's duration, block creation and tell the user there isn't enough time left in the day.
- Show "time remaining today" prominently on the main screen (e.g. near the top) so the user always knows how much of their day is still unallocated.
- When the AI agent creates a task and the user didn't specify a duration, the agent must estimate and assign a reasonable duration itself — duration is never left blank, whether set by the user or inferred by the AI.

## Priority System
Priority is a background-only ranking mechanism — no numbers, decimals, or ranks are ever shown to the user in the UI. Internally use fractional/lexo-rank ordering so any task can be inserted between any two others without renumbering the whole list; rebalance silently in the background as needed. User-facing interactions:
- Drag-and-drop reordering in the list.
- Voice/text AI command: "put this between Task A and Task B" → agent computes and applies the correct rank.
Task list is sorted by priority rank, highest first — but the number itself stays invisible.

## Fixed-Time Tasks & Reminders
- Tasks can have a fixed time (including recurring fixed times, e.g. daily at 5pm).
- Reminders must actually fire reliably at the exact scheduled time — this is currently broken/missing and needs to work properly, including surviving Doze/battery-saving restrictions through whatever legitimate permission flow the OS provides.
- When a fixed-time task's time is approaching/arrives: task moves to the top of the list regardless of priority rank, with a visually distinct "due now" treatment (color + icon + subtle animation), and stays pinned there until completed or the next day's rollover.
- Trigger both a system notification and an in-app visual alert at the fixed time.

## Persistent Background Notification (FIX — currently missing)
- There must be a persistent, low-priority notification indicating the app is active/running in the background, matching the standard pattern other apps use to stay alive for reliable reminders. This is currently missing and needs to be added back.
- The background-run permission request is currently non-functional — clicking to grant "run in background" does nothing. This needs to actually trigger the OS-level prompt/dialog asking the user to allow the app to run in the background, the way it correctly does in other apps. Fix this flow end to end.

## Time Zone & Clock Awareness (NEW)
- The app must have access to the device's current time and time zone at all times, and use it as the single source of truth for scheduling, "time remaining today," and due/overdue logic.
- The AI agent must also have access to the current date/time/time zone when reasoning about requests (e.g. "what day is it," "how much time is left today," relative scheduling like "this week").

## Sub-tasks
- Any task can have sub-tasks; sub-tasks can have their own sub-tasks (unlimited nesting).
- Tapping into a task shows its sub-tasks list, each with the same feature set (priority, duration, info, optional fixed time, etc.) — recursive UI, not a special-cased single level.

## Task Info Field
- Free-text field per task/sub-task for notes on how the task should be done.
- Hidden from the task list/row by default. Shown via a small "eye" icon on the task — tapping it opens the info in a quick-peek view (e.g. bottom sheet/dialog) rather than displaying the text inline.
- Info is edited only from the task's edit screen, not from the eye-icon peek view.

## Task Type Color-Coding (NEW)
Give each task a distinct visual treatment (color and/or label — whichever reads more cleanly) based on its type, so the kind of task is identifiable at a glance:
- Normal task, no fixed time, not recurring.
- Task with a fixed time, not recurring.
- Recurring task with a fixed time.
- Recurring task without a fixed time.
Keep these visually distinct but consistent with the overall Material color system rather than clashing with it.

## Completed Tasks (NEW/CHANGED)
- Completing a task should not just strike it through in place. It should move out of the active list into a separate "Completed" area/view.
- Completed tasks auto-delete after a configurable retention period (default a sensible short window, e.g. a week or two — expose this in Settings) so the completed list doesn't grow indefinitely.

## Time-Range Views (NEW)
- Beyond "today," the user needs to view tasks by week, month, and year — e.g. via a calendar picker or a segmented view switcher on the main screen.
- Recurring tasks must NOT be expanded/listed as repeated individual entries across these ranges (that would flood the view). Show the recurring task once, appropriately represented as recurring, not duplicated per occurrence.

## Editing
All task properties (title, priority, duration, time, recurrence, info, color/type, parent) editable after creation via a task detail/edit screen.

## AI Agent
- Settings: user can add multiple Gemini API keys; app fails over to the next key automatically if one fails/rate-limits. Uses the latest lightweight Gemini model suited for fast agentic use.
- Microphone button (persistent, easily accessible) is currently non-functional — clicking it does nothing. This needs to be fixed so it actually opens the voice input flow.
- Mic recording state needs a live waveform/audio-spectrum animation while recording (the familiar voice-note-style animation used by WhatsApp and similar chat apps) — not a static or missing indicator.
- Chat/voice interface lets the user speak or type natural language commands.
- Full CRUD + relational commands: create tasks, edit any field, change priority (including relative placement "between X and Y"), add/edit/delete sub-tasks at any depth, mark complete, etc. — the agent must have access to every action a user can do manually.
- The agent must handle open-ended, loosely-specified instructions and infer sensible defaults rather than requiring exact parameters for everything, e.g.:
  - "Set this to only happen on Tuesdays" → maps to weekly recurrence with Tuesday selected.
  - "Create three tasks I want to do this week but I don't care when — scatter them across the week" → creates multiple tasks, spreads them across distinct days/times, assigns reasonable durations and priority ranks, without demanding the user specify every detail.
  - The agent always has current date/time/time zone available for this kind of reasoning.
- After completing an action, the agent responds via:
  1. An in-app confirmation message in the chat interface describing what it did.
  2. A system notification with the same summary.

## Notifications
- **Fixed-time task due** — at-time alert (system + in-app "due now" state), default ON.
- **Overdue task** — a fixed-time task not completed at its due time, default ON.
- **Daily agenda summary** — morning notification listing today's fixed-time tasks and top-priority items, default ON.
- **All Gemini API keys failed** — alert when every fallback key has failed, default ON.
- **AI agent action confirmation** — summary of what the agent just did, default ON.
- **Approaching deadline** — optional lead-time warning before a fixed-time task, default OFF, configurable lead time.
- **Recurring instance generated** — low-priority ping when a new recurring instance is created, default OFF.
- **Persistent "app is running" notification** — always present while the background service is active (see Persistent Background Notification above); this is not user-togglable the way the others are, since it's required for reliability.

All non-persistent notification types toggleable individually in Settings > Notifications.

## Complete UI Redesign (NEW — top priority)
The current UI is not acceptable and needs to be fully reimagined, not incrementally patched:
- Full expressive design language throughout — rounded/organic "squiggly" shapes on buttons and interactive elements, not sharp/flat defaults.
- Rich, fluid motion: spring-based transitions, satisfying micro-animations on task completion, priority changes, the mic recording state, and the "due now" pinning behavior.
- Should look and feel premium and polished — on par with recent well-designed Google first-party apps, taking full advantage of the latest expressive Material design system rather than a generic/default look.
- Every screen and component should be reconsidered as part of this redesign, not just colors/theming — layout, spacing, iconography, and interaction patterns all included.

## Screens (adjust as needed)
1. **Main task list** — priority-sorted, due-now tasks pinned/highlighted, time-remaining-today indicator, time-range view switcher (today/week/month/year), FAB for add + mic.
2. **Task detail/edit** — all fields including duration and color/type, sub-task list, nested navigation.
3. **AI chat/voice panel** — conversation history, mic with live waveform animation, text input.
4. **Completed tasks view** — separate from the active list, shows retention/auto-delete behavior.
5. **Settings** — Gemini API key management, notification toggles, completed-task retention period, background/battery permission setup, theme.
6. **Onboarding/permissions flow** — requests notification, exact-alarm/reminder, and background-run permissions with plain-language explanations, and must actually trigger the real OS prompts (currently broken for background permission).

---

## 2026-09-22 — TaskFlow — Revision Pass 3 (Fixes)

This is a follow-up pass on the existing app (previous revision already implemented). These are new fixes only.

### Voice Input — Send Raw Audio to Gemini (FIX)
- Voice input currently uses the device's on-device text-to-speech/speech-to-text. This is wrong.
- Instead: record the raw audio and send the audio clip directly to Gemini, which natively handles audio transcription/understanding itself — Gemini receives and interprets the audio, not a locally-transcribed text string.
- While recording, show the live waveform/audio-spectrum animation (WhatsApp-voice-note style).
- Once recording ends, show a distinct "sending voice note" state/animation while the audio uploads, before Gemini's response comes back.

### Task Creation Flow (FIX)
- Currently the user must type a task name and create the task before they can set duration, info, or anything else. This is wrong.
- Correct flow: a "+" / add-task icon opens the full task creation sheet/screen up front — title, duration (mandatory), fixed time, recurrence, info, color/type, parent — all set in this one creation step, before the task is actually saved.
- The task is only written/created once the user confirms from this creation screen, with everything already configured — not created bare and edited afterward.
- All fields remain editable later from the task's edit screen as before — this only changes the *creation* flow.

### AI Task Creation Failing (FIX)
- The AI agent is currently failing to create tasks when asked via chat/voice. Diagnose and fix so create-task commands reliably succeed, respecting the mandatory-duration rule (agent infers a duration if the user didn't give one).

### Recurrence Expiration (NEW)
- Recurring tasks need an optional end condition: "repeat every day/week/etc. until <date>." After that date, the recurrence stops generating new instances.
- If no expiration is set, recurrence continues indefinitely as before.

### Unfinished & Expired Task Handling (NEW)
- **Normal (non-recurring, non-fixed-time) task left unfinished at day rollover** → carries over to the next day automatically, staying active.
- **Recurring task** → does not carry over as "unfinished"; it simply follows its own recurrence schedule (today's instance doesn't roll into tomorrow — tomorrow's instance is generated per the recurrence rule as normal).
- **Fixed-time task whose time has passed unfinished** → does not carry over and does not stay pinned. It disappears from the active list and moves to a distinct "Unfinished" view (separate from Completed), where the user can review missed tasks.

### Screens — Addition
- **Unfinished tasks view** — new screen/section, separate from Completed; holds fixed-time tasks whose time passed without completion.
