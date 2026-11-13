package com.axltv.launcher

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

data class TvAppInfo(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val banner: ImageBitmap?,
    val dominantColor: Color,
    val isSideloaded: Boolean
)

enum class AmbientTheme(val title: String, val primary: Color) {
    DYNAMIC("Adaptive", Color(0xFF2563EB)),
    ROYAL_FELT("Royal Casino", Color(0xFF059669)),
    MIDNIGHT("Midnight", Color(0xFF334155)),
    CRIMSON("Crimson", Color(0xFFE11D48))
}

enum class RobotType(val title: String, val subtitle: String, val accent: Color) {
    ASTRO("Axl-Bot Astro", "Companion Bot Bulat", Color(0xFF38BDF8)),
    SCORPIO("Scorpio SC-7", "Mecha Kalajengking Taktis", Color(0xFFF97316)),
    XGODUCK("XGO-Duck", "Bipedal Mecha Bebek", Color(0xFFFACC15)),
    CRAWLER("Cyber Crawler", "Hexapod Laba-Laba 6 Kaki", Color(0xFFA855F7)),
    TITAN("Mecha Titan", "Guardian Robot Berat", Color(0xFFEF4444)),
    NEKO("Neko-Bot", "Kucing Cyber Futuristik", Color(0xFFEC4899)),
    DRONE("Orb Drone", "Giroskop Melayang", Color(0xFF10B981))
}

enum class CardDeckFilter(val label: String, val suit: String) {
    ALL("Semua Deck", "♠♥♦♣"),
    FAVORITES("Favorit", "♥"),
    TV_APPS("Google TV", "♠"),
    SIDELOAD("Sideload", "♣")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BackHandler(enabled = true) {}
            AxlTvLauncherScreen()
        }
    }
}

