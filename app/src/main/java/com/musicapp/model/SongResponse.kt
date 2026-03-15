package com.musicapp.model

data class SongResponse(
    val songs: List<Song> = emptyList(),
    val total: Int = 0
)
