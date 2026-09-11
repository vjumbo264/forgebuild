# ForgeBuild — Architecture & Metadata Model (single-repo)

## The one-repository rule
**Everything lives in `vjumbo264/forgebuild`.** There are no per-app repos and
no separate Engine template repo. This is the permanent architecture; the
earlier per-app-repo design was a deliberate correction away from repo sprawl
and must not be reintroduced under any framing.

```
forgebuild/
  engine/                Engine base (copied into each new app; never a template repo)
  apps/<slug>/           one folder per generated app
  site/                  Dashboard SPA (Cloudflare Pages)
  PROMPT_HISTORY/<slug>.md   append-only per-app instruction history
  .github/workflows/
    release.yml          builds apps/<slug> -> release tagged <slug>-vN
    pages-deploy.yml     deploys site/ to Cloudflare Pages
  BUILD_STATE.json       project-level ForgeBuild state (per-app state is nested)
```

## Source of truth
**GitHub (this one repo) is the sole source of truth for all app/version data.**
- App inventory = the directories under `apps/` (GitHub Contents API).
- Versions = GitHub Releases **on `forgebuild`**, tagged `<app-slug>-v1`,
  `<app-slug>-v2`, …; the Dashboard and the release workflow filter the
  Releases API by the `<app-slug>-` tag prefix. Assets = signed APK +
  `forgebuild-manifest.json` + source archive.
- Per-app build progress = `apps/<slug>/BUILD_STATE.json` (nested — the
  repo-root `BUILD_STATE.json` is reserved for ForgeBuild's own project-level
  state). Read from raw.githubusercontent.com.
- Instruction history = `PROMPT_HISTORY/<slug>.md`, append-only.

## Engine relationship
`engine/` is a live folder, not a template repository. A new app build copies
`engine/` into `apps/<slug>/` and builds on the copy. Engine-level fixes (e.g.
the `pathData` → `.addPath(PathParser().parsePathString(d).toNodes(), fill =
SolidColor(Color.Black))` icon-generator fix in `engine/tools/gen_icons.py`)
are applied in `engine/` AND propagated into every app folder that copied the
broken version. App builds never modify `engine/` unless the operator's prompt
explicitly classifies the change as Engine-level.

## D1 decision (task-03 conclusion, still valid)
**No D1 database is used.** Every datum the Dashboard displays (apps, releases,
assets, build state, prompt history) is available live from the GitHub REST
API, which is CORS-enabled and callable directly from the browser. Caching in
D1 would introduce staleness and a second writer for zero functional gain. If
a future need appears, D1 may cache ONLY derived, regenerable display data and
must NEVER store: GitHub PATs, app source, APKs, or anything GitHub does not
already hold.

## GitHub API usage & CORS
The GitHub REST API sends `Access-Control-Allow-Origin: *`, so the Dashboard
calls it directly from the browser. **No Worker/Pages Function proxy is
required.**

## PAT handling (corrected auth model)
- **Dashboard browsing PAT**: entered ONCE by the operator, stored in the
  browser's `localStorage`, and the Dashboard never prompts again across
  visits/reloads unless the operator clicks the explicit **Disconnect** action
  (which clears it). Sent only to `api.github.com`. There is **no password, no
  login system, and no server-side session** — that was considered and
  explicitly rejected.
- **Build PAT inside prompts**: a placeholder (`GITHUB_PAT = <PLACEHOLDER>`)
  inside every generated build/extend/resume prompt, filled by the operator by
  hand each time before copying. Scoped by operator choice; recommended:
  fine-grained, `forgebuild` repo only, Contents R/W + Actions R/W, **no
  `delete_repo`** (build sessions never delete the repo). This is unrelated
  to, and not replaced by, the browsing PAT.

## Release workflow
`.github/workflows/release.yml` is `workflow_dispatch`-only: input `app_path`
(e.g. `apps/tapcounter`) selects the folder to build; the tag is
auto-incremented from existing releases sharing the `<slug>-` prefix; signing
uses the repo-level secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`,
`KEY_ALIAS`, `KEY_PASSWORD` — a true JKS keystore whose store and key passwords
match; PKCS12 stores ignore a distinct key password and break signing).

## Deployment
Static SPA (`site/`: index.html + app.js + styles.css, hash-routed: `#/` home,
`#/apps`, `#/app/{slug}`, `#/app/{slug}/version`) on Cloudflare Pages project
`forgebuild` (`https://forgebuild.pages.dev`). Deploys run from GitHub Actions
(`.github/workflows/pages-deploy.yml`) via wrangler on every push to `main`
touching `site/**`. Secrets: `CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID`.

## Explicitly not built (per contract)
Native app frontend, Telegram triggers, mid-push automatic builds (release
workflow runs only on explicit dispatch), IAP/paid tiers, multi-user, password
or login system, per-app repositories, template-repo generation.
