/* ForgeBuild Dashboard — static SPA, hash-routed, GitHub API direct (CORS-safe).
   SINGLE-REPO MODEL: everything lives in vjumbo264/forgebuild.
     - apps enumerated from the apps/ folder (GitHub Contents API)
     - releases live on forgebuild itself, filtered by "<app-slug>-" tag prefix
     - per-app build progress at apps/<slug>/BUILD_STATE.json
     - prompt history at PROMPT_HISTORY/<slug>.md
   AUTH MODEL: one-time GitHub PAT stored in localStorage; never re-prompted
   unless the operator clicks Disconnect. No password, no login, no session. */
const API='https://api.github.com', RAW='https://raw.githubusercontent.com';
const OWNER='vjumbo264', REPO='forgebuild';
const $=s=>document.querySelector(s);
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));

/* ---------- auth (M5: one-time localStorage PAT) ---------- */
const PAT_KEY='fb_pat';
const pat=()=>localStorage.getItem(PAT_KEY)||'';
function setPat(v){v?localStorage.setItem(PAT_KEY,v):localStorage.removeItem(PAT_KEY);renderPatBox();}
function renderPatBox(){
  const has=!!pat();
  $('#patStatus').textContent=has?'PAT connected':'No PAT — public data only';
  $('#patDisconnect').style.display=has?'':'none';
}
function gateIfNeeded(){
  // Show the one-time connect gate only if no PAT is stored AND the operator
  // has not previously chosen "browse without a PAT".
  const skipped=localStorage.getItem('fb_pat_skipped')==='1';
  $('#patGate').style.display=(pat()||skipped)?'none':'flex';
}
function bindGate(){
  $('#patGateSave').onclick=()=>{const v=$('#patGateInput').value.trim();if(!v)return;
    setPat(v);localStorage.removeItem('fb_pat_skipped');gateIfNeeded();route();};
  $('#patGateInput').addEventListener('keydown',e=>{if(e.key==='Enter')$('#patGateSave').click();});
  $('#patGateSkip').onclick=()=>{localStorage.setItem('fb_pat_skipped','1');gateIfNeeded();route();};
  $('#patDisconnect').onclick=()=>{if(!confirm('Disconnect? This clears the stored GitHub PAT from this browser (localStorage). You will be asked for it once on next visit.'))return;
    localStorage.removeItem(PAT_KEY);localStorage.removeItem('fb_pat_skipped');renderPatBox();gateIfNeeded();route();};
}

