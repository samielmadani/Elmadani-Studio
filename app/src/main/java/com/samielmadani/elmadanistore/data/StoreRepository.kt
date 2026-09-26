package com.samielmadani.elmadanistudio.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Base64
import androidx.core.content.FileProvider
import com.samielmadani.elmadanistudio.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

class StoreRepository(private val context: Context) {
    private val client = OkHttpClient()
    private val preferences = context.getSharedPreferences("store", Context.MODE_PRIVATE)
    private val trackingStore = TrackingStore(context)
    private val username = "samielmadani"
    private val selfRepo = "samielmadani/Elmadani-Studio"
    private val defaultIgnoredRepos = setOf(selfRepo)

    @Volatile
    var latestSelfUpdate: StoreApp? = null
        private set

    @Volatile
    var rateLimitStatus: RateLimitStatus = RateLimitStatus()
        private set

    suspend fun loadApps(onAppUpdated: (StoreApp) -> Unit = {}): List<StoreApp> = withContext(Dispatchers.IO) {
        val refreshStartedAt = System.nanoTime()
        Log.d(TAG, "Refresh started")
        try {
            val repositories = getJsonArray("https://api.github.com/users/$username/repos?type=owner&per_page=100", reportHttpError = true) ?: return@withContext emptyList()
            val ignored = ignoredRepos()
            val repoObjects = (0 until repositories.length()).mapNotNull { repositories.optJSONObject(it) }
                .filterNot { ignored.contains(repoKey(username, it.optString("name"))) }
            val limit = Semaphore(REPO_CONCURRENCY)
            val (apps, selfUpdate) = coroutineScope {
                val selfUpdateTask = async { loadSelfUpdate() }
                val appTasks = repoObjects.map { repo ->
                    async {
                        limit.withPermit {
                            val app = loadRepoApp(repo, ignored)
                            if (app != null) onAppUpdated(app)
                            app
                        }
                    }
                }
                appTasks.awaitAll().filterNotNull() to selfUpdateTask.await()
            }
            val currentRepoKeys = repoObjects.mapNotNull { it.optString("name").takeIf(String::isNotBlank)?.let { name -> repoKey(username, name) } }.toSet()
            val cachedByRepo = cachedApps().filter { repoKey(it.owner, it.repo) in currentRepoKeys }.associateBy { repoKey(it.owner, it.repo) }
            val freshByRepo = apps.associateBy { repoKey(it.owner, it.repo) }
            val mergedApps = repoObjects.mapNotNull { repo ->
                val owner = repo.optJSONObject("owner")?.optString("login") ?: username
                val key = repoKey(owner, repo.optString("name"))
                freshByRepo[key] ?: cachedByRepo[key]
            }
            preferences.edit().putString("apps_cache", JSONArray().apply { mergedApps.forEach { put(it.toCacheJson()) } }.toString()).apply()
            latestSelfUpdate = selfUpdate
            mergedApps
        } finally {
            Log.d(TAG, "Refresh finished in ${elapsedMillis(refreshStartedAt)} ms")
        }
    }

    suspend fun loadWorkflowProgress(apps: List<StoreApp>): WorkflowProgressScan = withContext(Dispatchers.IO) {
        val limit = Semaphore(3)
        val checks = coroutineScope {
            apps.distinctBy { workflowRepoKey(it) }.map { app ->
                async {
                    limit.withPermit {
                        val key = workflowRepoKey(app)
                        try {
                            WorkflowCheck(key, true, findWorkflowProgress(app))
                        } catch (error: CancellationException) {
                            throw error
                        } catch (error: Exception) {
                            Log.w(TAG, "Could not check workflow progress for $key", error)
                            WorkflowCheck(key, false, null)
                        }
                    }
                }
            }.awaitAll()
        }
        WorkflowProgressScan(
            checkedRepos = checks.filter { it.checked }.map { it.repoKey }.toSet(),
            activeRuns = checks.mapNotNull { check -> check.progress?.let { check.repoKey to it } }.toMap()
        )
    }

