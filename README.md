# ForgeBuild — personal Android APK factory

Describe an Android app in plain language → copy a generated prompt → hand it to
any AI session → get a signed, installable APK as a GitHub Release on that app's
own repo. The dashboard runs at **https://forgebuild.pages.dev** and works
entirely from an Android phone (browser + Termux) — no local CLI anywhere.

## The pieces
- **Engine** — `vjumbo264/forgebuild-engine`, a GitHub *template* repo: Kotlin +
  Jetpack Compose base, Material 3 theme (dynamic color, light/dark), bundled
  Material Symbols icons, conditional permission wiring, adaptive-icon
  generator, lean Gradle defaults (R8 + resource shrinking), and a release
  workflow that builds a signed APK into a GitHub Release. Every app repo is
  generated from this template.
- **Dashboard** — this repo's `site/`, a static SPA on Cloudflare Pages. It
  stores nothing: GitHub (repos + releases + each repo's `BUILD_STATE.json` and
  `PROMPT_HISTORY.md`) is the sole source of truth. See `ARCHITECTURE.md`.

## The three prompt types
| Type | Where | What it does |
|---|---|---|
| **1 · Build new app** | Home page (`#/`) | Free-text description → contract prompt that creates a new repo from the Engine template, builds checkpointed, releases **v1**. |
| **2 · Extend/update** | An app's page (`#/app/<repo>`), when no build is in progress | Free-text instruction → contract prompt that appends the instruction to `PROMPT_HISTORY.md`, applies the change, cuts **v(n+1)** without touching prior releases. |
| **3 · Resume** | In-progress version page (`#/app/<repo>/version`), shown automatically when a repo's `BUILD_STATE.json` has an `in_progress` task | No input needed — hands a fresh AI session the resume contract for that repo. |

## Filling in `GITHUB_PAT` (per-app build token)
Every generated prompt contains `GITHUB_PAT = <PLACEHOLDER…>`. Before copying it
to an AI session, create a **fine-grained PAT** scoped to **only that app's
repo** with **Contents: Read/Write** and **Actions: Read/Write**, and paste it
in. Never scope it to the Engine or Dashboard repos; never commit it anywhere.
On a phone: github.com → Settings → Developer settings → Fine-grained tokens.

The dashboard's *own* PAT field (top bar) is separate: it only lets the
dashboard list/delete releases for you, lives in that tab's session storage,
and is never sent anywhere except `api.github.com`.

## First-priority flow (full loop)
1. Open the dashboard → **New app** → describe the app → Generate → fill in the
   PAT placeholder → copy the prompt.
2. Hand the prompt to any AI session. It creates the repo from the Engine,
   checkpoints every step (`BUILD_STATE.json` pushed after each), and releases
   **v1** with a signed APK.
3. Back on the dashboard → **Apps** → your app appears (topic `forgebuild-app`)
   → open it → download the v1 APK, manifest, or source.
4. To change the app: on its page, type the instruction → Generate → copy the
   type-2 prompt → the AI cuts **v2**; v1 stays intact and downloadable.
5. Interrupted build? The app page links to the in-progress version page; copy
   the type-3 resume prompt and hand it to a fresh session.

## Deploying the dashboard itself
GitHub Actions (`.github/workflows/deploy.yml`) deploys `site/` to the
Cloudflare Pages project `forgebuild` on every push to `main` that touches the
site, using repo secrets `CLOUDFLARE_API_TOKEN` / `CLOUDFLARE_ACCOUNT_ID`.
Production URL is the free `forgebuild.pages.dev` subdomain — no custom domain,
no paid tier anywhere.
