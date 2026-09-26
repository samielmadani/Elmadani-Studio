package com.samielmadani.elmadanistudio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.samielmadani.elmadanistudio.data.RateLimitStatus
import com.samielmadani.elmadanistudio.data.SortMode
import com.samielmadani.elmadanistudio.data.StoreApp
import com.samielmadani.elmadanistudio.data.StoreRepository
import com.samielmadani.elmadanistudio.data.WorkflowProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class UpdateNotice(val app: StoreApp, val message: String)

class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = StoreRepository(application)
    private val _apps = MutableStateFlow<List<StoreApp>>(emptyList())
    val apps: StateFlow<List<StoreApp>> = _apps.asStateFlow()
    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _refreshingRepos = MutableStateFlow<Set<String>>(emptySet())
    val refreshingRepos: StateFlow<Set<String>> = _refreshingRepos.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()
    private val _failedDownloads = MutableStateFlow<Set<String>>(emptySet())
    val failedDownloads: StateFlow<Set<String>> = _failedDownloads.asStateFlow()
    private val _workflowProgress = MutableStateFlow<Map<String, WorkflowProgress>>(emptyMap())
    val workflowProgress: StateFlow<Map<String, WorkflowProgress>> = _workflowProgress.asStateFlow()
    private val _rateLimit = MutableStateFlow(RateLimitStatus())
    val rateLimit: StateFlow<RateLimitStatus> = _rateLimit.asStateFlow()
    private val _selfUpdate = MutableStateFlow<StoreApp?>(null)
    val selfUpdate: StateFlow<StoreApp?> = _selfUpdate.asStateFlow()
    private val _updateNotice = MutableStateFlow<UpdateNotice?>(null)
    val updateNotice: StateFlow<UpdateNotice?> = _updateNotice.asStateFlow()
    private val announcedReleases = mutableSetOf<String>()
    private var refreshJob: Job? = null
    private var workflowPollingJob: Job? = null

    init {
        _apps.value = repository.cachedApps()
        _loading.value = _apps.value.isEmpty()
        viewModelScope.launch {
            repository.trackedApps().collectLatest { trackedApps ->
                val trackedByRepo = trackedApps.associateBy { it.repo.lowercase() }
                _apps.value = _apps.value.map { app ->
                    trackedByRepo[app.repo.lowercase()]?.let { tracked ->
                        app.copy(
                            installedVersion = tracked.installedVersionName,
                            installedVersionCode = tracked.installedVersionCode,
                            packageName = tracked.packageName ?: app.packageName
                        )
                    } ?: app
                }
                _selfUpdate.value = _selfUpdate.value?.let { app ->
                    trackedByRepo[app.repo.lowercase()]?.let { tracked ->
                        app.copy(
                            installedVersion = tracked.installedVersionName,
                            installedVersionCode = tracked.installedVersionCode,
                            packageName = tracked.packageName ?: app.packageName
                        )
                    } ?: app
                }
            }
        }
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        repository.markManualRefresh()
        refreshJob = viewModelScope.launch {
            _loading.value = _apps.value.isEmpty()
            _refreshingRepos.value = _apps.value.map { it.repo.lowercase() }.toSet()
            _error.value = null
            try {
                val result = repository.loadApps { freshApp ->
                    _refreshingRepos.update { it - freshApp.repo.lowercase() }
                    _apps.update { currentApps ->
                        val existingIndex = currentApps.indexOfFirst {
                            it.owner.equals(freshApp.owner, ignoreCase = true) && it.repo.equals(freshApp.repo, ignoreCase = true)
                        }
                        if (existingIndex < 0) currentApps + freshApp
                        else currentApps.toMutableList().also { it[existingIndex] = freshApp }
                    }
                }
                if (result.isNotEmpty()) _apps.value = result
                _selfUpdate.value = repository.latestSelfUpdate
                updateWorkflowProgress(_apps.value + result + listOfNotNull(_selfUpdate.value))
                syncWorkflowPolling()
                val updates = pendingUpdates().filter { app -> announcedReleases.add("${app.owner}/${app.repo}:${app.releaseId}") }
                if (updates.isNotEmpty()) {
                    val app = updates.first()
                    _updateNotice.value = UpdateNotice(app, if (updates.size == 1) "${app.name} update available" else "${updates.size} updates available")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _error.value = error.message ?: "Could not connect to GitHub"
            } finally {
                _refreshingRepos.value = emptySet()
                _rateLimit.value = repository.rateLimitStatus
                _loading.value = false
                refreshJob = null
            }
        }
    }

    private suspend fun updateWorkflowProgress(apps: List<StoreApp>): Set<String> {
        val distinctApps = apps.distinctBy(::workflowRepoKey)
        val checked = repository.loadWorkflowProgress(distinctApps)
        val appKeys = distinctApps.map(::workflowRepoKey).toSet()
        _workflowProgress.update { current ->
            current.filterKeys { it in appKeys && it !in checked.checkedRepos } + checked.activeRuns
        }
        return checked.checkedRepos
    }

    private fun syncWorkflowPolling() {
        if (_workflowProgress.value.isEmpty()) {
            workflowPollingJob?.cancel()
            workflowPollingJob = null
            return
        }
        if (workflowPollingJob?.isActive == true) return
        workflowPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(WORKFLOW_POLL_INTERVAL_MS)
                val activeKeys = _workflowProgress.value.keys
                val activeApps = (_apps.value + listOfNotNull(_selfUpdate.value))
                    .distinctBy(::workflowRepoKey)
                    .filter { workflowRepoKey(it) in activeKeys }
                if (activeApps.isEmpty()) break
                val checked = updateWorkflowProgress(activeApps)
                val stillActive = _workflowProgress.value.keys
                if (activeKeys.any { it in checked && it !in stillActive }) {
                    delay(WORKFLOW_COMPLETION_REFRESH_DELAY_MS)
                    refresh()
                    break
                }
            }
        }
    }

    private fun workflowRepoKey(app: StoreApp) = "${app.owner}/${app.repo}".lowercase()

    fun pendingUpdates(): List<StoreApp> {
        val updates = _apps.value.filter { it.hasUpdate }
        val selfUpdate = _selfUpdate.value?.takeIf { it.hasUpdate }
        return (updates + listOfNotNull(selfUpdate)).distinctBy { "${it.owner}/${it.repo}" }
    }

    fun dismissUpdateNotice() { _updateNotice.value = null }

    fun download(app: StoreApp, onReady: (File) -> Unit) {
        viewModelScope.launch {
            _failedDownloads.value = _failedDownloads.value - app.repo
            runCatching {
                repository.download(app) { progress ->
                    _downloadProgress.value = _downloadProgress.value + (app.repo to progress)
                }
            }.onSuccess {
                _failedDownloads.value = _failedDownloads.value - app.repo
                onReady(it)
            }.onFailure {
                _failedDownloads.value = _failedDownloads.value + app.repo
            }
        }
    }

    suspend fun downloadFile(app: StoreApp): File? {
        _failedDownloads.value = _failedDownloads.value - app.repo
        return runCatching {
            repository.download(app) { progress ->
                _downloadProgress.value = _downloadProgress.value + (app.repo to progress)
            }
        }.onSuccess {
            _failedDownloads.value = _failedDownloads.value - app.repo
            return it
        }.onFailure {
            _failedDownloads.value = _failedDownloads.value + app.repo
            return null
        }.getOrNull()
    }

    fun install(file: File) = repository.install(file)
    fun clearDownloads() = repository.clearDownloads()
    fun saveToken(token: String) = repository.saveToken(token)
    fun ignoredRepos(): Set<String> = repository.ignoredRepos()
    fun saveIgnoredRepos(repos: String) = repository.saveIgnoredRepos(repos)
    fun overrideName(repo: String): String? = repository.overrideName(repo)
    fun saveOverrideName(repo: String, name: String) { repository.saveOverrideName(repo, name) }
    fun sorted(mode: SortMode): List<StoreApp> = when (mode) {
        SortMode.NAME -> _apps.value.sortedBy { it.name.lowercase() }
        SortMode.UPDATED -> _apps.value.sortedByDescending { it.publishedAt }
        SortMode.INSTALLED -> _apps.value.sortedByDescending { it.isInstalled }
    }

    private companion object {
        const val WORKFLOW_POLL_INTERVAL_MS = 20_000L
        const val WORKFLOW_COMPLETION_REFRESH_DELAY_MS = 2_000L
    }
}

