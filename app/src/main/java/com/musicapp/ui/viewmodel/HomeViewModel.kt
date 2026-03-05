package com.musicapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.musicapp.model.Song
import com.musicapp.repository.MusicRepository
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    private val _songs = MutableLiveData<List<Song>>()
    val songs: LiveData<List<Song>> get() = _songs

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    init {
        loadSongs()
    }

    fun loadSongs() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = repository.fetchSongs()
            result.onSuccess { songList ->
                _songs.value = songList
            }.onFailure { exception ->
                _error.value = exception.message ?: "Failed to load songs"
            }
            _isLoading.value = false
        }
    }

    fun refresh() = loadSongs()
}
