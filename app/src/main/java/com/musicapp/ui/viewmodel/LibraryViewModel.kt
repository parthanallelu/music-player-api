package com.musicapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.map
import com.musicapp.model.Playlist
import com.musicapp.model.Song
import com.musicapp.repository.MusicRepository
import com.musicapp.util.LocalMusicScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    val mostPlayed: LiveData<List<Song>> = repository.getMostPlayedSongs()
    
    val playlists: LiveData<List<Playlist>> = repository.getAllPlaylists().map { realPlaylists ->
        val smartPlaylists = listOf(
            Playlist(id = -1, name = "Recently Played"),
            Playlist(id = -2, name = "Most Played"),
            Playlist(id = -3, name = "Favorite Genre Mix")
        )
        smartPlaylists + realPlaylists
    }
    
    val downloadedSongs: LiveData<List<Song>> = repository.getDownloadedSongs()
    val recentlyPlayed: LiveData<List<Song>> = repository.getRecentlyPlayedSongs()

    private val _localMusic = MutableLiveData<List<Song>>()
    val localMusic: LiveData<List<Song>> get() = _localMusic

    fun loadLocalMusic() {
        viewModelScope.launch(Dispatchers.IO) {
            val songs = LocalMusicScanner.scanLocalMusic(getApplication())
            _localMusic.postValue(songs)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            repository.createPlaylist(name)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, songId)
        }
    }

    fun getSongsInPlaylist(playlistId: Long): LiveData<List<Song>> {
        return repository.getSongsInPlaylist(playlistId)
    }
}
