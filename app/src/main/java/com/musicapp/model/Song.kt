package com.musicapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey
    val id: String,
    val title: String,
    val album: String = "",
    val artist: String = "",
    val albumArtUrl: String = "",
    val perma_url: String = "",
    val streamUrl: String = "",
    val play_count: String = "0",
    val duration: Long = 0L,
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false,
    val source: String = "jiosaavn",
    val genre: String = "unknown",
    val localPlayCount: Int = 0
)
