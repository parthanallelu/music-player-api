package com.musicapp.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recently_played")
data class RecentlyPlayed(
    @PrimaryKey
    val songId: String,
    val playedAt: Long = System.currentTimeMillis()
)
