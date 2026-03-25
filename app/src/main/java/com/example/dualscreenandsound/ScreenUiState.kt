package com.example.dualscreenandsound

data class ScreenUiState(
    val name: String,
    val isOnline: Boolean,
    val playState: PlayState,
    val fileName: String?,
    val output: String,
    val audio: String
)