    private fun findWorkflowProgress(app: StoreApp): WorkflowProgress? {
        val baseUrl = "https://api.github.com/repos/${app.owner}/${app.repo}/actions"
        val runs = getJson("$baseUrl/runs?status=in_progress&per_page=1", reportHttpError = true)
            ?: error("GitHub returned no workflow run data")
        val run = runs.optJSONArray("workflow_runs")?.let { workflowRuns ->
            (0 until workflowRuns.length()).mapNotNull { workflowRuns.optJSONObject(it) }.firstOrNull()
        } ?: return null
        val runId = run.optLong("id").takeIf { it > 0L } ?: error("GitHub returned an invalid workflow run")
        var page = 1
        var fetchedJobs = 0
        var totalJobs = 1
        val stepStatuses = buildList {
            while (fetchedJobs < totalJobs) {
                val jobsResponse = getJson("$baseUrl/runs/$runId/jobs?per_page=100&page=$page", reportHttpError = true)
                    ?: error("GitHub returned no workflow job data")
                val jobs = jobsResponse.optJSONArray("jobs") ?: break
                totalJobs = jobsResponse.optInt("total_count", jobs.length())
                fetchedJobs += jobs.length()
                for (jobIndex in 0 until jobs.length()) {
                    val steps = jobs.optJSONObject(jobIndex)?.optJSONArray("steps") ?: continue
                    for (stepIndex in 0 until steps.length()) {
                        add(steps.optJSONObject(stepIndex)?.optString("status").orEmpty())
                    }
                }
                if (jobs.length() == 0) break
                page++
            }
        }
        return WorkflowProgress(calculateWorkflowPercent(stepStatuses))
    }

    private fun workflowRepoKey(app: StoreApp) = "${app.owner}/${app.repo}".lowercase()

    private data class WorkflowCheck(val repoKey: String, val checked: Boolean, val progress: WorkflowProgress?)