@Composable
fun AxlTvLauncherScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("axltv_prefs", Context.MODE_PRIVATE) }
    val coroutineScope = rememberCoroutineScope()

    var allApps by remember { mutableStateOf<List<TvAppInfo>>(emptyList()) }
    var pinnedPackages by remember {
        mutableStateOf(prefs.getStringSet("pinned_apps", emptySet())?.toList() ?: emptyList())
    }
    var kidsPackages by remember {
        mutableStateOf(prefs.getStringSet("kids_apps", emptySet())?.toList() ?: emptyList())
    }
    var isKidsMode by remember { mutableStateOf(prefs.getBoolean("is_kids_mode", false)) }
    var selectedTheme by remember {
        mutableStateOf(
            AmbientTheme.entries.find { it.name == prefs.getString("ambient_theme", "DYNAMIC") }
                ?: AmbientTheme.DYNAMIC
        )
    }
    var selectedRobot by remember {
        mutableStateOf(
            RobotType.entries.find { it.name == prefs.getString("selected_robot", "ASTRO") }
                ?: RobotType.ASTRO
        )
    }
    var activeFilter by remember { mutableStateOf(CardDeckFilter.ALL) }

    var focusedIndex by remember { mutableIntStateOf(0) }
    var contextMenuApp by remember { mutableStateOf<TvAppInfo?>(null) }
    var showRobotPicker by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }

    // Pelacakan pergerakan kartu untuk animasi robot
    var focusNormalizedX by remember { mutableFloatStateOf(0f) }
    var focusStepCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val loaded = loadInstalledApps(context)
        allApps = loaded
        if (pinnedPackages.isEmpty() && loaded.isNotEmpty()) {
            val defaults = loaded.take(6).map { it.packageName }
            pinnedPackages = defaults
            prefs.edit().putStringSet("pinned_apps", defaults.toSet()).apply()
        }
    }

    val deckApps = remember(allApps, isKidsMode, kidsPackages, activeFilter, pinnedPackages, searchQuery) {
        var list = if (!isKidsMode) allApps
        else {
            val filtered = allApps.filter { kidsPackages.contains(it.packageName) }
            filtered.ifEmpty {
                allApps.filter {
                    it.label.contains("YouTube", true) ||
                            it.label.contains("Kids", true) ||
                            it.label.contains("Disney", true) ||
                            it.label.contains("Netflix", true)
                }
            }
        }

        list = when (activeFilter) {
            CardDeckFilter.ALL -> list
            CardDeckFilter.FAVORITES -> list.filter { pinnedPackages.contains(it.packageName) }
            CardDeckFilter.TV_APPS -> list.filter { !it.isSideloaded }
            CardDeckFilter.SIDELOAD -> list.filter { it.isSideloaded }
        }

        if (searchQuery.isNotBlank()) {
            list = list.filter { it.label.contains(searchQuery, ignoreCase = true) }
        }
        list
    }

    val safeFocusedIndex = focusedIndex.coerceIn(0, (deckApps.size - 1).coerceAtLeast(0))
    val focusedApp = deckApps.getOrNull(safeFocusedIndex)

    val targetGlowColor = when {
        isKidsMode -> Color(0xFFF59E0B)
        selectedTheme == AmbientTheme.DYNAMIC -> focusedApp?.dominantColor ?: Color(0xFF1E293B)
        else -> selectedTheme.primary
    }

    val animatedGlowColor by animateColorAsState(
        targetValue = targetGlowColor,
        animationSpec = tween(durationMillis = 600),
        label = "bgGlow"
    )

    val listState = rememberLazyListState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070B))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        animatedGlowColor.copy(alpha = 0.50f),
                        Color(0xFF0A0E17).copy(alpha = 0.92f),
                        Color(0xFF040609)
                    ),
                    radius = 1600f
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. TOP BAR
            TopHeaderBar(
                isKidsMode = isKidsMode,
                currentTheme = selectedTheme,
                currentRobot = selectedRobot,
                showSearch = showSearchBar,
                searchQuery = searchQuery,
                onSearchQueryChange = {
                    searchQuery = it
                    focusedIndex = 0
                },
                onToggleSearch = {
                    showSearchBar = !showSearchBar
                    if (!showSearchBar) searchQuery = ""
                },
                onOpenRobotPicker = { showRobotPicker = true },
                onToggleKidsMode = {
                    isKidsMode = !isKidsMode
                    focusedIndex = 0
                    prefs.edit().putBoolean("is_kids_mode", isKidsMode).apply()
                },
                onCycleTheme = {
                    val nextIndex = (selectedTheme.ordinal + 1) % AmbientTheme.entries.size
                    selectedTheme = AmbientTheme.entries[nextIndex]
                    prefs.edit().putString("ambient_theme", selectedTheme.name).apply()
                }
            )

            // 2. TOP SHELF HERO + ROBOT PENDAMPING + FILTER SUIT KARTU REMI
            TopShelfDeckStage(
                app = focusedApp,
                cardNumber = if (deckApps.isEmpty()) 0 else safeFocusedIndex + 1,
                totalCards = deckApps.size,
                isPinned = focusedApp != null && pinnedPackages.contains(focusedApp.packageName),
                isKidsMode = isKidsMode,
                robotType = selectedRobot,
                activeFilter = activeFilter,
                lookX = focusNormalizedX,
                stepTrigger = focusStepCount,
                onSelectFilter = { filter ->
                    activeFilter = filter
                    focusedIndex = 0
                },
                onRobotClick = { showRobotPicker = true }
            )

            // 3. JAJARAN KARTU REMI (PLAYING CARD FAN CAROUSEL)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                if (deckApps.isEmpty()) {
                    Text(
                        text = "Tidak ada kartu aplikasi di kategori ini.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 16.sp
                    )
                } else {
                    LazyRow(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 120.dp, vertical = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy((-28).dp), // Efek kartu remi berjejer saling tumpang-tindih
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(deckApps, key = { _, app -> "card_${app.packageName}" }) { index, app ->
                            val isSelected = index == safeFocusedIndex
                            val distanceFromFocus = index - safeFocusedIndex

                            PlayingCardAppItem(
                                app = app,
                                index = index,
                                isSelected = isSelected,
                                distanceFromFocus = distanceFromFocus,
                                isPinned = pinnedPackages.contains(app.packageName),
                                onFocused = {
                                    focusedIndex = index
                                    focusStepCount += 1
                                    val ratio = if (deckApps.size <= 1) 0f
                                    else ((index.toFloat() / (deckApps.size - 1)) - 0.5f) * 2f
                                    focusNormalizedX = ratio.coerceIn(-1f, 1f)
                                    coroutineScope.launch {
                                        listState.animateScrollToItem(
                                            index = (index - 2).coerceAtLeast(0)
                                        )
                                    }
                                },
                                onClick = { launchApp(context, app.packageName) },
                                onLongClick = { contextMenuApp = app }
                            )
                        }
                    }
                }
            }
        }

        // Dialog Pilih Jenis Robot
        if (showRobotPicker) {
            RobotPickerDialog(
                selectedRobot = selectedRobot,
                onSelect = { robot ->
                    selectedRobot = robot
                    prefs.edit().putString("selected_robot", robot.name).apply()
                    focusStepCount += 1
                    showRobotPicker = false
                },
                onDismiss = { showRobotPicker = false }
            )
        }

        // Dialog Menu Konteks Aplikasi (Tekan Lama OK)
        contextMenuApp?.let { targetApp ->
            val isPinned = pinnedPackages.contains(targetApp.packageName)
            val isKidAllowed = kidsPackages.contains(targetApp.packageName)
            AppContextDialog(
                app = targetApp,
                isPinned = isPinned,
                isKidAllowed = isKidAllowed,
                onDismiss = { contextMenuApp = null },
                onTogglePin = {
                    val updated = if (isPinned) pinnedPackages - targetApp.packageName
                    else pinnedPackages + targetApp.packageName
                    pinnedPackages = updated
                    prefs.edit().putStringSet("pinned_apps", updated.toSet()).apply()
                    contextMenuApp = null
                },
                onToggleKidsWhitelist = {
                    val updated = if (isKidAllowed) kidsPackages - targetApp.packageName
                    else kidsPackages + targetApp.packageName
                    kidsPackages = updated
                    prefs.edit().putStringSet("kids_apps", updated.toSet()).apply()
                    contextMenuApp = null
                },
                onAppInfo = {
                    openAppDetails(context, targetApp.packageName)
                    contextMenuApp = null
                },
                onUninstall = {
                    uninstallApp(context, targetApp.packageName)
                    contextMenuApp = null
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayingCardAppItem(
    app: TvAppInfo,
    index: Int,
    isSelected: Boolean,
    distanceFromFocus: Int,
    isPinned: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // Simbol kartu remi bergantian per urutan
    val suits = listOf("♠", "♥", "♦", "♣")
    val suitSymbol = if (isPinned) "♥" else suits[index % suits.size]
    val isRedSuit = suitSymbol == "♥" || suitSymbol == "♦"
    val ranks = listOf("A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3", "2")
    val rankLabel = if (isPinned) "A" else ranks[index % ranks.size]

    // Animasi efek kartu remi saat dipilih: terangkat tinggi, membesar, dan lurus sempurna
    val liftOffsetY by animateFloatAsState(
        targetValue = if (isSelected) -34f else (abs(distanceFromFocus).coerceAtMost(4) * 5f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardLift"
    )

    val cardScale by animateFloatAsState(
        targetValue = if (isSelected) 1.18f else 0.94f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "cardScale"
    )

    // Efek kipas (fanning tilt): kartu di kiri miring ke kiri, kartu di kanan miring ke kanan, kartu terpilih tegak 0°
    val targetRotation = when {
        isSelected -> 0f
        distanceFromFocus < 0 -> (distanceFromFocus * 3.2f).coerceAtLeast(-14f)
        else -> (distanceFromFocus * 3.2f).coerceAtMost(14f)
    }
    val cardRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cardRotation"
    )

    // Geser kartu tetangga sedikit menjauh agar kartu terpilih terlihat utuh
    val spreadOffsetX by animateFloatAsState(
        targetValue = when {
            isSelected -> 0f
            distanceFromFocus < 0 -> -18f
            else -> 18f
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "spreadX"
    )

    val zOrder = if (isSelected) 50f else (20f - abs(distanceFromFocus).coerceAtMost(19))

    Box(
        modifier = Modifier
            .zIndex(zOrder)
            .graphicsLayer {
                translationY = liftOffsetY
                translationX = spreadOffsetX
                scaleX = cardScale
                scaleY = cardScale
                rotationZ = cardRotation
                cameraDistance = 12f * density
            }
            .width(168.dp)
            .height(242.dp)
            .shadow(
                elevation = if (isSelected) 32.dp else 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = if (isSelected) Color(0xFFFBBF24) else Color.Black,
                spotColor = if (isSelected) Color.White else Color.Black
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    colors = if (isSelected) {
                        listOf(
                            Color(0xFF1E293B),
                            app.dominantColor.copy(alpha = 0.85f),
                            Color(0xFF0F172A)
                        )
                    } else {
                        listOf(
                            Color(0xFF151C2C),
                            Color(0xFF0D121D)
                        )
                    }
                )
            )
            .border(
                border = if (isSelected) {
                    BorderStroke(
                        3.dp,
                        Brush.linearGradient(
                            listOf(Color(0xFFFDE68A), Color.White, Color(0xFFF59E0B))
                        )
                    )
                } else {
                    BorderStroke(1.2.dp, Color.White.copy(alpha = 0.22f))
                },
                shape = RoundedCornerShape(20.dp)
            )
            .onFocusChanged { state ->
                if (state.isFocused) onFocused()
            }
            .focusable()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(12.dp)
    ) {
        // Sudut Kiri Atas Kartu Remi (Rank + Suit)
        Column(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = rankLabel,
                color = if (isSelected) Color(0xFFFDE68A) else Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 15.sp
            )
            Text(
                text = suitSymbol,
                color = if (isRedSuit) Color(0xFFF87171) else Color(0xFF93C5FD),
                fontSize = 15.sp,
                lineHeight = 15.sp
            )
        }

        // Badge Sideload di Kanan Atas jika aplikasi HP
        if (app.isSideloaded) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "APK",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Bagian Tengah Kartu: Ikon Aplikasi + Bingkai Dalam Emas/Kaca + Nama Aplikasi
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .shadow(if (isSelected) 16.dp else 4.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(app.dominantColor.copy(alpha = 0.35f))
                    .border(
                        1.5.dp,
                        if (isSelected) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.15f),
                        RoundedCornerShape(20.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
            }

            Text(
                text = app.label,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )
        }

        // Sudut Kanan Bawah Kartu Remi (Terbalik 180 derajat ala kartu remi asli)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .graphicsLayer { rotationZ = 180f },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = rankLabel,
                color = if (isSelected) Color(0xFFFDE68A) else Color.White.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 15.sp
            )
            Text(
                text = suitSymbol,
                color = if (isRedSuit) Color(0xFFF87171) else Color(0xFF93C5FD),
                fontSize = 15.sp,
                lineHeight = 15.sp
            )
        }

        // Kilauan diagonal khas kartu foil saat disorot
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.22f),
                                Color.Transparent,
                                Color(0xFFFDE68A).copy(alpha = 0.10f)
                            )
                        )
                    )
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopShelfDeckStage(
    app: TvAppInfo?,
    cardNumber: Int,
    totalCards: Int,
    isPinned: Boolean,
    isKidsMode: Boolean,
    robotType: RobotType,
    activeFilter: CardDeckFilter,
    lookX: Float,
    stepTrigger: Int,
    onSelectFilter: (CardDeckFilter) -> Unit,
    onRobotClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Banner Info Kartu Aktif + Robot Pendamping
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(142.dp)
                .shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = app?.dominantColor ?: Color.Black)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            (app?.dominantColor ?: Color(0xFF1E293B)).copy(alpha = 0.58f),
                            Color(0xFF111624).copy(alpha = 0.88f),
                            Color(0xFF0A0D16).copy(alpha = 0.96f)
                        )
                    )
                )
                .border(
                    1.2.dp,
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.08f))
                    ),
                    RoundedCornerShape(24.dp)
                )
                .padding(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (app != null) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        Image(
                            bitmap = app.icon,
                            contentDescription = app.label,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(18.dp))
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = app.label,
                                    color = Color.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                BadgePill(
                                    text = "KARTU $cardNumber / $totalCards",
                                    color = Color(0xFFF59E0B).copy(alpha = 0.35f)
                                )
                                if (isPinned) {
                                    BadgePill(text = "♥ FAVORIT", color = Color(0xFFEF4444).copy(alpha = 0.45f))
                                }
                                if (app.isSideloaded) {
                                    BadgePill(text = "♣ SIDELOAD", color = Color.White.copy(alpha = 0.18f))
                                }
                            }
                            Text(
                                text = "Geser Kiri/Kanan untuk memilih kartu  •  OK: Buka  •  Tahan OK: Opsi Kartu",
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // Robot Pendamping Interaktif
                var robotFocused by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (robotFocused) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                        .border(
                            width = if (robotFocused) 2.dp else 1.dp,
                            color = if (robotFocused) Color.White else Color.White.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .onFocusChanged { robotFocused = it.isFocused }
                        .focusable()
                        .combinedClickable(onClick = onRobotClick)
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    InteractiveRobotCanvas(
                        robotType = robotType,
                        accentColor = app?.dominantColor ?: robotType.accent,
                        lookX = lookX,
                        lookY = 0.35f, // Robot menunduk melihat jajaran kartu di bawahnya
                        stepTrigger = stepTrigger,
                        modifier = Modifier.size(98.dp)
                    )
                    Column {
                        Text(
                            text = robotType.title,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Klik ganti robot",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Baris Tab Filter Deck Kartu Remi (♠ ♥ ♦ ♣)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CardDeckFilter.entries.forEach { filter ->
                val selected = filter == activeFilter
                GlassActionPill(
                    icon = when (filter) {
                        CardDeckFilter.ALL -> Icons.Rounded.Style
                        CardDeckFilter.FAVORITES -> Icons.Rounded.Favorite
                        CardDeckFilter.TV_APPS -> Icons.Rounded.Tv
                        CardDeckFilter.SIDELOAD -> Icons.Rounded.Extension
                    },
                    label = "${filter.suit} ${filter.label}",
                    highlight = selected,
                    onClick = { onSelectFilter(filter) }
                )
            }
        }
    }
}

