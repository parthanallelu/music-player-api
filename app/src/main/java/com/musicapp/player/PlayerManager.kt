package com.musicapp.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.LiveData
import com.musicapp.model.Song

object PlayerManager {

    private var service: MusicPlayerService? = null
    private var bound = false

    val isPlaying: LiveData<Boolean> get() = MusicPlayerService.isPlaying
    val currentSong: LiveData<Song?> get() = MusicPlayerService.currentSong
    val currentPosition: LiveData<Long> get() = MusicPlayerService.currentPosition
    val duration: LiveData<Long> get() = MusicPlayerService.duration
    val playlist: LiveData<List<Song>> get() = MusicPlayerService.playlist
    val currentIndex: LiveData<Int> get() = MusicPlayerService.currentIndex

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val musicBinder = binder as MusicPlayerService.MusicBinder
            service = musicBinder.getService()
            bound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            bound = false
        }
    }

    fun bindService(context: Context) {
        val intent = Intent(context, MusicPlayerService::class.java)
        // Only bind, don't startService here — foreground service must not be started
        // until we actually need to play music (otherwise Android 12+ crashes)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    fun unbindService(context: Context) {
        if (bound) {
            context.unbindService(connection)
            bound = false
        }
    }

    fun playSong(context: Context, song: Song, songs: List<Song> = listOf(song), startIndex: Int = 0) {
        if (song.source == "youtube" && song.perma_url.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(song.perma_url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        }

        if (!bound) {
            bindService(context)
        }
        // Start as foreground service for playback notification
        val intent = Intent(context, MusicPlayerService::class.java)
        androidx.core.content.ContextCompat.startForegroundService(context, intent)

        // Small delay to ensure binding
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            service?.playSong(song, songs, startIndex)
        }, if (bound) 0 else 500)
    }

    fun play() { service?.play() }
    fun pause() { service?.pause() }
    fun playPause() { service?.playPause() }
    fun next() { service?.next() }
    fun previous() { service?.previous() }
    fun seekTo(position: Long) { service?.seekTo(position) }
}
