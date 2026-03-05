package com.musicapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey
    val id: String,
    val title: String,
    val album: String = "",
    val primary_artists: String = "",
    val image: String = "",
    val perma_url: String = "",
    val media_preview_url: String = "",
    val play_count: String = "0",
    val duration: Long = 0L,
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false
)