/* ---------- GitHub helpers ---------- */
async function gh(path,opts={}){
  opts.headers={Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28',...(opts.headers||{}),...(pat()?{Authorization:'Bearer '+pat()}:{})};
  const r=await fetch(API+path,opts);
  if(!r.ok){let t='';try{t=(await r.json()).message||r.status}catch(e){t=r.status}
    if(r.status===401)throw new Error('GitHub 401 — the stored PAT is invalid/expired. Disconnect (header) and save a fresh one. '+t);
    if(r.status===403)throw new Error('GitHub 403 — rate-limited or insufficient scope. A saved PAT raises limits and unlocks private actions. '+t);
    throw new Error('GitHub '+r.status+': '+t);}
  return r.status===204?null:r.json();
}
async function raw(path){const r=await fetch(`${RAW}/${OWNER}/${REPO}/main/${path}`);return r.ok?r.text():null;}
async function listApps(){
  const d=await gh(`/repos/${OWNER}/${REPO}/contents/apps`);
  return (d||[]).filter(e=>e.type==='dir').map(e=>e.name);
}
async function releasesFor(slug){
  const all=await gh(`/repos/${OWNER}/${REPO}/releases?per_page=100`);
  return (all||[]).filter(r=>r.tag_name&&r.tag_name.startsWith(slug+'-v'));
}
function fmtDate(d){try{return new Date(d).toLocaleDateString(undefined,{year:'numeric',month:'short',day:'numeric'})}catch(e){return d}}
function copyFrom(id,btn){const el=$('#'+id);el.select();el.setSelectionRange(0,999999);
  const done=()=>{const o=btn.textContent;btn.textContent='Copied ✓';setTimeout(()=>btn.textContent=o,1600)};
  if(navigator.clipboard?.writeText)navigator.clipboard.writeText(el.value).then(done);else{document.execCommand('copy');done()}}

/* ---------- prompt templates (single-repo model) ---------- */
function contractHead(kind){return `# ForgeBuild App Build Contract — ${kind}

CREDENTIALS (operator fills before copying — never commit these values):
GITHUB_PAT = <PLACEHOLDER — operator: create a fine-grained GitHub PAT scoped to ONLY the repository ${OWNER}/${REPO}, permissions: Contents = Read and Write, Actions = Read and Write. Paste it here before handing this prompt to an AI session. Recommended: NO delete_repo scope — the build session never deletes the repo.>

REPO = https://github.com/${OWNER}/${REPO} (the ONE ForgeBuild repo — engine, dashboard and every app live here)
`; }

const RULES = `
You are one of many independent AI sessions working sequentially on this app. You have no reliable memory of previous sessions. The repository, git history, and per-app BUILD_STATE.json are the project's persistent memory. Your task is to determine what the repository says needs to be done next and do it — never stop just because part of the work is already done; never return a status report instead of work; never ask the operator what to do next; never wait for user input.

THE REPOSITORY IS THE SOURCE OF TRUTH. At session start: clone/pull ${OWNER}/${REPO}, read this app's apps/<slug>/BUILD_STATE.json completely, resume from the first pending or in_progress item. Never reset existing progress, never redo work marked done, never overwrite previous releases.

PER-STEP COMMIT AND PUSH PROTOCOL (mandatory, never batched):
START STEP → PERFORM STEP → VALIDATE STEP → UPDATE apps/<slug>/BUILD_STATE.json → COMMIT → PUSH → VERIFY PUSH LANDED on origin/main → ONLY THEN start the next step. Every push must be verified via the GitHub API. "Done" = implemented + validated + checkpoint updated + committed + pushed + push verified.

PROMPT HISTORY is append-only at PROMPT_HISTORY/<slug>.md: append every operator instruction in order before acting on it.

BUILD QUALITY CONSTRAINTS (non-negotiable):
- Material 3 only: build the UI with ForgeBuildTheme (dynamic color, light/dark) and EngineIcons from engine/. Never emoji, never mismatched icon sets, never raw unstyled UI.
- The engine/ folder is a component/theme library AND the starting point: copy its content into your app folder, then write real Kotlin + Jetpack Compose code on top of the copy. Never modify engine/ itself UNLESS the operator's prompt explicitly says the change is an Engine-level fix — in that case apply it in engine/ AND propagate it into every apps/<slug>/ folder that carries a copy.
- Lightweight by default: keep R8 + resource shrinking on; add a dependency only when a feature truly needs it, and record the justification in BUILD_STATE.json notes.
- Proper adaptive icon: run tools/make_adaptive_icon.py (inside your app folder) with foreground artwork (operator-supplied if given, otherwise create simple artwork first). Never a square icon with white padding.
- Permissions: enable only what the app legitimately needs — manifest entries + com.forgebuild.engine.permissions.PermissionWiring (STORAGE, CAMERA, NOTIFICATIONS, FOREGROUND_SERVICE, BACKGROUND_LOCATION, DEVICE_ADMIN available).
- Free tier only. No paid services. The operator works from an Android phone (Termux/browser) — no local-CLI assumptions in any docs you write.

SIGNING & RELEASES: release signing secrets (KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD) already exist as GitHub Actions secrets on ${OWNER}/${REPO} — reuse them; only regenerate if explicitly told. To release: dispatch the "Build & Release App APK" workflow (Actions > workflow_dispatch, or POST /repos/${OWNER}/${REPO}/actions/workflows/release.yml/dispatches with ref=main and inputs app_path="apps/<slug>", release_notes). The workflow builds the folder, auto-increments the tag as <slug>-vN, and creates a GitHub Release on this repo with the APK + forgebuild-manifest.json. Never overwrite an existing release/tag — versions are <slug>-v1, <slug>-v2, ... monotonically.

STOPPING: only stop at a genuine execution boundary after committing/pushing an updated apps/<slug>/BUILD_STATE.json whose notes say exactly what is done, what remains, and the exact next operation — or when the release is fully verified live on the GitHub Releases API.`;

function promptNewApp(desc,slug){
return contractHead('NEW APP')+`APP_SLUG = ${slug}
APP_FOLDER = apps/${slug}/ (inside ${OWNER}/${REPO} — do NOT create a new repository)
APP_DESCRIPTION (the operator's full intent — build exactly this):
"""
${desc}
"""

TASKS (seed apps/${slug}/BUILD_STATE.json with these, all pending, then execute in order):
1. Create apps/${slug}/ if absent and copy engine/'s full content into it as the starting point (it is a folder in this repo, not a template to "generate" from). Add PROMPT_HISTORY/${slug}.md and seed apps/${slug}/BUILD_STATE.json. Commit + push immediately as push-access proof.
2. Set identity inside apps/${slug}/: namespace/applicationId in app/build.gradle.kts (derive a safe package, e.g. com.forgebuild.${slug.replace(/[^a-z0-9]/g,'')||'app'}), app_name in res/values/strings.xml.
3. Adaptive icon via apps/${slug}/tools/make_adaptive_icon.py (create artwork if none supplied). Commit + push.
4. Confirm the repo-level signing secrets exist (see SIGNING & RELEASES); do not regenerate them.
5. Implement the app described in APP_DESCRIPTION, ONE FEATURE PER COMMIT/PUSH STEP, following the per-step protocol and quality constraints above.
6. Validate the build configuration, then release ${slug}-v1 (see SIGNING & RELEASES: dispatch release.yml with app_path=apps/${slug}) and verify the release + APK asset exist via the GitHub Releases API on ${OWNER}/${REPO}.
7. Set apps/${slug}/BUILD_STATE.json build_complete: true only after ${slug}-v1 is verified live.
`+RULES;}

function promptExtend(slug,instruction,latest){
return contractHead('EXTEND / UPDATE')+`APP_SLUG = ${slug}
APP_FOLDER = apps/${slug}/ (inside ${OWNER}/${REPO})
CURRENT LATEST RELEASE = ${latest||'(check the Releases API filtered by prefix ${slug}-)'}
NEW INSTRUCTION FROM OPERATOR (apply exactly this; append it to PROMPT_HISTORY/${slug}.md first):
"""
${instruction}
"""

TASKS:
1. Clone/pull ${OWNER}/${REPO}. Read apps/${slug}/BUILD_STATE.json — if any task is in_progress, this prompt does not apply: switch to the RESUME contract (finish the in-progress work first).
2. Append the NEW INSTRUCTION to PROMPT_HISTORY/${slug}.md with today's date. Commit + push.
3. Seed/refresh apps/${slug}/BUILD_STATE.json tasks for this update (implement change → validate → release).
4. Implement the instruction following the per-step protocol and quality constraints below, working from the latest released source in apps/${slug}/. If the change is an Engine-level fix, apply it in engine/ AND propagate it into apps/${slug}/ (and note it).
5. Release the next version via the release.yml workflow (app_path=apps/${slug}); the tag auto-increments to ${slug}-v(N+1). NEVER overwrite or delete any prior release. Verify the new release live via the API, then set build_complete: true.
`+RULES;}

function promptResume(slug){
return contractHead('RESUME UNFINISHED BUILD')+`APP_SLUG = ${slug}
APP_FOLDER = apps/${slug}/ (inside ${OWNER}/${REPO})

This is a RESUME contract. There is nothing new to build — the previous session was interrupted mid-build.

TASKS:
1. Clone/pull ${OWNER}/${REPO}. Read apps/${slug}/BUILD_STATE.json completely — it is the source of truth for where the build stopped.
2. Read PROMPT_HISTORY/${slug}.md for the full history of operator intent.
3. Resume from the first task marked pending or in_progress (the notes field says exactly what was done, what remains, and the exact next operation). Do not redo tasks marked done.
4. Continue the per-step protocol below through to the release the in-progress version was targeting (dispatch release.yml with app_path=apps/${slug}), verify the release live on the GitHub Releases API (tag prefix ${slug}-), then set build_complete: true.
`+RULES;}

/* ---------- views ---------- */
function patNote(){return pat()?'':'<div class="banner warn">No PAT saved — you are browsing public data only. Save a PAT from the connect screen (Disconnect first if one was stored) to raise rate limits and enable delete actions.</div>';}
function setActive(n){document.querySelectorAll('[data-nav]').forEach(a=>a.classList.toggle('active',a.dataset.nav===n));}

function renderHome(){setActive('home');
$('#view').innerHTML=`
<h1>Build a new app</h1>
<p class="sub">Describe the Android app you want. ForgeBuild generates a copy-ready persistent-session build contract — hand it to any AI session and it will create a new folder under <code>apps/</code> in the single <b>forgebuild</b> repo (never a new repository), build in checkpointed increments, and release a signed APK as a <code>&lt;slug&gt;-vN</code> release.</p>
<div class="card">
  <label for="desc">App description</label>
  <textarea id="desc" rows="5" placeholder="e.g. A Pomodoro timer with session stats, a home-screen widget and daily streaks"></textarea>
  <label for="slug">App slug (optional — derived from the description if blank; becomes the apps/&lt;slug&gt;/ folder and the &lt;slug&gt;-vN release prefix)</label>
  <input id="slug" type="text" placeholder="e.g. pomodoro-streaks">
  <div class="row"><button class="btn" id="gen">Generate build prompt</button></div>
</div>
<div class="card" id="out" style="display:none">
  <div class="row" style="margin:0 0 10px;justify-content:space-between"><b>Type 1 · Build new app</b><button class="btn tonal small" id="copy">Copy prompt</button></div>
  <div class="banner">Before handing this to an AI session: replace <code>GITHUB_PAT = &lt;PLACEHOLDER…&gt;</code> with a fine-grained PAT scoped to <b>only the forgebuild repo</b> (Contents: R/W + Actions: R/W; no delete_repo needed).</div>
  <textarea id="prompt" class="code" readonly></textarea>
</div>`;
$('#gen').onclick=()=>{const d=$('#desc').value.trim();if(!d){$('#desc').focus();return}
  const r=($('#slug').value.trim().toLowerCase().replace(/[^a-z0-9-]+/g,'-').replace(/^-+|-+$/g,''))||(d.toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-+|-+$/g,'').slice(0,40));
  $('#prompt').value=promptNewApp(d,r);$('#out').style.display='block';$('#out').scrollIntoView({behavior:'smooth'});};
$('#copy').onclick=e=>copyFrom('prompt',e.target);
}

