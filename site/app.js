/* ForgeBuild Dashboard — static SPA, hash-routed, GitHub API direct (CORS-safe), PAT in sessionStorage only. */
const API='https://api.github.com', RAW='https://raw.githubusercontent.com', OWNER='vjumbo264';
const $=s=>document.querySelector(s);
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const pat=()=>sessionStorage.getItem('fb_pat')||'';
function setPat(v){v?sessionStorage.setItem('fb_pat',v):sessionStorage.removeItem('fb_pat');$('#pat').value=v;}
async function gh(path,opts={}){
  opts.headers={Accept:'application/vnd.github+json','X-GitHub-Api-Version':'2022-11-28',...(opts.headers||{}),...(pat()?{Authorization:'Bearer '+pat()}:{})};
  const r=await fetch(API+path,opts);
  if(!r.ok){let t='';try{t=(await r.json()).message||r.status}catch(e){t=r.status}
    if(r.status===401||r.status===403)throw new Error('GitHub '+r.status+' — save a valid PAT in the header (public repos can be browsed without one). '+t);
    throw new Error('GitHub '+r.status+': '+t);}
  return r.status===204?null:r.json();
}
async function raw(repo,path){const r=await fetch(`${RAW}/${OWNER}/${repo}/main/${path}`);return r.ok?r.text():null;}
function fmtDate(d){try{return new Date(d).toLocaleDateString(undefined,{year:'numeric',month:'short',day:'numeric'})}catch(e){return d}}
function copyFrom(id,btn){const el=$('#'+id);el.select();el.setSelectionRange(0,999999);
  const done=()=>{const o=btn.textContent;btn.textContent='Copied ✓';setTimeout(()=>btn.textContent=o,1600)};
  if(navigator.clipboard?.writeText)navigator.clipboard.writeText(el.value).then(done);else{document.execCommand('copy');done()}}

/* ---------- prompt templates ---------- */
function contractHead(kind){return `# ForgeBuild App Build Contract — ${kind}

CREDENTIALS (operator fills before copying — never commit these values):
GITHUB_PAT = <PLACEHOLDER — operator: create a fine-grained GitHub PAT scoped to ONLY the repository ${kind==='NEW APP'?'named below':'below'}, permissions: Contents = Read and Write, Actions = Read and Write. Paste it here before handing this prompt to an AI session.>

APP_REPO = https://github.com/${OWNER}/`; }

const RULES = `
You are one of many independent AI sessions working sequentially on this app. You have no reliable memory of previous sessions. The repository, git history, and BUILD_STATE.json are the project's persistent memory. Your task is to determine what the repository says needs to be done next and do it — never stop just because part of the work is already done; never return a status report instead of work; never ask the operator what to do next; never wait for user input.

THE REPOSITORY IS THE SOURCE OF TRUTH. At session start: clone/pull the repo, read BUILD_STATE.json completely, resume from the first pending or in_progress item. Never reset existing progress, never redo work marked done, never overwrite previous releases.

PER-STEP COMMIT AND PUSH PROTOCOL (mandatory, never batched):
START STEP → PERFORM STEP → VALIDATE STEP → UPDATE BUILD_STATE.json → COMMIT → PUSH → VERIFY PUSH LANDED on origin/main → ONLY THEN start the next step. Every push must be verified via the GitHub API. "Done" = implemented + validated + checkpoint updated + committed + pushed + push verified.

PROMPT_HISTORY.md is append-only: append every operator instruction in order before acting on it.

BUILD QUALITY CONSTRAINTS (non-negotiable):
- Material 3 only: build the UI with ForgeBuildTheme (dynamic color, light/dark) and EngineIcons from the Engine. Never emoji, never mismatched icon sets, never raw unstyled UI.
- The Engine is a component/theme library: write real Kotlin + Jetpack Compose code against it. Any app type and layout is allowed (InputMethodService, foreground Service, DeviceAdminReceiver, etc.) — no fixed schema.
- Lightweight by default: keep R8 + resource shrinking on; add a dependency only when a feature truly needs it, and record the justification in BUILD_STATE.json notes.
- Proper adaptive icon: run tools/make_adaptive_icon.py with foreground artwork (operator-supplied if given, otherwise create simple artwork first). Never a square icon with white padding.
- Permissions: enable only what the app legitimately needs — manifest entries + com.forgebuild.engine.permissions.PermissionWiring (STORAGE, CAMERA, NOTIFICATIONS, FOREGROUND_SERVICE, BACKGROUND_LOCATION, DEVICE_ADMIN available).
- Free tier only. No paid services. The operator works from an Android phone (Termux/browser) — no local-CLI assumptions in any docs you write.

SIGNING & RELEASES: create a keystore with tools/make_keystore.sh once, then store these GitHub Actions secrets on the app repo via the API (never in git): KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS (value: forgebuild), KEY_PASSWORD. To release: dispatch the "Build & Release APK" workflow (Actions > workflow_dispatch, or POST /repos/${OWNER}/<repo>/actions/workflows/release.yml/dispatches with ref=main and inputs version_tag, release_notes). The workflow builds a signed APK and creates a GitHub Release with the APK + forgebuild-manifest.json. Never overwrite an existing release/tag — versions are v1, v2, ... monotonically.

STOPPING: only stop at a genuine execution boundary after committing/pushing an updated BUILD_STATE.json whose notes say exactly what is done, what remains, and the exact next operation — or when the release is fully verified live on the GitHub Releases API.`;

