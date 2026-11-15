package com.nijatva.music.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nijatva.music.player.model.Track
import com.nijatva.music.player.model.toAlbums
import com.nijatva.music.player.model.toArtists
import com.nijatva.music.player.model.toFolders
import com.nijatva.music.player.model.toGenres
import com.nijatva.music.player.player.PlaybackController
import com.nijatva.music.player.util.formatSummary

enum class LibraryTab(val label: String) {
    Artists("Artists"),
    Albums("Albums"),
    Songs("Songs"),
    Playlists("Playlists"),
    Genres("Genres"),
}

private data class Drilldown(val title: String, val subtitle: String, val tracks: List<Track>)

@Composable
fun LibraryScreen(
    tracks: List<Track>,
    scanning: Boolean,
    mergeAlbums: Boolean,
    controller: PlaybackController,
    favourites: Set<String>,
    onPlay: (List<Track>, Int) -> Unit,
    onRescan: () -> Unit,
    foldersOnly: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    var libTab by remember { mutableStateOf(LibraryTab.Songs) }
    var drill by remember { mutableStateOf<Drilldown?>(null) }
    val currentId = controller.current?.id

    val open = drill
    if (open != null) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { drill = null }) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = scheme.onBackground)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        open.title,
                        color = scheme.onBackground,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(open.subtitle, color = scheme.onSurfaceVariant, fontSize = 12.sp)
                }
                TextButton(onClick = { onPlay(open.tracks, 0) }) {
                    Text("Play all", color = scheme.primary, fontSize = 13.sp)
                }
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(open.tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        isCurrent = track.id == currentId,
                        onClick = {
                            val index = open.tracks.indexOfFirst { it.id == track.id }
                            onPlay(open.tracks, if (index >= 0) index else 0)
                        },
                    )
                }
            }
        }
        return
    }

    if (tracks.isEmpty()) {
        EmptyLibrary(scanning = scanning, onRescan = onRescan)
        return
    }

    Column(Modifier.fillMaxSize()) {
        if (!foldersOnly) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LibraryTab.entries.forEach { entry ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { libTab = entry },
                    ) {
                        Text(
                            entry.label,
                            color = if (libTab == entry) scheme.onBackground else scheme.onSurfaceVariant,
                            fontSize = 14.sp,
                            fontWeight = if (libTab == entry) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            Modifier
                                .height(3.dp)
                                .width(if (libTab == entry) 28.dp else 0.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(scheme.primary),
                        )
                    }
                }
            }
        }

        when {
            foldersOnly -> FoldersView(tracks, currentId, onPlay) { title, subtitle, list -> drill = Drilldown(title, subtitle, list) }

            libTab == LibraryTab.Songs -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(tracks, key = { it.id }) { track ->
                    val index = tracks.indexOfFirst { it.id == track.id }
                    TrackRow(
                        track = track,
                        isCurrent = track.id == currentId,
                        onClick = { onPlay(tracks, if (index >= 0) index else 0) },
                    )
                }
            }

            libTab == LibraryTab.Albums -> {
                val albums = remember(tracks, mergeAlbums) { tracks.toAlbums(mergeAlbums) }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(albums, key = { it.title + it.artist }) { album ->
                        Column(
                            Modifier.clickable {
                                drill = Drilldown(
                                    album.title,
                                    formatSummary(album.tracks.size, album.durationMs),
                                    album.tracks,
                                )
                            },
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(
                                                scheme.primary.copy(alpha = 0.9f),
                                                scheme.primary.copy(alpha = 0.3f),
                                            ),
                                        ),
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    album.initials,
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontSize = 40.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Text(
                                album.title,
                                color = scheme.onBackground,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            Text(
                                album.artist,
                                color = scheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            libTab == LibraryTab.Artists -> {
                val artists = remember(tracks) { tracks.toArtists() }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(artists, key = { it.name }) { artist ->
                        CollectionRow(
                            title = artist.name,
                            subtitle = formatSummary(artist.tracks.size, artist.tracks.sumOf { it.durationMs }),
                            initials = artist.name.take(1),
                            round = true,
                            onClick = {
                                drill = Drilldown(
                                    artist.name,
                                    formatSummary(artist.tracks.size, artist.tracks.sumOf { it.durationMs }),
                                    artist.tracks,
                                )
                            },
                        )
                    }
                }
            }

            libTab == LibraryTab.Genres -> {
                val genres = remember(tracks) { tracks.toGenres() }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(genres, key = { it.first }) { (genre, list) ->
                        CollectionRow(
                            title = genre,
                            subtitle = formatSummary(list.size, list.sumOf { it.durationMs }),
                            initials = genre.take(1),
                            round = false,
                            onClick = {
                                drill = Drilldown(genre, formatSummary(list.size, list.sumOf { it.durationMs }), list)
                            },
                        )
                    }
                }
            }

            else -> {
                val favouriteTracks = remember(tracks, favourites) { tracks.filter { favourites.contains(it.id) } }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        CollectionRow(
                            title = "Favourites",
                            subtitle = formatSummary(favouriteTracks.size, favouriteTracks.sumOf { it.durationMs }),
                            initials = "F",
                            round = false,
                            onClick = {
                                if (favouriteTracks.isNotEmpty()) {
                                    drill = Drilldown("Favourites", formatSummary(favouriteTracks.size, 0), favouriteTracks)
                                }
                            },
                        )
                    }
                    item {
                        val queue = controller.queue
                        CollectionRow(
                            title = "Queue",
                            subtitle = formatSummary(queue.size, queue.sumOf { it.durationMs }),
                            initials = "Q",
                            round = false,
                            onClick = {
                                if (queue.isNotEmpty()) drill = Drilldown("Queue", "Now playing", queue)
                            },
                        )
                    }
                    item {
                        Text(
                            "Favourites live in this session only — Nijatva Music keeps no database on disk.",
                            color = scheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FoldersView(
    tracks: List<Track>,
    currentId: String?,
    onPlay: (List<Track>, Int) -> Unit,
    onDrill: (String, String, List<Track>) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val folders = remember(tracks) { tracks.toFolders() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        items(folders, key = { it.path }) { folder ->
            CollectionRow(
                title = folder.name,
                subtitle = formatSummary(folder.tracks.size, folder.tracks.sumOf { it.durationMs }),
                initials = "D",
                round = false,
                onClick = {
                    onDrill(folder.name, formatSummary(folder.tracks.size, folder.tracks.sumOf { it.durationMs }), folder.tracks)
                },
            )
        }
    }
}

@Composable
private fun EmptyLibrary(scanning: Boolean, onRescan: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.Folder,
            contentDescription = null,
            tint = scheme.onSurfaceVariant,
            modifier = Modifier.height(56.dp).width(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (scanning) "Reading your library…" else "No music found",
            color = scheme.onBackground,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "Nijatva Music plays audio files already on this device. Add some, then scan again.",
            color = scheme.onSurfaceVariant,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(18.dp))
        TextButton(onClick = onRescan, enabled = !scanning) {
            Text(if (scanning) "Scanning…" else "Scan again", color = scheme.primary)
        }
    }
}