@Composable
fun TopHeaderBar(
    isKidsMode: Boolean,
    currentTheme: AmbientTheme,
    currentRobot: RobotType,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onOpenRobotPicker: () -> Unit,
    onToggleKidsMode: () -> Unit,
    onCycleTheme: () -> Unit
) {
    val context = LocalContext.current
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var networkLabel by remember { mutableStateOf("Offline") }
    var isConnected by remember { mutableStateOf(false) }
    var ramInfo by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFmt = SimpleDateFormat("EEE, d MMM", Locale("id", "ID"))
        while (true) {
            val now = Date()
            currentTime = timeFmt.format(now)
            currentDate = dateFmt.format(now)
            val netStatus = checkNetworkStatus(context)
            networkLabel = netStatus.first
            isConnected = netStatus.second
            ramInfo = getAvailableRamText(context)
            delay(5000L)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "axl",
                    color = Color.White,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Tv",
                    color = currentRobot.accent,
                    fontSize = 23.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            GlassActionPill(
                icon = Icons.Rounded.SmartToy,
                label = currentRobot.title,
                accentColor = currentRobot.accent,
                onClick = onOpenRobotPicker
            )
            GlassActionPill(
                icon = Icons.Rounded.Search,
                label = "Cari",
                onClick = onToggleSearch
            )
            GlassActionPill(
                icon = if (isKidsMode) Icons.Rounded.ChildCare else Icons.Rounded.Person,
                label = if (isKidsMode) "Kids" else "Dewasa",
                highlight = isKidsMode,
                onClick = onToggleKidsMode
            )
            GlassActionPill(
                icon = Icons.Rounded.Palette,
                label = currentTheme.title,
                onClick = onCycleTheme
            )
            GlassActionPill(
                icon = if (isConnected) Icons.Rounded.Wifi else Icons.Rounded.WifiOff,
                label = networkLabel,
                onClick = { context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
            )
            GlassActionPill(
                icon = Icons.Rounded.Settings,
                label = "Setelan",
                onClick = { context.startActivity(Intent(Settings.ACTION_SETTINGS)) }
            )

            if (showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Cari kartu...", color = Color.White.copy(0.5f), fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .width(170.dp)
                        .height(44.dp),
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        focusedContainerColor = Color.White.copy(alpha = 0.1f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.08f)
                    )
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (ramInfo.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = ramInfo,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = currentDate,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GlassActionPill(
    icon: ImageVector,
    label: String,
    highlight: Boolean = false,
    accentColor: Color? = null,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.09f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillScale"
    )

    val bgColor = when {
        isFocused -> Color.White
        accentColor != null -> accentColor.copy(alpha = 0.25f)
        highlight -> Color(0xFFF59E0B).copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.11f)
    }

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = 1.dp,
                color = if (isFocused) Color.White else (accentColor?.copy(alpha = 0.5f) ?: Color.White.copy(alpha = 0.18f)),
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isFocused) Color.Black else (accentColor ?: Color.White),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White.copy(alpha = 0.94f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun InteractiveRobotCanvas(
    robotType: RobotType,
    accentColor: Color,
    lookX: Float,
    lookY: Float,
    stepTrigger: Int,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "robotIdle")
    val idleFloat by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idleFloat"
    )
    val legSwing by infiniteTransition.animateFloat(
        initialValue = -14f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "legSwing"
    )

    var hopState by remember { mutableStateOf(false) }
    LaunchedEffect(stepTrigger) {
        if (stepTrigger > 0) {
            hopState = true
            delay(150)
            hopState = false
        }
    }

    val hopOffset by animateFloatAsState(
        targetValue = if (hopState) -14f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "hopOffset"
    )
    val strideAngle by animateFloatAsState(
        targetValue = if (stepTrigger % 2 == 0) 18f else -18f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "strideAngle"
    )
    val smoothLookX by animateFloatAsState(
        targetValue = lookX,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "lookX"
    )
    val smoothLookY by animateFloatAsState(
        targetValue = lookY,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "lookY"
    )

    val ledColor by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(400),
        label = "ledColor"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f + idleFloat + hopOffset

        drawOval(
            color = Color.Black.copy(alpha = 0.42f),
            topLeft = Offset(cx - w * 0.28f, h - 12f),
            size = Size(w * 0.56f, 9f)
        )

        rotate(degrees = smoothLookX * 9f, pivot = Offset(cx, cy)) {
            when (robotType) {
                RobotType.ASTRO -> drawAstroBot(cx, cy, w, h, smoothLookX, smoothLookY, ledColor, hopState)
                RobotType.SCORPIO -> drawScorpioBot(cx, cy, w, h, smoothLookX, strideAngle, ledColor)
                RobotType.XGODUCK -> drawXgoDuckBot(cx, cy, w, h, smoothLookX, smoothLookY, strideAngle, ledColor)
                RobotType.CRAWLER -> drawCrawlerBot(cx, cy, w, h, smoothLookX, legSwing + strideAngle, ledColor)
                RobotType.TITAN -> drawTitanBot(cx, cy, w, h, smoothLookX, smoothLookY, strideAngle, ledColor)
                RobotType.NEKO -> drawNekoBot(cx, cy, w, h, smoothLookX, smoothLookY, ledColor, hopState)
                RobotType.DRONE -> drawDroneBot(cx, cy, w, h, smoothLookX, smoothLookY, legSwing, ledColor)
            }
        }
    }
}

