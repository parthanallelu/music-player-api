package com.musicapp.api

import com.musicapp.model.Song
import com.musicapp.model.SongResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface MusicApiService {

    // Node proxy popular songs endpoint
    @GET("songs")
    suspend fun getSongs(
        @Query("q") query: String? = null,
        @Query("limit") limit: Int = 50
    ): Response<SongResponse>

    // Node proxy search endpoint
    @GET("search")
    suspend fun searchSongs(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): Response<SongResponse>

    // Node proxy single song endpoint
    @GET("songs/{id}")
    suspend fun getSongDetails(
        @Path("id") songId: String
    ): Response<Song>
}
