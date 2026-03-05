package com.musicapp.api

import com.musicapp.model.Song
import com.musicapp.model.SongResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MusicApiService {

    // JioSaavn search endpoint
    @GET("api.php?__call=search.getResults")
    suspend fun searchSongs(
        @Query("query") query: String,
        @Query("type") type: String = "song",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("_format") format: String = "json",
        @Query("_marker") marker: String = "0"
    ): Response<SongResponse>

    // JioSaavn song details endpoint
    @GET("api.php?__call=song.getDetails")
    suspend fun getSongDetails(
        @Query("pids") songIds: String,
        @Query("_format") format: String = "json",
        @Query("_marker") marker: String = "0"
    ): Response<Map<String, Song>>
}
