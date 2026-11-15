package com.nijatva.music.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Equalizer presets, five bands: 60 Hz · 230 Hz · 910 Hz · 3.6 kHz · 14 kHz (millibels). */
val equalizerPresets: List<Pair<String, List<Int>>> = listOf(
    "Flat" to listOf(0, 0, 0, 0, 0),
    "Bass" to listOf(900, 600, 100, 0, 0),
    "Vocal" to listOf(-200, 100, 500, 400, 100),
    "Treble" to listOf(0, 0, 200, 600, 900),
    "Warm" to listOf(500, 300, 0, -100, -200),
)

@Composable
fun SettingsScreen(
    mode: ThemeMode,
    onMode: (ThemeMode) -> Unit,
    accent: Accent,
    onAccent: (Accent) -> Unit,
    mergeAlbums: Boolean,
    onMergeAlbums: (Boolean) -> Unit,
    hideShortSongs: Boolean,
    onHideShortSongs: (Boolean) -> Unit,
    trackCount: Int,
    sourceLabel: String,
    scanning: Boolean,
    onRescan: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        SectionLabel("Appearance")
        Text("Theme", color = scheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { entry ->
                Pill(entry.label, mode == entry, onClick = { onMode(entry) })
            }
        }

        Spacer(Modifier.height(16.dp))
        Text("Accent colour", color = scheme.onSurfaceVariant, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Accent.entries.forEach { entry ->
                Pill(entry.label, accent == entry, onClick = { onAccent(entry) })
            }
        }
        Text(
            "Saved in this app only. Nothing about your choice leaves this device.",
            color = scheme.onSurfaceVariant,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 10.dp),
        )

        HorizontalDivider(Modifier.padding(vertical = 18.dp), color = scheme.outline.copy(alpha = 0.5f))

        SectionLabel("Library")
        SettingSwitch(
            title = "Merge albums",
            subtitle = "Group albums that share a name. Turn off if you see duplicates.",
            checked = mergeAlbums,
            onCheckedChange = onMergeAlbums,
        )
        SettingSwitch(
            title = "Filter songs",
            subtitle = "Hide very short audio files such as ringtones and voice notes.",
            checked = hideShortSongs,
            onCheckedChange = onHideShortSongs,
        )

        Spacer(Modifier.height(14.dp))
        Text(
            "$trackCount tracks from $sourceLabel",
            color = scheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(10.dp))
        Button(onClick = onRescan, enabled = !scanning) {
            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(if (scanning) "Scanning…" else "Scan your music library")
        }

        HorizontalDivider(Modifier.padding(vertical = 18.dp), color = scheme.outline.copy(alpha = 0.5f))

        SectionLabel("About")
        Text(
            "Nijatva Music 1.1.2",
            color = scheme.onBackground,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
        )
        Text(
            "Offline player. No accounts, no ads, no analytics, no Google Play Services.",
            color = scheme.onSurfaceVariant,
            fontSize = 12.5.sp,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(20.dp))
        DeveloperCard()
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = scheme.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = scheme.onSurfaceVariant, fontSize = 12.5.sp)
        }
        Spacer(Modifier.size(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun DeveloperCard() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = scheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(36.dp))
                    .background(Brush.linearGradient(listOf(scheme.primary, scheme.primary.copy(alpha = 0.6f)))),
                contentAlignment = Alignment.Center,
            ) {
                Text("VD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
            Spacer(Modifier.height(12.dp))
            Text("Vasudev Dangaya", color = scheme.onSurface, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text("@vasudev-work · India", color = scheme.onSurfaceVariant, fontSize = 12.5.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Android apps in Kotlin and Jetpack Compose, without Google Play Services.",
                color = scheme.onSurfaceVariant,
                fontSize = 12.5.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "github.com/vasudev-work",
                color = scheme.primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
    Spacer(Modifier.height(16.dp))
    Surface(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.horizontalGradient(listOf(Color(0xFFFF9933), Color(0xFF138808)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "MAKE IN INDIA",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
fun EqualizerDialog(
    selectedPreset: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
        title = { Text("Equalizer") },
        text = {
            Column {
                Text(
                    "Presets are applied to the platform audio session where the device supports it.",
                    color = scheme.onSurfaceVariant,
                    fontSize = 12.5.sp,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    equalizerPresets.take(3).forEach { (name, _) ->
                        Pill(name, selectedPreset == name, onClick = { onSelect(name) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    equalizerPresets.drop(3).forEach { (name, _) ->
                        Pill(name, selectedPreset == name, onClick = { onSelect(name) })
                    }
                }
            }
        },
    )
}
