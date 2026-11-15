package com.nijatva.music.player.model

/** One playable audio file, as read from the platform's library. */
data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val uri: String,
    val durationMs: Long,
    val folder: String = "",
    val genre: String? = null,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val artUri: String? = null,
) {
    val displayTitle: String get() = title.ifBlank { uri.substringAfterLast('/').ifBlank { "Untitled" } }
    val displayArtist: String get() = artist.ifBlank { "Unknown artist" }
    val displayAlbum: String get() = album.ifBlank { "Unknown album" }
    val displayGenre: String get() = genre?.takeIf { it.isNotBlank() } ?: "Unknown genre"
    val initials: String get() = displayTitle.trim().take(1).uppercase().ifBlank { "♪" }
}

data class Album(
    val title: String,
    val artist: String,
    val tracks: List<Track>,
) {
    val durationMs: Long get() = tracks.sumOf { it.durationMs }
    val initials: String get() = title.trim().take(1).uppercase().ifBlank { "♪" }
}

data class Artist(
    val name: String,
    val tracks: List<Track>,
)

data class Folder(
    val path: String,
    val name: String,
    val tracks: List<Track>,
)

/** Groups tracks into albums, optionally merging same-named albums (the "Merge Album" setting). */
fun List<Track>.toAlbums(merge: Boolean): List<Album> =
    groupBy { if (merge) it.displayAlbum.lowercase() else "${it.displayAlbum.lowercase()}\u0000${it.displayArtist.lowercase()}" }
        .map { (_, tracks) ->
            Album(
                title = tracks.first().displayAlbum,
                artist = tracks.first().displayArtist,
                tracks = tracks.sortedWith(compareBy({ it.trackNumber }, { it.displayTitle.lowercase() })),
            )
        }
        .sortedBy { it.title.lowercase() }

fun List<Track>.toArtists(): List<Artist> =
    groupBy { it.displayArtist }
        .map { (name, tracks) -> Artist(name, tracks.sortedBy { it.displayTitle.lowercase() }) }
        .sortedBy { it.name.lowercase() }

fun List<Track>.toGenres(): List<Pair<String, List<Track>>> =
    groupBy { it.displayGenre }
        .map { (genre, tracks) -> genre to tracks.sortedBy { it.displayTitle.lowercase() } }
        .sortedBy { it.first.lowercase() }

fun List<Track>.toFolders(): List<Folder> =
    groupBy { it.folder }
        .map { (path, tracks) ->
            Folder(
                path = path,
                name = path.trimEnd('/').substringAfterLast('/').ifBlank { path.ifBlank { "Unknown folder" } },
                tracks = tracks.sortedBy { it.displayTitle.lowercase() },
            )
        }
        .sortedBy { it.name.lowercase() }
