package com.musicapp.model

import androidx.room.*
import com.google.gson.annotations.SerializedName
import com.musicapp.util.Constants

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
    val permaUrl: String? = "",
    val streamUrl: String? = "",
    @SerializedName("play_count")
    val playCount: String? = "0",
    val duration: Long = 0L,
    val localFilePath: String? = null,
    val isDownloaded: Boolean = false,
    val source: String = "jiosaavn",
    val genre: String? = "unknown",
    val year: Int = 0,
    val artistImage: String? = "",
    val localPlayCount: Int = 0
) {
    @get:Ignore
    val absoluteStreamUrl: String
        get() = if (streamUrl?.startsWith("/") == true) {
            val base = Constants.BASE_URL.removeSuffix("v1/")
            base + streamUrl.removePrefix("/")
        } else streamUrl ?: ""

    @get:Ignore
    val absoluteAlbumArtUrl: String
        get() = if (albumArtUrl?.startsWith("/") == true) {
            val base = Constants.BASE_URL.removeSuffix("v1/")
            base + albumArtUrl.removePrefix("/")
        } else albumArtUrl ?: ""
}