async function renderApps(){setActive('apps');
$('#view').innerHTML=`<h1>Your apps</h1><p class="sub">Every folder under <code>apps/</code> in <b>${OWNER}/${REPO}</b> — live from the GitHub Contents API.</p>${patNote()}<div class="loading">Loading apps…</div>`;
try{
  const apps=await listApps();
  document.querySelector('.loading')?.remove();
  if(!apps.length){$('#view').insertAdjacentHTML('beforeend','<div class="empty">No apps yet. <a href="#/">Build your first app →</a></div>');return;}
  const rows=await Promise.all(apps.map(async slug=>{
    let info='';
    try{const rels=await releasesFor(slug);info=rels.length?`${rels[0].tag_name} · ${fmtDate(rels[0].published_at)}`:'no releases yet';}catch(e){info='releases unavailable';}
    return `<a class="listitem" href="#/app/${encodeURIComponent(slug)}">
      <div class="grow"><div class="t">${esc(slug)}</div><div class="d">${esc(info)}</div></div>
      <span>→</span></a>`;
  }));
  $('#view').insertAdjacentHTML('beforeend',rows.join(''));
}catch(e){document.querySelector('.loading')?.remove();$('#view').insertAdjacentHTML('beforeend',`<div class="banner warn">${esc(e.message)}</div>`);}
}

