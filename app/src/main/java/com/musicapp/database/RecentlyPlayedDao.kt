package com.musicapp.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.musicapp.model.RecentlyPlayed
import com.musicapp.model.Song

@Dao
interface RecentlyPlayedDao {

    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN recently_played rp ON s.id = rp.songId 
        ORDER BY rp.playedAt DESC 
        LIMIT 20
    """)
    fun getRecentlyPlayedSongs(): LiveData<List<Song>>

    @Query("""
        SELECT s.* FROM songs s 
        INNER JOIN recently_played rp ON s.id = rp.songId 
        ORDER BY rp.playedAt DESC 
        LIMIT 20
    """)
    suspend fun getRecentlyPlayedSongsList(): List<Song>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentlyPlayed(recentlyPlayed: RecentlyPlayed)

    @Query("DELETE FROM recently_played")
    suspend fun clearAll()
}
