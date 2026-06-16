package com.jellyfin.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.jellyfin.client.ui.theme.JellyfinClientTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            JellyfinClientTheme {
                val context = LocalContext.current
                val sharedPreferences = remember { context.getSharedPreferences("JellyfinPrefs", android.content.Context.MODE_PRIVATE) }
                var appLanguage by remember { mutableStateOf(sharedPreferences.getString("app_language", "id") ?: "id") }
                
                val currentStrings = when (appLanguage) {
                    "en" -> stringsEn
                    "zh" -> stringsZh
                    else -> stringsId
                }
                
                androidx.compose.runtime.CompositionLocalProvider(LocalAppStrings provides currentStrings) {
                    JellyfinApp(
                        appLanguage = appLanguage,
                        onLanguageChange = { newLang ->
                            appLanguage = newLang
                            sharedPreferences.edit().putString("app_language", newLang).apply()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun JellyfinApp(
    appLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val sharedPreferences = remember { context.getSharedPreferences("JellyfinPrefs", android.content.Context.MODE_PRIVATE) }
    
    var serverUrl by remember { mutableStateOf(sharedPreferences.getString("server_url", "") ?: "") }
    var accessToken by remember { mutableStateOf(sharedPreferences.getString("access_token", "") ?: "") }
    var userId by remember { mutableStateOf(sharedPreferences.getString("user_id", "") ?: "") }
    var isLoggedIn by remember { mutableStateOf(sharedPreferences.getBoolean("is_logged_in", false)) }
    
    var layoutMode by remember { mutableStateOf(sharedPreferences.getString("layout_mode", "mobile") ?: "mobile") }

    var resumeItems by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var movieItems by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var tvItems by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var latestMovies by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }
    var latestShows by remember { mutableStateOf<List<JellyfinItem>>(emptyList()) }

    var activeVideoUrl by remember { mutableStateOf<String?>(null) }
    var activeVideoItemId by remember { mutableStateOf<String?>(null) }
    var activeVideoItemName by remember { mutableStateOf("") }
    var activeVideoItemType by remember { mutableStateOf("") }
    var activeVideoImageUrl by remember { mutableStateOf("") }
    var activeVideoStartPositionMs by remember { mutableStateOf(0L) }
    var localResumeItems by remember { mutableStateOf<List<LocalResumeItem>>(emptyList()) }

    var activeDetailItem by remember { mutableStateOf<JellyfinItem?>(null) }
    var activeEpisodeDetail by remember { mutableStateOf<JellyfinEpisode?>(null) }
    var selectedTab by remember { mutableStateOf("home") }

    // Cache state synchronously to ensure smooth exit transitions without frame lag
    var detailItemToDisplay by remember { mutableStateOf<JellyfinItem?>(null) }
    if (activeDetailItem != null && activeDetailItem != detailItemToDisplay) {
        detailItemToDisplay = activeDetailItem
    }
    
    val isTablet = layoutMode == "tablet"

    var episodeDetailToDisplay by remember { mutableStateOf<JellyfinEpisode?>(null) }
    if (activeEpisodeDetail != null && activeEpisodeDetail != episodeDetailToDisplay) {
        episodeDetailToDisplay = activeEpisodeDetail
    }
    var refreshTrigger by remember { mutableStateOf(0) }

    val isImeVisible = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0

    // Enforce Orientation and Status Bar layout rules
    val activity = context as? ComponentActivity
    val view = LocalView.current
    val window = activity?.window
    LaunchedEffect(layoutMode, activeVideoUrl) {
        if (activeVideoUrl == null) {
            if (layoutMode == "mobile") {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else if (layoutMode == "tablet") {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            if (window != null) {
                val windowInsetsController = WindowCompat.getInsetsController(window, view)
                windowInsetsController.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
                windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
            }
        }
    }

    // Android System Back Gestures / Back Button Interception
    BackHandler(enabled = activeVideoUrl != null || activeEpisodeDetail != null || activeDetailItem != null) {
        if (activeVideoUrl != null) {
            activeVideoUrl = null
            activeVideoItemId = null
            activeVideoItemName = ""
            activeVideoItemType = ""
            activeVideoImageUrl = ""
            activeVideoStartPositionMs = 0L
            refreshTrigger++
        } else if (activeEpisodeDetail != null) {
            activeEpisodeDetail = null
        } else if (activeDetailItem != null) {
            activeDetailItem = null
        }
    }
    
    // Auto load data saat status login atau refreshTrigger berubah
    LaunchedEffect(isLoggedIn, refreshTrigger) {
        localResumeItems = LocalResumeManager.getLocalResumeItems(context)
        if (isLoggedIn && serverUrl.isNotEmpty() && accessToken.isNotEmpty() && userId.isNotEmpty()) {
            JellyfinService.fetchResumeItems(serverUrl, accessToken, userId) { items ->
                resumeItems = items
            }
            JellyfinService.fetchItems(serverUrl, accessToken, userId, "Movie") { items ->
                movieItems = items
            }
            JellyfinService.fetchItems(serverUrl, accessToken, userId, "Series") { items ->
                tvItems = items
            }
            JellyfinService.fetchLatestItems(serverUrl, accessToken, userId, "Movie") { items ->
                latestMovies = items
            }
            JellyfinService.fetchLatestItems(serverUrl, accessToken, userId, "Series") { items ->
                latestShows = items
            }
        } else {
            resumeItems = emptyList()
            movieItems = emptyList()
            tvItems = emptyList()
            latestMovies = emptyList()
            latestShows = emptyList()
        }
    }

    // Muat daftar unduhan offline saat app dibuka
    LaunchedEffect(Unit) { DownloadManager.load(context) }

    // 5 judul acak dari semua film+serial untuk hero banner — diacak ulang tiap sesi
    val heroItems = remember(movieItems, tvItems) { (movieItems + tvItems).shuffled().take(5) }

    val combinedResumeItems = remember(resumeItems, localResumeItems) {
        val list = mutableListOf<JellyfinItem>()
        localResumeItems.forEach { local ->
            list.add(
                JellyfinItem(
                    id = local.id,
                    name = local.name,
                    type = local.type,
                    imageUrl = local.imageUrl,
                    streamUrl = local.streamUrl ?: "",
                    positionMs = local.positionMs,
                    durationMs = local.durationMs
                )
            )
        }
        resumeItems.forEach { server ->
            if (list.none { it.id == server.id }) {
                list.add(server)
            }
        }
        list
    }

    val hazeState = remember { HazeState() }
    val sidebarWidth = 220.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Haze capture area — selalu diberi offset sidebar di mode tablet
            Box(modifier = Modifier.fillMaxSize().padding(start = if (isTablet) sidebarWidth else 0.dp).haze(state = hazeState)) {
        
        AnimatedVisibility(
            visible = selectedTab == "home" && activeDetailItem == null,
            enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { 1 }),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { 1 })
        ) {
            HomeScreen(
                isLoggedIn = isLoggedIn,
                heroItems = heroItems,
                resumeItems = combinedResumeItems,
                latestMovies = latestMovies,
                latestShows = latestShows,
                onItemClick = { item -> activeDetailItem = item },
                onResumeClick = { url, itemId, name, type, imgUrl -> 
                    // Fetch details to get playback ticks on-the-fly
                    JellyfinService.fetchItemDetails(serverUrl, accessToken, userId, itemId) { details ->
                        val startMs = (details?.playbackPositionTicks ?: 0L) / 10000L
                        val local = localResumeItems.firstOrNull { it.id == itemId }
                        val finalStartMs = if (local != null && local.positionMs > startMs) local.positionMs else startMs

                        activeVideoUrl = url
                        activeVideoItemId = itemId
                        activeVideoItemName = name
                        activeVideoItemType = type
                        activeVideoImageUrl = imgUrl
                        activeVideoStartPositionMs = finalStartMs
                    }
                },
                onRemoveResume = { itemId ->
                    // Hapus dari penyimpanan lokal
                    LocalResumeManager.removeLocalProgress(context, itemId)
                    localResumeItems = LocalResumeManager.getLocalResumeItems(context)
                    resumeItems = resumeItems.filter { it.id != itemId }
                    // Reset progress di server Jellyfin agar tidak muncul lagi saat refresh
                    if (serverUrl.isNotEmpty() && accessToken.isNotEmpty() && userId.isNotEmpty()) {
                        JellyfinService.resetPlaybackPosition(serverUrl, accessToken, userId, itemId)
                    }
                },
                onNavigateToLogin = { selectedTab = "settings" },
                onRefresh = { refreshTrigger++ },
                isTablet = isTablet
            )
        }
        
        AnimatedVisibility(
            visible = selectedTab == "movies" && activeDetailItem == null,
            enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { 1 }),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { 1 })
        ) {
            MoviesScreen(
                isLoggedIn = isLoggedIn,
                movieItems = movieItems,
                onItemClick = { item -> activeDetailItem = item },
                onNavigateToLogin = { selectedTab = "settings" },
                isTablet = isTablet
            )
        }

        AnimatedVisibility(
            visible = selectedTab == "tv" && activeDetailItem == null,
            enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { 1 }),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { 1 })
        ) {
            TvSeriesScreen(
                isLoggedIn = isLoggedIn,
                tvItems = tvItems,
                onItemClick = { item -> activeDetailItem = item },
                onNavigateToLogin = { selectedTab = "settings" },
                isTablet = isTablet
            )
        }

        AnimatedVisibility(
            visible = selectedTab == "settings" && activeDetailItem == null,
            enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { 1 }),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { 1 })
        ) {
            SettingsScreen(
                onLoginSuccess = { url, token, uId ->
                    serverUrl = url
                    accessToken = token
                    userId = uId
                    isLoggedIn = true
                },
                onLogout = {
                    serverUrl = ""
                    accessToken = ""
                    userId = ""
                    isLoggedIn = false
                },
                layoutMode = layoutMode,
                onLayoutModeChange = { mode ->
                    layoutMode = mode
                    sharedPreferences.edit().putString("layout_mode", mode).apply()
                },
                appLanguage = appLanguage,
                onLanguageChange = onLanguageChange
            )
        }

        // Halaman Unduhan (Offline)
        AnimatedVisibility(
            visible = selectedTab == "download" && activeDetailItem == null,
            enter = fadeIn(animationSpec = tween(500)) + slideInVertically(initialOffsetY = { 1 }),
            exit = fadeOut(animationSpec = tween(300)) + slideOutVertically(targetOffsetY = { 1 })
        ) {
            DownloadsScreen(
                onPlayOffline = { path, id, name, type, img ->
                    val local = LocalResumeManager.getLocalResumeItems(context).firstOrNull { it.id == id }
                    activeVideoUrl = path
                    activeVideoItemId = id
                    activeVideoItemName = name
                    activeVideoItemType = type
                    activeVideoImageUrl = img
                    activeVideoStartPositionMs = local?.positionMs ?: 0L
                }
            )
        }

        // Overlay Detail Halaman (Film / Serial) dengan Transisi Slide
        AnimatedVisibility(
            visible = activeDetailItem != null,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(450, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(450)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(450, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            detailItemToDisplay?.let { item ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A0A))
                ) {
                    if (item.type == "Movie") {
                        MovieDetailScreen(
                                    item = item,
                                    sidebarPadding = if (isTablet) sidebarWidth else 0.dp,
                            serverUrl = serverUrl,
                            accessToken = accessToken,
                            userId = userId,
                            onPlay = { url, id, name, type, imgUrl, startMs -> 
                                activeVideoUrl = url
                                activeVideoItemId = id
                                activeVideoItemName = name
                                activeVideoItemType = type
                                activeVideoImageUrl = imgUrl
                                activeVideoStartPositionMs = startMs
                            },
                            onBack = { activeDetailItem = null }
                        )
                    } else if (item.type == "Series") {
                        TvShowDetailScreen(
                                    item = item,
                                    sidebarPadding = if (isTablet) sidebarWidth else 0.dp,
                            serverUrl = serverUrl,
                            accessToken = accessToken,
                            userId = userId,
                            onEpisodeClick = { ep -> activeEpisodeDetail = ep },
                            onResumeEpisode = { url, id, name, type, imgUrl, startMs ->
                                activeVideoUrl = url
                                activeVideoItemId = id
                                activeVideoItemName = name
                                activeVideoItemType = type
                                activeVideoImageUrl = imgUrl
                                activeVideoStartPositionMs = startMs
                            },
                            onBack = { activeDetailItem = null }
                        )
                    }
                }
            }
        }

        // Overlay Detail Episode dengan Transisi Slide
        AnimatedVisibility(
            visible = activeEpisodeDetail != null,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(450, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(450)),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(450, easing = FastOutSlowInEasing)) + fadeOut(animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            episodeDetailToDisplay?.let { ep ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0A0A))
                ) {
                    EpisodeDetailScreen(
                        episode = ep,
                        sidebarPadding = if (isTablet) sidebarWidth else 0.dp,
                        onPlay = { url, id, name, type, imgUrl, startMs ->
                            activeVideoUrl = url
                            activeVideoItemId = id
                            activeVideoItemName = name
                            activeVideoItemType = type
                            activeVideoImageUrl = imgUrl
                            activeVideoStartPositionMs = startMs
                        },
                        onBack = { activeEpisodeDetail = null }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = selectedTab != "home" && selectedTab != "movies" && selectedTab != "tv" && selectedTab != "settings" && selectedTab != "download" && activeDetailItem == null,
            enter = fadeIn(animationSpec = tween(500)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Halaman ${selectedTab.replaceFirstChar { it.uppercase() }} (${strings.notAvailable})", color = Color.White)
            }
        }
        
        } // End of Haze capture Box

        // Sidebar tablet — di-render SETELAH content box agar selalu tampil di atas
        if (isTablet) {
            AnimatedVisibility(
                visible = activeVideoUrl == null && !isImeVisible,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300))
            ) {
                GlassNavigationRail(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    hazeState = hazeState,
                    modifier = Modifier.width(sidebarWidth)
                )
            }
        }

        } // End of outer Box

        // Floating Glassmorphism Bottom Navigation for Mobile
        if (!isTablet) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                AnimatedVisibility(
                    visible = activeVideoUrl == null && activeDetailItem == null && activeEpisodeDetail == null && !isImeVisible,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(300))
                ) {
                    GlassBottomNavigation(
                        selectedTab = selectedTab,
                        onTabSelected = { selectedTab = it },
                        hazeState = hazeState,
                        modifier = Modifier
                    )
                }
            }
        }

        // Fullscreen ExoPlayer Overlay
        activeVideoUrl?.let { url ->
            ExoPlayerScreen(
                videoUrl = url,
                itemId = activeVideoItemId ?: "",
                itemName = activeVideoItemName,
                itemType = activeVideoItemType,
                imageUrl = activeVideoImageUrl,
                serverUrl = serverUrl,
                accessToken = accessToken,
                startPositionMs = activeVideoStartPositionMs,
                onBack = { 
                    activeVideoUrl = null
                    activeVideoItemId = null
                    activeVideoItemName = ""
                    activeVideoItemType = ""
                    activeVideoImageUrl = ""
                    activeVideoStartPositionMs = 0L
                    refreshTrigger++
                }
            )
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isLoggedIn: Boolean,
    heroItems: List<JellyfinItem>,
    resumeItems: List<JellyfinItem>,
    latestMovies: List<JellyfinItem>,
    latestShows: List<JellyfinItem>,
    onItemClick: (JellyfinItem) -> Unit,
    onResumeClick: (String, String, String, String, String) -> Unit,
    onRemoveResume: (String) -> Unit = {},
    onNavigateToLogin: () -> Unit,
    onRefresh: () -> Unit = {},
    isTablet: Boolean = false
) {
    val strings = LocalAppStrings.current
    if (!isLoggedIn) {
        NotLoggedInStub(onNavigateToLogin)
    } else {
        var removeConfirmItem by remember { mutableStateOf<JellyfinItem?>(null) }
        var isRefreshing by remember { mutableStateOf(false) }
        
        if (isRefreshing) {
            LaunchedEffect(true) {
                onRefresh()
                kotlinx.coroutines.delay(1000)
                isRefreshing = false
            }
        }

        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = if (isTablet) 0.dp else 100.dp)
            ) {
            // Hero Banner — 5 judul acak, auto-scroll tiap 6 detik
            val pagerState = rememberPagerState(pageCount = { heroItems.size.coerceAtLeast(1) })
            LaunchedEffect(pagerState.settledPage, heroItems.size) {
                if (heroItems.size > 1) {
                    delay(6000)
                    pagerState.animateScrollToPage((pagerState.currentPage + 1) % heroItems.size)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isTablet) 550.dp else 450.dp)
            ) {
                if (heroItems.isNotEmpty()) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val item = heroItems[page]
                        val heroImage = if (isTablet) item.backdropUrl ?: item.imageUrl else item.imageUrl
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = heroImage,
                                contentDescription = item.name,
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Gradient overlay
                            if (isTablet) {
                                // Horizontal: kiri gelap → kanan transparan (buat teks terbaca)
                                Box(
                                    modifier = Modifier.fillMaxSize().background(
                                        Brush.horizontalGradient(
                                            colors = listOf(Color(0xE8000000), Color(0x55000000), Color.Transparent)
                                        )
                                    )
                                )
                                // Vertical: bawah hitam → atas transparan (fade bawah banner)
                                Box(
                                    modifier = Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Transparent, Color(0xCC0A0A0A), Color(0xFF0A0A0A))
                                        )
                                    )
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize().background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color(0xFF0A0A0A))
                                        )
                                    )
                                )
                            }
                            // Konten hero — tengah-kiri di tablet, bawah di mobile
                            Column(
                                modifier = if (isTablet) {
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .fillMaxWidth(0.48f)
                                        .padding(start = 32.dp, end = 16.dp)
                                } else {
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(start = 20.dp, end = 20.dp, bottom = 86.dp)
                                }
                            ) {
                                Text(
                                    text = item.type,
                                    color = Color.LightGray,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.name,
                                    color = Color.White,
                                    fontSize = if (isTablet) 34.sp else 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                item.year?.let {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = it.toString(), color = Color.LightGray, fontSize = 13.sp)
                                }
                                if (!item.overview.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = item.overview,
                                        color = Color.LightGray.copy(alpha = 0.85f),
                                        fontSize = 13.sp,
                                        maxLines = if (isTablet) 4 else 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            // Tombol Detail Info — selalu di pojok kiri bawah
                            Button(
                                onClick = { onItemClick(item) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B3FE4)),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(
                                        start = if (isTablet) 32.dp else 20.dp,
                                        bottom = 28.dp
                                    )
                                    .height(44.dp)
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.detailInfo, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    // Dot indicator
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(heroItems.size) { index ->
                            val isActive = index == pagerState.currentPage
                            Box(
                                modifier = Modifier
                                    .size(if (isActive) 8.dp else 5.dp)
                                    .background(
                                        if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                                        CircleShape
                                    )
                            )
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A)))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Continue Watching
            if (resumeItems.isNotEmpty()) {
                Text(
                    text = strings.continueWatching,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(resumeItems.size) { index ->
                        val item = resumeItems[index]
                        val progress = if (item.durationMs > 0L) (item.positionMs.toFloat() / item.durationMs.toFloat()) else 0f
                        HorizontalCard(
                            imageUrl = item.imageUrl,
                            progress = progress,
                            onClick = { onResumeClick(item.streamUrl, item.id, item.name, item.type, item.imageUrl) },
                            onLongPress = { removeConfirmItem = item }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Latest Movies Section
            if (latestMovies.isNotEmpty()) {
                Text(
                    text = strings.latestMovies,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(latestMovies.size) { index ->
                        val item = latestMovies[index]
                        VerticalCard(
                            title = item.name,
                            subtitle = item.year?.toString() ?: "",
                            imageUrl = item.imageUrl,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Latest Shows Section
            if (latestShows.isNotEmpty()) {
                Text(
                    text = strings.latestShows,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(latestShows.size) { index ->
                        val item = latestShows[index]
                        VerticalCard(
                            title = item.name,
                            subtitle = item.year?.toString() ?: "",
                            imageUrl = item.imageUrl,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Empty state jika server belum ada konten
            if (latestMovies.isEmpty() && latestShows.isEmpty() && resumeItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text(strings.emptyServer, color = Color.Gray)
                }
            }
        }
        }

        // Dialog glass: tahan kartu Continue Watching untuk hapus
        removeConfirmItem?.let { item ->
            Dialog(onDismissRequest = { removeConfirmItem = null }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xEA0E0E1C))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                        .padding(24.dp)
                ) {
                    Column {
                        Text(
                            text = strings.removeFromContinue,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = strings.removeFromContinueMsg.format(item.name),
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { removeConfirmItem = null }) {
                                Text(strings.cancelBtn, color = Color.Gray)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    onRemoveResume(item.id)
                                    removeConfirmItem = null
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B3FE4))
                            ) {
                                Text(strings.downloadDeleteBtn, color = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MoviesScreen(
    isLoggedIn: Boolean,
    movieItems: List<JellyfinItem>,
    onItemClick: (JellyfinItem) -> Unit,
    onNavigateToLogin: () -> Unit,
    isTablet: Boolean = false
) {
    val strings = LocalAppStrings.current
    if (!isLoggedIn) {
        NotLoggedInStub(onNavigateToLogin)
    } else {
        // Urutkan abjad secara dinamis
        val sortedMovies = remember(movieItems) { movieItems.sortedBy { it.name } }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 40.dp)
        ) {
            Text(
                text = strings.moviesAZ,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (sortedMovies.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(strings.emptyMovies, color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(130.dp),
                    contentPadding = PaddingValues(bottom = if (isTablet) 16.dp else 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedMovies.size) { index ->
                        val movie = sortedMovies[index]
                        VerticalCard(
                            title = movie.name,
                            subtitle = movie.year?.toString() ?: "",
                            imageUrl = movie.imageUrl,
                            onClick = { onItemClick(movie) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TvSeriesScreen(
    isLoggedIn: Boolean,
    tvItems: List<JellyfinItem>,
    onItemClick: (JellyfinItem) -> Unit,
    onNavigateToLogin: () -> Unit,
    isTablet: Boolean = false
) {
    val strings = LocalAppStrings.current
    if (!isLoggedIn) {
        NotLoggedInStub(onNavigateToLogin)
    } else {
        // Urutkan abjad secara dinamis
        val sortedTv = remember(tvItems) { tvItems.sortedBy { it.name } }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 40.dp)
        ) {
            Text(
                text = strings.tvSeriesAZ,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            if (sortedTv.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(strings.emptyTv, color = Color.Gray)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(130.dp),
                    contentPadding = PaddingValues(bottom = if (isTablet) 16.dp else 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(sortedTv.size) { index ->
                        val show = sortedTv[index]
                        VerticalCard(
                            title = show.name,
                            subtitle = show.year?.toString() ?: "",
                            imageUrl = show.imageUrl,
                            onClick = { onItemClick(show) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    onLoginSuccess: (String, String, String) -> Unit,
    onLogout: () -> Unit,
    layoutMode: String,
    onLayoutModeChange: (String) -> Unit,
    appLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("JellyfinPrefs", android.content.Context.MODE_PRIVATE) }
    var isLanguageExpanded by remember { mutableStateOf(false) }
    var isLayoutExpanded by remember { mutableStateOf(false) }
    var isSubtitleExpanded by remember { mutableStateOf(false) }

    var defaultSubtitleSize by remember { mutableStateOf(sharedPreferences.getFloat("default_subtitle_size", 18f)) }
    var defaultSubtitleEdgeType by remember { mutableStateOf(sharedPreferences.getString("default_subtitle_edge", androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE.toString())?.toIntOrNull() ?: androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 40.dp)
            .padding(bottom = if (layoutMode == "tablet") 0.dp else 100.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = strings.settingsHeader,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 1. App Layout Configuration Section
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x16FFFFFF)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().clickable { isLayoutExpanded = !isLayoutExpanded }.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.layoutSetting, color = Color(0xFF7B3FE4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Icon(imageVector = if (isLayoutExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                }
                AnimatedVisibility(visible = isLayoutExpanded) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLayoutModeChange("mobile") },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = layoutMode == "mobile" || layoutMode == "phone",
                                onClick = { onLayoutModeChange("mobile") },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7B3FE4))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(strings.layoutMobile, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(strings.layoutMobileDesc, color = Color.Gray, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLayoutModeChange("tablet") },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = layoutMode == "tablet",
                                onClick = { onLayoutModeChange("tablet") },
                                colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7B3FE4))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(strings.layoutTablet, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                Text(strings.layoutTabletDesc, color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 2. App Language Section
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x16FFFFFF)),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().clickable { isLanguageExpanded = !isLanguageExpanded }.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.appLanguage, color = Color(0xFF7B3FE4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Icon(imageVector = if (isLanguageExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                }
                AnimatedVisibility(visible = isLanguageExpanded) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                        listOf("id" to strings.langIndonesian, "en" to strings.langEnglish, "zh" to strings.langChinese).forEach { (code, label) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onLanguageChange(code) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = appLanguage == code,
                                    onClick = { onLanguageChange(code) },
                                    colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7B3FE4))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(label, color = Color.White, fontSize = 15.sp)
                            }
                            if (code != "zh") Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Subtitle Section Card

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0x16FFFFFF)),
            modifier = Modifier.fillMaxWidth().border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().clickable { isSubtitleExpanded = !isSubtitleExpanded }.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(strings.subtitleSetting, color = Color(0xFF7B3FE4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Icon(imageVector = if (isSubtitleExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White)
                }
                AnimatedVisibility(visible = isSubtitleExpanded) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp)) {
                        Text(strings.subtitleSize, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            listOf(18f to strings.sizeSmall, 22f to strings.sizeNormal, 25f to strings.sizeLarge).forEach { (size, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = defaultSubtitleSize == size, onClick = { 
                                        defaultSubtitleSize = size
                                        sharedPreferences.edit().putFloat("default_subtitle_size", size).apply()
                                    }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7B3FE4), unselectedColor = Color.Gray))
                                    Text(label, color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(strings.subtitleEdge, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            listOf(androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE to strings.edgeNone, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE to strings.edgeOutline, androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW to strings.edgeShadow).forEach { (type, label) ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(selected = defaultSubtitleEdgeType == type, onClick = { 
                                        defaultSubtitleEdgeType = type
                                        sharedPreferences.edit().putString("default_subtitle_edge", type.toString()).apply()
                                    }, colors = RadioButtonDefaults.colors(selectedColor = Color(0xFF7B3FE4), unselectedColor = Color.Gray))
                                    Text(label, color = Color.White, fontSize = 14.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        ProfileSection(
            onLoginSuccess = onLoginSuccess,
            onLogout = onLogout
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = strings.appVersion,
            color = Color.Gray,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 100.dp)
        )
    }
}
@Composable
fun ProfileSection(
    onLoginSuccess: (String, String, String) -> Unit,
    onLogout: () -> Unit
) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val sharedPreferences = remember { context.getSharedPreferences("JellyfinPrefs", android.content.Context.MODE_PRIVATE) }
    
    var serverUrl by remember { mutableStateOf(sharedPreferences.getString("server_url", "") ?: "") }
    var username by remember { mutableStateOf(sharedPreferences.getString("username", "") ?: "") }
    var password by remember { mutableStateOf("") }
    var isLoggedIn by remember { mutableStateOf(sharedPreferences.getBoolean("is_logged_in", false)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    
    var isProfileExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x16FFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().clickable { isProfileExpanded = !isProfileExpanded }.padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (isLoggedIn) strings.connectionDetail else strings.loginToNexfin, color = Color(0xFF7B3FE4), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Icon(imageVector = if (isProfileExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = Color.White)
            }
            AnimatedVisibility(visible = isProfileExpanded) {
                Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isLoggedIn) {
                        Text(strings.serverUrl, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
                        Text(serverUrl, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(strings.username, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
                        Text(username, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(Color(0xFF4CAF50))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.connectedToServer, color = Color(0xFF4CAF50), fontSize = 14.sp)
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                sharedPreferences.edit().apply {
                                    putBoolean("is_logged_in", false)
                                    putString("access_token", "")
                                    putString("user_id", "")
                                    apply()
                                }
                                isLoggedIn = false
                                password = ""
                                onLogout()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(strings.logoutBtn, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text(strings.serverUrl, color = Color.Gray) },
                            placeholder = { Text(strings.serverUrlPlaceholder, color = Color.DarkGray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF7B3FE4),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = Color(0xFF7B3FE4),
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            label = { Text(strings.username, color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF7B3FE4),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = Color(0xFF7B3FE4),
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text(strings.password, color = Color.Gray) },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF7B3FE4),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                                focusedLabelColor = Color(0xFF7B3FE4),
                                unfocusedLabelColor = Color.Gray,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (serverUrl.isBlank() || username.isBlank()) {
                                    errorMessage = "URL dan Username wajib diisi"
                                    return@Button
                                }
                                isLoading = true
                                errorMessage = null
                                
                                val urlToUse = if (!serverUrl.startsWith("http")) "http://$serverUrl" else serverUrl
                                JellyfinService.authenticate(urlToUse, username, password) { success: Boolean, token: String?, userId: String?, error: String? ->
                                    isLoading = false
                                    if (error != null) {
                                        errorMessage = error
                                    } else if (token != null && userId != null) {
                                        sharedPreferences.edit().apply {
                                            putString("server_url", urlToUse)
                                            putString("username", username)
                                            putString("access_token", token)
                                            putString("user_id", userId)
                                            putBoolean("is_logged_in", true)
                                            apply()
                                        }
                                        serverUrl = urlToUse
                                        isLoggedIn = true
                                        onLoginSuccess(urlToUse, token ?: "", userId ?: "")
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B3FE4)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoading
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Login, contentDescription = "Login")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.loginBtn, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        if (errorMessage != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = errorMessage!!,
                                color = Color.Red,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

data class LocalResumeItem(
    val id: String,
    val name: String,
    val type: String,
    val imageUrl: String,
    val streamUrl: String? = null,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val timestamp: Long = 0
)

object LocalResumeManager {
    fun saveLocalProgress(
        context: android.content.Context,
        itemId: String,
        name: String,
        type: String,
        imageUrl: String,
        streamUrl: String,
        positionMs: Long,
        durationMs: Long
    ) {
        val prefs = context.getSharedPreferences("LocalResumePrefs", android.content.Context.MODE_PRIVATE)
        val gson = com.google.gson.Gson()
        
        if (positionMs < 5000L) {
            removeLocalProgress(context, itemId)
            return
        }
        
        val items = getLocalResumeItems(context).toMutableList()
        val existingIndex = items.indexOfFirst { it.id == itemId }
        val newItem = LocalResumeItem(itemId, name, type, imageUrl, streamUrl, positionMs, durationMs, System.currentTimeMillis())
        
        if (existingIndex != -1) {
            items[existingIndex] = newItem
        } else {
            items.add(0, newItem)
        }
        
        prefs.edit().putString("resume_items", gson.toJson(items.take(20))).apply()
    }

    fun removeLocalProgress(context: android.content.Context, itemId: String) {
        val prefs = context.getSharedPreferences("LocalResumePrefs", android.content.Context.MODE_PRIVATE)
        val items = getLocalResumeItems(context).toMutableList()
        items.removeAll { it.id == itemId }
        prefs.edit().putString("resume_items", com.google.gson.Gson().toJson(items)).apply()
    }

    fun getLocalResumeItems(context: android.content.Context): List<LocalResumeItem> {
        val prefs = context.getSharedPreferences("LocalResumePrefs", android.content.Context.MODE_PRIVATE)
        val itemsJson = prefs.getString("resume_items", null)
        if (itemsJson.isNullOrEmpty()) return emptyList()
        
        val type = object : com.google.gson.reflect.TypeToken<List<LocalResumeItem>>() {}.type
        return try {
            com.google.gson.Gson().fromJson(itemsJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@Composable
fun NotLoggedInStub(onNavigateToLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.CloudOff,
            contentDescription = "Not Connected",
            tint = Color(0xFF7B3FE4),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Belum Terhubung",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Silakan hubungkan aplikasi dengan server Jellyfin Anda di menu Pengaturan untuk memuat pustaka film.",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onNavigateToLogin,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B3FE4)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Pergi ke Pengaturan", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VerticalCard(title: String, subtitle: String, imageUrl: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(12.dp))
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HorizontalCard(imageUrl: String, progress: Float = 0f, onClick: () -> Unit, onLongPress: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = { onLongPress?.invoke() })
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = "Movie Thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .background(Color(0xFF7B3FE4))
                )
            }
        }
    }
}

@Composable
fun MovieDetailScreen(
    item: JellyfinItem,
    serverUrl: String,
    accessToken: String,
    userId: String,
    onPlay: (String, String, String, String, String, Long) -> Unit,
    onBack: () -> Unit
, sidebarPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var details by remember { mutableStateOf<JellyfinDetails?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(item.id) {
        isLoading = true
        JellyfinService.fetchItemDetails(serverUrl, accessToken, userId, item.id) { result ->
            details = result
            isLoading = false
        }
    }

    val localProgress = remember(item.id) {
        LocalResumeManager.getLocalResumeItems(context).firstOrNull { it.id == item.id }
    }
    val finalResumeMs = remember(details, localProgress) {
        val serverMs = (details?.playbackPositionTicks ?: 0L) / 10000L
        val localMs = localProgress?.positionMs ?: 0L
        if (localMs > serverMs) localMs else serverMs
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF7B3FE4))
            }
        } else {
            details?.let { d ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 32.dp)
                ) {
                    // Backdrop Cover Image
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) 550.dp else 260.dp)
                    ) {
                        AsyncImage(
                            model = d.backdropUrl,
                            contentDescription = d.name,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color(0xFF0A0A0A)),
                                        startY = 180f
                                    )
                                )
                        )
                    }

                    // Content Details
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = d.name,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Metadata Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            d.year?.let { Text(text = "$it", color = Color.Gray, fontSize = 14.sp) }
                            d.rating?.let {
                                Text(
                                    text = it,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Color(0x33FFFFFF), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            d.runTimeMinutes?.let { Text(text = "$it mnt", color = Color.Gray, fontSize = 14.sp) }
                            d.communityRating?.let {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = "Rating",
                                        tint = Color(0xFFFFD700),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = String.format("%.1f", it), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Play Buttons (Continue or Restart) + Download — pill shape
                        if (finalResumeMs > 0L) {
                            val minutes = (finalResumeMs / 60000).toInt()
                            val seconds = ((finalResumeMs % 60000) / 1000).toInt()

                            PlayDownloadPill(
                                playLabel = "${strings.resumeBtn} (${minutes}:${String.format("%02d", seconds)})",
                                onPlay = { onPlay(item.streamUrl, item.id, d.name, "Movie", d.backdropUrl, finalResumeMs) },
                                itemId = item.id, itemName = d.name, itemType = "Movie",
                                imageUrl = item.imageUrl, streamUrl = item.streamUrl
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedButton(
                                onClick = { onPlay(item.streamUrl, item.id, d.name, "Movie", d.backdropUrl, 0L) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(45.dp)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Replay, contentDescription = "Restart", tint = Color.LightGray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(strings.playFromBeginningBtn, color = Color.LightGray, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            PlayDownloadPill(
                                playLabel = strings.playBtn,
                                onPlay = { onPlay(item.streamUrl, item.id, d.name, "Movie", d.backdropUrl, 0L) },
                                itemId = item.id, itemName = d.name, itemType = "Movie",
                                imageUrl = item.imageUrl, streamUrl = item.streamUrl
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Genre Chips
                        if (d.genres.isNotEmpty()) {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(d.genres.size) { index ->
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0x16FFFFFF), RoundedCornerShape(20.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                                            .padding(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(text = d.genres[index], color = Color.LightGray, fontSize = 12.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(20.dp))
                        }

                        // Synopsis
                        d.overview?.let {
                            var isExpanded by remember { mutableStateOf(false) }
                            var isOverflowing by remember { mutableStateOf(false) }
                            Text(
                                text = strings.synopsis,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = it,
                                color = Color.LightGray,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                                onTextLayout = { textLayoutResult ->
                                    if (!isExpanded && textLayoutResult.hasVisualOverflow) {
                                        isOverflowing = true
                                    }
                                }
                            )
                            if (isOverflowing && !isExpanded) {
                                Text(
                                    text = strings.seeMore,
                                    color = Color(0xFF7B3FE4),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .padding(top = 4.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { isExpanded = true }
                                        )
                                )
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Cast List
                        if (d.cast.isNotEmpty()) {
                            Text(
                                text = strings.mainCast,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(d.cast.size) { index ->
                                    val actor = d.cast[index]
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(80.dp)
                                    ) {
                                        AsyncImage(
                                            model = actor.imageUrl,
                                            contentDescription = actor.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(65.dp)
                                                .clip(CircleShape)
                                                .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = actor.name,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        actor.role?.let {
                                            Text(
                                                text = it,
                                                color = Color.Gray,
                                                fontSize = 10.sp,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Back Button floating at top-left
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
        }
    }
}

@Composable
fun TvShowDetailScreen(
    item: JellyfinItem,
    serverUrl: String,
    accessToken: String,
    userId: String,
    onEpisodeClick: (JellyfinEpisode) -> Unit,
    onResumeEpisode: (String, String, String, String, String, Long) -> Unit,
    onBack: () -> Unit
, sidebarPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    var details by remember { mutableStateOf<JellyfinDetails?>(null) }
    var episodes by remember { mutableStateOf<List<JellyfinEpisode>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedSeason by remember { mutableStateOf(1) }

    LaunchedEffect(item.id) {
        isLoading = true
        JellyfinService.fetchItemDetails(serverUrl, accessToken, userId, item.id) { resultDetails ->
            details = resultDetails
            JellyfinService.fetchEpisodes(serverUrl, accessToken, userId, item.id) { resultEpisodes ->
                episodes = resultEpisodes
                
                // Set default season ke season pertama yang ditemukan di data
                val seasonsList = resultEpisodes.map { it.seasonNumber }.distinct().sorted()
                if (seasonsList.isNotEmpty()) {
                    selectedSeason = seasonsList.first()
                }
                
                isLoading = false
            }
        }
    }

    val resolvedEpisodes = remember(episodes, isLoading) {
        val locals = LocalResumeManager.getLocalResumeItems(context)
        episodes.map { ep ->
            val local = locals.firstOrNull { it.id == ep.id }
            val serverMs = ep.playbackPositionTicks / 10000L
            val localMs = local?.positionMs ?: 0L
            val finalMs = if (localMs > serverMs) localMs else serverMs
            ep.copy(playbackPositionTicks = finalMs * 10000L)
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF7B3FE4))
            }
        } else {
            details?.let { d ->
                // Cari episode pertama yang memiliki progress tontonan
                val episodeToResume = resolvedEpisodes.firstOrNull { it.playbackPositionTicks > 0L }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    item {
                        // Backdrop Cover Image
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) 550.dp else 260.dp)
                        ) {
                            AsyncImage(
                                model = d.backdropUrl,
                                contentDescription = d.name,
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color(0xFF0A0A0A)),
                                            startY = 180f
                                        )
                                    )
                            )
                        }
                    }

                    item {
                        // Title and Metadata
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Text(
                                text = d.name,
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                d.year?.let { Text(text = "$it", color = Color.Gray, fontSize = 14.sp) }
                                d.rating?.let {
                                    Text(
                                        text = it,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .background(Color(0x33FFFFFF), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Text(
                                    text = "Serial",
                                    color = Color(0xFF7B3FE4),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                d.communityRating?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = "Rating",
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(text = String.format("%.1f", it), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Resume Button (Lanjutkan Menonton Serial)
                            if (episodeToResume != null) {
                                val resumeMs = episodeToResume.playbackPositionTicks / 10000L
                                val minutes = (resumeMs / 60000).toInt()
                                val seconds = ((resumeMs % 60000) / 1000).toInt()

                                Button(
                                    onClick = { onResumeEpisode(episodeToResume.streamUrl, episodeToResume.id, episodeToResume.name, "Episode", episodeToResume.imageUrl, resumeMs) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7B3FE4)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Lanjutkan", tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Lanjutkan: S${episodeToResume.seasonNumber} Ep ${episodeToResume.episodeNumber} (${minutes}:${String.format("%02d", seconds)})",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Genres
                            if (d.genres.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(d.genres.size) { index ->
                                        Box(
                                            modifier = Modifier
                                                .background(Color(0x16FFFFFF), RoundedCornerShape(20.dp))
                                                .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                        ) {
                                            Text(text = d.genres[index], color = Color.LightGray, fontSize = 12.sp)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                            }

                            // Synopsis
                            d.overview?.let {
                                var isExpanded by remember { mutableStateOf(false) }
                                var isOverflowing by remember { mutableStateOf(false) }
                                Text(
                                    text = strings.synopsis,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = it,
                                    color = Color.LightGray,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                                    overflow = TextOverflow.Ellipsis,
                                    onTextLayout = { textLayoutResult ->
                                        if (!isExpanded && textLayoutResult.hasVisualOverflow) {
                                            isOverflowing = true
                                        }
                                    }
                                )
                                if (isOverflowing && !isExpanded) {
                                    Text(
                                        text = strings.seeMore,
                                        color = Color(0xFF7B3FE4),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .padding(top = 4.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { isExpanded = true }
                                            )
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            // Cast List
                            if (d.cast.isNotEmpty()) {
                                Text(
                                    text = strings.mainCast,
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(d.cast.size) { index ->
                                        val actor = d.cast[index]
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.width(80.dp)
                                        ) {
                                            AsyncImage(
                                                model = actor.imageUrl,
                                                contentDescription = actor.name,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(65.dp)
                                                    .clip(CircleShape)
                                                    .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = actor.name,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(28.dp))
                            }

                            // Episode Section Header
                            Text(
                                text = strings.episodeList,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }

                    // Season Filter Chips
                    val seasonsList = resolvedEpisodes.map { it.seasonNumber }.distinct().sorted()
                    if (seasonsList.size > 1) {
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                items(seasonsList.size) { index ->
                                    val seasonNum = seasonsList[index]
                                    val isSelected = selectedSeason == seasonNum
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedSeason = seasonNum },
                                        label = { Text("Season $seasonNum", fontWeight = FontWeight.Bold) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = Color(0xFF7B3FE4),
                                            selectedLabelColor = Color.White,
                                            containerColor = Color(0x16FFFFFF),
                                            labelColor = Color.Gray
                                        ),
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = Color.White.copy(alpha = 0.1f),
                                            selectedBorderColor = Color(0xFF7B3FE4)
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Episodes List
                    val filteredEpisodes = resolvedEpisodes.filter { it.seasonNumber == selectedSeason }
                    if (filteredEpisodes.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(strings.noEpisodes, color = Color.Gray)
                            }
                        }
                    } else {
                        items(filteredEpisodes.size) { index ->
                            val ep = filteredEpisodes[index]
                            EpisodeItemRow(episode = ep, onEpisodeClick = { onEpisodeClick(ep) })
                        }
                    }
                }
            }
        }

        // Back Button floating at top-left
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
        }
    }
}

@Composable
fun EpisodeItemRow(episode: JellyfinEpisode, onEpisodeClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0x0AFFFFFF)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
            .clickable(onClick = onEpisodeClick),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 70.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = episode.imageUrl,
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Info Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Info,
                        contentDescription = "Info",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Episode Text Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Eps ${episode.episodeNumber}",
                        color = Color(0xFF7B3FE4),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (episode.playbackPositionTicks > 0L) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF7B3FE4).copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("Progres", color = Color(0xFF7B3FE4), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = episode.name,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                episode.overview?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun EpisodeDetailScreen(
    episode: JellyfinEpisode,
    onPlay: (String, String, String, String, String, Long) -> Unit,
    onBack: () -> Unit
, sidebarPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val strings = LocalAppStrings.current
    val context = LocalContext.current
    val localProgress = remember(episode.id) {
        LocalResumeManager.getLocalResumeItems(context).firstOrNull { it.id == episode.id }
    }
    val finalResumeMs = remember(episode, localProgress) {
        val serverMs = episode.playbackPositionTicks / 10000L
        val localMs = localProgress?.positionMs ?: 0L
        if (localMs > serverMs) localMs else serverMs
    }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            // Backdrop Cover Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) 550.dp else 260.dp)
            ) {
                AsyncImage(
                    model = episode.imageUrl,
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xFF0A0A0A)),
                                startY = 120f
                            )
                        )
                )
            }

            // Episode Metadata and Details
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    text = "Season ${episode.seasonNumber} • Episode ${episode.episodeNumber}",
                    color = Color(0xFF7B3FE4),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = episode.name,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Play / Resume Button + Download — pill shape
                if (finalResumeMs > 0L) {
                    val minutes = (finalResumeMs / 60000).toInt()
                    val seconds = ((finalResumeMs % 60000) / 1000).toInt()

                    PlayDownloadPill(
                        playLabel = "${strings.resumeBtn} (${minutes}:${String.format("%02d", seconds)})",
                        onPlay = { onPlay(episode.streamUrl, episode.id, episode.name, "Episode", episode.imageUrl, finalResumeMs) },
                        itemId = episode.id, itemName = episode.name, itemType = "Episode",
                        imageUrl = episode.imageUrl, streamUrl = episode.streamUrl
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { onPlay(episode.streamUrl, episode.id, episode.name, "Episode", episode.imageUrl, 0L) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(45.dp)
                            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Replay, contentDescription = "Ulangi", tint = Color.LightGray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(strings.playFromBeginningBtn, color = Color.LightGray, fontWeight = FontWeight.Bold)
                    }
                } else {
                    PlayDownloadPill(
                        playLabel = strings.playBtn,
                        onPlay = { onPlay(episode.streamUrl, episode.id, episode.name, "Episode", episode.imageUrl, 0L) },
                        itemId = episode.id, itemName = episode.name, itemType = "Episode",
                        imageUrl = episode.imageUrl, streamUrl = episode.streamUrl
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Overview
                Text(
                    text = strings.synopsisEpisode,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                val overviewText = episode.overview ?: strings.noSynopsis
                var isExpanded by remember { mutableStateOf(false) }
                var isOverflowing by remember { mutableStateOf(false) }
                Text(
                    text = overviewText,
                    color = Color.LightGray,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { textLayoutResult ->
                        if (!isExpanded && textLayoutResult.hasVisualOverflow) {
                            isOverflowing = true
                        }
                    }
                )
                if (isOverflowing && !isExpanded) {
                    Text(
                        text = strings.seeMore,
                        color = Color(0xFF7B3FE4),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { isExpanded = true }
                            )
                    )
                }
            }
        }

        // Floating Back Button
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(16.dp)
                .align(Alignment.TopStart)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
        }
    }
}

@Composable
fun GlassBottomNavigation(selectedTab: String, onTabSelected: (String) -> Unit, hazeState: HazeState, modifier: Modifier = Modifier) {
    val navItems = listOf(
        Pair("home", Icons.Filled.Home),
        Pair("movies", Icons.Filled.Movie),
        Pair("tv", Icons.Filled.Tv),
        Pair("download", Icons.Filled.Download),
        Pair("settings", Icons.Filled.Settings)
    )

    val selectedIndex = navItems.indexOfFirst { it.first == selectedTab }.takeIf { it >= 0 } ?: 0

    BoxWithConstraints(
        modifier = modifier
            .padding(bottom = 20.dp)
            .fillMaxWidth(0.9f)
            .height(65.dp)
    ) {
        val segmentWidth = maxWidth / navItems.size
        
        // Background layer with Haze Blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(40.dp))
                .hazeChild(state = hazeState, shape = RoundedCornerShape(40.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0x40FFFFFF), // Slightly more visible glass effect
                            Color(0x267B3FE4),
                            Color(0x26FFFFFF)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    ),
                    shape = RoundedCornerShape(40.dp)
                )
        )
        
        val indicatorOffset by animateDpAsState(
            targetValue = segmentWidth * selectedIndex,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
            label = "indicatorOffset"
        )
        
        Box(
            modifier = Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF7B3FE4))
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, (route, icon) ->
                val isSelected = selectedIndex == index
                
                val iconColor by animateColorAsState(
                    targetValue = if (isSelected) Color.White else Color.Gray,
                    animationSpec = tween(durationMillis = 300),
                    label = "iconColor"
                )
                
                val iconSize by animateDpAsState(
                    targetValue = if (isSelected) 28.dp else 24.dp,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                    label = "iconSize"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onTabSelected(route) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = route,
                        tint = iconColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }
    }
}

@Composable
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun ExoPlayerScreen(
    videoUrl: String,
    itemId: String,
    itemName: String,
    itemType: String,
    imageUrl: String,
    serverUrl: String,
    accessToken: String,
    startPositionMs: Long = 0L,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val view = LocalView.current
    val window = activity?.window

    var subtitles by remember { mutableStateOf<List<JellyfinSubtitle>>(emptyList()) }
    var isSubtitlesLoaded by remember { mutableStateOf(false) }

    val sharedPrefs = context.getSharedPreferences("JellyfinPrefs", android.content.Context.MODE_PRIVATE)
    var subtitleSizeSp by remember { mutableStateOf(sharedPrefs.getFloat("default_subtitle_size", 18f)) }
    var subtitleBgOpacity by remember { mutableStateOf(0.0f) }
    var subtitleColorState by remember { mutableStateOf(android.graphics.Color.WHITE) }
    var subtitleEdgeType by remember { mutableStateOf(sharedPrefs.getString("default_subtitle_edge", androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE.toString())?.toIntOrNull() ?: androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE) }
    var showSubSettings by remember { mutableStateOf(false) }
    var isControllerVisible by remember { mutableStateOf(true) }

    var currentVideoUrl by remember { mutableStateOf(videoUrl) }
    var selectedQuality by remember { mutableStateOf("Original") }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var currentSeekPosition by remember { mutableStateOf(startPositionMs) }
    var selectedAspectRatio by remember { mutableStateOf(androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var selectedPlaybackSpeed by remember { mutableStateOf(1f) }

    // Lock screen orientation to Landscape, hide System Bars, and keep screen on
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Prevent screen from dimming during playback
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            window?.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_HDR
        }
        
        if (window != null) {
            val windowInsetsController = WindowCompat.getInsetsController(window, view)
            windowInsetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            // Re-allow screen to sleep when exiting player
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                window?.colorMode = android.content.pm.ActivityInfo.COLOR_MODE_DEFAULT
            }

            if (originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
            if (window != null) {
                val windowInsetsController = WindowCompat.getInsetsController(window, view)
                // Restore only navigation bars, keeping status bars hidden globally!
                windowInsetsController.show(WindowInsetsCompat.Type.navigationBars())
                windowInsetsController.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    // Fetch Subtitles from Jellyfin API
    LaunchedEffect(itemId) {
        if (itemId.isNotEmpty()) {
            JellyfinService.fetchSubtitles(serverUrl, accessToken, itemId) { result ->
                subtitles = result
                isSubtitlesLoaded = true
            }
        } else {
            isSubtitlesLoaded = true
        }
    }

    // Build Player once subtitles are loaded
    val exoPlayer = remember(subtitles, isSubtitlesLoaded, currentVideoUrl) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build().apply {
            val subtitleConfigurations = subtitles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(sub.url))
                    .setMimeType(sub.mimeType)
                    .setLanguage(sub.language)
                    .setLabel(sub.label)
                    .setSelectionFlags(androidx.media3.common.C.SELECTION_FLAG_DEFAULT)
                    .build()
            }
            
            val mediaItem = MediaItem.Builder()
                .setUri(currentVideoUrl)
                .setSubtitleConfigurations(subtitleConfigurations)
                .build()
            
            setMediaItem(mediaItem)
            
            // Prefer Indonesian subtitles
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredTextLanguage("ind")
                .build()

            // Seek to start position (resume)
            if (currentSeekPosition > 0L) {
                seekTo(currentSeekPosition)
            }
            
            prepare()
            playWhenReady = true
        }
    }

    // Progress Reporting Loop (Setiap 10 detik)
    LaunchedEffect(exoPlayer) {
        while (true) {
            delay(10000)
            val currentPos = exoPlayer.currentPosition
            val duration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            val isPlaying = exoPlayer.isPlaying
            
            // Save local progress
            LocalResumeManager.saveLocalProgress(
                context = context,
                itemId = itemId,
                name = itemName,
                type = itemType,
                imageUrl = imageUrl,
                streamUrl = currentVideoUrl,
                positionMs = currentPos,
                durationMs = duration
            )
            
            // Report to Jellyfin server
            JellyfinService.reportPlaybackProgress(
                serverUrl = serverUrl,
                accessToken = accessToken,
                itemId = itemId,
                positionMs = currentPos,
                isPaused = !isPlaying
            )
        }
    }

    // Report Stopped Status on exit/dispose
    DisposableEffect(exoPlayer) {
        onDispose {
            val finalPos = exoPlayer.currentPosition
            val duration = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            
            // Save final local progress
            LocalResumeManager.saveLocalProgress(
                context = context,
                itemId = itemId,
                name = itemName,
                type = itemType,
                imageUrl = imageUrl,
                streamUrl = currentVideoUrl,
                positionMs = finalPos,
                durationMs = duration
            )
            
            // Report stopped to Jellyfin server
            JellyfinService.reportPlaybackStopped(
                serverUrl = serverUrl,
                accessToken = accessToken,
                itemId = itemId,
                positionMs = finalPos
            )
            exoPlayer.release()
        }
    }

    var isPlayingState by remember { mutableStateOf(true) }
    var currentPosState by remember { mutableStateOf(startPositionMs) }
    var durationState by remember { mutableStateOf(0L) }

    LaunchedEffect(exoPlayer) {
        while (true) {
            isPlayingState = exoPlayer.isPlaying
            currentPosState = exoPlayer.currentPosition
            durationState = exoPlayer.duration.takeIf { it > 0 } ?: 0L
            kotlinx.coroutines.delay(500)
        }
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    var subtitleBottomPadding by remember { mutableStateOf(0.08f) }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (!isSubtitlesLoaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF7B3FE4))
            }
        } else {
            AndroidView(
                factory = {
                    PlayerView(context).apply {
                        player = exoPlayer
                        useController = false
                        setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.resizeMode = selectedAspectRatio
                    if (exoPlayer.playbackParameters.speed != selectedPlaybackSpeed) {
                        exoPlayer.setPlaybackSpeed(selectedPlaybackSpeed)
                    }
                    // Apply Netflix-style subtitle rendering
                    playerView.subtitleView?.apply {
                        setApplyEmbeddedStyles(false)
                        setApplyEmbeddedFontSizes(false)
                        setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleSizeSp)

                        // Netflix-like caption style: white text, bold, thick black outline, user-controlled background
                        val bgAlpha = (subtitleBgOpacity * 255).toInt()
                        val bgColor = android.graphics.Color.argb(bgAlpha, 0, 0, 0)
                        val captionStyle = androidx.media3.ui.CaptionStyleCompat(
                            subtitleColorState,                 // foreground (text) color
                            bgColor,                            // background color with user opacity
                            android.graphics.Color.TRANSPARENT, // window color
                            subtitleEdgeType,                   // edge style
                            android.graphics.Color.BLACK,       // edge (outline) color
                            android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD) // bold sans-serif like Netflix
                        )
                        setStyle(captionStyle)
                        setBottomPaddingFraction(subtitleBottomPadding)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        CustomPlayerControls(
            isPlaying = isPlayingState,
            onPlayPauseToggle = {
                if (isPlayingState) exoPlayer.pause() else exoPlayer.play()
            },
            onSeekForward = {
                exoPlayer.seekTo(exoPlayer.currentPosition + 10000)
            },
            onSeekBackward = {
                exoPlayer.seekTo((exoPlayer.currentPosition - 10000).coerceAtLeast(0L))
            },
            currentPositionMs = currentPosState,
            durationMs = durationState,
            onSeekPositionChange = { newPos ->
                exoPlayer.seekTo(newPos)
                currentPosState = newPos
            },
            onBack = onBack,
            onSubtitlesClick = { showSubSettings = !showSubSettings; showSettingsMenu = false },
            onSettingsClick = { showSettingsMenu = !showSettingsMenu; showSubSettings = false },
            onSubtitleDrag = { dragAmount ->
                val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                subtitleBottomPadding = (subtitleBottomPadding - (dragAmount / screenHeightPx)).coerceIn(0.01f, 0.9f)
            }
        )

        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            // Subtitle Settings Dialog
                if (showSubSettings) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(360.dp)
                            .padding(16.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.75f))), RoundedCornerShape(16.dp))
                            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 1f), Color.Transparent)), RoundedCornerShape(16.dp))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.material3.IconButton(
                                onClick = { showSubSettings = false },
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = Color.Black)
                            }
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 20.dp, vertical = 20.dp)
                                    .fillMaxHeight(0.85f)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                            Text(
                                text = "Pengaturan Subtitle",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            // --- Font Size ---
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Ukuran Font", color = Color.DarkGray, fontSize = 13.sp)
                                Text("${subtitleSizeSp.toInt()} sp", color = Color(0xFF7B3FE4), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = subtitleSizeSp,
                                onValueChange = { subtitleSizeSp = it },
                                valueRange = 12f..36f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF7B3FE4),
                                    activeTrackColor = Color(0xFF7B3FE4)
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // --- Background Opacity ---
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Latar Belakang", color = Color.DarkGray, fontSize = 13.sp)
                                Text("${(subtitleBgOpacity * 100).toInt()}%", color = Color(0xFF7B3FE4), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = subtitleBgOpacity,
                                onValueChange = { subtitleBgOpacity = it },
                                valueRange = 0f..1f,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF7B3FE4),
                                    activeTrackColor = Color(0xFF7B3FE4)
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // --- Edge Style ---
                            Text("Gaya Tepi (Bayangan)", color = Color.DarkGray, fontWeight = FontWeight.Bold)
                            val edgeOptions = listOf(
                                Pair("Garis", androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE),
                                Pair("Drop Shadow", androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW),
                                Pair("Timbul", androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_RAISED)
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.Start
                            ) {
                                edgeOptions.forEach { (label, edgeType) ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().clickable { subtitleEdgeType = edgeType }.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (subtitleEdgeType == edgeType) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (subtitleEdgeType == edgeType) Color(0xFF7B3FE4) else Color.Gray,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(text = label, color = Color.Black, fontSize = 14.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Live Preview
                            val previewBg = Color.Black.copy(alpha = subtitleBgOpacity)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .background(Color(0x0D000000), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Halo Dunia / Hello World",
                                    color = Color(subtitleColorState),
                                    fontSize = subtitleSizeSp.coerceIn(12f, 22f).sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif,
                                    modifier = Modifier
                                        .background(previewBg, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                        }
                    }
                }

                // Unified Settings Menu Dialog
                if (showSettingsMenu) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(340.dp)
                            .padding(16.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp))
                            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.9f), Color.White.copy(alpha = 0.75f))), RoundedCornerShape(16.dp))
                            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 1f), Color.Transparent)), RoundedCornerShape(16.dp))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            androidx.compose.material3.IconButton(
                                onClick = { showSettingsMenu = false },
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = Color.Black)
                            }
                            Column(
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxHeight(0.85f)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.Start
                            ) {
                            Text(
                                text = "Pengaturan",
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            // --- Aspek Rasio ---
                            Text("Aspek Rasio", color = Color.DarkGray, fontWeight = FontWeight.Bold)
                            val aspectOptions = listOf(
                                Pair("Fit Screen", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT),
                                Pair("Zoom", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
                                Pair("Stretch", androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL)
                            )
                            aspectOptions.forEach { (label, mode) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { selectedAspectRatio = mode }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (selectedAspectRatio == mode) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (selectedAspectRatio == mode) Color(0xFF7B3FE4) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = label, color = Color.Black, fontSize = 14.sp)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color.LightGray)
                            Spacer(modifier = Modifier.height(12.dp))

                            // --- Kecepatan Putar ---
                            Text("Kecepatan Putar", color = Color.DarkGray, fontWeight = FontWeight.Bold)
                            val speedOptions = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                            speedOptions.forEach { speed ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { selectedPlaybackSpeed = speed }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (selectedPlaybackSpeed == speed) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (selectedPlaybackSpeed == speed) Color(0xFF7B3FE4) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = "${speed}x", color = Color.Black, fontSize = 14.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color.LightGray)
                            Spacer(modifier = Modifier.height(12.dp))

                            // --- Kualitas Video ---
                            Text("Kualitas Video", color = Color.DarkGray, fontWeight = FontWeight.Bold)
                            val qualityOptions = listOf(
                                Pair("Original (Direct Play)", ""),
                                Pair("1080p (10 Mbps)", "10000000"),
                                Pair("720p (3 Mbps)", "3000000"),
                                Pair("480p (1.5 Mbps)", "1500000")
                            )
                            qualityOptions.forEach { (label, bitrate) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable {
                                        if (selectedQuality != label) {
                                            selectedQuality = label
                                            currentSeekPosition = exoPlayer.currentPosition
                                            showSettingsMenu = false
                                            if (bitrate.isEmpty()) {
                                                currentVideoUrl = videoUrl
                                            } else {
                                                val base = videoUrl.replace("static=true&", "").replace("static=true", "")
                                                val separator = if (base.contains("?")) "&" else "?"
                                                currentVideoUrl = "${base}${separator}MaxStreamingBitrate=$bitrate&VideoCodec=hevc,h265&Profile=Main10"
                                            }
                                        }
                                    }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (selectedQuality == label) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (selectedQuality == label) Color(0xFF7B3FE4) else Color.Gray,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(text = label, color = Color.Black, fontSize = 14.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

}

@Composable
fun GlassNavigationRail(
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    hazeState: dev.chrisbanes.haze.HazeState,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val navItems = listOf(
        Pair("home",     Pair(strings.tabHome,     androidx.compose.material.icons.Icons.Filled.Home)),
        Pair("movies",   Pair(strings.tabMovies,   androidx.compose.material.icons.Icons.Filled.Movie)),
        Pair("tv",       Pair(strings.tabTv,       androidx.compose.material.icons.Icons.Filled.Tv)),
        Pair("download", Pair(strings.tabDownload, androidx.compose.material.icons.Icons.Filled.Download)),
        Pair("settings", Pair(strings.tabSettings, androidx.compose.material.icons.Icons.Filled.Settings))
    )

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF080808),   // kiri: hitam solid
                        Color(0xFF1C0B3F),   // kanan: ungu gelap
                    )
                )
            )
    ) {
        // Shimmer highlight kiri atas (efek pantulan cahaya kaca)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .width(1.5.dp)
                .fillMaxHeight(0.55f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.25f),
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Border tipis kanan dengan glow ungu
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(1.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x607B3FE4),
                            Color(0x907B3FE4),
                            Color(0x607B3FE4),
                            Color.Transparent
                        )
                    )
                )
        )

        // Konten navigasi — selalu tampil dengan icon + label
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 32.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // Logo Header
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Start
            ) {
                androidx.compose.material3.Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.PlayCircle,
                    contentDescription = "Nexfin",
                    tint = Color(0xFF7B3FE4),
                    modifier = Modifier.size(30.dp)
                )
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(14.dp))
                androidx.compose.material3.Text(
                    text = "Nexfin",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(40.dp))

            navItems.forEach { item ->
                val id = item.first
                val label = item.second.first
                val icon = item.second.second
                val isSelected = selectedTab == id

                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                        .background(
                            if (isSelected)
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF7B3FE4).copy(alpha = 0.35f), Color(0xFF7B3FE4).copy(alpha = 0.10f))
                                )
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .border(
                            width = if (isSelected) 1.dp else 0.dp,
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF7B3FE4).copy(alpha = 0.5f), Color.Transparent)
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        )
                        .clickable { onTabSelected(id) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Start
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) Color(0xFFB388FF) else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(14.dp))
                    androidx.compose.material3.Text(
                        text = label,
                        color = if (isSelected) Color.White else Color.Gray,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}