package com.forgebuild.clipforgeandroid.data

import android.util.Base64
import kotlinx.coroutines.delay
import org.json.JSONObject

/**
 * Shadow Clone creation — faithful port of bot/src/github.js (motionssalt/clipforge):
 *   beginShadowCloneCreation -> (inline poll; the app IS the poller, so the bot's
 *   cron-side pollShadowCloneJob guards run in the same loop) -> finalizeShadowClone.
 *
 * Why this replaced the old simplified createClone (fix #2): the old code created an
 * EMPTY auto_init repo and seeded 4 branding files — no source content, no polling,
 * "usable" the instant the repo existed. The bot's real flow (live-verified in the
 * reference repo, bug-45/46/47/51/63 comments preserved below) is:
 *
 *  1. resolve identity, pick/validate a name (auto `clipforge-clone-<suffix>` when blank)
 *  2. enumerate the SOURCE tree at the current main revision (minus excludes)
 *  3. POST /user/repos (private, auto_init FALSE — the Contents-API bootstrap PUT
 *     creates the first commit + ref; bug-46: the Git Data API 409s on a repo with
 *     zero refs, the Contents API does not)
 *  4. bootstrap commit = .clipforge-sync.json + the one-time clone-copy workflow,
 *     on the branch POST /user/repos ANNOUNCED (bug-47)
 *  5. resolve the branch that actually became default (up to 5 tries; GitHub can
 *     report the planned branch before the ref settles — observed live)
 *  6. dispatch clone-copy.yml with {source_sha, bootstrap_commit, expected_files}
 *     (retry 404/422 — GitHub indexes new workflow files slightly after the push)
 *  7. POLL .clipforge-clone-status.json until state=complete (bug-51 budgets:
 *     START 120s / STALL 6min / absolute DEADLINE 10min) — a shadow clone is NOT
 *     ready the instant the repo is created; the copy takes minutes on a runner
 *  8. finalize: copy the source's own .github/workflows tree via the Contents API with
 *     the user's PAT (bug-63: a run's GITHUB_TOKEN may never touch workflow paths),
 *     best-effort delete of the one-time workflow, verify the head advanced past
 *     the bootstrap commit, normalize the default branch to main when the account
 *     preference made it something else, and verify the live default branch points
 *     at the copied tree with enough blobs.
 *
 * The caller then logs into the new clone immediately — "create it and you're in".
 */
object ShadowClone {

    // ---- constants: bot github.js, verbatim values ----
    const val SOURCE = "motionssalt/clipforge"
    const val DEFAULT_BRANCH = "main"
    const val CLONE_COPY_WORKFLOW = "clone-copy.yml"
    const val CLONE_STATUS_PATH = ".clipforge-clone-status.json"
    const val CLONE_SYNC_PATH = ".clipforge-sync.json"
    const val POLL_MS = 2_000L          // CLONE_COPY_POLL_MS
    const val START_MS = 120_000L       // CLONE_COPY_START_MS (no status written => never started)
    const val STALL_MS = 360_000L       // CLONE_COPY_STALL_MS (status stopped advancing => dead run)
    const val DEADLINE_MS = 600_000L    // CLONE_COPY_DEADLINE_MS (absolute ceiling)

    // SHADOW_CLONE_EXCLUDES, order and flags preserved.
    private val EXCLUDES = listOf(
        Regex("^branding/"),
        Regex("^jobs/"),
        Regex("^audio-library/"),
        Regex("keys", RegexOption.IGNORE_CASE),
        Regex("accounts", RegexOption.IGNORE_CASE),
        Regex("queue", RegexOption.IGNORE_CASE)
    )

    /** bot sourcePathAllowed(): true when the source path may be copied. */
    fun sourcePathAllowed(path: String): Boolean =
        path.isNotBlank() && EXCLUDES.none { it.containsMatchIn(path) }

    class CloneException(message: String) : Exception(message)

    /** One cloneable blob from the source tree. */
    data class SourceFile(val path: String, val sha: String)

