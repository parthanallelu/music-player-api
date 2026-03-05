package com.musicapp.model

data class SongResponse(
    val results: List<Song> = emptyList(),
    val total: Int = 0
)