function promptNewApp(desc,repo){
return contractHead('NEW APP')+repo+`
APP_REPO_NAME = ${repo}
APP_DESCRIPTION (the operator's full intent — build exactly this):
"""
${desc}
"""

TASKS (seed BUILD_STATE.json with these, all pending, then execute in order):
1. If the repo does not exist, create it from the Engine template: POST https://api.github.com/repos/${OWNER}/forgebuild-engine/generate {"owner":"${OWNER}","name":"${repo}","private":false} using GITHUB_PAT. Add the topic: PUT /repos/${OWNER}/${repo}/topics {"names":["forgebuild-app"]}. Push-access proof: seed BUILD_STATE.json + PROMPT_HISTORY.md and push immediately.
2. Set identity: namespace/applicationId in app/build.gradle.kts (derive a safe package, e.g. com.forgebuild.${repo.replace(/[^a-z0-9]/g,'')||'app'}), app_name in res/values/strings.xml.
3. Adaptive icon via tools/make_adaptive_icon.py (create artwork if none supplied). Commit + push.
4. Set up signing secrets (see SIGNING & RELEASES). Commit + push any non-secret wiring.
5. Implement the app described in APP_DESCRIPTION, ONE FEATURE PER COMMIT/PUSH STEP, following the per-step protocol and quality constraints above.
6. Validate the build configuration, then release v1 (see SIGNING & RELEASES) and verify the release + APK asset exist via the GitHub Releases API.
7. Set BUILD_STATE.json build_complete: true only after v1 is verified live.
`+RULES;}

function promptExtend(repo,instruction,latest){
return contractHead('EXTEND / UPDATE')+repo+`
APP_REPO_NAME = ${repo}
CURRENT LATEST RELEASE = ${latest||'(check the Releases API)'}
NEW INSTRUCTION FROM OPERATOR (apply exactly this; append it to PROMPT_HISTORY.md first):
"""
${instruction}
"""

TASKS:
1. Clone/pull the repo. Read BUILD_STATE.json — if any task is in_progress, this prompt does not apply: switch to the RESUME contract (finish the in-progress work first).
2. Append the NEW INSTRUCTION to PROMPT_HISTORY.md with today's date. Commit + push.
3. Seed/refresh BUILD_STATE.json tasks for this update (implement change → validate → release). 
4. Implement the instruction following the per-step protocol and quality constraints below, working from the latest released source.
5. Determine the next version number N from the Releases API (latest vN → v(N+1)) and release it per SIGNING & RELEASES. NEVER overwrite or delete any prior release. Verify the new release live via the API, then set build_complete: true.
`+RULES;}

function promptResume(repo){
return contractHead('RESUME UNFINISHED BUILD')+repo+`
APP_REPO_NAME = ${repo}

This is a RESUME contract. There is nothing new to build — the previous session was interrupted mid-build.

TASKS:
1. Clone/pull the repo. Read BUILD_STATE.json completely — it is the source of truth for where the build stopped.
2. Read PROMPT_HISTORY.md for the full history of operator intent.
3. Resume from the first task marked pending or in_progress (the notes field says exactly what was done, what remains, and the exact next operation). Do not redo tasks marked done.
4. Continue the per-step protocol below through to the release the in-progress version was targeting, verify the release live on the GitHub Releases API, then set build_complete: true.
`+RULES;}