    /** Terminal success record — mirrors finalizeShadowClone's return. */
    data class CloneResult(
        val repo: String,        // "login/name"
        val login: String,
        val name: String,
        val branch: String,      // normalized (main) branch
        val sourceSha: String,
        val copiedFiles: Int
    )

    /** Progress stages, same names the bot reports through onProgress. */
    const val STAGE_SOURCE = "source"
    const val STAGE_COPY = "copy"
    const val STAGE_FINALIZE = "finalize"

    /** Best-effort progress reporter — a failing callback must never break the clone. */
    private suspend fun report(
        onProgress: (suspend (stage: String, done: Int, total: Int) -> Unit)?,
        stage: String, done: Int, total: Int
    ) {
        if (onProgress == null) return
        try { onProgress(stage, done, total) } catch (_: Exception) {}
    }

    /** bot cloneRepositoryName(): validate a user-supplied name. */
    fun cloneRepositoryName(value: String): String {
        val name = value.trim()
        if (!Regex("^[A-Za-z0-9_.-]{1,100}$").matches(name) || name.lowercase().endsWith(".git")) {
            throw CloneException("Shadow Clone repository name may contain letters, numbers, dots, hyphens, and underscores only.")
        }
        return name
    }

    /** bot autoCloneRepositoryName(): first free `clipforge-clone-<suffix>` (bug-45). */
    private suspend fun autoCloneRepositoryName(pat: String, login: String): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        for (attempt in 0 until 6) {
            val suffix = (1..6).map { alphabet.random() }.joinToString("")
            val candidate = "clipforge-clone-$suffix"
            val probe = GitHubClient(pat, login, candidate)
            try {
                probe.repoDetails() // 200 => name taken, try the next candidate.
            } catch (e: GitHubClient.GhException) {
                if (e.code == 404) return candidate
                throw e
            }
        }
        throw CloneException("Could not find a free repository name automatically. Send a name yourself instead.")
    }

    private fun b64encode(text: String): String =
        Base64.encodeToString(text.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)

    private fun ghMessage(e: GitHubClient.GhException): String = try {
        JSONObject(e.body).optString("message", e.body)
    } catch (_: Exception) { e.body }

    /** Enumerate the cloneable blobs of the source tree at [sourceCommitSha]. */
    private suspend fun enumerateSourceFiles(source: GitHubClient, sourceCommitSha: String): List<SourceFile> {
        val sourceTreeSha = try {
            source.gitCommitTreeSha(sourceCommitSha)
        } catch (e: GitHubClient.GhException) {
            throw CloneException("Could not resolve the ClipForge source file tree.")
        }
        val tree = source.gitTreeRecursive(sourceTreeSha)
        if (tree.optBoolean("truncated", false)) {
            throw CloneException("The ClipForge source tree is too large to clone safely.")
        }
        val entries = tree.optJSONArray("tree") ?: throw CloneException("Could not resolve the ClipForge source file tree.")
        val files = mutableListOf<SourceFile>()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            if (entry.optString("type") == "blob" && sourcePathAllowed(entry.optString("path"))) {
                files.add(SourceFile(entry.optString("path"), entry.optString("sha")))
            }
        }
        if (files.isEmpty()) throw CloneException("The ClipForge source tree did not contain any cloneable files.")
        return files
    }

    /** bot readCloneCopyStatus(): the copy workflow's status file, or null while absent/unparseable. */
    private suspend fun readCloneCopyStatus(target: GitHubClient, branch: String): JSONObject? = try {
        val file = target.readFile(CLONE_STATUS_PATH, branch) ?: return null
        val parsed = JSONObject(file.first)
        if (!parsed.optString("state").isNotBlank()) null else parsed
    } catch (_: Exception) {
        null
    }

    /**
     * bug-51: locate the one-time copy workflow's newest run on the clone, for
     * cancellation after a terminal failure. The dispatch response carries no run
     * id, and no list endpoint filters by workflow until GitHub has indexed the
     * brand-new workflow file (observed live on freshly created repos), so the
     * primary read is the repo-wide run list — the clone has exactly one workflow
     * at this point, so its newest run IS the copy run. The per-workflow list is
     * the fallback. Returns null when nothing can be identified yet.
     */
    private fun newestRunId(body: JSONObject): Long? {
        val runs = body.optJSONArray("workflow_runs") ?: return null
        var first: Long? = null
        for (i in 0 until runs.length()) {
            val run = runs.optJSONObject(i) ?: continue
            val id = run.optLong("id", 0)
            if (id <= 0) continue
            if (first == null) first = id
            if (run.optString("event") == "workflow_dispatch") return id
        }
        return first
    }

    private suspend fun findCloneCopyRunId(target: GitHubClient): Long? {
        try {
            newestRunId(target.actionsRuns(10))?.let { return it }
        } catch (_: Exception) { /* fall through to the per-workflow list */ }
        return try {
            newestRunId(target.workflowRuns(CLONE_COPY_WORKFLOW, 10))
        } catch (_: Exception) {
            null
        }
    }

    /** bug-51: cancel the copy workflow's run, best-effort — never throws. Used
     *  when a clone is declared dead so a queued/stalled Actions run does not keep
     *  burning minutes — or complete LATER and mutate a repo the user was told failed. */
    private suspend fun cancelShadowCloneRun(target: GitHubClient, runId: Long?) {
        if (runId == null || runId <= 0) return
        try { target.cancelRun(runId) } catch (_: Exception) {}
    }

    /** Terminal poll failure: cancel the copy run (best-effort) then surface the error. */
    private suspend fun failWithCancel(target: GitHubClient, message: String): Nothing {
        val runId = try { findCloneCopyRunId(target) } catch (_: Exception) { null }
        cancelShadowCloneRun(target, runId)
        throw CloneException(message)
    }

    /**
     * The whole creation. Inline-polls the copy workflow to completion (this app has
     * no cron trigger, so it owns the job through the same START/STALL/DEADLINE
     * guards the bot's cron poller enforces), then finalizes.
     */
    suspend fun begin(
        pat: String,
        requestedName: String,
        onProgress: (suspend (stage: String, done: Int, total: Int) -> Unit)? = null
    ): CloneResult {
        val token = pat.trim()
        val identityClient = GitHubClient(token, "", "")
        val login = try {
            identityClient.whoami().optString("login")
        } catch (e: GitHubClient.GhException) {
            throw CloneException("Could not identify the GitHub account for this token (${e.code}): ${ghMessage(e)}")
        }
        if (login.isBlank()) throw CloneException("Could not identify the GitHub account for this token.")

        // bug-45: blank name = "choose for me", resolved BEFORE creating anything.
        val name = if (requestedName.trim().isNotBlank()) cloneRepositoryName(requestedName)
                   else autoCloneRepositoryName(token, login)

        val source = GitHubClient(token, SOURCE.substringBefore("/"), SOURCE.substringAfter("/"))
        val sourceCommitSha = try {
            source.branchRefSha(DEFAULT_BRANCH)
        } catch (e: GitHubClient.GhException) {
            throw CloneException("Could not resolve the current ClipForge source revision.")
        }
        val files = enumerateSourceFiles(source, sourceCommitSha)
        report(onProgress, STAGE_SOURCE, 0, files.size)

        // Create the target repo. bug-45: repo creation is gated on the PAT's
        // Administration permission — surface 403/404 plainly (bot wording).
        val target: JSONObject
        try {
            target = identityClient.createRepo(name)
        } catch (e: GitHubClient.GhException) {
            when (e.code) {
                422 -> throw CloneException("A repository with that name already exists in your account. Use “Connect existing clone” instead, or choose a new name.")
                403, 404 -> throw CloneException("The token could not create a repository on your account. A classic PAT needs the “repo” scope; a fine-grained PAT needs “Administration” (write) access.")
                else -> throw e
            }
        }
        val repo = if (target.optString("full_name").isNotBlank()) target.optString("full_name") else "$login/$name"
        val targetClient = GitHubClient(token, login, name)

        // Bootstrap commit on the branch POST /user/repos ANNOUNCED (bug-46/47):
        // sync marker + one-time copy workflow. Contents API PUT works on an empty
        // repo and creates the first commit + ref in one call.
        val initialBranch = if (target.optString("default_branch").isNotBlank()) target.optString("default_branch") else DEFAULT_BRANCH
        val sync = JSONObject()
            .put("source", SOURCE)
            .put("synced_sha", sourceCommitSha)
            .put("synced_at", java.time.Instant.now().toString())
        val bootstrap: JSONObject
        try {
            bootstrap = JSONObject(
                targetClient.putFile(
                    CLONE_SYNC_PATH,
                    (sync.toString(2) + "\n").toByteArray(Charsets.UTF_8),
                    "Initialize Shadow Clone from $SOURCE@${sourceCommitSha.take(7)}",
                    branch = initialBranch
                )
            )
            targetClient.putFile(
                ".github/workflows/$CLONE_COPY_WORKFLOW",
                (ShadowCloneWorkflow.YAML + "\n").toByteArray(Charsets.UTF_8),
                "clipforge: install one-time Shadow Clone copy workflow",
                branch = initialBranch
            )
        } catch (e: GitHubClient.GhException) {
            throw CloneException("GitHub could not initialize the new repository: ${ghMessage(e)}")
        }

        // bug-47: resolve the branch that actually became the repo's default from the
        // live repo object — GitHub can still report the PLANNED branch right after
        // the bootstrap commit (observed live), so only trust it once the ref exists.
        var targetBranch = initialBranch
        for (attempt in 0 until 5) {
            var liveBranch = targetBranch
            try {
                val liveRepo = targetClient.repoDetails()
                if (liveRepo.optString("default_branch").isNotBlank()) liveBranch = liveRepo.optString("default_branch")
                if (targetClient.branchRefShaOrNull(liveBranch) != null) {
                    targetBranch = liveBranch
                    break
                }
            } catch (_: Exception) {
                // Reported branch has no ref yet — GitHub is still settling; retry.
            }
            targetBranch = liveBranch
            if (attempt < 4) delay(1_000)
        }
        val bootstrapCommitSha = bootstrap.optJSONObject("commit")?.optString("sha")
        if (bootstrapCommitSha.isNullOrBlank()) {
            throw CloneException("GitHub did not return a bootstrap commit for the new repository.")
        }
        report(onProgress, STAGE_COPY, 0, files.size)

        // Dispatch the one-time copy workflow. GitHub indexes new workflow files
        // slightly after the push that creates them; retry 404/422 before fatal.
        var dispatched = false
        var dispatchError: GitHubClient.GhException? = null
        for (attempt in 0 until 6) {
            try {
                targetClient.dispatchWorkflow(
                    CLONE_COPY_WORKFLOW,
                    mapOf(
                        "source_sha" to sourceCommitSha,
                        "bootstrap_commit" to bootstrapCommitSha,
                        "expected_files" to files.size.toString()
                    ),
                    ref = targetBranch
                )
                dispatched = true
                break
            } catch (e: GitHubClient.GhException) {
                dispatchError = e
                if (e.code != 404 && e.code != 422) throw e
                delay(2_000)
            }
        }
        if (!dispatched) {
            val detail = dispatchError?.let { " (${ghMessage(it)})" } ?: ""
            throw CloneException("GitHub could not start the clone copy workflow$detail. Connect to the repository anyway (Settings → GitHub clone → Connect existing clone) and use Sync from source to fill in the missing files.")
        }

        // Poll the copy workflow's status file to completion. The bot hands off to a
        // cron trigger after a 30s fast path; this app owns the wait inline with the
        // SAME guard budgets (START/STALL/DEADLINE) and the same precedence order
        // (deadline before stall; failed/complete before both).
        val startedAt = System.currentTimeMillis()
        var lastAdvanceAt = startedAt
        var lastKey = ""
        try {
            while (true) {
                delay(POLL_MS)
                val status = readCloneCopyStatus(targetClient, targetBranch)
                val now = System.currentTimeMillis()
                if (status != null) {
                    val key = "${status.optString("state")}:${status.optLong("done")}:${status.optLong("total")}"
                    if (key != lastKey) {
                        lastKey = key
                        lastAdvanceAt = now
                        when (status.optString("state")) {
                            "copying" -> report(onProgress, STAGE_COPY, status.optInt("done", 0), status.optInt("total", 0))
                            "finalizing" -> report(onProgress, STAGE_FINALIZE, status.optInt("done", files.size), status.optInt("total", files.size))
                        }
                    }
                    when (status.optString("state")) {
                        "failed" -> {
                            val err = status.optString("error", "")
                            failWithCancel(
                                targetClient,
                                "The clone copy workflow failed${if (err.isNotBlank()) ": $err" else "."} Connect to the repository anyway (Settings → GitHub clone → Connect existing clone) and use Sync from source to fill in the missing files."
                            )
                        }
                        "complete" -> return finalize(token, login, name, repo, targetBranch, sourceCommitSha, bootstrapCommitSha, onProgress)
                    }
                    if (now - startedAt > DEADLINE_MS) {
                        failWithCancel(targetClient, "The clone copy workflow took too long.")
                    }
                    if (now - lastAdvanceAt > STALL_MS) {
                        failWithCancel(targetClient, "The clone copy workflow stopped reporting progress (the Actions run may have failed).")
                    }
                } else {
                    if (now - startedAt > START_MS) {
                        failWithCancel(targetClient, "The clone copy workflow never started.")
                    }
                    if (now - startedAt > DEADLINE_MS) {
                        failWithCancel(targetClient, "The clone copy workflow took too long.")
                    }
                }
            }
        } catch (e: GitHubClient.GhException) {
            throw CloneException("GitHub rejected a write to the new repository (${e.code}): ${ghMessage(e)}")
        }
    }

    /**
     * bot finalizeShadowClone(): everything that must happen AFTER the copy workflow
     * reports complete. `job` fields carried as parameters here.
     */
    private suspend fun finalize(
        token: String,
        login: String,
        name: String,
        repo: String,
        branchIn: String,
        sourceCommitSha: String,
        bootstrapCommitSha: String,
        onProgress: (suspend (stage: String, done: Int, total: Int) -> Unit)?
    ): CloneResult {
        val source = GitHubClient(token, SOURCE.substringBefore("/"), SOURCE.substringAfter("/"))
        val targetClient = GitHubClient(token, login, name)
        var targetBranch = if (branchIn.isNotBlank()) branchIn else DEFAULT_BRANCH

        // Re-enumerate the source tree at the recorded revision (the file list is
        // too large to carry through the job record — same reason as the bot).
        val files = enumerateSourceFiles(source, sourceCommitSha)

        try {
            // bug-63: the run's GITHUB_TOKEN may not write the .github/workflows tree — the
            // copy workflow excluded them on purpose. Finish that job here with the
            // user's PAT (Contents API PUT, sha of any existing file included).
            val workflowFiles = files.filter {
                it.path.startsWith(".github/workflows/") && it.path != ".github/workflows/$CLONE_COPY_WORKFLOW"
            }
            for (file in workflowFiles) {
                val blob = source.gitBlobBase64(file.sha)
                val content = blob.optString("content")
                if (blob.optString("encoding") != "base64" || content.isBlank()) {
                    throw CloneException("Could not read source file ${file.path}.")
                }
                val existingSha = try {
                    targetClient.readFile(file.path, targetBranch)?.second
                } catch (e: GitHubClient.GhException) {
                    if (e.code != 404) throw e else null
                }
                targetClient.putFile(
                    file.path,
                    Base64.decode(content.replace("\n", ""), Base64.DEFAULT),
                    "clipforge: copy workflow file ${file.path}",
                    sha = existingSha,
                    branch = targetBranch
                )
            }
            // Best-effort delete of the one-time workflow (a lingering copy workflow
            // is inert — workflow_dispatch only, requires the exact bootstrap inputs).
            try {
                targetClient.readFile(".github/workflows/$CLONE_COPY_WORKFLOW", targetBranch)?.let { existing ->
                    targetClient.deleteFile(
                        ".github/workflows/$CLONE_COPY_WORKFLOW",
                        existing.second,
                        "clipforge: remove one-time clone copy workflow",
                        branch = targetBranch
                    )
                }
            } catch (_: Exception) {}

            // The workflow built the final tree on top of the bootstrap commit and
            // fast-forwarded the live default branch; the PAT writes above layered
            // the source workflow files on top. Read the head back for verification.
            val finalCommitSha = targetClient.branchRefShaOrNull(targetBranch)
            if (finalCommitSha.isNullOrBlank() || finalCommitSha == bootstrapCommitSha) {
                throw CloneException("Shadow Clone verification failed: the copy workflow reported completion but the repository head did not advance past the bootstrap commit.")
            }
            val finalTreeSha = try {
                targetClient.gitCommitTreeSha(finalCommitSha)
            } catch (e: GitHubClient.GhException) {
                throw CloneException("Shadow Clone verification failed: GitHub did not return the copied file tree.")
            }
            report(onProgress, STAGE_FINALIZE, files.size, files.size)

            // bug-47: normalize to main when the account's default-branch preference
            // made the first branch something else — explicit create-ref -> PATCH
            // default_branch -> delete-old-ref (synchronous, unlike branch rename).
            if (targetBranch != DEFAULT_BRANCH) {
                targetClient.createRef("refs/heads/$DEFAULT_BRANCH", finalCommitSha)
                targetClient.patchDefaultBranch(DEFAULT_BRANCH)
                targetClient.deleteBranchRef(targetBranch)
                targetBranch = DEFAULT_BRANCH
            }

            // Post-conditions: the clone is only "created" when the repo's DEFAULT
            // branch head is the commit carrying the full copied tree, with enough
            // blobs — verified against the live API, never against our own return.
            val verifyRepo = targetClient.repoDetails()
            val verifyBranch = if (verifyRepo.optString("default_branch").isNotBlank()) verifyRepo.optString("default_branch") else targetBranch
            val verifySha = targetClient.branchRefShaOrNull(verifyBranch)
            if (verifySha != finalCommitSha) {
                throw CloneException("Shadow Clone verification failed: the repository's default branch ($verifyBranch) does not point at the copied file tree.")
            }
            val verifyTree = targetClient.gitTreeRecursive(finalTreeSha)
            val verifyEntries = verifyTree.optJSONArray("tree")
            var verifyBlobs = 0
            if (verifyEntries != null) {
                for (i in 0 until verifyEntries.length()) {
                    val entry = verifyEntries.optJSONObject(i) ?: continue
                    if (entry.optString("type") == "blob") verifyBlobs += 1
                }
            }
            if (verifyTree.optBoolean("truncated", false) || verifyBlobs < files.size) {
                throw CloneException("Shadow Clone verification failed: the pushed file tree holds $verifyBlobs files, expected at least ${files.size}.")
            }
        } catch (e: GitHubClient.GhException) {
            // bug-46 wording: repo-side Git Data API failures must not read as PAT-scope advice.
            throw CloneException("GitHub rejected a write to the new repository (${e.code}): ${ghMessage(e)}")
        }

        return CloneResult(
            repo = repo,
            login = login,
            name = name,
            branch = targetBranch,
            sourceSha = sourceCommitSha,
            copiedFiles = files.size
        )
    }
}
