package com.nijatva.music.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nijatva.music.player.player.Playback
import com.nijatva.music.player.player.RepeatMode
import com.nijatva.music.player.util.formatDuration

@Composable
fun NowPlayingScreen(
    controller: Playback,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onBack: () -> Unit,
    onEqualizer: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val track = controller.current
    val position = controller.positionMs
    val duration = controller.durationMs.coerceAtLeast(track?.durationMs ?: 0L)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = scheme.onBackground)
            }
            Text(
                "Now playing",
                color = scheme.onSurfaceVariant,
                fontSize = 12.sp,
                letterSpacing = 1.2.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = onEqualizer) {
                Icon(Icons.Rounded.GraphicEq, contentDescription = "Equalizer", tint = scheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(MaterialTheme.shapes.extraLarge)
                .background(
                    Brush.linearGradient(
                        listOf(scheme.primary.copy(alpha = 0.95f), scheme.primary.copy(alpha = 0.25f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(96.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    track?.displayTitle ?: "Nothing playing",
                    color = scheme.onBackground,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    track?.displayArtist ?: "—",
                    color = scheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onToggleFavourite, enabled = track != null) {
                Icon(
                    if (isFavourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = "Favourite",
                    tint = if (isFavourite) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Slider(
            value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { fraction -> controller.seekTo((fraction * duration).toLong()) },
            enabled = track != null && duration > 0,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(position), color = scheme.onSurfaceVariant, fontSize = 12.sp)
            Text(formatDuration(duration), color = scheme.onSurfaceVariant, fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = { controller.shuffle = !controller.shuffle }) {
                Icon(
                    Icons.Rounded.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (controller.shuffle) scheme.primary else scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { controller.previous() }) {
                Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", tint = scheme.onBackground)
            }
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = { controller.toggle() }, enabled = track != null) {
                    Icon(
                        if (controller.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (controller.isPlaying) "Pause" else "Play",
                        tint = scheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            IconButton(onClick = { controller.next() }) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next", tint = scheme.onBackground)
            }
            IconButton(onClick = {
                controller.repeat = when (controller.repeat) {
                    RepeatMode.Off -> RepeatMode.All
                    RepeatMode.All -> RepeatMode.One
                    RepeatMode.One -> RepeatMode.Off
                }
            }) {
                Icon(
                    if (controller.repeat == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                    contentDescription = "Repeat",
                    tint = if (controller.repeat == RepeatMode.Off) scheme.onSurfaceVariant else scheme.primary,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (controller.sleepMinutes > 0) {
            Text(
                "Sleep timer · stops in ${controller.sleepMinutes / 60}:${(controller.sleepMinutes % 60).toString().padStart(2, '0')}",
                color = scheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }

        Text(
            text = "Queue ${controller.index + 1} of ${controller.queue.size}",
            color = scheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp),
            textAlign = TextAlign.Center,
        )
    }
}
