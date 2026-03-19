package com.musicapp.player

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.musicapp.R
import com.musicapp.model.Song
import com.musicapp.repository.MusicRepository
import com.musicapp.ui.PlayerActivity
import com.musicapp.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MusicPlayerService : LifecycleService() {

    private var exoPlayer: ExoPlayer? = null
    private val binder = MusicBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: MusicRepository

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }

    companion object {
        val isPlaying = MutableLiveData(false)
        val currentSong = MutableLiveData<Song?>(null)
        val currentPosition = MutableLiveData(0L)
        val duration = MutableLiveData(0L)
        val playlist = MutableLiveData<List<Song>>(emptyList())
        val currentIndex = MutableLiveData(0)
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        repository = MusicRepository(applicationContext)
        initializePlayer()
    }

    private fun initializePlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    MusicPlayerService.isPlaying.postValue(playing)
                    if (playing) {
                        updateNotification()
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val index = exoPlayer?.currentMediaItemIndex ?: 0
                    MusicPlayerService.currentIndex.postValue(index)
                    val songs = MusicPlayerService.playlist.value ?: return
                    if (index in songs.indices) {
                        MusicPlayerService.currentSong.postValue(songs[index])
                        updateNotification()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            MusicPlayerService.duration.postValue(exoPlayer?.duration ?: 0L)
                        }
                        Player.STATE_ENDED -> {
                            MusicPlayerService.isPlaying.postValue(false)
                            val current = MusicPlayerService.currentSong.value
                            if (current != null && exoPlayer?.hasNextMediaItem() == false) {
                                serviceScope.launch {
                                    handleAutoRadio(current)
                                }
                            }
                        }
                        else -> {}
                    }
                }
            })
        }

        // Position update using Handler on main thread (ExoPlayer requires main thread access)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val positionUpdater = object : Runnable {
            override fun run() {
                exoPlayer?.let { player ->
                    if (player.isPlaying) {
                        MusicPlayerService.currentPosition.postValue(player.currentPosition)
                    }
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.postDelayed(positionUpdater, 500)
    }

    private suspend fun handleAutoRadio(currentSong: Song) {
        try {
            if (currentSong.genre != "unknown" && currentSong.genre.isNotEmpty()) {
                val genreRes = repository.fetchSongs(currentSong.genre)
                genreRes.onSuccess { songs ->
                    val filtered = songs.filter { it.id != currentSong.id && (it.source == "jiosaavn" || it.source == "mega") }
                    if (filtered.isNotEmpty()) {
                        val nextSong = filtered.random()
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            playSong(nextSong, listOf(nextSong), 0)
                        }
                        return
                    }
                }
            }
            
            if (currentSong.artist != "Unknown Artist" && currentSong.artist.isNotEmpty()) {
                val artistRes = repository.searchSongs(currentSong.artist)
                artistRes.onSuccess { songs ->
                    val filtered = songs.filter { it.id != currentSong.id && (it.source == "jiosaavn" || it.source == "mega") }
                    if (filtered.isNotEmpty()) {
                        val nextSong = filtered.random()
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            playSong(nextSong, listOf(nextSong), 0)
                        }
                        return
                    }
                }
            }

            val trendingRes = repository.fetchSongs("top hits")
            trendingRes.onSuccess { songs ->
                val filtered = songs.filter { it.id != currentSong.id && (it.source == "jiosaavn" || it.source == "mega") }
                if (filtered.isNotEmpty()) {
                    val nextSong = filtered.random()
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        playSong(nextSong, listOf(nextSong), 0)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun playSong(song: Song, songs: List<Song> = listOf(song), startIndex: Int = 0) {
        MusicPlayerService.playlist.postValue(songs)
        MusicPlayerService.currentSong.postValue(song)
        MusicPlayerService.currentIndex.postValue(startIndex)

        exoPlayer?.let { player ->
            player.clearMediaItems()
            songs.forEach { s ->
                val uri = if (!s.localFilePath.isNullOrEmpty()) s.localFilePath else s.streamUrl
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .build()
                    )
                    .build()
                player.addMediaItem(mediaItem)
            }
            player.seekTo(startIndex, 0)
            player.prepare()
            player.play()
        }

        startForeground(Constants.PLAYER_NOTIFICATION_ID, createNotification())
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun playPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) player.pause() else player.play()
        }
    }

    fun next() {
        exoPlayer?.let { player ->
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
            }
        }
    }

    fun previous() {
        exoPlayer?.let { player ->
            if (player.currentPosition > 3000) {
                player.seekTo(0)
            } else if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem()
            }
        }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    private fun createNotification(): Notification {
        val song = MusicPlayerService.currentSong.value
        val isCurrentlyPlaying = exoPlayer?.isPlaying == true

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.PLAYER_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(song?.title ?: "Music Player")
            .setContentText(song?.artist ?: "")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(contentIntent)
            .setOngoing(isCurrentlyPlaying)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .addAction(
                R.drawable.ic_skip_previous,
                "Previous",
                createActionPendingIntent("PREVIOUS")
            )
            .addAction(
                if (isCurrentlyPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isCurrentlyPlaying) "Pause" else "Play",
                createActionPendingIntent("PLAY_PAUSE")
            )
            .addAction(
                R.drawable.ic_skip_next,
                "Next",
                createActionPendingIntent("NEXT")
            )
            .build()
    }

    private fun updateNotification() {
        try {
            val notification = createNotification()
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(Constants.PLAYER_NOTIFICATION_ID, notification)
        } catch (_: Exception) { }
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Must call startForeground immediately when started as foreground service
        // Otherwise Android 12+ will crash with ForegroundServiceStartNotAllowed
        startForeground(Constants.PLAYER_NOTIFICATION_ID, createNotification())

        when (intent?.action) {
            "PLAY_PAUSE" -> playPause()
            "NEXT" -> next()
            "PREVIOUS" -> previous()
            "STOP" -> {
                exoPlayer?.stop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
