# ClipForge — Prompt History

Append-only log of every operator instruction, in order.

---

## Session 1 — new app (2026-09-11)

**Instruction (verbatim):**

> **Repository:** https://github.com/motionssalt/clipforge — read this repo
> first, specifically the Telegram bot's source, to understand exactly how
> cloning/login, task creation, production.json handling, and series
> grouping currently work there, so the app mirrors that same backend
> behavior and data model. Don't guess at these mechanics — pull the real
> task states, clone/session model, and API/storage calls the bot already
> uses, and build the Android UI as a proper native front end on top of the
> same backend, not a reimplementation of its logic from scratch.
>
> **App:** ClipForge for Android — a native companion app for the ClipForge
> video pipeline, replacing the Telegram bot as the primary interface.
>
> **Auth / clones:** On first open, the user logs into an existing clone or
> creates a new one (same clone concept as the bot). Once a clone is created
> and started, it logs in automatically from then on — no repeated login
> step on later opens, same as how starting a clone in the bot immediately
> authenticates it.
>
> **Tasks:**
> - A normal task list, separate from a distinct Series section — tapping
>   a series task does not open it inline in the normal task list, it
>   navigates to its own dedicated series view, since series are structurally
>   different (multi-part, sequential) from single-video tasks.
> - Every ongoing task has a live, polling log view — open a task and see
>   the actual pipeline log streaming as it happens, not just a static status
>   label. Finished tasks keep their full log available to reopen and review
>   afterward.
> - Multi-select support on lists (tasks, music files, etc.) so multiple
>   items can be deleted in a single action instead of one at a time.
>
> **Uploads:** Anything being uploaded — production.json, music files, a
> torrent/magnet source, anything — shows a real progress bar, not a spinner
> or silent wait.
>
> **production.json input:** Since this is a native app with no message-size
> limit, production.json can be either uploaded as a file or pasted directly
> as raw text into a text field — paste doesn't need to fit under any
> chat-message length constraint.
>
> **Narration/audio preview:** Audio and narration tracks can be previewed
> by tapping play directly in the app — streamed, not downloaded first. Once
> a track has been played once, it's cached locally so replaying it again
> doesn't require network access.
>
> **Video delivery:** No 15MB size limit on delivered videos (that was a
> Telegram constraint, not a real one). When the user saves a finished video,
> the app requests storage permission and saves it into a `ClipForge` folder
> under the device's Movies (or Music, per whichever the pipeline currently
> treats as the output root) directory — created automatically if it doesn't
> exist yet — using the explicit storage-permission-gated save flow, not
> Android's native Downloads manager.
>
> **Design:** Material 3 (dynamic color, light/dark), Material Symbols
> iconography, lightweight — this is a control/monitoring app for a backend
> pipeline, not a heavy on-device video editor. No slug — let the build
> session choose one once it has read this description and the actual
> ClipForge bot source.

**Slug chosen this session:** `clipforge` (checked repo — no collision with
existing `calcom` / `forge-companion` / `tapcounter` folders; matches the
upstream repo name `motionssalt/clipforge` which the app fronts).

