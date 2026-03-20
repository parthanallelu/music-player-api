package com.musicapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.musicapp.model.Song
import com.musicapp.repository.MusicRepository
import kotlinx.coroutines.async
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
                // Fetch user preferences first (local DB, fast)
                val topGenres = repository.getTopGenres(3)
                val topArtists = repository.getTopArtists(3)

                val genresStr = topGenres.joinToString(",")
                val artistsStr = topArtists.joinToString(",")
                val favGenre = topGenres.firstOrNull() ?: "hindi"
                _becauseYouListenedGenre.value = favGenre

                // Fire all network calls in parallel
                val recommendedDeferred = async { repository.getRecommendations(genresStr, artistsStr) }
                val becauseDeferred = async { repository.fetchSongs(favGenre) }
                val trendingDeferred = async { repository.fetchSongs("top hits") }
                val hindiDeferred = async { repository.fetchSongs("hindi 2024") }
                val englishDeferred = async { repository.fetchSongs("english hits") }
                val technoDeferred = async { repository.fetchSongs("techno mix") }

                // Await results individually — one failure shouldn't kill the others
                var anySuccess = false

                try { recommendedDeferred.await().onSuccess { _recommendedSongs.value = it; anySuccess = true } } catch (_: Exception) {}
                try { becauseDeferred.await().onSuccess { _becauseYouListenedSongs.value = it; anySuccess = true } } catch (_: Exception) {}
                try { trendingDeferred.await().onSuccess { _trendingSongs.value = it; anySuccess = true } } catch (_: Exception) {}
                try { hindiDeferred.await().onSuccess { _hindiSongs.value = it; anySuccess = true } } catch (_: Exception) {}
                try { englishDeferred.await().onSuccess { _englishSongs.value = it; anySuccess = true } } catch (_: Exception) {}
                try { technoDeferred.await().onSuccess { _technoSongs.value = it; anySuccess = true } } catch (_: Exception) {}

                if (!anySuccess) {
                    _error.value = "Could not load songs. Check your internet connection."
                }
            } catch (exception: Exception) {
                _error.value = exception.message ?: "Failed to load songs"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() = loadSongs()
}