/* ---------- views ---------- */
function patNote(){return pat()?'':'<div class="banner warn">No PAT saved — public data only, and delete actions need a PAT. Paste a fine-grained GitHub PAT in the header (stored in this tab\'s session storage only).</div>';}
function setActive(n){document.querySelectorAll('[data-nav]').forEach(a=>a.classList.toggle('active',a.dataset.nav===n));}

function renderHome(){setActive('home');
$('#view').innerHTML=`
<h1>Build a new app</h1>
<p class="sub">Describe the Android app you want. ForgeBuild generates a copy-ready persistent-session build contract — hand it to any AI session and it will create the app's own repo from the ForgeBuild Engine, build in checkpointed increments, and release a signed APK.</p>
<div class="card">
  <label for="desc">App description</label>
  <textarea id="desc" rows="5" placeholder="e.g. A Pomodoro timer with session stats, a home-screen widget and daily streaks"></textarea>
  <label for="repo">Repository name (optional — derived from the description if blank)</label>
  <input id="repo" type="text" placeholder="e.g. pomodoro-streaks">
  <div class="row"><button class="btn" id="gen">Generate build prompt</button></div>
</div>
<div class="card" id="out" style="display:none">
  <div class="row" style="margin:0 0 10px;justify-content:space-between"><b>Type 1 · Build new app</b><button class="btn tonal small" id="copy">Copy prompt</button></div>
  <div class="banner">Before handing this to an AI session: replace <code>GITHUB_PAT = &lt;PLACEHOLDER…&gt;</code> with a fine-grained PAT scoped to <b>only this app repo</b> (Contents: R/W + Actions: R/W).</div>
  <textarea id="prompt" class="code" readonly></textarea>
</div>`;
$('#gen').onclick=()=>{const d=$('#desc').value.trim();if(!d){$('#desc').focus();return}
  const r=($('#repo').value.trim().toLowerCase().replace(/[^a-z0-9-]+/g,'-').replace(/^-+|-+$/g,''))||('app-'+d.toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-+|-+$/g,'').slice(0,40));
  $('#prompt').value=promptNewApp(d,r);$('#out').style.display='block';$('#out').scrollIntoView({behavior:'smooth'});};
$('#copy').onclick=e=>copyFrom('prompt',e.target);
}

async function renderApps(){setActive('apps');
$('#view').innerHTML=`<h1>Your apps</h1><p class="sub">Every repository under ${OWNER} tagged with the <span class="chip">forgebuild-app</span> topic — live from the GitHub API.</p>${patNote()}<div class="loading">Loading apps…</div>`;
try{
  const q=encodeURIComponent(`topic:forgebuild-app user:${OWNER}`);
  const d=await gh(`/search/repositories?q=${q}&per_page=100&sort=updated`);
  if(!d.items.length){$('#view').insertAdjacentHTML('beforeend','<div class="empty">No apps yet. <a href="#/">Build your first app →</a></div>');return;}
  document.querySelector('.loading').remove();
  $('#view').insertAdjacentHTML('beforeend',d.items.map(r=>`
    <a class="listitem" href="#/app/${encodeURIComponent(r.name)}">
      <div class="grow"><div class="t">${esc(r.name)}</div><div class="d">${esc(r.description||'')} · updated ${fmtDate(r.updated_at)}</div></div>
      <span>→</span></a>`).join(''));
}catch(e){$('#view').insertAdjacentHTML('beforeend',`<div class="banner warn">${esc(e.message)}</div>`);}
}

