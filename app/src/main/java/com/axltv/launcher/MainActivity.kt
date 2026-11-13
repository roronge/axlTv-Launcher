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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class TvAppInfo(
    val label: String,
    val packageName: String,
    val icon: ImageBitmap,
    val banner: ImageBitmap?,
    val dominantColor: Color,
    val isSideloaded: Boolean
)

enum class AmbientTheme(val title: String, val primary: Color, val secondary: Color) {
    DYNAMIC("Adaptive Icon", Color(0xFF2563EB), Color(0xFF06080C)),
    MIDNIGHT("Apple Midnight", Color(0xFF1E293B), Color(0xFF020617)),
    AURORA("Nordic Aurora", Color(0xFF0D9488), Color(0xFF042F2E)),
    SUNSET("Cinema Crimson", Color(0xFFE11D48), Color(0xFF190409))
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

    var focusedApp by remember { mutableStateOf<TvAppInfo?>(null) }
    var contextMenuApp by remember { mutableStateOf<TvAppInfo?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val loaded = loadInstalledApps(context)
        allApps = loaded
        if (pinnedPackages.isEmpty() && loaded.isNotEmpty()) {
            val defaults = loaded.take(6).map { it.packageName }
            pinnedPackages = defaults
            prefs.edit().putStringSet("pinned_apps", defaults.toSet()).apply()
        }
        focusedApp = loaded.firstOrNull()
    }

    val visibleApps = remember(allApps, isKidsMode, kidsPackages) {
        if (!isKidsMode) allApps
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
    }

    val pinnedApps = remember(visibleApps, pinnedPackages) {
        pinnedPackages.mapNotNull { pkg -> visibleApps.find { it.packageName == pkg } }
    }

    val filteredApps = remember(visibleApps, searchQuery) {
        if (searchQuery.isBlank()) visibleApps
        else visibleApps.filter { it.label.contains(searchQuery, ignoreCase = true) }
    }

    val targetGlowColor = when {
        isKidsMode -> Color(0xFFF59E0B)
        selectedTheme == AmbientTheme.DYNAMIC -> focusedApp?.dominantColor ?: Color(0xFF1E293B)
        else -> selectedTheme.primary
    }

