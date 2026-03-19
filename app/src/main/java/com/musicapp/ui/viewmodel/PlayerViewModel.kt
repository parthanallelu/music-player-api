package com.musicapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.musicapp.model.Song
import com.musicapp.player.PlayerManager
import com.musicapp.repository.MusicRepository
import kotlinx.coroutines.launch

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    val isPlaying: LiveData<Boolean> = PlayerManager.isPlaying
    val currentSong: LiveData<Song?> = PlayerManager.currentSong
    val currentPosition: LiveData<Long> = PlayerManager.currentPosition
    val duration: LiveData<Long> = PlayerManager.duration

    fun playPause() = PlayerManager.playPause()
    fun next() = PlayerManager.next()
    fun previous() = PlayerManager.previous()
    fun seekTo(position: Long) = PlayerManager.seekTo(position)

    fun playSong(song: Song, songs: List<Song>, index: Int) {
        PlayerManager.playSong(getApplication(), song, songs, index)
        viewModelScope.launch {
            repository.addToRecentlyPlayed(song)
        }
    }
}
