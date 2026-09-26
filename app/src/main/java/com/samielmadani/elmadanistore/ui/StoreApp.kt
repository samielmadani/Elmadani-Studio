package com.samielmadani.elmadanistudio.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.MotionEvent
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.samielmadani.elmadanistudio.CrashReporter
import com.samielmadani.elmadanistudio.data.SortMode
import com.samielmadani.elmadanistudio.data.StoreApp
import com.samielmadani.elmadanistudio.data.ThemeMode
import com.samielmadani.elmadanistudio.data.WebsiteCatalog
import com.samielmadani.elmadanistudio.data.WebsiteEntry
import com.samielmadani.elmadanistudio.data.WorkflowProgress
import com.samielmadani.elmadanistudio.ui.theme.ThemeSettings
import com.samielmadani.elmadanistudio.ui.theme.LocalLiquidGlass
import kotlinx.coroutines.launch

private enum class TopLevelPage { Apps, Websites, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreApp(initialRepo: String? = null, storeViewModel: StoreViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { TopLevelPage.entries.size })
    var selectedPage by remember { mutableIntStateOf(0) }
    var selectedApp by remember { mutableStateOf<StoreApp?>(null) }
    var fullScreenWebsite by rememberSaveable { mutableStateOf<String?>(null) }
    val apps by storeViewModel.apps.collectAsState()
    val loading by storeViewModel.loading.collectAsState()
    val refreshingRepos by storeViewModel.refreshingRepos.collectAsState()
    val error by storeViewModel.error.collectAsState()
    val progress by storeViewModel.downloadProgress.collectAsState()
    val failedDownloads by storeViewModel.failedDownloads.collectAsState()
    val workflowProgress by storeViewModel.workflowProgress.collectAsState()
    val updateNotice by storeViewModel.updateNotice.collectAsState()
    val selfUpdate by storeViewModel.selfUpdate.collectAsState()
    val pendingUpdates = storeViewModel.pendingUpdates()
    val snackbarHostState = remember { SnackbarHostState() }
    val sortSheetOpen = remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("store", Context.MODE_PRIVATE) }
    var onboardingDone by rememberSaveable { mutableStateOf(prefs.getBoolean("onboarding_done", false)) }
    var pendingInstallMessage by remember { mutableStateOf<String?>(null) }
    var showCrashDialog by remember { mutableStateOf(CrashReporter.read(context) != null) }
    var crashLog by remember { mutableStateOf(CrashReporter.read(context)) }

    fun openUnknownSources() {
        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
        context.startActivity(intent)
    }

    fun handleInstall(app: StoreApp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            pendingInstallMessage = "Install is blocked because this app is not allowed to install apps from outside the Play Store. Open Settings to allow it once, then try again."
            return
        }
        storeViewModel.download(app) { file ->
            context.startActivity(storeViewModel.install(file))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    fun handleUpdateAll() = scope.launch {
        val updates = storeViewModel.pendingUpdates()
        if (updates.isEmpty()) return@launch
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            pendingInstallMessage = "Install is blocked because this app is not allowed to install apps from outside the Play Store. Open Settings to allow it once, then try again."
            return@launch
        }
        updates.forEach { app ->
            val file = storeViewModel.downloadFile(app) ?: run {
                snackbarHostState.showSnackbar("Download failed — tap to retry")
                return@launch
            }
            context.startActivity(storeViewModel.install(file))
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        snackbarHostState.showSnackbar("All updates finished.")
    }

    LaunchedEffect(apps, initialRepo) {
        initialRepo?.let { repo -> apps.firstOrNull { it.repo.equals(repo, true) }?.let { selectedApp = it } }
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { selectedPage = it }
    }
    LaunchedEffect(updateNotice) {
        updateNotice?.let { notice ->
            val result = snackbarHostState.showSnackbar(
                message = notice.message,
                actionLabel = "Install",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                handleInstall(notice.app)
            }
            storeViewModel.dismissUpdateNotice()
        }
    }
    BackHandler(enabled = selectedApp != null || sortSheetOpen.value || selectedPage != 0 || !onboardingDone) {
        when {
            fullScreenWebsite != null -> fullScreenWebsite = null
            selectedApp != null -> selectedApp = null
            sortSheetOpen.value -> sortSheetOpen.value = false
            !onboardingDone -> onboardingDone = true
            else -> scope.launch { pagerState.animateScrollToPage(0) }
        }
    }

    if (!onboardingDone) {
        OnboardingScreen(onComplete = {
            onboardingDone = true
            prefs.edit().putBoolean("onboarding_done", true).apply()
        }, onSkip = {
            onboardingDone = true
            prefs.edit().putBoolean("onboarding_done", true).apply()
        })
        return
    }

    Box(Modifier.fillMaxSize()) {
        if (LocalLiquidGlass.current) {
            val glassColors = MaterialTheme.colorScheme
            Box(Modifier.fillMaxSize().blur(38.dp).drawBehind {
                drawLine(glassColors.primary.copy(alpha = 0.24f), Offset(-size.width * 0.15f, size.height * 0.3f), Offset(size.width * 1.1f, size.height * 0.64f), size.height * 0.24f, StrokeCap.Round)
                drawLine(glassColors.tertiary.copy(alpha = 0.2f), Offset(-size.width * 0.1f, size.height * 0.78f), Offset(size.width * 1.12f, size.height * 0.42f), size.height * 0.18f, StrokeCap.Round)
            })
        }
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (TopLevelPage.entries[page]) {
                TopLevelPage.Apps -> HomePage(apps, selfUpdate, loading, refreshingRepos, error, progress, failedDownloads, workflowProgress, pendingUpdates, updateNotice != null, storeViewModel, { selectedApp = it }, { sortSheetOpen.value = true }, sortSheetOpen.value, { sortSheetOpen.value = false }, { app -> handleInstall(app) }, { app -> handleInstall(app) }, { handleUpdateAll() })
                TopLevelPage.Websites -> WebsitesPage(fullScreenWebsite, { website ->
                    fullScreenWebsite = if (fullScreenWebsite == website) null else website
                })
                TopLevelPage.Settings -> SettingsPage(storeViewModel, selfUpdate, { openUnknownSources() })
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SnackbarHost(hostState = snackbarHostState, modifier = Modifier.fillMaxWidth()) { data ->
                val noticeApp = updateNotice?.takeIf { it.message == data.visuals.message }?.app
                val actionLabel = data.visuals.actionLabel
                if (noticeApp != null && actionLabel != null) {
                    UpdateAvailableToast(
                        app = noticeApp,
                        message = data.visuals.message,
                        actionLabel = actionLabel,
                        onAction = data::performAction
                    )
                } else {
                    androidx.compose.material3.Snackbar(snackbarData = data)
                }
            }
            Card(
                shape = RoundedCornerShape(28.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = if (LocalLiquidGlass.current) MaterialTheme.colorScheme.surface.copy(alpha = 0.64f) else MaterialTheme.colorScheme.surface),
                border = if (LocalLiquidGlass.current) BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.68f), MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)))) else null,
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FloatingNavigationButton("Apps", Icons.Default.CloudDownload, selectedPage == TopLevelPage.Apps.ordinal, pendingUpdates.size, onClick = { scope.launch { pagerState.animateScrollToPage(TopLevelPage.Apps.ordinal) } })
                    FloatingNavigationButton("Websites", Icons.Default.Language, selectedPage == TopLevelPage.Websites.ordinal, 0, onClick = { scope.launch { pagerState.animateScrollToPage(TopLevelPage.Websites.ordinal) } })
                    FloatingNavigationButton("Settings", Icons.Default.Settings, selectedPage == TopLevelPage.Settings.ordinal, 0, onClick = { scope.launch { pagerState.animateScrollToPage(TopLevelPage.Settings.ordinal) } })
                }
            }
        }
    }

    if (pendingInstallMessage != null) {
        AlertDialog(
            onDismissRequest = { pendingInstallMessage = null },
            title = { Text("Install blocked") },
            text = { Text(pendingInstallMessage!!) },
            confirmButton = {
                TextButton(onClick = {
                    pendingInstallMessage = null
                    openUnknownSources()
                }) { Text("Open Settings") }
            },
            dismissButton = { TextButton(onClick = { pendingInstallMessage = null }) { Text("Close") } }
        )
    }

    if (showCrashDialog && crashLog != null) {
        AlertDialog(
            onDismissRequest = { showCrashDialog = false; CrashReporter.clear(context) },
            title = { Text("App crash report") },
            text = { Text("A crash was detected. You can email the saved error details to Sami. Nothing else is included.") },
            confirmButton = {
                TextButton(onClick = {
                    val intent = CrashReporter.emailIntent(context)
                    context.startActivity(intent)
                    CrashReporter.clear(context)
                    showCrashDialog = false
                }) { Text("Send report") }
            },
            dismissButton = { TextButton(onClick = { CrashReporter.clear(context); showCrashDialog = false }) { Text("Dismiss") } }
        )
    }

    selectedApp?.let { app ->
        DetailPage(app, progress[app.repo], failedDownloads.contains(app.repo), storeViewModel, { selectedApp = null }, { handleInstall(app) }, { handleInstall(app) })
    }
}