    val animatedGlowColor by animateColorAsState(
        targetValue = targetGlowColor,
        animationSpec = tween(durationMillis = 650),
        label = "bgGlow"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        animatedGlowColor.copy(alpha = 0.45f),
                        Color(0xFF0B0E17).copy(alpha = 0.90f),
                        Color(0xFF05070B)
                    ),
                    radius = 1450f
                )
            )
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 48.dp)
        ) {
            item {
                TopHeaderBar(
                    isKidsMode = isKidsMode,
                    currentTheme = selectedTheme,
                    showSearch = showSearchBar,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onToggleSearch = {
                        showSearchBar = !showSearchBar
                        if (!showSearchBar) searchQuery = ""
                    },
                    onToggleKidsMode = {
                        isKidsMode = !isKidsMode
                        prefs.edit().putBoolean("is_kids_mode", isKidsMode).apply()
                    },
                    onCycleTheme = {
                        val nextIndex = (selectedTheme.ordinal + 1) % AmbientTheme.entries.size
                        selectedTheme = AmbientTheme.entries[nextIndex]
                        prefs.edit().putString("ambient_theme", selectedTheme.name).apply()
                    }
                )
            }

            item {
                TopShelfHeroCard(
                    app = focusedApp,
                    isKidsMode = isKidsMode
                )
            }

            if (pinnedApps.isNotEmpty() && searchQuery.isBlank()) {
                item {
                    SectionTitle(title = if (isKidsMode) "Favorit Anak" else "Top Shelf • Favorit")
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        items(pinnedApps, key = { "pin_${it.packageName}" }) { app ->
                            AppleTvAppTile(
                                app = app,
                                isLarge = true,
                                onFocus = { focusedApp = app },
                                onClick = { launchApp(context, app.packageName) },
                                onLongClick = { contextMenuApp = app }
                            )
                        }
                    }
                }
            }

            item {
                val title = when {
                    searchQuery.isNotBlank() -> "Hasil Pencarian: \"$searchQuery\" (${filteredApps.size})"
                    isKidsMode -> "Aplikasi Mode Anak (${filteredApps.size})"
                    else -> "Semua Aplikasi (${filteredApps.size})"
                }
                SectionTitle(title = title)
            }

            val rows = filteredApps.chunked(5)
            items(rows) { rowApps ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp)
                ) {
                    rowApps.forEach { app ->
                        Box(modifier = Modifier.weight(1f)) {
                            AppleTvAppTile(
                                app = app,
                                isLarge = false,
                                onFocus = { focusedApp = app },
                                onClick = { launchApp(context, app.packageName) },
                                onLongClick = { contextMenuApp = app }
                            )
                        }
                    }
                    repeat(5 - rowApps.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

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

@Composable
fun TopHeaderBar(
    isKidsMode: Boolean,
    currentTheme: AmbientTheme,
    showSearch: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: () -> Unit,
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
            .padding(horizontal = 48.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "axlTv",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.width(8.dp))

            GlassActionPill(
                icon = Icons.Rounded.Search,
                label = "Cari",
                onClick = onToggleSearch
            )
            GlassActionPill(
                icon = if (isKidsMode) Icons.Rounded.ChildCare else Icons.Rounded.Person,
                label = if (isKidsMode) "Kids Mode" else "Dewasa",
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
                icon = Icons.Rounded.Bluetooth,
                label = "Remote",
                onClick = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) }
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
                    placeholder = { Text("Cari aplikasi...", color = Color.White.copy(0.5f), fontSize = 13.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .width(210.dp)
                        .height(48.dp),
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
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (ramInfo.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = ramInfo,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = currentTime,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = currentDate,
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp
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
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pillScale"
    )

    val bgColor = when {
        isFocused -> Color.White
        highlight -> Color(0xFFF59E0B).copy(alpha = 0.35f)
        else -> Color.White.copy(alpha = 0.12f)
    }

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(CircleShape)
            .background(bgColor)
            .border(
                width = 1.dp,
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.18f),
                shape = CircleShape
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isFocused) Color.Black else Color.White,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White.copy(alpha = 0.92f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun TopShelfHeroCard(
    app: TvAppInfo?,
    isKidsMode: Boolean
) {
    if (app == null) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 6.dp)
            .height(168.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        app.dominantColor.copy(alpha = 0.58f),
                        Color(0xFF141824).copy(alpha = 0.85f),
                        Color(0xFF0E121B).copy(alpha = 0.95f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(24.dp))
            .padding(26.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .shadow(16.dp, RoundedCornerShape(20.dp))
                )
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = app.label,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (app.isSideloaded) {
                            BadgePill(text = "SIDELOAD", color = Color.White.copy(alpha = 0.18f))
                        }
                        if (isKidsMode) {
                            BadgePill(text = "KIDS SAFE", color = Color(0xFFF59E0B).copy(alpha = 0.45f))
                        }
                    }
                    Text(
                        text = app.packageName,
                        color = Color.White.copy(alpha = 0.58f),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Tekan OK untuk membuka • Tekan lama OK untuk Pin / Kids Mode / Info",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp
                    )
                }
            }
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
fun SectionTitle(title: String) {
    Text(
        text = title,
        color = Color.White.copy(alpha = 0.92f),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 48.dp, top = 16.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppleTvAppTile(
    app: TvAppInfo,
    isLarge: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.09f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "tileScale"
    )

    val cardWidth = if (isLarge) 210.dp else 172.dp
    val cardHeight = if (isLarge) 122.dp else 104.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .onFocusChanged { state ->
                isFocused = state.isFocused
                if (state.isFocused) onFocus()
            }
            .focusable()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .shadow(
                    elevation = if (isFocused) 24.dp else 4.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = app.dominantColor,
                    spotColor = Color.White
                )
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            app.dominantColor.copy(alpha = 0.65f),
                            Color(0xFF1A202C)
                        )
                    )
                )
                .border(
                    border = if (isFocused) BorderStroke(3.dp, Color.White)
                    else BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    shape = RoundedCornerShape(18.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (app.banner != null) {
                Image(
                    bitmap = app.banner,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Image(
                    bitmap = app.icon,
                    contentDescription = app.label,
                    modifier = Modifier
                        .size(if (isLarge) 64.dp else 54.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = app.label,
            color = if (isFocused) Color.White else Color.White.copy(alpha = 0.72f),
            fontSize = 13.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(cardWidth)
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
                .background(Color(0xFF161B26))
                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
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
                icon = if (isPinned) Icons.Rounded.PushPin else Icons.Rounded.Star,
                text = if (isPinned) "Hapus dari Top Shelf" else "Sematkan ke Top Shelf",
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
        "RAM Bebas: ${availMb}MB"
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