private fun DrawScope.drawAstroBot(
    cx: Float, cy: Float, w: Float, h: Float,
    lookX: Float, lookY: Float, led: Color, boosting: Boolean
) {
    if (boosting) {
        drawCircle(
            color = led.copy(alpha = 0.75f),
            radius = w * 0.14f,
            center = Offset(cx, cy + h * 0.34f)
        )
    }
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFCBD5E1))),
        topLeft = Offset(cx - w * 0.22f, cy + h * 0.04f),
        size = Size(w * 0.44f, h * 0.28f),
        cornerRadius = CornerRadius(24f, 24f)
    )
    drawRoundRect(
        color = Color(0xFFE2E8F0),
        topLeft = Offset(cx - w * 0.33f, cy + h * 0.06f + lookY * 5f),
        size = Size(w * 0.08f, h * 0.18f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = Color(0xFFE2E8F0),
        topLeft = Offset(cx + w * 0.25f, cy + h * 0.06f - lookY * 5f),
        size = Size(w * 0.08f, h * 0.18f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color.White, Color(0xFFE2E8F0))),
        topLeft = Offset(cx - w * 0.28f, cy - h * 0.30f),
        size = Size(w * 0.56f, h * 0.33f),
        cornerRadius = CornerRadius(32f, 32f)
    )
    drawRoundRect(
        color = Color(0xFF0F172A),
        topLeft = Offset(cx - w * 0.22f, cy - h * 0.24f),
        size = Size(w * 0.44f, h * 0.21f),
        cornerRadius = CornerRadius(20f, 20f)
    )
    val eyeOffsetX = lookX * 9f
    val eyeOffsetY = lookY * 5f
    drawCircle(color = led, radius = 5.5f, center = Offset(cx - 11f + eyeOffsetX, cy - h * 0.13f + eyeOffsetY))
    drawCircle(color = led, radius = 5.5f, center = Offset(cx + 11f + eyeOffsetX, cy - h * 0.13f + eyeOffsetY))
}

