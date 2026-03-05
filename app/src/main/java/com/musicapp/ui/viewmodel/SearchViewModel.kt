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

    fun search(query: String) {
        _query.value = query
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300) // Debounce
            _isLoading.value = true
            val result = repository.searchSongs(query)
            result.onSuccess { songs ->
                _searchResults.value = songs
            }.onFailure {
                _searchResults.value = emptyList()
            }
            _isLoading.value = false
        }
    }
}