@Composable
private fun UpdateAvailableToast(app: StoreApp, message: String, actionLabel: String, onAction: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsyncImage(
                model = app.iconUrl,
                contentDescription = "${app.name} icon",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Update available", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp), modifier = Modifier.heightIn(min = 40.dp)) {
                Text(actionLabel, maxLines = 1)
            }
        }
    }
}

@Composable
private fun FloatingNavigationButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, badgeCount: Int, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
    ) {
        BadgedBox(badge = { if (badgeCount > 0) Badge { Text(badgeCount.toString()) } }) {
            Icon(icon, contentDescription = label, tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebsitesPage(fullScreenWebsite: String?, toggleFullScreen: (String) -> Unit) {
    var expandedWebsite by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold { padding ->
        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
            val listState = rememberLazyListState()
            val visibleWebsites = if (fullScreenWebsite == null) WebsiteCatalog.entries else WebsiteCatalog.entries.filter { it.url == fullScreenWebsite }
            val fullHeight = (maxHeight - 250.dp).coerceAtLeast(320.dp)

            LaunchedEffect(fullScreenWebsite) {
                if (fullScreenWebsite != null) listState.animateScrollToItem(0)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 112.dp)
            ) {
                item {
                    Text("Websites", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
                items(visibleWebsites, key = { it.url }) { website ->
                    WebsiteCard(
                        website = website,
                        expanded = expandedWebsite == website.url,
                        fullHeight = fullScreenWebsite == website.url,
                        onClick = {
                            val collapsing = expandedWebsite == website.url
                            expandedWebsite = if (collapsing) null else website.url
                            if (collapsing && fullScreenWebsite == website.url) toggleFullScreen(website.url)
                        },
                        onToggleFullHeight = {
                            if (expandedWebsite != website.url) expandedWebsite = website.url
                            toggleFullScreen(website.url)
                        },
                        webViewHeight = fullHeight
                    )
                }
            }
        }
    }
}

@Composable
private fun WebsiteCard(
    website: WebsiteEntry,
    expanded: Boolean,
    fullHeight: Boolean,
    onClick: () -> Unit,
    onToggleFullHeight: () -> Unit,
    webViewHeight: androidx.compose.ui.unit.Dp
) {
    val glass = LocalLiquidGlass.current
    var retryCount by remember(website.url) { mutableIntStateOf(0) }
    var loading by remember(website.url) { mutableStateOf(true) }
    var loadFailed by remember(website.url) { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = if (glass) MaterialTheme.colorScheme.surface.copy(alpha = 0.64f) else MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth().animateContentSize()
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f).clickable(onClick = onClick).padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(website.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    website.description?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                IconButton(onClick = onToggleFullHeight) {
                    Icon(
                        imageVector = if (fullHeight) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = if (fullHeight) "Exit full height" else "Expand to full height"
                    )
                }
            }
            AnimatedVisibility(visible = expanded) {
                Column(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(if (fullHeight) webViewHeight else 520.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                    ) {
                        if (loadFailed) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text("Couldn't load this site", color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick = {
                                    loadFailed = false
                                    loading = true
                                    retryCount++
                                }) { Text("Retry") }
                            }
                        } else {
                            key(retryCount) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { context ->
                                        WebsiteWebView(context).apply {
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.allowFileAccess = false
                                            settings.allowContentAccess = false
                                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                                            settings.javaScriptCanOpenWindowsAutomatically = false
                                            settings.setSupportMultipleWindows(false)
                                            webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                                    val targetHost = Uri.parse(website.url).host.orEmpty().removePrefix("www.")
                                                    val requestUri = request.url
                                                    val requestHost = requestUri.host.orEmpty().removePrefix("www.")
                                                    return requestUri.scheme != "https" || !requestHost.equals(targetHost, ignoreCase = true)
                                                }

                                                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                                                    loading = true
                                                    loadFailed = false
                                                }

                                                override fun onPageFinished(view: WebView, url: String?) {
                                                    loading = false
                                                }

                                                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                                                    if (request.isForMainFrame) {
                                                        loading = false
                                                        loadFailed = true
                                                    }
                                                }

                                                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: WebResourceResponse) {
                                                    if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                                                        loading = false
                                                        loadFailed = true
                                                    }
                                                }

                                                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                                                    handler.cancel()
                                                    loading = false
                                                    loadFailed = true
                                                }
                                            }
                                            loadUrl(website.url)
                                        }
                                    },
                                    onRelease = { webView ->
                                        webView.stopLoading()
                                        webView.destroy()
                                    }
                                )
                            }
                            if (loading) {
                                Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.92f)), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private class WebsiteWebView(context: Context) : WebView(context) {
    private var previousTouchY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val fingerMovedDown = event.y > previousTouchY
                val canScrollInGestureDirection = canScrollVertically(if (fingerMovedDown) -1 else 1)
                parent?.requestDisallowInterceptTouchEvent(canScrollInGestureDirection)
                previousTouchY = event.y
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomePage(
    apps: List<StoreApp>,
    selfUpdate: StoreApp?,
    loading: Boolean,
    refreshingRepos: Set<String>,
    error: String?,
    progress: Map<String, Int>,
    failedDownloads: Set<String>,
    workflowProgress: Map<String, WorkflowProgress>,
    pendingUpdates: List<StoreApp>,
    updateToastVisible: Boolean,
    vm: StoreViewModel,
    openDetails: (StoreApp) -> Unit,
    openSort: () -> Unit,
    sortSheetOpen: Boolean,
    closeSort: () -> Unit,
    install: (StoreApp) -> Unit,
    retry: (StoreApp) -> Unit,
    updateAll: () -> Unit
) {
    var sort by remember { mutableStateOf(SortMode.UPDATED) }
    var search by rememberSaveable { mutableStateOf("") }
    val listApps = buildList {
        addAll(vm.sorted(sort))
        selfUpdate?.takeIf { it.hasUpdate || workflowProgress.containsKey("${it.owner}/${it.repo}".lowercase()) }?.let { add(it) }
    }.distinctBy { it.repo }
    val shownApps = listApps.filter { app ->
        val needle = search.trim()
        if (needle.isBlank()) true else app.name.contains(needle, ignoreCase = true) || app.description.contains(needle, ignoreCase = true)
    }
    val totalUpdates = pendingUpdates.size

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = if (updateToastVisible) 220.dp else 112.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Elmadani Studio", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("Personal app catalogue", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = openSort) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort apps") }
                }
            }
            when {
                loading && apps.isEmpty() -> item { LoadingList() }
                error != null && apps.isEmpty() -> item { EmptyState("Could not load apps", error ?: "Try again", Icons.Default.CloudDownload, null) }
                apps.isEmpty() -> item { EmptyState("No apps yet", "New updates will appear here.", Icons.Default.CloudDownload, null) }
                else -> {
                    item {
                        Text("${apps.size} apps, ${totalUpdates} updates available.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (totalUpdates >= 2) {
                            Button(onClick = updateAll, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(percent = 50)) {
                                Text("Update all ($totalUpdates)")
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = search,
                            onValueChange = { search = it },
                            placeholder = { Text("Search apps") },
                            leadingIcon = { Icon(Icons.Default.Search, null) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            singleLine = true
                        )
                    }
                    items(shownApps, key = { it.repo }) { app ->
                        AppCard(app, progress[app.repo], failedDownloads.contains(app.repo), app.repo.lowercase() in refreshingRepos, workflowProgress["${app.owner}/${app.repo}".lowercase()], { openDetails(app) }, { install(app) }, { retry(app) })
                    }
                }
            }
            item {
                RefreshButton(loading = loading, onClick = vm::refresh)
            }
        }
    }
    if (sortSheetOpen) {
        ModalBottomSheet(onDismissRequest = closeSort, sheetState = rememberModalBottomSheetState(), containerColor = if (LocalLiquidGlass.current) MaterialTheme.colorScheme.surface.copy(alpha = 0.86f) else MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sort apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                SortMode.entries.forEach { mode ->
                    DropdownMenuItem(text = { Text(mode.label) }, onClick = { sort = mode; closeSort() })
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AppCard(app: StoreApp, progress: Int?, failed: Boolean, refreshing: Boolean, publishing: WorkflowProgress?, openDetails: () -> Unit, install: () -> Unit, retry: () -> Unit) {
    val glass = LocalLiquidGlass.current
    val isInstalling = progress != null && progress < 100
    val accentColor = when {
        publishing != null -> MaterialTheme.colorScheme.tertiary
        isInstalling -> MaterialTheme.colorScheme.tertiary
        app.hasUpdate -> MaterialTheme.colorScheme.error
        app.isInstalled -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }
    val cardColor = if (glass) MaterialTheme.colorScheme.surface.copy(alpha = 0.64f) else when {
        publishing != null -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.20f)
        app.hasUpdate -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.20f)
        isInstalling -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.20f)
        app.isInstalled -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        else -> MaterialTheme.colorScheme.surface
    }
    val actionLabel = when {
        failed -> "Retry"
        app.hasUpdate -> "Update"
        app.isInstalled -> "Up to date"
        else -> "Install"
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = if (glass) BorderStroke(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.72f), accentColor.copy(alpha = 0.42f), Color.White.copy(alpha = 0.2f)))) else BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = openDetails)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = app.iconUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(app.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (app.isNew) { AssistChip(onClick = {}, label = { Text("New") }, enabled = false, modifier = Modifier.height(28.dp), colors = AssistChipDefaults.assistChipColors(disabledContainerColor = MaterialTheme.colorScheme.primaryContainer, disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer)) }
                    }
                    Text(app.description, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (publishing != null) {
                Column(Modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Publishing update…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        publishing.percent?.let { Text("$it%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold) }
                    }
                    if (publishing.percent == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.tertiary)
                    } else {
                        LinearProgressIndicator(
                            progress = { publishing.percent.coerceIn(0, 100) / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.tertiary,
                            trackColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.38f)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(app.version, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                Text("Updated ${app.lastUpdatedText}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.weight(1f))
                if (refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.width(8.dp))
                }
                if (publishing == null) StatusChip(app, isInstalling, failed)
            }
            Spacer(Modifier.height(6.dp))
            if (isInstalling) {
                LinearProgressIndicator(progress = { (progress ?: 0) / 100f }, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.tertiary, trackColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.38f))
            } else if (failed) {
                Text("Download failed — tap to retry", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                if (failed) {
                    Button(onClick = retry, shape = RoundedCornerShape(percent = 50), modifier = Modifier.heightIn(min = 40.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text(actionLabel) }
                } else if (app.downloadUrl.isBlank()) {
                    Text(if (refreshing) "Refreshing app details" else "Cached details unavailable offline", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else if (isInstalling) {
                    CircularProgressIndicator(progress = { (progress ?: 0) / 100f }, modifier = Modifier.size(26.dp), strokeWidth = 3.dp, color = MaterialTheme.colorScheme.tertiary)
                } else {
                    AppActionButton(app, onClick = install)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(app: StoreApp, isInstalling: Boolean = false, failed: Boolean = false) {
    val text = when {
        failed -> "Failed"
        isInstalling -> "Installing"
        app.hasUpdate -> "Update available"
        app.isInstalled -> "Up to date"
        else -> "Ready"
    }
    val container = when {
        failed -> MaterialTheme.colorScheme.errorContainer
        isInstalling -> MaterialTheme.colorScheme.tertiaryContainer
        app.hasUpdate -> MaterialTheme.colorScheme.errorContainer
        app.isInstalled -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val labelColor = when {
        failed -> MaterialTheme.colorScheme.onErrorContainer
        isInstalling -> MaterialTheme.colorScheme.onTertiaryContainer
        app.hasUpdate -> MaterialTheme.colorScheme.onErrorContainer
        app.isInstalled -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text) },
        leadingIcon = { Icon(Icons.Default.CheckCircle, null) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = container,
            disabledLabelColor = labelColor,
            disabledLeadingIconContentColor = labelColor
        ),
        modifier = Modifier.height(32.dp)
    )
}

@Composable
private fun AppActionButton(app: StoreApp, onClick: () -> Unit) {
    val label = if (app.hasUpdate) "Update" else if (app.isInstalled) "Installed" else "Install"
    val enabled = app.needsInstall
    when {
        app.hasUpdate || !app.isInstalled -> Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)
            ),
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { Text(label) }
        else -> FilledTonalButton(
            onClick = {},
            enabled = false,
            shape = RoundedCornerShape(percent = 50),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            ),
            modifier = Modifier.heightIn(min = 48.dp),
            contentPadding = PaddingValues(horizontal = 16.dp)
        ) { Text(label) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailPage(app: StoreApp, progress: Int?, failed: Boolean, vm: StoreViewModel, back: () -> Unit, install: () -> Unit, retry: () -> Unit) {
    val context = LocalContext.current
    var advanced by remember { mutableStateOf(false) }
    var customName by remember { mutableStateOf(vm.overrideName(app.repo).orEmpty()) }
    ModalBottomSheet(onDismissRequest = back, sheetState = rememberModalBottomSheetState(), containerColor = if (LocalLiquidGlass.current) MaterialTheme.colorScheme.surface.copy(alpha = 0.86f) else MaterialTheme.colorScheme.surface) {
        Scaffold(topBar = { TopAppBar(title = { Text(app.name) }, navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(padding)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = app.iconUrl, contentDescription = null, modifier = Modifier.size(82.dp).clip(RoundedCornerShape(22.dp)))
                        Spacer(Modifier.width(18.dp))
                        Column {
                            Text(app.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Latest ${app.version}", color = MaterialTheme.colorScheme.primary)
                            Text("Updated ${app.lastUpdatedText}  •  ${app.formattedSize}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Button(
                        onClick = { if (failed) retry() else install() },
                        enabled = !app.isInstalled || app.hasUpdate || failed,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(percent = 50),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                    ) {
                        Icon(Icons.Default.CloudDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (failed) "Retry download" else if (app.hasUpdate) "Update app" else if (app.isInstalled) "Installed" else "Install app")
                    }
                }
                if (progress != null && progress < 100) item { LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth()) }
                if (failed) item { Text("Download failed — tap to retry", color = MaterialTheme.colorScheme.error) }
                item { Section("About this app") { Text(app.description, style = MaterialTheme.typography.bodyLarge) } }
                item { Section("Update notes") { Text(app.releaseNotes.ifBlank { "No update notes provided." }, style = MaterialTheme.typography.bodyMedium) } }
                item { Section("Version history") { app.releases.forEach { release -> Text("${release.version}  •  ${release.formattedDate}", fontWeight = FontWeight.Medium); Text(release.assetName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
                item { Section("Personalize") { OutlinedTextField(customName, { customName = it }, label = { Text("Display name override") }, modifier = Modifier.fillMaxWidth(), singleLine = true); TextButton(onClick = { vm.saveOverrideName(app.repo, customName) }) { Text("Save name") } } }
                item {
                    Card(shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Advanced", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide" else "Show") }
                            }
                            AnimatedVisibility(advanced) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Repository: ${app.owner}/${app.repo}", style = MaterialTheme.typography.bodySmall)
                                    Text("App asset: ${app.assetName}", style = MaterialTheme.typography.bodySmall)
                                    Text("Pre-release: ${if (app.prerelease) "Yes" else "No"}", style = MaterialTheme.typography.bodySmall)
                                    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(app.repositoryUrl))) }) { Text("View update on GitHub") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsPage(vm: StoreViewModel, selfUpdate: StoreApp?, openSettings: () -> Unit) {
    val context = LocalContext.current
    var token by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }
    val rateLimit by vm.rateLimit.collectAsState()
    val feedbackSubject = Uri.encode("Elmadani Studio feedback")
    val feedbackBody = Uri.encode("I'd like to share feedback about Elmadani Studio.\n\nApp version: ${context.packageManager.getPackageInfo(context.packageName, 0).versionName}\nAndroid: ${Build.VERSION.RELEASE}\n")

    Scaffold { padding ->
        Column(Modifier.padding(padding).padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("About Elmadani Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Built by Sami Elmadani, a software engineer based in Christchurch, New Zealand.", style = MaterialTheme.typography.bodyMedium)
                    Text("This app is not on the Play Store because it installs and updates apps directly from trusted GitHub releases outside the Play Store rules.", style = MaterialTheme.typography.bodyMedium)
                    Text("Last updated: ${selfUpdate?.lastUpdatedText ?: "Unknown"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        onClick = { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:hello@samielmadani.dev?subject=$feedbackSubject&body=$feedbackBody"))) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(percent = 50)
                    ) { Text("Send feedback") }
                }
            }
            Text("Advanced", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("GitHub access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("A personal access token increases API limits and enables private repositories. It is stored only on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(token, { token = it; saved = false }, modifier = Modifier.fillMaxWidth(), label = { Text("Personal access token") }, singleLine = true)
            Button(onClick = { vm.saveToken(token); saved = true; vm.refresh() }, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(percent = 50), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text(if (saved) "Saved" else "Save token") }
            Text("API status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${rateLimit.remaining?.toString() ?: "Unknown"} requests remaining${rateLimit.limit?.let { " of $it" } ?: ""}. Reset: ${rateLimit.resetText}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Appearance", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("Theme", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                ThemeMode.entries.forEach { mode ->
                    val selected = ThemeSettings.mode.value == mode
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f) else Color.Transparent)
                            .clickable { ThemeSettings.setMode(mode) }
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { ThemeSettings.setMode(mode) })
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.OLED -> "OLED black"
                                ThemeMode.LIQUID_GLASS -> "Liquid Glass"
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                }
            }
            Text("Accent", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
                listOf(Color(0xFF315F90), Color(0xFF006B5E), Color(0xFF8B4A60), Color(0xFF745900), Color(0xFF006A7C)).forEach { color ->
                    val selected = ThemeSettings.accent.value.toArgb() == color.toArgb()
                    Box(
                        modifier = Modifier.size(44.dp).clip(CircleShape).background(color)
                            .then(if (selected) Modifier.padding(3.dp).border(2.dp, Color.White, CircleShape) else Modifier.border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f), CircleShape))
                            .clickable { ThemeSettings.setAccent(color) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Icon(Icons.Default.CheckCircle, contentDescription = "Selected accent color", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
            Text("Storage", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            OutlinedButton(onClick = vm::clearDownloads, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(percent = 50)) { Icon(Icons.Default.Close, null); Spacer(Modifier.width(8.dp)); Text("Clear cached app files") }
            Button(onClick = openSettings, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(percent = 50)) { Text("Open install settings") }
            TextButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Elmadani Studio debug export\nPackage: ${context.packageName}\nAndroid: ${Build.VERSION.RELEASE}"), "Export logs")) }, modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Export debug logs") }
            Text("Updates are checked when Elmadani Studio refreshes. Background checks will notify you when a newer update is available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RefreshButton(loading: Boolean, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        OutlinedButton(onClick = onClick, enabled = !loading, shape = RoundedCornerShape(percent = 50)) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.CloudDownload, contentDescription = null)
            }
            Spacer(Modifier.width(8.dp))
            Text(if (loading) "Refreshing" else "Refresh")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold); content() } }

@Composable
private fun OnboardingScreen(onComplete: () -> Unit, onSkip: () -> Unit) {
    val pages = listOf(
        "Elmadani Studio helps you find apps and keep them updated from trusted GitHub releases.",
        "Some apps are outside the Play Store, so Android may ask you to allow installation from this app once. That is expected and safe when you choose the app.",
        "You remain in control: you can review updates, install only what you want, and check your settings anytime."
    )
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) { Text("Skip") }
            }
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Info, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(20.dp))
                    Text("Welcome", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(pages[page], style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                repeat(pages.size) { index ->
                    val color = if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    Box(Modifier.size(if (pagerState.currentPage == index) 12.dp else 8.dp).background(color, CircleShape))
                    if (index < pages.size - 1) Spacer(Modifier.width(8.dp))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (pagerState.currentPage < pages.lastIndex) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            onComplete()
                        }
                    },
                    shape = RoundedCornerShape(percent = 50),
                    modifier = Modifier.height(48.dp)
                ) { Text(if (pagerState.currentPage < pages.lastIndex) "Next" else "Get started") }
            }
        }
    }
}

@Composable
private fun LoadingList() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(4) { AppSkeletonCard() }
    }
}

@Composable
private fun AppSkeletonCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Skeleton(modifier = Modifier.size(58.dp), shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skeleton(modifier = Modifier.fillMaxWidth(0.7f).height(20.dp), shape = RoundedCornerShape(10.dp))
                Skeleton(modifier = Modifier.fillMaxWidth(0.9f).height(14.dp), shape = RoundedCornerShape(10.dp))
            }
        }
        Row(Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            Skeleton(modifier = Modifier.width(92.dp).height(30.dp), shape = RoundedCornerShape(50))
            Spacer(Modifier.width(8.dp))
            Skeleton(modifier = Modifier.width(74.dp).height(38.dp), shape = RoundedCornerShape(50))
        }
    }
}

@Composable
private fun Skeleton(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(12.dp), baseColor: Color = MaterialTheme.colorScheme.surfaceVariant, shimmerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)) {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "skeleton_shimmer_offset"
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(baseColor.copy(alpha = 0.55f))
            .drawBehind {
                val start = size.width * (shimmerOffset - 0.25f)
                val end = size.width * (shimmerOffset + 0.75f)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Transparent, shimmerColor, Color.Transparent),
                        start = Offset(start, 0f),
                        end = Offset(end, size.height)
                    )
                )
            }
    )
}

@Composable
private fun EmptyState(title: String, message: String, icon: androidx.compose.ui.graphics.vector.ImageVector, retry: (() -> Unit)?) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            retry?.let {
                Button(onClick = it, shape = RoundedCornerShape(percent = 50), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)) { Text("Try again") }
            }
        }
    }
}

