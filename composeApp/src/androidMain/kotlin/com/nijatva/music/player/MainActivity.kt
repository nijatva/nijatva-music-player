package com.nijatva.music.player

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.nijatva.music.player.model.Track
import com.nijatva.music.player.player.ExternalOpen

/** The audio permission the running Android version actually uses. */
fun audioPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

fun hasAudioPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

class MainActivity : ComponentActivity() {

    private val requestAudio = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!hasAudioPermission(this)) {
            requestAudio.launch(audioPermission())
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleIntent(intent)
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    /** "Open with Music" arrives as ACTION_VIEW with an audio URI. */
    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        ExternalOpen.request = trackFromUri(this, uri)
    }
}

/** Best-effort track for a URI handed over by another app: the file name is all we get. */
fun trackFromUri(context: Context, uri: Uri): Track {
    var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Audio"
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (column >= 0) cursor.getString(column)?.let { displayName = it }
                }
            }
    }
    val title = displayName.substringBeforeLast('.', displayName)
    return Track(
        id = uri.toString(),
        title = title,
        artist = "",
        album = "",
        uri = uri.toString(),
        durationMs = 0L,
    )
}