async function renderApp(slug){setActive('apps');
$('#view').innerHTML=`<p class="sub"><a href="#/apps">← Apps</a></p><h1>${esc(slug)}</h1>${patNote()}<div class="loading">Loading…</div>`;
let releases=[],hist=null,bs=null;const errs=[];
try{releases=await releasesFor(slug);}catch(e){errs.push(e.message);}
try{hist=await raw(`PROMPT_HISTORY/${slug}.md`);}catch(e){}
try{const b=await raw(`apps/${slug}/BUILD_STATE.json`);bs=b?JSON.parse(b):null;}catch(e){}
document.querySelector('.loading')?.remove();
if(errs.length)$('#view').insertAdjacentHTML('beforeend',`<div class="banner warn">${esc(errs.join(' · '))}</div>`);
const inProg=bs&&bs.tasks&&bs.tasks.some(t=>t.status==='in_progress')&&!bs.build_complete;
const latest=releases.length?releases[0].tag_name:'';
let html='';
if(inProg)html+=`<div class="banner">A build is currently <b>in progress</b> on this app. Use the resume prompt, not a new instruction.<div class="row"><a class="btn small" href="#/app/${encodeURIComponent(slug)}/version">Open in-progress version →</a></div></div>`;
html+=`<h2>Versions (GitHub Releases on ${REPO})</h2>`;
if(!releases.length)html+=`<div class="empty">No releases yet.</div>`;
for(const rel of releases){
  const assets=rel.assets.map(a=>`<div class="asset"><code>${esc(a.name)}</code><span>${(a.size/1024/1024).toFixed(2)} MB · <a href="${a.browser_download_url}">Download</a></span></div>`).join('');
  html+=`<div class="card"><div class="row" style="margin:0;justify-content:space-between">
    <span class="chip">${esc(rel.tag_name)}</span>
    <span class="sub" style="margin:0">${fmtDate(rel.published_at)}</span></div>
    ${rel.body?`<p style="font-size:14px">${esc(rel.body)}</p>`:''}
    ${assets}
    <div class="asset"><code>Source code (zip)</code><span><a href="${rel.zipball_url}">Download</a></span></div>
    <div class="row"><button class="btn danger small" data-del="${rel.id}" data-tag="${esc(rel.tag_name)}">Delete ${esc(rel.tag_name)}</button></div></div>`;
}
html+=`<h2>Prompt history</h2>${hist?`<pre>${esc(hist)}</pre>`:'<div class="empty">No PROMPT_HISTORY/'+esc(slug)+'.md found.</div>'}`;
if(!inProg)html+=`<h2>Extend / update this app</h2>
<div class="card"><label for="instr">New instruction</label>
<textarea id="instr" rows="3" placeholder="e.g. Add a dark-only mode toggle, or fix the crash on rotation"></textarea>
<div class="row"><button class="btn" id="gen2">Generate extend prompt</button></div></div>
<div class="card" id="out2" style="display:none">
<div class="row" style="margin:0 0 10px;justify-content:space-between"><b>Type 2 · Extend/update</b><button class="btn tonal small" id="copy2">Copy prompt</button></div>
<div class="banner">Fill in <code>GITHUB_PAT</code> (scoped to the <b>${REPO}</b> repo, Contents R/W + Actions R/W) before copying to an AI session.</div>
<textarea id="prompt2" class="code" readonly></textarea></div>`;
$('#view').insertAdjacentHTML('beforeend',html);
document.querySelectorAll('[data-del]').forEach(b=>b.onclick=async()=>{
  if(!pat()){alert('Connect a PAT first (it needs Contents: R/W on '+REPO+').');return;}
  if(!confirm(`Delete release ${b.dataset.tag}? This calls the GitHub API with your stored PAT. The git tag is kept.`))return;
  b.disabled=true;b.textContent='Deleting…';
  try{await gh(`/repos/${OWNER}/${REPO}/releases/${b.dataset.del}`,{method:'DELETE'});renderApp(slug);}
  catch(e){alert('Delete failed: '+e.message);b.disabled=false;b.textContent='Delete';}});
const g2=$('#gen2');if(g2)g2.onclick=()=>{const i=$('#instr').value.trim();if(!i)return;
  $('#prompt2').value=promptExtend(slug,i,latest);$('#out2').style.display='block';$('#out2').scrollIntoView({behavior:'smooth'});};
const c2=$('#copy2');if(c2)c2.onclick=e=>copyFrom('prompt2',e.target);
}