async function renderApp(repo){setActive('apps');
$('#view').innerHTML=`<p class="sub"><a href="#/apps">← Apps</a></p><h1>${esc(repo)}</h1>${patNote()}<div class="loading">Loading…</div>`;
let releases=[],hist=null,bs=null,repoInfo=null;
const errs=[];
try{repoInfo=await gh(`/repos/${OWNER}/${repo}`);}catch(e){errs.push(e.message);}
try{releases=await gh(`/repos/${OWNER}/${repo}/releases?per_page=100`);}catch(e){errs.push(e.message);}
try{hist=await raw(repo,'PROMPT_HISTORY.md');}catch(e){}
try{const b=await raw(repo,'BUILD_STATE.json');bs=b?JSON.parse(b):null;}catch(e){}
document.querySelector('.loading')?.remove();
if(errs.length)$('#view').insertAdjacentHTML('beforeend',`<div class="banner warn">${esc(errs.join(' · '))}</div>`);
const inProg=bs&&bs.tasks&&bs.tasks.some(t=>t.status==='in_progress');
const latest=releases.length?releases[0].tag_name:'';
let html='';
if(inProg)html+=`<div class="banner">A build is currently <b>in progress</b> on this repo. Use the resume prompt, not a new instruction.<div class="row"><a class="btn small" href="#/app/${encodeURIComponent(repo)}/version">Open in-progress version →</a></div></div>`;
html+=`<h2>Versions (GitHub Releases)</h2>`;
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
html+=`<h2>Prompt history</h2>${hist?`<pre>${esc(hist)}</pre>`:'<div class="empty">No PROMPT_HISTORY.md found.</div>'}`;
if(!inProg)html+=`<h2>Extend / update this app</h2>
<div class="card"><label for="instr">New instruction</label>
<textarea id="instr" rows="3" placeholder="e.g. Add a dark-only mode toggle, or fix the crash on rotation"></textarea>
<div class="row"><button class="btn" id="gen2">Generate extend prompt</button></div></div>
<div class="card" id="out2" style="display:none">
<div class="row" style="margin:0 0 10px;justify-content:space-between"><b>Type 2 · Extend/update</b><button class="btn tonal small" id="copy2">Copy prompt</button></div>
<div class="banner">Fill in <code>GITHUB_PAT</code> (scoped to <b>${esc(repo)}</b> only, Contents R/W + Actions R/W) before copying to an AI session.</div>
<textarea id="prompt2" class="code" readonly></textarea></div>`;
$('#view').insertAdjacentHTML('beforeend',html);
document.querySelectorAll('[data-del]').forEach(b=>b.onclick=async()=>{
  if(!pat()){alert('Save a PAT in the header first (needs Contents: R/W on this repo).');return;}
  if(!confirm(`Delete release ${b.dataset.tag}? This calls the GitHub API with your session PAT. The git tag is kept.`))return;
  b.disabled=true;b.textContent='Deleting…';
  try{await gh(`/repos/${OWNER}/${repo}/releases/${b.dataset.del}`,{method:'DELETE'});renderApp(repo);}
  catch(e){alert('Delete failed: '+e.message);b.disabled=false;b.textContent='Delete';}});
const g2=$('#gen2');if(g2)g2.onclick=()=>{const i=$('#instr').value.trim();if(!i)return;
  $('#prompt2').value=promptExtend(repo,i,latest);$('#out2').style.display='block';$('#out2').scrollIntoView({behavior:'smooth'});};
const c2=$('#copy2');if(c2)c2.onclick=e=>copyFrom('prompt2',e.target);
}

async function renderVersion(repo){setActive('apps');
$('#view').innerHTML=`<p class="sub"><a href="#/app/${encodeURIComponent(repo)}">← ${esc(repo)}</a></p><h1>${esc(repo)} — in-progress version</h1><div class="loading">Loading BUILD_STATE.json…</div>`;
let bs=null;try{const b=await raw(repo,'BUILD_STATE.json');bs=b?JSON.parse(b):null;}catch(e){}
document.querySelector('.loading')?.remove();
if(!bs){$('#view').insertAdjacentHTML('beforeend','<div class="banner warn">No BUILD_STATE.json found on main — nothing to resume. Use the app page instead.</div>');return;}
const cur=bs.tasks?.find(t=>t.status==='in_progress');
const next=bs.tasks?.find(t=>t.status==='pending');
$('#view').insertAdjacentHTML('beforeend',`
<div class="banner">${bs.build_complete?'This build is marked complete — resume prompt not needed.':`Currently active: <b>${esc(cur?.id||'—')}</b>${next?` · next pending: <b>${esc(next.id)}</b>`:''}`}</div>
<h2>Type 3 · Resume prompt</h2>
<div class="card"><div class="row" style="margin:0 0 10px;justify-content:space-between"><b>Copy this to a fresh AI session to continue the build</b><button class="btn tonal small" id="copy3">Copy prompt</button></div>
<div class="banner">Fill in <code>GITHUB_PAT</code> (scoped to <b>${esc(repo)}</b> only, Contents R/W + Actions R/W) before copying.</div>
<textarea id="prompt3" class="code" readonly>${esc(promptResume(repo))}</textarea></div>
<h2>BUILD_STATE.json (read-only, live)</h2>
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
$('#pat').value=pat();
$('#patSave').onclick=()=>{setPat($('#pat').value.trim());route();};
$('#pat').addEventListener('keydown',e=>{if(e.key==='Enter'){setPat(e.target.value.trim());route();}});
window.addEventListener('hashchange',route);
route();
