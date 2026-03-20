package com.musicapp.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.SerializedName

@Entity(
    tableName = "songs",
    indices = [
        Index("genre"),
        Index("artist"),
        Index("isDownloaded"),
        Index("source")
    ]
)
data class Song(
    @PrimaryKey
    val id: String,
    val title: String,
    val album: String = "",
    val artist: String = "",
    val albumArtUrl: String = "",
    @SerializedName("perma_url")
    val permaUrl: String = "",
    val streamUrl: String = "",
    @SerializedName("play_count")
    val playCount: String = "0",
    val duration: Long = 0L,
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false,
    val source: String = "jiosaavn",
    val genre: String = "unknown",
    val year: Int = 0,
    val artistImage: String = "",
    val localPlayCount: Int = 0
)
