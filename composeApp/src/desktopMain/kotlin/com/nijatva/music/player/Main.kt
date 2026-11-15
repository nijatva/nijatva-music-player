package com.nijatva.music.player

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Music",
        state = rememberWindowState(width = 420.dp, height = 880.dp),
    ) {
        App()
    }
}