async function renderVersion(slug){setActive('apps');
$('#view').innerHTML=`<p class="sub"><a href="#/app/${encodeURIComponent(slug)}">← ${esc(slug)}</a></p><h1>${esc(slug)} — in-progress version</h1><div class="loading">Loading apps/${esc(slug)}/BUILD_STATE.json…</div>`;
let bs=null;try{const b=await raw(`apps/${slug}/BUILD_STATE.json`);bs=b?JSON.parse(b):null;}catch(e){}
document.querySelector('.loading')?.remove();
if(!bs){$('#view').insertAdjacentHTML('beforeend','<div class="banner warn">No apps/'+esc(slug)+'/BUILD_STATE.json found on main — nothing to resume. Use the app page instead.</div>');return;}
const cur=bs.tasks?.find(t=>t.status==='in_progress');
const next=bs.tasks?.find(t=>t.status==='pending');
$('#view').insertAdjacentHTML('beforeend',`
<div class="banner">${bs.build_complete?'This build is marked complete — resume prompt not needed.':`Currently active: <b>${esc(cur?.id||'—')}</b>${next?` · next pending: <b>${esc(next.id)}</b>`:''}`}</div>
<h2>Type 3 · Resume prompt</h2>
<div class="card"><div class="row" style="margin:0 0 10px;justify-content:space-between"><b>Copy this to a fresh AI session to continue the build</b><button class="btn tonal small" id="copy3">Copy prompt</button></div>
<div class="banner">Fill in <code>GITHUB_PAT</code> (scoped to the <b>${REPO}</b> repo, Contents R/W + Actions R/W) before copying.</div>
<textarea id="prompt3" class="code" readonly>${esc(promptResume(slug))}</textarea></div>
<h2>apps/${esc(slug)}/BUILD_STATE.json (read-only, live)</h2>
<div class="card high checks"><b>build_complete:</b> ${String(bs.build_complete)} · <b>current_task:</b> ${esc(bs.current_task||'—')}</div>
<pre>${esc(JSON.stringify(bs,null,2))}</pre>`);
$('#copy3').onclick=e=>copyFrom('prompt3',e.target);
}

/* ---------- router ---------- */
function route(){const h=location.hash||'#/';
  if(h.startsWith('#/app/')){const p=decodeURIComponent(h.slice(6));const i=p.indexOf('/');
    if(i>=0&&p.slice(i+1)==='version')return renderVersion(p.slice(0,i));
    return renderApp(i>=0?p.slice(0,i):p);}
  if(h==='#/apps')return renderApps();
  return renderHome();}
renderPatBox();bindGate();gateIfNeeded();
window.addEventListener('hashchange',route);
route();
