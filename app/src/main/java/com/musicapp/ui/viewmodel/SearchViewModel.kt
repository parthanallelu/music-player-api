package com.musicapp.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.musicapp.model.Song
import com.musicapp.repository.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    private val _searchResults = MutableLiveData<List<Song>>()
    val searchResults: LiveData<List<Song>> get() = _searchResults

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> get() = _isLoading

    private val _query = MutableLiveData("")
    val query: LiveData<String> get() = _query

    private var searchJob: Job? = null
    private var currentPage = 1
    var isLastPage = false
        private set

    fun search(query: String) {
        val trimmedQuery = query.trim()
        if (_query.value == trimmedQuery) return
        
        _query.value = trimmedQuery
        searchJob?.cancel()
        
        if (trimmedQuery.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        
        searchJob = viewModelScope.launch {
            delay(150) // Reduced debounce for better real-time feel
            _isLoading.value = true
            currentPage = 1
            isLastPage = false
            
            val result = repository.searchSongs(trimmedQuery, currentPage)
            result.onSuccess { songs ->
                _searchResults.value = songs
                if (songs.size < 20) isLastPage = true
            }.onFailure {
                _searchResults.value = emptyList()
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (_isLoading.value == true || isLastPage) return
        val currentQuery = _query.value ?: return
        if (currentQuery.isBlank()) return

        currentPage++
        searchJob = viewModelScope.launch {
            _isLoading.value = true
            val result = repository.searchSongs(currentQuery, currentPage)
            result.onSuccess { newSongs ->
                val currentList = _searchResults.value ?: emptyList()
                _searchResults.value = currentList + newSongs
                if (newSongs.size < 20) isLastPage = true
            }
            _isLoading.value = false
        }
    }
}
