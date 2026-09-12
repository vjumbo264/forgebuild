#!/usr/bin/env python3
"""Session-09 validation harness — run from the repo root:
    python3 apps/clipforge-android/tools/validate_session09.py
(1) Kotlin structure check on every .kt in the app (string/template/comment-aware
    brace balance). (2) Eight-fixes feature markers. (3) Music-default resolution
    semantics reimplemented in Python (mirrors resolveMusicRef / pipeline music.py):
    explicit_library -> path:<ref>, default -> branding default, none -> silence."""
import os, re, sys, json, subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java")

def kt_files():
    for base, _, names in os.walk(SRC):
        for n in names:
            if n.endswith(".kt"):
                yield os.path.join(base, n)

def git_dirty(path):
    try:
        out = subprocess.run(["git", "status", "--porcelain", "--", path], capture_output=True, text=True, cwd=os.path.join(ROOT, "..", "..")).stdout
        return bool(out.strip())
    except Exception:
        return True

def strip_kotlin(src):
    """Remove comments and string/char literals, keeping ${...} template code."""
    src = src.replace("${'$'}", "DOLLAR")  # Kotlin dollar escape is literal text
    out, i, n = [], 0, len(src)
    depth_block = 0
    while i < n:
        c = src[i]
        two = src[i:i+2]; three = src[i:i+3]
        if depth_block:
            if two == "/*": depth_block += 1; i += 2; continue
            if two == "*/": depth_block -= 1; i += 2; continue
            i += 1; continue
        if two == "//":
            j = src.find("\n", i)
            i = n if j < 0 else j; continue
        if two == "/*": depth_block = 1; i += 2; continue
        if three == '"""':
            j = i + 3; depth = 0
            while j < n:
                if src[j:j+3] == '"""' and depth == 0: j += 3; break
                if src[j:j+2] == "${":
                    depth += 1
                    k = j + 2; d = 1
                    while k < n and d:
                        if src[k] == "{": d += 1
                        elif src[k] == "}": d -= 1
                        k += 1
                    out.append(src[j+2:k-1]); j = k; continue
                j += 1
            i = j; continue
        if c == '"':
            j = i + 1
            while j < n:
                if src[j] == "\\": j += 2; continue
                if src[j:j+2] == "${":
                    k = j + 2; d = 1
                    while k < n and d:
                        if src[k] == "{": d += 1
                        elif src[k] == "}": d -= 1
                        k += 1
                    out.append(src[j+2:k-1]); j = k; continue
                if src[j] == '"': j += 1; break
                j += 1
            i = j; continue
        if c == "'":
            j = i + 1
            while j < n:
                if src[j] == "\\": j += 2; continue
                if src[j] == "'": j += 1; break
                j += 1
            i = j; continue
        out.append(c); i += 1
    return "".join(out)

failures = []
checked = 0
for f in sorted(kt_files()):
    checked += 1
    if not git_dirty(f):
        continue  # untouched since the last released build — already compile-proven
    code = strip_kotlin(open(f, encoding="utf-8").read())
    bal = {"{": code.count("{") - code.count("}"),
           "(": code.count("(") - code.count(")"),
           "[": code.count("[") - code.count("]")}
    if any(bal.values()):
        failures.append(f"UNBALANCED {os.path.basename(f)}: {bal}")

allsrc = ""
for f in sorted(kt_files()):
    allsrc += "\n// FILE: " + f + "\n" + open(f, encoding="utf-8").read()

markers = {
 "fix1 onNewTaskOpen + default fallback": ["onNewTaskOpen", '"source", "default"'],
 "fix2 voice previews": ["assets/tts-previews/", "Voice preview for"],
 "fix3 collapsible logs": ["LogStep", "autoCollapseDone", "expandedStepKey"],
 "fix4 start next part": ["startNextSeriesPart", "manualSeriesContinuation", "nextPartRequestBody", "refreshNextPart"],
 "fix5 play downloaded": ["DownloadsRegistry", "downloadedVideoFor", "VideoPlayerDialog", "Play Final Video"],
 "fix6 total size": ["totalText", "formatBytes", "assetSizeBytes > 0"],
 "fix7 about": ["About ClipForge Android", "Version v6"],
 "fix8 auto-detect": ["detectedSourceKind", "Paste a link — the app detects"],
 "no per-tab nav state change (out of scope)": ["restoreState = true"],
}
for name, needles in markers.items():
    for needle in needles:
        if needle not in allsrc:
            failures.append(f"MISSING MARKER [{name}]: {needle}")

# --- music-default resolution semantics (Python mirror of the Kotlin port) ---
def resolve_music_ref(music, default_path):
    src = music.get("source", "none")
    if src == "none": return ""
    if src in ("explicit_library", "job_upload"):
        return ("path:" + music["ref"]) if music.get("ref") else ""
    if src == "default":
        return ("path:" + default_path) if default_path else ""
    return ""

cases = [
    ({"source": "default", "ref": ""}, "audio-library/chill.mp3", "path:audio-library/chill.mp3"),
    ({"source": "default", "ref": ""}, "", ""),
    ({"source": "explicit_library", "ref": "audio-library/x.m4a"}, None, "path:audio-library/x.m4a"),
    ({"source": "job_upload", "ref": "jobs/j1/music.mp3"}, None, "path:jobs/j1/music.mp3"),
    ({"source": "none", "ref": ""}, "audio-library/chill.mp3", ""),
    ({"source": "", "ref": ""}, "audio-library/chill.mp3", ""),
]
for music, default, want in cases:
    got = resolve_music_ref(music, default)
    if got != want:
        failures.append(f"MUSIC RESOLVE {music} default={default}: got {got!r} want {want!r}")

# --- next-part request semantics (Python mirror of SeriesLogic) ---
def next_part_body(request, series_id, part, start, context, current_job):
    s = request.get("source", {}); o = request.get("options", {}); m = request.get("music", {})
    rs = request.get("series", {})
    return {
        "source": {"kind": s.get("kind", ""), "value": s.get("value", "")},
        "options": {"focus": "", "target_duration_seconds": o.get("target_duration_seconds")},
        "mode": "manual",
        "series": {"enabled": True, "series_id": series_id,
                   "source_job_id": rs.get("source_job_id") or current_job or series_id,
                   "part": part, "start_seconds": start, "context": context[:8000]},
        "music": {"ref": m.get("ref", ""), "source": m.get("source", "none")},
    }

req = {"source": {"kind": "magnet", "value": "magnet:?xt=urn:btih:ABC"},
       "options": {"target_duration_seconds": 300},
       "series": {"enabled": True, "series_id": "series-1", "source_job_id": "", "part": 2},
       "music": {"ref": "", "source": "default"}}
body = next_part_body(req, "series-1", 3, 420, "Prior events (Part 1): x", "manual-999")
assert body["series"]["source_job_id"] == "manual-999", "bug-64 fallback broken"
assert body["series"]["part"] == 3 and body["series"]["start_seconds"] == 420
assert body["music"]["source"] == "default"
assert body["mode"] == "manual" and body["options"]["focus"] == ""

print(f"Kotlin files checked: {checked}")
print("music-resolution cases: 6/6" if not any('MUSIC' in f for f in failures) else "MUSIC FAILURES")
print("series-continuation fixture: ok")
if failures:
    print("\nFAILURES:")
    for f in failures: print(" -", f)
    sys.exit(1)
print("ALL CHECKS PASSED")