    private suspend fun loadRepoApp(repo: JSONObject, ignored: Set<String>): StoreApp? {
        val owner = repo.optJSONObject("owner")?.optString("login") ?: username
        val name = repo.optString("name")
        if (name.isBlank() || ignored.contains(repoKey(owner, name))) return null
        val startedAt = System.nanoTime()
        Log.d(TAG, "Repo refresh started: $owner/$name")
        try {
            val release = getJson("https://api.github.com/repos/$owner/$name/releases/latest") ?: return null
            val assets = release.optJSONArray("assets") ?: return null
            val apk = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk", true) } ?: return null
            val metadata = loadMetadata(owner, name, assets)
            val packageName = metadata?.optString("packageName").orEmpty().ifBlank { null }
            val releaseVersionCode = releaseVersionCode(release, metadata)
            val history = getJsonArray("https://api.github.com/repos/$owner/$name/releases?per_page=20")?.let { releases ->
                (0 until releases.length()).mapNotNull { index ->
                    val item = releases.optJSONObject(index) ?: return@mapNotNull null
                    val itemAssets = item.optJSONArray("assets") ?: return@mapNotNull null
                    val itemApk = (0 until itemAssets.length()).mapNotNull { itemAssets.optJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk", true) } ?: return@mapNotNull null
                    ReleaseSummary(item.optString("tag_name"), item.optString("body"), item.optString("published_at"), itemApk.optString("name"), itemApk.optLong("size"), itemApk.optString("browser_download_url"), item.optBoolean("prerelease"))
                }
            }.orEmpty()
            val displayName = overrideName(name).orEmpty().ifBlank { metadata?.optString("name").orEmpty().ifBlank { name } }
            val description = normalizeDescription(metadata?.optString("description"))
                ?: normalizeDescription(repo.optString("description"))
                ?: "No description provided."
            val app = StoreApp(owner = owner, repo = name, name = displayName, description = description, iconUrl = iconFor(owner, name), repositoryUrl = "https://github.com/$owner/$name", releaseId = release.optLong("id"), version = release.optString("tag_name"), releaseNotes = release.optString("body"), publishedAt = release.optString("published_at"), assetName = apk.optString("name"), assetSize = apk.optLong("size"), downloadUrl = apk.optString("browser_download_url"), prerelease = release.optBoolean("prerelease"), packageName = packageName, releaseVersionCode = releaseVersionCode, releases = history)
            val tracked = trackingStore.recordLatest(app)
            val reconciled = trackingStore.reconcileInstalled(app)
            val installState = reconciled ?: tracked
            return app.copy(installedVersion = installState.installedVersionName, installedVersionCode = installState.installedVersionCode)
        } finally {
            Log.d(TAG, "Repo refresh finished: $owner/$name in ${elapsedMillis(startedAt)} ms")
        }
    }

    private suspend fun loadSelfUpdate(): StoreApp? {
        val release = getJson("https://api.github.com/repos/$selfRepo/releases/latest") ?: return null
        val assets = release.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).mapNotNull { assets.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", true) } ?: return null
        val packageInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        @Suppress("DEPRECATION")
        val installedCode = packageInfo?.let { if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else it.versionCode.toLong() }
        val app = StoreApp(
            owner = "samielmadani", repo = "Elmadani-Studio", name = "Elmadani Studio",
            description = "The store application", iconUrl = "android.resource://${context.packageName}/${R.mipmap.ic_launcher}",
            repositoryUrl = "https://github.com/$selfRepo", releaseId = release.optLong("id"),
            version = release.optString("tag_name"), releaseNotes = release.optString("body"),
            publishedAt = release.optString("published_at"), assetName = apk.optString("name"),
            assetSize = apk.optLong("size"), downloadUrl = apk.optString("browser_download_url"),
            prerelease = release.optBoolean("prerelease"), packageName = context.packageName,
            releaseVersionCode = releaseVersionCode(release),
            installedVersion = packageInfo?.versionName, installedVersionCode = installedCode
        )
        trackingStore.recordLatest(app)
        return app
    }

    suspend fun download(app: StoreApp, onProgress: (Int) -> Unit): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "${app.repo}-${app.version}.apk")
        val request = Request.Builder().url(app.downloadUrl).header("User-Agent", "Elmadani-Studio").build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download failed: ${response.code}" }
            val body = response.body ?: error("Empty download")
            val total = body.contentLength()
            var read = 0L
            body.byteStream().use { input -> FileOutputStream(target).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    read += count
                    if (total > 0) onProgress((read * 100 / total).toInt())
                }
            } }
        }
        val packageName = app.packageName ?: context.packageManager.getPackageArchiveInfo(target.path, 0)?.packageName
        packageName?.let { trackingStore.updatePackageName(app.repo, it) }
        target
    }

    fun install(file: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun clearDownloads() { context.cacheDir.listFiles()?.filter { it.extension == "apk" }?.forEach(File::delete) }
    fun token(): String = preferences.getString("github_token", "").orEmpty()
    fun cachedApps(): List<StoreApp> {
        val cached = preferences.getString("apps_cache", null) ?: return emptyList()
        if (!cached.trimStart().startsWith("[")) {
            return cached.lineSequence().mapNotNull { line ->
                val fields = line.split('|', limit = 4)
                if (fields.size < 3 || fields[0].isBlank() || fields[1].isBlank()) return@mapNotNull null
                StoreApp(
                    owner = fields[0],
                    repo = fields[1],
                    name = fields[1],
                    description = fields.getOrElse(3) { "No description provided." }.ifBlank { "No description provided." },
                    iconUrl = iconFor(fields[0], fields[1]),
                    repositoryUrl = "https://github.com/${fields[0]}/${fields[1]}",
                    releaseId = 0L,
                    version = fields[2],
                    releaseNotes = "",
                    publishedAt = "",
                    assetName = "",
                    assetSize = 0L,
                    downloadUrl = "",
                    prerelease = false
                )
            }.toList()
        }
        return runCatching {
        val json = JSONArray(cached)
        (0 until json.length()).mapNotNull { index ->
            runCatching { json.getJSONObject(index).toStoreApp() }.getOrNull()
        }
        }.getOrDefault(emptyList())
    }

    fun saveToken(value: String) { preferences.edit().putString("github_token", value.trim()).apply() }
    fun isManualRefreshThrottled(now: Long = System.currentTimeMillis()) = now - preferences.getLong("manual_refresh_at", 0L) < 60_000L
    fun markManualRefresh(now: Long = System.currentTimeMillis()) { preferences.edit().putLong("manual_refresh_at", now).apply() }
    fun ignoredRepos(): Set<String> = preferences.getStringSet("ignored_repos", defaultIgnoredRepos)?.map(::normaliseRepo)?.filter(String::isNotBlank)?.toSet().orEmpty()
    fun saveIgnoredRepos(value: String) { preferences.edit().putStringSet("ignored_repos", value.split(',', '\n').map(::normaliseRepo).filter(String::isNotBlank).toSet()).apply() }
    fun overrideName(repo: String): String? = preferences.getString("name_$repo", null)
    fun saveOverrideName(repo: String, name: String) { preferences.edit().putString("name_$repo", name.trim()).apply() }

    private fun getJson(url: String, reportHttpError: Boolean = false): JSONObject? = request(url, reportHttpError)?.let { runCatching { JSONObject(it) }.getOrNull() }

    private fun getJsonArray(url: String, reportHttpError: Boolean = false): JSONArray? = request(url, reportHttpError)?.let { runCatching { JSONArray(it) }.getOrNull() }

    private fun request(url: String, reportHttpError: Boolean = false): String? {
        val cacheKey = "cache_${url.hashCode()}"
        val cached = preferences.getString("${cacheKey}_body", null)
        val etag = preferences.getString("${cacheKey}_etag", null)
        val token = token()
        val startedAt = System.nanoTime()
        var outcome = "failed"
        return runCatching {
            val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").header("User-Agent", "Elmadani-Studio")
            .apply {
                etag?.let { header("If-None-Match", it) }
                token.takeIf(String::isNotBlank)?.let { header("Authorization", "Bearer $it") }
            }.build()
            client.newCall(request).execute().use { response ->
                updateRateLimit(response)
                val body = response.body?.string().orEmpty()
                when {
                    response.code == 304 -> {
                        outcome = if (cached == null) "304-without-body" else "304-cache-hit"
                        cached
                    }
                    response.code == 403 && response.header("X-RateLimit-Remaining") == "0" -> throw RateLimitException(rateLimitStatus.resetAt)
                    response.isSuccessful -> {
                        outcome = if (etag != null) "${response.code}-cache-revalidated" else "${response.code}-cache-miss"
                        preferences.edit().putString("${cacheKey}_body", body).apply()
                        response.header("ETag")?.let { preferences.edit().putString("${cacheKey}_etag", it).apply() }
                        body
                    }
                    reportHttpError -> throw GithubApiException(response.code, body)
                    else -> {
                        outcome = "${response.code}-error"
                        null
                    }
                }
            }
        }.getOrElse { error ->
            if (error is RateLimitException || error is GithubApiException || reportHttpError) throw error
            Log.e(TAG, "GitHub request failed: $url", error)
            null
        }.also {
            Log.d(TAG, "GitHub request finished: $url outcome=$outcome in ${elapsedMillis(startedAt)} ms")
        }
    }

    private fun updateRateLimit(response: okhttp3.Response) {
        rateLimitStatus = RateLimitStatus(
            remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull(),
            limit = response.header("X-RateLimit-Limit")?.toIntOrNull(),
            resetAt = response.header("X-RateLimit-Reset")?.toLongOrNull()
        )
        preferences.edit().putInt("rate_remaining", rateLimitStatus.remaining ?: -1).putLong("rate_reset", rateLimitStatus.resetAt ?: 0L).apply()
    }

    private fun loadMetadata(owner: String, repo: String, assets: JSONArray): JSONObject? {
        listOf("store.json", "elmadani-studio.json").firstNotNullOfOrNull { file ->
            getJson("https://api.github.com/repos/$owner/$repo/contents/$file")?.let { content ->
                content.optString("content").takeIf(String::isNotBlank)?.let { encoded ->
                    runCatching { JSONObject(String(Base64.decode(encoded.replace("\n", ""), Base64.DEFAULT))) }.getOrNull()
                }
            }
        }?.let { return it }
        return assets.metadataAsset()?.let { getJson(it.optString("browser_download_url")) }
    }

    private fun releaseVersionCode(release: JSONObject, metadata: JSONObject? = null): Long? =
        metadata?.optLong("versionCode")?.takeIf { it > 0L }
            ?: Regex("(?i)\\bversion\\s*code\\s*:\\s*`?(\\d+)").find(release.optString("body"))
                ?.groupValues?.get(1)?.toLongOrNull()?.takeIf { it > 0L }

    suspend fun recordInstalled(app: StoreApp): TrackedAppEntity? = trackingStore.markInstalled(app)
    fun trackedApps(): Flow<List<TrackedAppEntity>> = trackingStore.flow()
    suspend fun recordPackageInstalled(packageName: String): TrackedAppEntity? = trackingStore.markPackageInstalled(packageName)

    private fun iconFor(owner: String, repo: String) = "https://raw.githubusercontent.com/$owner/$repo/main/public/icon-512.png"

    private fun JSONArray.metadataAsset(): JSONObject? = (0 until length()).mapNotNull { optJSONObject(it) }.firstOrNull { it.optString("name").equals("elmadani-app.json", true) }

    private fun StoreApp.toCacheJson() = JSONObject().apply {
        put("owner", owner)
        put("repo", repo)
        put("name", name)
        put("description", description.take(500))
        put("iconUrl", iconUrl ?: JSONObject.NULL)
        put("repositoryUrl", repositoryUrl)
        put("releaseId", releaseId)
        put("version", version)
        put("releaseNotes", releaseNotes.take(1_000))
        put("publishedAt", publishedAt)
        put("assetName", assetName)
        put("assetSize", assetSize)
        put("downloadUrl", downloadUrl)
        put("prerelease", prerelease)
        put("packageName", packageName ?: JSONObject.NULL)
        put("releaseVersionCode", releaseVersionCode ?: JSONObject.NULL)
        put("installedVersion", installedVersion ?: JSONObject.NULL)
        put("installedVersionCode", installedVersionCode ?: JSONObject.NULL)
        put("releases", JSONArray().apply {
            releases.take(5).forEach { release ->
                put(JSONObject().apply {
                    put("version", release.version)
                    put("notes", release.notes.take(300))
                    put("publishedAt", release.publishedAt)
                    put("assetName", release.assetName)
                    put("assetSize", release.assetSize)
                    put("downloadUrl", release.downloadUrl)
                    put("prerelease", release.prerelease)
                })
            }
        })
    }

    private fun JSONObject.toStoreApp() = StoreApp(
        owner = getString("owner"),
        repo = getString("repo"),
        name = getString("name"),
        description = getString("description"),
        iconUrl = optNullableString("iconUrl"),
        repositoryUrl = getString("repositoryUrl"),
        releaseId = getLong("releaseId"),
        version = getString("version"),
        releaseNotes = getString("releaseNotes"),
        publishedAt = getString("publishedAt"),
        assetName = getString("assetName"),
        assetSize = getLong("assetSize"),
        downloadUrl = getString("downloadUrl"),
        prerelease = getBoolean("prerelease"),
        packageName = optNullableString("packageName"),
        releaseVersionCode = optNullableLong("releaseVersionCode"),
        installedVersion = optNullableString("installedVersion"),
        installedVersionCode = optNullableLong("installedVersionCode"),
        releases = optJSONArray("releases")?.let { items ->
            (0 until items.length()).mapNotNull { index ->
                items.optJSONObject(index)?.let { release ->
                    ReleaseSummary(
                        version = release.optString("version"),
                        notes = release.optString("notes"),
                        publishedAt = release.optString("publishedAt"),
                        assetName = release.optString("assetName"),
                        assetSize = release.optLong("assetSize"),
                        downloadUrl = release.optString("downloadUrl"),
                        prerelease = release.optBoolean("prerelease")
                    )
                }
            }
        }.orEmpty()
    )

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeUnless { it == "null" }

    private fun JSONObject.optNullableLong(name: String): Long? =
        if (isNull(name)) null else optLong(name)

    private fun normalizeDescription(raw: String?): String? = raw?.trim()?.takeUnless { it.equals("null", true) || it.equals("<null>", true) }?.takeIf { it.isNotBlank() }

    private fun repoKey(owner: String, repo: String) = "${owner.lowercase()}/${repo.lowercase()}"
    private fun normaliseRepo(value: String) = value.trim().trim('/').lowercase()
    private fun elapsedMillis(startedAt: Long) = (System.nanoTime() - startedAt) / 1_000_000

    private companion object {
        const val TAG = "StoreRepository"
        const val REPO_CONCURRENCY = 4
    }
}

class RateLimitException(resetAt: Long?) : Exception("GitHub is rate limited. Retry at ${resetAt?.let { java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it * 1000)) } ?: "the reset time"}.")

class GithubApiException(val statusCode: Int, responseBody: String) : Exception("GitHub API returned HTTP $statusCode: ${responseBody.take(500)}")
