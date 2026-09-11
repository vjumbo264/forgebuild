package com.forgebuild.aandroidversionofforgebuild

data class BuildTask(
    val id: String,
    val title: String,
    val status: String, // done, in_progress, pending, failed
    val notes: String
)

data class BuildHistoryEntry(
    val session: String,
    val event: String
)

data class BuildState(
    val project: String,
    val buildComplete: Boolean,
    val updatedBy: String,
    val currentTask: String,
    val tasks: List<BuildTask>,
    val history: List<BuildHistoryEntry>
)

data class AppRelease(
    val tagName: String,
    val version: String, // e.g. "v1"
    val name: String,
    val body: String,
    val publishedAt: String,
    val apkDownloadUrl: String?,
    val apkSizeFormatted: String?,
    val apkFileName: String?,
    val manifestUrl: String?
)

data class ForgeApp(
    val slug: String,
    val name: String,
    val buildState: BuildState? = null,
    val latestRelease: AppRelease? = null,
    val releases: List<AppRelease> = emptyList(),
    val promptHistory: String? = null
)

data class WorkflowRun(
    val id: Long,
    val name: String,
    val status: String, // queued, in_progress, completed
    val conclusion: String?, // success, failure, cancelled, null
    val htmlUrl: String,
    val createdAt: String,
    val updatedAt: String,
    val runNumber: Int,
    val event: String,
    val headCommitMessage: String
)
