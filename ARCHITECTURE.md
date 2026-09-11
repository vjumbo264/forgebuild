# ForgeBuild Dashboard — Architecture & Metadata Model

## Source of truth
**GitHub is the sole source of truth for all app/version data.** Specifically:
- App inventory = GitHub repos owned by `vjumbo264` tagged with topic `forgebuild-app`.
- Versions = GitHub Releases on each app repo (`v1`, `v2`, ...), assets = APK +
  `forgebuild-manifest.json` + source archive.
- Build progress = each app repo's own `BUILD_STATE.json` (read from raw.githubusercontent.com).
- Instruction history = each app repo's append-only `PROMPT_HISTORY.md`.

## D1 decision (task-03 conclusion)
**No D1 database is used.** Rationale: every datum the Dashboard displays
(apps, releases, assets, build state, prompt history) is available live from
the GitHub REST API, which is CORS-enabled and callable directly from the
browser with the operator's session-scoped PAT. Caching in D1 would introduce
staleness and a second writer for zero functional gain. If a future need
appears (e.g. caching expensive cross-repo aggregation), D1 may cache ONLY
derived, regenerable display data and must NEVER store: GitHub PATs, app
source, APKs, or anything GitHub does not already hold.

## GitHub API usage & CORS
The GitHub REST API sends `Access-Control-Allow-Origin: *`, so the Dashboard
calls it directly from the browser. **No Worker/Pages Function proxy is
required** — this is the deliberate design decision; a proxy would only be
added if GitHub ever restricted CORS.

## PAT handling
- Dashboard browsing PAT: entered by the operator in the UI, held ONLY in
  `sessionStorage` (memory of that tab/session), never sent anywhere except
  `api.github.com`, never stored server-side (there is no server state).
- Per-app build PAT: a placeholder inside generated prompts
  (`GITHUB_PAT = <PLACEHOLDER>`), filled by the operator before copying,
  scoped contents+actions to the single app repo.

## Deployment
Static SPA (`site/`: index.html + app.js + styles.css, hash-routed:
`#/` home, `#/apps`, `#/app/{repo}`, `#/app/{repo}/version`) on Cloudflare
Pages project `forgebuild` (production URL `https://forgebuild.pages.dev`).
Deploys run from GitHub Actions (`.github/workflows/deploy.yml`) via
wrangler on every push to `main` touching `site/**`. Secrets:
`CLOUDFLARE_API_TOKEN`, `CLOUDFLARE_ACCOUNT_ID` (GitHub Actions secrets).

## Explicitly not built (per contract)
Native app frontend, Telegram triggers, mid-push automatic builds (release
workflow runs only on explicit release action), IAP/paid tiers, multi-user.