private fun DrawScope.drawScorpioBot(
    cx: Float, cy: Float, w: Float, h: Float,
    lookX: Float, stride: Float, led: Color
) {
    val tailPath = Path().apply {
        moveTo(cx, cy + 4f)
        cubicTo(cx - 24f, cy - 38f, cx + 16f + lookX * 12f, cy - 46f, cx + 10f + lookX * 16f, cy - 22f)
    }
    drawPath(tailPath, color = Color(0xFF475569), style = Stroke(width = 8f, cap = StrokeCap.Round))
    drawCircle(color = led, radius = 5f, center = Offset(cx + 10f + lookX * 16f, cy - 21f))

    for (i in -1..1 step 2) {
        rotate(stride * i, pivot = Offset(cx + i * 14f, cy + 8f)) {
            drawLine(
                color = Color(0xFF94A3B8),
                start = Offset(cx + i * 12f, cy + 8f),
                end = Offset(cx + i * 28f, cy + 25f),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
        }
    }
    drawRoundRect(
        color = Color(0xFF1E293B),
        topLeft = Offset(cx - 22f, cy - 6f),
        size = Size(44f, 20f),
        cornerRadius = CornerRadius(10f, 10f)
    )
    drawRoundRect(
        color = led,
        topLeft = Offset(cx - 14f + lookX * 7f, cy),
        size = Size(28f, 7f),
        cornerRadius = CornerRadius(4f, 4f)
    )
}

private fun DrawScope.drawXgoDuckBot(
    cx: Float, cy: Float, w: Float, h: Float,
    lookX: Float, lookY: Float, stride: Float, led: Color
) {
    rotate(stride, pivot = Offset(cx - 8f, cy + 14f)) {
        drawLine(Color(0xFFF59E0B), Offset(cx - 8f, cy + 14f), Offset(cx - 10f, cy + 30f), 5f, StrokeCap.Round)
    }
    rotate(-stride, pivot = Offset(cx + 8f, cy + 14f)) {
        drawLine(Color(0xFFF59E0B), Offset(cx + 8f, cy + 14f), Offset(cx + 10f, cy + 30f), 5f, StrokeCap.Round)
    }
    drawRoundRect(
        color = Color(0xFFFACC15),
        topLeft = Offset(cx - 19f, cy - 4f),
        size = Size(38f, 20f),
        cornerRadius = CornerRadius(12f, 12f)
    )
    drawCircle(color = Color.White, radius = 15f, center = Offset(cx + lookX * 5f, cy - 16f + lookY * 4f))
    drawRoundRect(
        color = Color(0xFFF97316),
        topLeft = Offset(cx - 9f + lookX * 10f, cy - 14f + lookY * 4f),
        size = Size(18f, 7f),
        cornerRadius = CornerRadius(5f, 5f)
    )
    drawCircle(color = led, radius = 3.5f, center = Offset(cx - 5f + lookX * 7f, cy - 19f))
    drawCircle(color = led, radius = 3.5f, center = Offset(cx + 5f + lookX * 7f, cy - 19f))
}

private fun DrawScope.drawCrawlerBot(
    cx: Float, cy: Float, w: Float, h: Float,
    lookX: Float, legPhase: Float, led: Color
) {
    val angles = listOf(-40f, 0f, 40f)
    angles.forEachIndexed { index, baseAngle ->
        val swing = if (index % 2 == 0) legPhase else -legPhase
        rotate(baseAngle + swing, pivot = Offset(cx - 16f, cy + 5f)) {
            drawLine(Color(0xFFA855F7), Offset(cx - 16f, cy + 5f), Offset(cx - 36f, cy + 21f), 4.5f, StrokeCap.Round)
        }
        rotate(-baseAngle - swing, pivot = Offset(cx + 16f, cy + 5f)) {
            drawLine(Color(0xFFA855F7), Offset(cx + 16f, cy + 5f), Offset(cx + 36f, cy + 21f), 4.5f, StrokeCap.Round)
        }
    }
    drawCircle(color = Color(0xFF1E1B4B), radius = 20f, center = Offset(cx, cy))
    drawCircle(color = led, radius = 5f, center = Offset(cx + lookX * 8f, cy - 3f))
    drawCircle(color = led, radius = 3f, center = Offset(cx - 9f + lookX * 6f, cy))
    drawCircle(color = led, radius = 3f, center = Offset(cx + 9f + lookX * 6f, cy))
}

private fun DrawScope.drawTitanBot(
    cx: Float, cy: Float, w: Float, h: Float,
    lookX: Float, lookY: Float, stride: Float, led: Color
) {
    drawLine(Color.White, Offset(cx, cy - 28f), Offset(cx + lookX * 5f, cy - 40f), 3.5f, StrokeCap.Round)
    drawCircle(led, 4f, Offset(cx + lookX * 5f, cy - 41f))
    drawRoundRect(Color(0xFF334155), Offset(cx - 30f, cy - 7f), Size(60f, 26f), CornerRadius(9f, 9f))
    drawCircle(led, 7f, Offset(cx, cy + 6f))
    drawRoundRect(Color(0xFF94A3B8), Offset(cx - 16f + lookX * 4f, cy - 28f), Size(32f, 19f), CornerRadius(5f, 5f))
    drawRect(led, Offset(cx - 10f + lookX * 7f, cy - 21f + lookY * 3f), Size(20f, 5f))
}

private fun DrawScope.drawNekoBot(
    cx: Float, cy: Float, w: Float, h: Float,
    lookX: Float, lookY: Float, led: Color, happy: Boolean
) {
    val leftEar = Path().apply {
        moveTo(cx - 21f, cy - 14f)
        lineTo(cx - 15f, cy - 35f)
        lineTo(cx - 4f, cy - 19f)
        close()
    }
    val rightEar = Path().apply {
        moveTo(cx + 21f, cy - 14f)
        lineTo(cx + 15f, cy - 35f)
        lineTo(cx + 4f, cy - 19f)
        close()
    }
    drawPath(leftEar, color = Color.White)
    drawPath(rightEar, color = Color.White)
    drawRoundRect(Color.White, Offset(cx - 22f, cy - 21f), Size(44f, 33f), CornerRadius(18f, 18f))
    drawRoundRect(Color(0xFF0F172A), Offset(cx - 17f, cy - 15f), Size(34f, 22f), CornerRadius(12f, 12f))
    val ex = lookX * 7f
    val ey = lookY * 4f
    drawCircle(led, if (happy) 5.5f else 4f, Offset(cx - 8f + ex, cy - 5f + ey))
    drawCircle(led, if (happy) 5.5f else 4f, Offset(cx + 8f + ex, cy - 5f + ey))
}

private fun DrawScope.drawDroneBot(
    cx: Float, cy: Float, w: Float, h: Float,
    lookX: Float, lookY: Float, spin: Float, led: Color
) {
    rotate(spin * 2f, pivot = Offset(cx, cy)) {
        drawOval(
            color = led.copy(alpha = 0.7f),
            topLeft = Offset(cx - 34f, cy - 10f),
            size = Size(68f, 20f),
            style = Stroke(width = 3.5f)
        )
    }
    drawCircle(Color(0xFF1E293B), 19f, Offset(cx, cy))
    drawCircle(Color.White, 19f, Offset(cx, cy), style = Stroke(2f))
    drawCircle(led, 8f, Offset(cx + lookX * 7f, cy + lookY * 5f))
    drawCircle(Color.White, 3f, Offset(cx + lookX * 7f - 2f, cy + lookY * 5f - 2f))
}

@Composable
fun RobotPickerDialog(
    selectedRobot: RobotType,
    onSelect: (RobotType) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Color(0xFF111622))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(26.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Pilih Karakter Robot Pendamping",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Robot akan menoleh dan bergerak mengikuti kartu remi yang kamu pilih.",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 13.sp
            )

            val rows = RobotType.entries.chunked(2)
            rows.forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { robot ->
                        Box(modifier = Modifier.weight(1f)) {
                            RobotOptionCard(
                                robot = robot,
                                isSelected = robot == selectedRobot,
                                onClick = { onSelect(robot) }
                            )
                        }
                    }
                    if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RobotOptionCard(
    robot: RobotType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.04f else 1f, label = "optScale")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isFocused -> Color.White
                    isSelected -> robot.accent.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = 0.07f)
                }
            )
            .border(
                width = if (isSelected || isFocused) 2.dp else 1.dp,
                color = if (isFocused) Color.White else if (isSelected) robot.accent else Color.White.copy(alpha = 0.12f),
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .combinedClickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        InteractiveRobotCanvas(
            robotType = robot,
            accentColor = robot.accent,
            lookX = 0.2f,
            lookY = 0f,
            stepTrigger = 1,
            modifier = Modifier.size(54.dp)
        )
        Column {
            Text(
                text = robot.title,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = robot.subtitle,
                color = if (isFocused) Color.Black.copy(0.7f) else Color.White.copy(0.6f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun BadgePill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun AppContextDialog(
    app: TvAppInfo,
    isPinned: Boolean,
    isKidAllowed: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleKidsWhitelist: () -> Unit,
    onAppInfo: () -> Unit,
    onUninstall: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF141925))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Column {
                    Text(app.label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(app.packageName, color = Color.White.copy(0.55f), fontSize = 12.sp, maxLines = 1)
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

            DialogActionButton(
                icon = if (isPinned) Icons.Rounded.PushPin else Icons.Rounded.Favorite,
                text = if (isPinned) "Hapus dari Kartu Favorit (♥)" else "Jadikan Kartu Favorit (♥)",
                onClick = onTogglePin
            )
            DialogActionButton(
                icon = Icons.Rounded.ChildCare,
                text = if (isKidAllowed) "Hapus dari Daftar Kids Mode" else "Izinkan di Kids Mode",
                onClick = onToggleKidsWhitelist
            )
            DialogActionButton(
                icon = Icons.Rounded.Info,
                text = "Info Aplikasi (Paksa Berhenti / Cache)",
                onClick = onAppInfo
            )
            DialogActionButton(
                icon = Icons.Rounded.Delete,
                text = "Uninstall Aplikasi",
                isDestructive = true,
                onClick = onUninstall
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DialogActionButton(
    icon: ImageVector,
    text: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val bgColor = when {
        isFocused && isDestructive -> Color(0xFFEF4444)
        isFocused -> Color.White
        else -> Color.White.copy(alpha = 0.08f)
    }
    val contentColor = when {
        isFocused && isDestructive -> Color.White
        isFocused -> Color.Black
        isDestructive -> Color(0xFFF87171)
        else -> Color.White
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
        Text(text = text, color = contentColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

// --- HELPER FUNCTIONS ---

suspend fun loadInstalledApps(context: Context): List<TvAppInfo> = withContext(Dispatchers.IO) {
    val pm = context.packageManager
    val appMap = LinkedHashMap<String, TvAppInfo>()

    val tvIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    }
    val tvActivities = pm.queryIntentActivities(tvIntent, 0)
    for (resolve in tvActivities) {
        val pkg = resolve.activityInfo.packageName
        if (pkg == context.packageName) continue
        val label = resolve.loadLabel(pm).toString()
        val iconBmp = drawableToBitmap(resolve.loadIcon(pm))
        val bannerDrawable = try {
            pm.getApplicationBanner(pkg) ?: resolve.activityInfo.loadBanner(pm)
        } catch (e: Exception) {
            null
        }
        val dominant = extractDominantColor(iconBmp)
        appMap[pkg] = TvAppInfo(
            label = label,
            packageName = pkg,
            icon = iconBmp.asImageBitmap(),
            banner = bannerDrawable?.let { drawableToBitmap(it, 320, 180).asImageBitmap() },
            dominantColor = dominant,
            isSideloaded = false
        )
    }

    val mobileIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val mobileActivities = pm.queryIntentActivities(mobileIntent, 0)
    for (resolve in mobileActivities) {
        val pkg = resolve.activityInfo.packageName
        if (pkg == context.packageName || appMap.containsKey(pkg)) continue
        val label = resolve.loadLabel(pm).toString()
        val iconBmp = drawableToBitmap(resolve.loadIcon(pm))
        val dominant = extractDominantColor(iconBmp)
        appMap[pkg] = TvAppInfo(
            label = label,
            packageName = pkg,
            icon = iconBmp.asImageBitmap(),
            banner = null,
            dominantColor = dominant,
            isSideloaded = true
        )
    }

    appMap.values.sortedBy { it.label.lowercase(Locale.getDefault()) }
}

fun drawableToBitmap(drawable: Drawable, width: Int = 144, height: Int = 144): Bitmap {
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        return drawable.bitmap
    }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

fun extractDominantColor(bitmap: Bitmap): Color {
    return try {
        val palette = Palette.from(bitmap).generate()
        val colorInt = palette.getVibrantColor(
            palette.getDominantColor(0xFF2563EB.toInt())
        )
        Color(colorInt)
    } catch (e: Exception) {
        Color(0xFF2563EB)
    }
}

fun checkNetworkStatus(context: Context): Pair<String, Boolean> {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return "Offline" to false
    val caps = cm.getNetworkCapabilities(network) ?: return "Offline" to false
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet" to true
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi" to true
        else -> "Online" to true
    }
}

fun getAvailableRamText(context: Context): String {
    return try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024 * 1024)
        "RAM: ${availMb}MB"
    } catch (e: Exception) {
        ""
    }
}

fun launchApp(context: Context, packageName: String) {
    val pm = context.packageManager
    val intent = pm.getLeanbackLaunchIntentForPackage(packageName)
        ?: pm.getLaunchIntentForPackage(packageName)
    intent?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(it)
    }
}

fun openAppDetails(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

fun uninstallApp(context: Context, packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}