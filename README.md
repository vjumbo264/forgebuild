# ForgeBuild — personal Android APK factory

Describe an Android app in plain language → copy a generated prompt → hand it to
any AI session → get a signed, installable APK as a GitHub Release on **this one
repo**. The dashboard runs at **https://forgebuild.pages.dev** and works
entirely from an Android phone (browser + Termux) — no local CLI anywhere.

## The single-repo model

Everything lives in **`vjumbo264/forgebuild`** — there are no per-app
repositories and no separate template repository:

- **`engine/`** — the ForgeBuild Engine, a plain folder: Kotlin + Jetpack
  Compose base, Material 3 theme (dynamic color, light/dark), bundled Material
  Symbols icons, conditional permission wiring, adaptive-icon generator, lean
  Gradle defaults (R8 + resource shrinking). A new app starts by **copying this
  folder** into `apps/<slug>/` — not by generating from a template repo.
- **`apps/<slug>/`** — every generated app lives here, one folder per app.
  Never a new GitHub repository.
- **`site/`** — the Dashboard SPA (Cloudflare Pages). It stores nothing:
  GitHub (this repo's `apps/` folders, releases, per-app `BUILD_STATE.json`
  and `PROMPT_HISTORY/`) is the sole source of truth. See `ARCHITECTURE.md`.
- **`PROMPT_HISTORY/<slug>.md`** — append-only per-app instruction history.
- **Releases** — one release per app version **on this repo**, tagged
  `<app-slug>-v1`, `<app-slug>-v2`, … The Dashboard filters the Releases API by
  the per-app tag prefix instead of querying separate repos.

## The three prompt types
| Type | Where | What it does |
|---|---|---|
| **1 · Build new app** | Home page (`#/`) | Free-text description → contract prompt that creates `apps/<slug>/` from a copy of `engine/`, builds checkpointed, releases **`<slug>-v1`**. |
| **2 · Extend/update** | An app's page (`#/app/<slug>`), when no build is in progress | Free-text instruction → contract prompt that appends the instruction to `PROMPT_HISTORY/<slug>.md`, applies the change in `apps/<slug>/`, cuts **`<slug>-v(n+1)`** without touching prior releases. |
| **3 · Resume** | In-progress version page (`#/app/<slug>/version`), shown automatically when `apps/<slug>/BUILD_STATE.json` has an `in_progress` task | No input needed — hands a fresh AI session the resume contract for that app folder. |

## Filling in `GITHUB_PAT` (build token inside prompts)
Every generated prompt contains `GITHUB_PAT = <PLACEHOLDER…>`. Before copying it
to an AI session, create a **fine-grained PAT** scoped to **only the
`forgebuild` repo** with **Contents: Read/Write** and **Actions: Read/Write**,
and paste it in. It never needs `delete_repo` — the build session never deletes
the repository. Never commit it anywhere.
On a phone: github.com → Settings → Developer settings → Fine-grained tokens.

The Dashboard's *own* browsing PAT is separate and unrelated: you enter it
**once**, it is stored in the browser's `localStorage`, and the Dashboard never
asks again unless you click **Disconnect** (which clears it). It only lets the
Dashboard list apps/releases and delete a release for you, and is never sent
anywhere except `api.github.com`.

## First-priority flow (full loop)
1. Open the dashboard → (first visit only: paste your browsing PAT once) →
   **New app** → describe the app → Generate → fill in the PAT placeholder →
   copy the prompt.
2. Hand the prompt to any AI session. It creates `apps/<slug>/` from the
   `engine/` copy, checkpoints every step (`apps/<slug>/BUILD_STATE.json`
   pushed after each), and releases **`<slug>-v1`** with a signed APK.
3. Back on the dashboard → **Apps** → your app appears (folder under `apps/`)
   → open it → download the v1 APK, manifest, or source.
4. To change the app: on its page, type the instruction → Generate → copy the
   type-2 prompt → the AI cuts **v2**; v1 stays intact and downloadable.
5. Interrupted build? The app page links to the in-progress version page; copy
   the type-3 resume prompt and hand it to a fresh session.

## Deploying the dashboard itself
GitHub Actions (`.github/workflows/pages-deploy.yml`) deploys `site/` to the
Cloudflare Pages project `forgebuild` on every push to `main` that touches the
site, using repo secrets `CLOUDFLARE_API_TOKEN` / `CLOUDFLARE_ACCOUNT_ID`.
Production URL is the free `forgebuild.pages.dev` subdomain — no custom domain,
no paid tier anywhere.

## Building an app APK
`.github/workflows/release.yml` is a single `workflow_dispatch` workflow: give
it `app_path` (e.g. `apps/tapcounter`) and optional notes; it builds that
folder, auto-increments the tag to `<slug>-vN`, signs with the repo-level
secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`),
and creates the GitHub Release with the APK + `forgebuild-manifest.json`.