**Backend contract extracted from motionssalt/clipforge bot source
(bot/src/*, schemas/*, ARCHITECTURE.md):**

- **Auth model.** Per-chat encrypted PAT + `owner/repo` clone slug. Login =
  provide PAT (scopes: repo + workflow; fine-grained needs Administration
  write for repo creation) + the clone repo as `owner/repository`. Once the
  clone is created (or connected) the bot never asks again — same behavior
  the app must mirror. On Android that means: store the PAT + repo in
  EncryptedSharedPreferences on first successful login, then auto-login on
  every subsequent open.
- **All state lives in the clone repo, on branch `main`.** GitHub REST is
  the API surface:
  - `GET /repos/{owner}/{repo}/contents/{path}?ref=main` — read a file
    (returns base64 `content` + `sha`)
  - `PUT /repos/{owner}/{repo}/contents/{path}` — write a file
    (`{message, content, branch: "main", sha?}`)
  - `POST /repos/{owner}/{repo}/actions/workflows/{file}/dispatches` —
    workflow_dispatch with `{ref: "main", inputs: {...}}`
  - `GET /repos/{owner}/{repo}/releases/tags/{tag}` — release + `assets[]`
    with `browser_download_url` (private-release download uses PAT as
    `Authorization: Bearer` with `Accept: application/octet-stream`)
  - `GET /repos/{owner}/{repo}/actions/runs/{run_id}` and
    `GET /repos/{owner}/{repo}/actions/runs/{run_id}/logs` (zip) for the
    live pipeline log
- **Job identity.** `jobs/<job_id>/{stage-a-request.json, status.json,
  production.json}`. Job IDs are `manual-<epoch_ms>`. Series-part IDs are
  `<series_id>-p<N>` where `series_id = series-<epoch_ms>` and N is the
  1-based part index.
- **Task listing.** `GET /contents/jobs` returns directory entries — each
  is a job id. For each, read `status.json` for its state.
- **State machine** (from `bot/src/jobs.js`, canonical):
  `queued → stage_a_running → (awaiting_torrent_selection | awaiting_plan)
  → stage_b_queued → stage_b_running → complete | error | cancelled`.
  Terminal set: complete / error / cancelled. Re-entering the same state
  is idempotent.
- **status.json shape** (v2): `{version, job_id, mode, series:
  {enabled, series_id, part, start_seconds, is_final}, state, message,
  created_at_epoch, updated_at_epoch, expires_at_epoch, release_tag,
  release_url, assets:{...}, run:{workflow_run_id, workflow_run_url,
  code_ref}, publishing:{status, posts[], idempotency_key}}`.
- **Series grouping.** A task is a series part iff `status.series.enabled`
  is true. Group by `status.series.series_id`; order parts by
  `status.series.part` (1..N). Continuation of a completed part is derived
  by `manualSeriesContinuation(status, request, plan)` — a completed
  manual part whose plan is not `is_final=true` and has a valid
  `end_seconds` yields the next part's coords (`part+1`,
  `start_seconds=end_seconds`), and its job id is
  `nextPartJobId(...)` = `${series_id}-p${part+1}`. Series-mode tasks skip
  the "focus" wizard step.
- **production.json schema** (from `bot/src/plan.js`,
  `schemas/production_plan.schema.json`, canonical cross-validated with
  `pipeline/plan/schema.py`): top-level object with
  `video_duration_seconds`, `target_total_duration_seconds` (positive
  integers), optional `title`, optional `hashtags[5..8]` (each `#`-prefixed,
  whitespace-free), optional `youtube_tags[10..20]`, optional `series`
  block (nested `{series_id, part, start_seconds, end_seconds, is_final,
  summary}` — legacy flat `series_*` siblings accepted as input only),
  required `cuts[≥1]` (each `{start_seconds, end_seconds, voiceover_text
  | raw_narration}`, non-overlapping, within video_duration). Validator
  runs client-side before upload so paste-and-check has instant feedback.
- **Sources.** SOURCE_KINDS = `url | drive | magnet | torrent_file |
  telegram_channel | telegram_relay`. For `torrent_file` the .torrent is
  ≤1 MB (uploaded as `jobs/<id>/source.torrent`, referenced by
  `source.torrent_file_index`). For `magnet` value = magnet URI.
- **Music library.** `audio-library/<name>` files (safe path regex
  `^audio-library/[^/\\-]+$`). Default recorded in
  `branding/music_default.json`; per-task selection carried in
  `stage-a-request.json.music = {ref, source}` where source ∈
  `none|default|explicit_library|job_upload`.
- **Voices / TTS config.** Voice catalogue is the constant `VOICES` map
  in `bot/src/constants.js` (12 en-US / en-NG neural voices); default
  `en-US-AndrewNeural`. TTS settings persisted in
  `branding/tts_settings.json`. There is NO server-side voice sample —
  "preview" for narration is client-side TTS or the actual delivered
  audio track from a completed job (Stage B renders narration into the
  final video's audio).
- **Delivered video.** GitHub Release `clipforge-<jobId>` carries the
  finished asset(s). Filename is recorded in `status.assets` (typically
  `final.mp4`, plus derivatives). Download = the asset's
  `browser_download_url` with the PAT — no 15 MB cap because we skip
  Telegram entirely.
- **Live log.** `status.json.run.workflow_run_id` → GitHub Actions runs
  API. During execution, poll `status.json` (state + message) every ~2-3
  seconds for a live view of the pipeline's own progress messages; for
  finer granularity, `GET /actions/runs/{run_id}/jobs` gives per-step
  status, and `.../logs` (application/zip) gives the full text log for a
  completed run.
- **Dispatch inputs** (canonical):
  - `stage-a.yml`: `{job_id, code_ref}` (code_ref = current default-branch
    SHA at dispatch time)
  - `stage-b.yml`: `{job_id, code_ref}`
  - `publish.yml`: `{action, job_id?, code_ref, ...}`
  - `clone-copy.yml`: one-time bootstrap dispatched during clone creation;
    progress in `.clipforge-clone-status.json` at repo root
- **Cleanup.** Deleting a task removes every `jobs/<jobId>/*` blob AND
  the releases tagged `clipforge-<jobId>` and
  `clipforge-relay-input-<jobId>`.

**Executed this session:** (progress tracked in `apps/clipforge/BUILD_STATE.json`)
