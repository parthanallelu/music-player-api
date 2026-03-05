package com.musicapp.model

import androidx.room.Entity

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSong(
    val playlistId: Long,
    val songId: String,
    val addedAt: Long = System.currentTimeMillis()
)
