#!/usr/bin/env python3
"""Session-12 (v8) validation harness — run from the repo root:
    python3 apps/clipforge-android/tools/validate_session12.py
Covers the four operator fixes of the v8 cycle (task-74..77):
  74: swipe-to-refresh (PullToRefreshBox) + spinning corner refresh affordance
  75: SAF diagnostic-log export (CreateDocument + SafeSave) with the old
      app-private export removed
  76: per-part 'Part 1/2/3...' rows on the Series overview incl. upcoming
      Super Series parts
  77: prompt wording parity (series part number; Super Series identity)
Plus: string/template/comment-aware brace balance on EVERY .kt file (no
git-dirty skip — this cycle touched many files), and negative checks that
forbidden patterns are gone. Version must be v8 everywhere.
"""
import os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/main/java")

def kt_files():
    for base, _, names in os.walk(SRC):
        for n in sorted(names):
            if n.endswith(".kt"):
                yield os.path.join(base, n)

def strip_kotlin(src):
    """Remove comments and string/char literals, keeping ${...} template code."""
    src = src.replace("${'$'}", "DOLLAR")
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

stripped = {}  # comment/string-stripped code per file, for forbidden-pattern checks
failures = []
files = {}
checked = 0
for f in kt_files():
    checked += 1
    raw = open(f, encoding="utf-8").read()
    files[os.path.basename(f)] = raw
    code = strip_kotlin(raw)
    stripped[os.path.basename(f)] = code
    bal = {"{": code.count("{") - code.count("}"),
           "(": code.count("(") - code.count(")"),
           "[": code.count("[") - code.count("]")}
    if any(bal.values()):
        failures.append(f"UNBALANCED {os.path.basename(f)}: {bal}")

def rel(name):
    return files[name]

def has(name, needle, count=None):
    n = rel(name).count(needle)
    if count is not None:
        if n != count:
            failures.append(f"MARKER COUNT [{name}] {needle!r}: found {n}, want {count}")
    elif n < 1:
        failures.append(f"MISSING MARKER [{name}]: {needle}")

def absent(name, needle, why):
    if needle in stripped[name]:
        failures.append(f"FORBIDDEN [{name}] {needle!r} still present ({why})")

def absent_everywhere(needle, why):
    """Checked against comment/string-stripped code so explanatory comments
    mentioning the forbidden API are not false-positived."""
    for name, code in stripped.items():
        if needle in code:
            failures.append(f"FORBIDDEN [{name}] {needle!r} still present ({why})")
            return

# ---- task-74: swipe-to-refresh + spinning corner refresh ----
has("TasksScreens.kt", "import androidx.compose.material3.pulltorefresh.PullToRefreshBox")
has("TasksScreens.kt", "PullToRefreshBox(", count=2)          # active + completed lists
has("TasksScreens.kt", "onRefresh = { vm.refreshTasks() }", count=2)
has("TasksScreens.kt", "CircularProgressIndicator(", count=3)  # 2 corner buttons + 1 pre-existing (line ~470)
has("SeriesScreens.kt", "import androidx.compose.material3.pulltorefresh.PullToRefreshBox")
has("SeriesScreens.kt", "PullToRefreshBox(", count=1)
has("SeriesScreens.kt", "onRefresh = { vm.refreshTasks() }", count=1)
has("SeriesScreens.kt", "CircularProgressIndicator(", count=1) # corner button
# swipe and corner tap must share the same state
for name in ("TasksScreens.kt", "SeriesScreens.kt"):
    has(name, "vm.tasksRefreshing")

# ---- task-75: SAF diag-log export ----
has("SettingsScreen.kt", 'ActivityResultContracts.CreateDocument("text/plain")')
has("SettingsScreen.kt", "diagExportLauncher.launch(")
has("SettingsScreen.kt", "SafeSave.writeBytes")
has("SettingsScreen.kt", "DiagLog.exportText")
has("DiagLog.kt", "fun exportText(")
absent("DiagLog.kt", "fun export(", "old app-private filesDir export must be gone")
absent_everywhere("exportDiagLog", "old export entry point must be gone")
absent_everywhere("DownloadManager", "Engine rule: never DownloadManager")

# ---- task-76: per-part rows on Series overview ----
has("SeriesScreens.kt", "\"Part ${part.part}\"")            # per-created-part rows
has("SeriesScreens.kt", "\"Part $n\"")                      # upcoming super-series parts
has("SeriesScreens.kt", "queued (upcoming)")
has("SeriesScreens.kt", "onSelectTask: (String) -> Unit", count=2)  # SeriesScreen + SeriesDetailScreen
has("App.kt", "onSelectTask = { jobId ->")

# ---- task-77: prompt wording parity ----
has("Models.kt", "SERIES MODE — THIS IS PART ")
has("Models.kt", "(not any other part number)")
has("Models.kt", "SUPER SERIES MODE — THIS IS A SUPER SERIES TASK")
has("Models.kt", "Part 1, Part 2, Part 3, and so on through the final part")
has("Models.kt", "buildSuperSeriesPrompt")
has("Models.kt", "optBoolean(\"super_series\", false)")
# the copy surface passes the request through so routing works
has("TasksScreens.kt", "AgentPromptBuilder.build(currentStatus, request)")

# ---- task-78: version bump ----
gradle = open(os.path.join(ROOT, "app/build.gradle.kts"), encoding="utf-8").read()
if "versionCode = 8" not in gradle: failures.append("MISSING: versionCode = 8")
if 'versionName = "8"' not in gradle: failures.append('MISSING: versionName = "8"')
has("SettingsScreen.kt", "Version v8")

# R8 + resource shrinking still on
if "isMinifyEnabled = true" not in gradle: failures.append("MISSING: R8 minify")
if "isShrinkResources = true" not in gradle: failures.append("MISSING: resource shrinking")

print(f"Kotlin files checked: {checked}")
if failures:
    print("\nFAILURES:")
    for f in failures: print(" -", f)
    sys.exit(1)
print("ALL v8 CHECKS PASSED (tasks 74-77 + version 8)")
