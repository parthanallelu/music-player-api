package com.musicapp.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.musicapp.api.RetrofitClient
import com.musicapp.database.AppDatabase
import com.musicapp.model.*
import com.musicapp.util.NetworkUtils
import android.util.Log

class MusicRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val songDao = db.songDao()
    private val playlistDao = db.playlistDao()
    private val recentlyPlayedDao = db.recentlyPlayedDao()
    private val apiService = RetrofitClient.apiService
    private val appContext = context.applicationContext

    // ─── Songs ───────────────────────────────────────────────

    suspend fun fetchSongs(genre: String = "Hindi"): Result<List<Song>> {
        return try {
            if (NetworkUtils.isNetworkAvailable(appContext)) {
                val response = apiService.getSongs(query = genre)
                if (response.isSuccessful) {
                    val songs = response.body()?.songs ?: emptyList()
                    Log.d("SONG_DEBUG", "fetchSongs ($genre): ${songs.size} songs received. MEGA songs: ${songs.count { it.source == "mega" }}")
                    songDao.insertSongs(songs)
                    Result.success(songs)
                } else {
                    Result.success(songDao.getAllSongsList())
                }
            } else {
                Result.success(songDao.getAllSongsList())
            }
        } catch (e: Exception) {
            try {
                Result.success(songDao.getAllSongsList())
            } catch (dbError: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun searchSongs(query: String, page: Int = 1): Result<List<Song>> {
        return try {
            if (NetworkUtils.isNetworkAvailable(appContext)) {
                val response = apiService.searchSongs(query = query, page = page)
                if (response.isSuccessful) {
                    val songs = response.body()?.songs ?: emptyList()
                    Log.d("SONG_DEBUG", "searchSongs ($query): ${songs.size} songs received. MEGA songs: ${songs.count { it.source == "mega" }}")
                    songDao.insertSongs(songs)
                    Result.success(songs)
                } else {
                    Result.success(songDao.searchSongs(query))
                }
            } else {
                Result.success(songDao.searchSongs(query))
            }
        } catch (e: Exception) {
            try {
                Result.success(songDao.searchSongs(query))
            } catch (dbError: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun getSongById(songId: String): Song? {
        return try {
            if (NetworkUtils.isNetworkAvailable(appContext)) {
                val response = apiService.getSongDetails(songId = songId)
                if (response.isSuccessful) {
                    val song = response.body()
                    song?.let { songDao.insertSong(it) }
                    song
                } else {
                    songDao.getSongById(songId)
                }
            } else {
                songDao.getSongById(songId)
            }
        } catch (e: Exception) {
            songDao.getSongById(songId)
        }
    }

    suspend fun insertSong(song: Song) {
        songDao.insertSong(song)
    }

    // ─── Downloads ───────────────────────────────────────────

    fun getDownloadedSongs(): LiveData<List<Song>> = songDao.getDownloadedSongs()

    suspend fun getDownloadedSongsList(): List<Song> = songDao.getDownloadedSongsList()

    suspend fun markAsDownloaded(songId: String, filePath: String) {
        songDao.markAsDownloaded(songId, filePath)
    }

    suspend fun removeDownload(songId: String) {
        songDao.removeDownload(songId)
    }

    // ─── Playlists ───────────────────────────────────────────

    fun getAllPlaylists(): LiveData<List<Playlist>> = playlistDao.getAllPlaylists()

    suspend fun createPlaylist(name: String): Long {
        return playlistDao.insertPlaylist(Playlist(name = name))
    }

    suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: String) {
        playlistDao.addSongToPlaylist(PlaylistSong(playlistId = playlistId, songId = songId))
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    fun getSongsInPlaylist(playlistId: Long): LiveData<List<Song>> {
        return playlistDao.getSongsInPlaylist(playlistId)
    }

    // ─── Recently Played ─────────────────────────────────────

    fun getRecentlyPlayedSongs(): LiveData<List<Song>> {
        return recentlyPlayedDao.getRecentlyPlayedSongs()
    }

    suspend fun addToRecentlyPlayed(song: Song) {
        // Ensure the song is in the local database
        songDao.insertSongs(listOf(song))

        // Add to recently played
        recentlyPlayedDao.insertRecentlyPlayed(
            RecentlyPlayed(songId = song.id, playedAt = System.currentTimeMillis())
        )

        // Increment play count
        try {
            songDao.incrementPlayCount(song.id)
        } catch (e: Exception) {
            // Ignore if song doesn't exist locally
        }
    }

    suspend fun getTopGenres(limit: Int = 3): List<String> = songDao.getTopGenres(limit)
    
    suspend fun getTopArtists(limit: Int = 3): List<String> = songDao.getTopArtists(limit)

    suspend fun getRecommendations(genres: String, artists: String): Result<List<Song>> {
        return try {
            if (NetworkUtils.isNetworkAvailable(appContext)) {
                val response = apiService.getRecommendations(genres = genres, artists = artists)
                if (response.isSuccessful) {
                    val songs = response.body()?.songs ?: emptyList()
                    songDao.insertSongs(songs)
                    Result.success(songs)
                } else {
                    Result.failure(Exception("Failed to fetch recommendations"))
                }
            } else {
                Result.failure(Exception("No network available"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getMostPlayedSongs(): LiveData<List<Song>> = songDao.getMostPlayedSongs()
}
