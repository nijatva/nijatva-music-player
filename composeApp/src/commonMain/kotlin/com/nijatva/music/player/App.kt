package com.nijatva.music.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nijatva.music.player.library.rememberMusicLibrary
import com.nijatva.music.player.model.Track
import com.nijatva.music.player.player.Playback
import com.nijatva.music.player.player.rememberPlayback
import com.nijatva.music.player.ui.Accent
import com.nijatva.music.player.ui.EqualizerDialog
import com.nijatva.music.player.ui.LibraryScreen
import com.nijatva.music.player.ui.MiniPlayer
import com.nijatva.music.player.ui.NijatvaTheme
import com.nijatva.music.player.ui.NowPlayingScreen
import com.nijatva.music.player.ui.SettingsScreen
import com.nijatva.music.player.ui.ThemeMode
import com.nijatva.music.player.ui.equalizerPresets
import kotlinx.coroutines.delay

enum class HomeTab(val label: String, val icon: ImageVector) {
    Music("Music", Icons.Rounded.Home),
    Folders("Folders", Icons.Rounded.Folder),
    Radio("Radio", Icons.Rounded.Radio),
    More("More", Icons.Rounded.Menu),
}

@Composable
fun App() {
    var mode by remember { mutableStateOf(ThemeMode.System) }
    var accent by remember { mutableStateOf(Accent.Saffron) }
    var mergeAlbums by remember { mutableStateOf(true) }
    var hideShortSongs by remember { mutableStateOf(true) }

    val playback = rememberPlayback()
    val library = rememberMusicLibrary()

    var allTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var scanToken by remember { mutableIntStateOf(0) }

    var tab by remember { mutableStateOf(HomeTab.Music) }
    var showPlayer by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var favourites by remember { mutableStateOf<Set<String>>(emptySet()) }
    var preset by remember { mutableStateOf("Flat") }

    LaunchedEffect(library, scanToken) {
        scanning = true
        allTracks = runCatching { library.scan() }.getOrDefault(emptyList())
        scanning = false
    }

    LaunchedEffect(playback) {
        while (true) {
            delay(500)
            playback.tick()
        }
    }

    val tracks = remember(allTracks, hideShortSongs) {
        if (hideShortSongs) allTracks.filter { it.durationMs == 0L || it.durationMs >= 30_000L } else allTracks
    }
    val visible = remember(tracks, query) {
        if (query.isBlank()) tracks
        else tracks.filter {
            it.displayTitle.contains(query, ignoreCase = true) ||
                it.displayArtist.contains(query, ignoreCase = true) ||
                it.displayAlbum.contains(query, ignoreCase = true)
        }
    }

    fun applyPreset(name: String) {
        preset = name
        val gains = equalizerPresets.firstOrNull { it.first == name }?.second
        playback.setEqualizer(gains)
    }

    NijatvaTheme(mode = mode, accent = accent) {
        val scheme = MaterialTheme.colorScheme
        val current = playback.current
        val progress = if (playback.durationMs > 0) playback.positionMs.toFloat() / playback.durationMs else 0f

        if (showPlayer) {
            NowPlayingScreen(
                controller = playback,
                isFavourite = current != null && favourites.contains(current.id),
                onToggleFavourite = {
                    current?.id?.let { id ->
                        favourites = if (favourites.contains(id)) favourites - id else favourites + id
                    }
                },
                onBack = { showPlayer = false },
                onEqualizer = { showEqualizer = true },
            )
        } else {
            Scaffold(
                containerColor = scheme.background,
                bottomBar = {
                    Column {
                        if (current != null) {
                            MiniPlayer(
                                track = current,
                                isPlaying = playback.isPlaying,
                                progress = progress,
                                onOpen = { showPlayer = true },
                                onToggle = { playback.toggle() },
                                onNext = { playback.next() },
                            )
                        }
                        NavigationBar(containerColor = scheme.surface, tonalElevation = 0.dp) {
                            HomeTab.entries.forEach { entry ->
                                NavigationBarItem(
                                    selected = tab == entry,
                                    onClick = {
                                        tab = entry
                                        if (entry != HomeTab.Music) searching = false
                                    },
                                    icon = { Icon(entry.icon, contentDescription = entry.label) },
                                    label = { Text(entry.label, fontSize = 11.sp) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = scheme.onPrimary,
                                        selectedTextColor = scheme.primary,
                                        indicatorColor = scheme.primary,
                                        unselectedIconColor = scheme.onSurfaceVariant,
                                        unselectedTextColor = scheme.onSurfaceVariant,
                                    ),
                                )
                            }
                        }
                    }
                },
                topBar = {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = when (tab) {
                                    HomeTab.Music -> "Music"
                                    HomeTab.Folders -> "Folders"
                                    HomeTab.Radio -> "Radio"
                                    HomeTab.More -> "Settings"
                                },
                                color = scheme.onBackground,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (tab == HomeTab.Music) {
                                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                                    Icon(Icons.Rounded.Search, contentDescription = "Search", tint = scheme.onBackground)
                                }
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = scheme.onBackground)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Shuffle all") },
                                        leadingIcon = { Icon(Icons.Rounded.Shuffle, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            if (tracks.isNotEmpty()) {
                                                playback.shuffle = true
                                                playback.playNow(tracks.shuffled(), 0)
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Scan your music library") },
                                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                        onClick = { menuOpen = false; scanToken += 1 },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sleep timer") },
                                        leadingIcon = { Icon(Icons.Rounded.Timer, contentDescription = null) },
                                        onClick = { menuOpen = false; showSleep = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Equalizer") },
                                        leadingIcon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                                        onClick = { menuOpen = false; showEqualizer = true },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Settings") },
                                        leadingIcon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                                        onClick = { menuOpen = false; tab = HomeTab.More },
                                    )
                                }
                            }
                        }

                        if (searching && tab == HomeTab.Music) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Songs, artists, albums") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (tab) {
                        HomeTab.Music -> LibraryScreen(
                            tracks = visible,
                            scanning = scanning,
                            mergeAlbums = mergeAlbums,
                            controller = playback,
                            favourites = favourites,
                            onPlay = { list, index -> playback.playNow(list, index) },
                            onRescan = { scanToken += 1 },
                        )

                        HomeTab.Folders -> LibraryScreen(
                            tracks = tracks,
                            scanning = scanning,
                            mergeAlbums = mergeAlbums,
                            controller = playback,
                            favourites = favourites,
                            onPlay = { list, index -> playback.playNow(list, index) },
                            onRescan = { scanToken += 1 },
                            foldersOnly = true,
                        )

                        HomeTab.Radio -> RadioScreen(
                            onPlayStream = { url ->
                                playback.playNow(
                                    listOf(
                                        Track(
                                            id = url,
                                            title = "Stream",
                                            artist = url.substringAfterLast('/').ifBlank { "Internet radio" },
                                            album = "",
                                            uri = url,
                                            durationMs = 0L,
                                        ),
                                    ),
                                    0,
                                )
                            },
                        )

                        HomeTab.More -> SettingsScreen(
                            mode = mode,
                            onMode = { mode = it },
                            accent = accent,
                            onAccent = { accent = it },
                            mergeAlbums = mergeAlbums,
                            onMergeAlbums = { mergeAlbums = it },
                            hideShortSongs = hideShortSongs,
                            onHideShortSongs = { hideShortSongs = it },
                            trackCount = tracks.size,
                            sourceLabel = library.sourceLabel,
                            scanning = scanning,
                            onRescan = { scanToken += 1 },
                        )
                    }
                }
            }
        }

        if (showSleep) {
            SleepTimerDialog(
                current = playback.sleepMinutes,
                onPick = { minutes -> playback.startSleepTimer(minutes); showSleep = false },
                onDismiss = { showSleep = false },
            )
        }

        if (showEqualizer) {
            EqualizerDialog(
                selectedPreset = preset,
                onSelect = { applyPreset(it) },
                onDismiss = { showEqualizer = false },
            )
        }
    }
}

@Composable
private fun SleepTimerDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text("Sleep timer") },
        text = {
            Column {
                Text(
                    if (current > 0) "Stops playback in ${current / 60} hr ${current % 60} min." else "Playback stops after the time you pick.",
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
                listOf(15, 30, 45, 60).forEach { minutes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (current == minutes) scheme.primary.copy(alpha = 0.15f) else scheme.background)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { onPick(minutes) }) {
                            Text("$minutes minutes", color = scheme.onSurface)
                        }
                    }
                }
                TextButton(onClick = { onPick(0) }) {
                    Text("Turn off", color = scheme.primary)
                }
            }
        },
    )
}

@Composable
private fun RadioScreen(onPlayStream: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    var url by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "Play an internet radio stream by pasting its direct URL (an http(s) link that serves audio).",
            color = scheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Stream URL") },
            placeholder = { Text("https://…/stream.mp3") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        TextButton(
            onClick = { if (url.isNotBlank()) onPlayStream(url.trim()) },
            enabled = url.isNotBlank(),
        ) {
            Icon(Icons.Rounded.Radio, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Play stream")
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "Radio is a stream player, not a catalogue: Nijatva ships no station list and contacts no directory service.",
            color = scheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}
