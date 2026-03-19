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

    val recentlyPlayedSongs: LiveData<List<Song>> = repository.getRecentlyPlayedSongs()

    private val _recommendedSongs = MutableLiveData<List<Song>>()
    val recommendedSongs: LiveData<List<Song>> get() = _recommendedSongs

    private val _becauseYouListenedSongs = MutableLiveData<List<Song>>()
    val becauseYouListenedSongs: LiveData<List<Song>> get() = _becauseYouListenedSongs
    
    private val _becauseYouListenedGenre = MutableLiveData<String>()
    val becauseYouListenedGenre: LiveData<String> get() = _becauseYouListenedGenre

    private val _trendingSongs = MutableLiveData<List<Song>>()
    val trendingSongs: LiveData<List<Song>> get() = _trendingSongs

    private val _hindiSongs = MutableLiveData<List<Song>>()
    val hindiSongs: LiveData<List<Song>> get() = _hindiSongs

    private val _englishSongs = MutableLiveData<List<Song>>()
    val englishSongs: LiveData<List<Song>> get() = _englishSongs

    private val _technoSongs = MutableLiveData<List<Song>>()
    val technoSongs: LiveData<List<Song>> get() = _technoSongs

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

            try {
                // Fetch preferences
                val topGenres = repository.getTopGenres(3)
                val topArtists = repository.getTopArtists(3)
                
                // Fetch AI Recommendations
                val genresStr = topGenres.joinToString(",")
                val artistsStr = topArtists.joinToString(",")
                
                val recommendedRes = repository.getRecommendations(genresStr, artistsStr)
                
                // "Because you listened to..." based on top genre
                val favGenre = topGenres.firstOrNull() ?: "hindi"
                _becauseYouListenedGenre.value = favGenre
                val becauseYouListenedRes = repository.fetchSongs(favGenre)

                // Standard categories
                val trendingRes = repository.fetchSongs("top hits")
                val hindiRes = repository.fetchSongs("hindi 2024")
                val englishRes = repository.fetchSongs("english hits")
                val technoRes = repository.fetchSongs("techno mix")

                recommendedRes.onSuccess { _recommendedSongs.value = it }
                becauseYouListenedRes.onSuccess { _becauseYouListenedSongs.value = it }
                trendingRes.onSuccess { _trendingSongs.value = it }
                hindiRes.onSuccess { _hindiSongs.value = it }
                englishRes.onSuccess { _englishSongs.value = it }
                technoRes.onSuccess { _technoSongs.value = it }

            } catch (exception: Exception) {
                _error.value = exception.message ?: "Failed to load songs"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() = loadSongs()
}
