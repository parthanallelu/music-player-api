package com.musicapp.database

import androidx.lifecycle.LiveData
import androidx.room.*
import com.musicapp.model.Song

@Dao
interface SongDao {

    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): LiveData<List<Song>>

    @Query("SELECT * FROM songs ORDER BY title ASC")
    suspend fun getAllSongsList(): List<Song>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getSongById(songId: String): Song?

    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY title ASC")
    fun getDownloadedSongs(): LiveData<List<Song>>

    @Query("SELECT * FROM songs WHERE isDownloaded = 1 ORDER BY title ASC")
    suspend fun getDownloadedSongsList(): List<Song>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%'")
    suspend fun searchSongs(query: String): List<Song>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Update
    suspend fun updateSong(song: Song)

    @Delete
    suspend fun deleteSong(song: Song)

    @Query("UPDATE songs SET isDownloaded = 1, localFilePath = :filePath WHERE id = :songId")
    suspend fun markAsDownloaded(songId: String, filePath: String)

    @Query("UPDATE songs SET isDownloaded = 0, localFilePath = null WHERE id = :songId")
    suspend fun removeDownload(songId: String)

    @Query("SELECT * FROM songs WHERE localPlayCount > 3 ORDER BY localPlayCount DESC LIMIT 20")
    fun getMostPlayedSongs(): LiveData<List<Song>>

    @Query("SELECT genre FROM songs GROUP BY genre ORDER BY SUM(localPlayCount) DESC LIMIT 1")
    suspend fun getFavoriteGenre(): String?

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY localPlayCount DESC LIMIT 20")
    fun getSongsByGenre(genre: String): LiveData<List<Song>>

    @Query("SELECT genre FROM songs GROUP BY genre ORDER BY SUM(localPlayCount) DESC LIMIT :limit")
    suspend fun getTopGenres(limit: Int): List<String>

    @Query("SELECT artist FROM songs GROUP BY artist ORDER BY SUM(localPlayCount) DESC LIMIT :limit")
    suspend fun getTopArtists(limit: Int): List<String>

    @Query("UPDATE songs SET localPlayCount = localPlayCount + 1 WHERE id = :songId")
    suspend fun incrementPlayCount(songId: String)
}
