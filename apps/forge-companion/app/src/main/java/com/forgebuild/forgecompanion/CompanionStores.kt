package com.forgebuild.forgecompanion

import android.content.Context
import com.forgebuild.engine.data.CacheFirstStore

class AppsStore(
    context: Context,
    gitHubService: GitHubService
) : CacheFirstStore<AppSummary>(
    context = context,
    cacheFileName = "cached_apps.json",
    toJson = { it.toJson() },
    fromJson = { AppSummary.fromJson(it) },
    fetchRemote = { gitHubService.fetchApps() },
    keyOf = { it.slug }
)

class ReleasesStore(
    context: Context,
    slug: String,
    gitHubService: GitHubService
) : CacheFirstStore<ReleaseInfo>(
    context = context,
    cacheFileName = "cached_releases_${slug.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.json",
    toJson = { it.toJson() },
    fromJson = { ReleaseInfo.fromJson(it) },
    fetchRemote = { gitHubService.fetchReleases(slug) },
    keyOf = { it.id.toString() }
)
