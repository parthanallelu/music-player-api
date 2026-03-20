package com.musicapp.player

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
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

    // Position updater — stored for cleanup
    private val handler = Handler(Looper.getMainLooper())
    private val positionUpdater = object : Runnable {
        override fun run() {
            exoPlayer?.let { player ->
                if (player.isPlaying) {
                    _currentPosition.postValue(player.currentPosition)
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }

    companion object {
        // Encapsulated state — only LiveData is publicly exposed
        private val _isPlaying = MutableLiveData(false)
        val isPlaying: LiveData<Boolean> get() = _isPlaying

        private val _currentSong = MutableLiveData<Song?>(null)
        val currentSong: LiveData<Song?> get() = _currentSong

        private val _currentPosition = MutableLiveData(0L)
        val currentPosition: LiveData<Long> get() = _currentPosition

        private val _duration = MutableLiveData(0L)
        val duration: LiveData<Long> get() = _duration

        private val _playlist = MutableLiveData<List<Song>>(emptyList())
        val playlist: LiveData<List<Song>> get() = _playlist

        private val _currentIndex = MutableLiveData(0)
        val currentIndex: LiveData<Int> get() = _currentIndex
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
                    _isPlaying.postValue(playing)
                    if (playing) updateNotification()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val index = exoPlayer?.currentMediaItemIndex ?: 0
                    _currentIndex.postValue(index)
                    val songs = _playlist.value ?: return
                    if (index in songs.indices) {
                        _currentSong.postValue(songs[index])
                        updateNotification()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            _duration.postValue(exoPlayer?.duration ?: 0L)
                        }
                        Player.STATE_ENDED -> {
                            _isPlaying.postValue(false)
                            val current = _currentSong.value
                            if (current != null && exoPlayer?.hasNextMediaItem() == false) {
                                serviceScope.launch { handleAutoRadio(current) }
                            }
                        }
                        else -> {}
                    }
                }
            })
        }

        handler.postDelayed(positionUpdater, 500)
    }

    // ─── Auto-Radio ──────────────────────────────────────

    private suspend fun handleAutoRadio(currentSong: Song) {
        try {
            // Try by genre → artist → trending (in priority order)
            if (!currentSong.genre.isNullOrEmpty() && currentSong.genre != "unknown") {
                if (tryAutoRadioFrom(currentSong.genre!!, currentSong.id)) return
            }
            if (!currentSong.artist.isNullOrEmpty() && currentSong.artist != "Unknown Artist") {
                if (tryAutoRadioFrom(currentSong.artist!!, currentSong.id)) return
            }
            tryAutoRadioFrom("top hits", currentSong.id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /** Fetch songs for a query and auto-play a random match. Returns true if successful. */
    private suspend fun tryAutoRadioFrom(query: String, excludeId: String): Boolean {
        val result = repository.fetchSongs(query)
        result.onSuccess { songs ->
            val candidates = songs.filter { it.id != excludeId && it.source in listOf("jiosaavn", "mega") }
            if (candidates.isNotEmpty()) {
                val next = candidates.random()
                handler.post { playSong(next, listOf(next), 0) }
                return true
            }
        }
        return false
    }

    // ─── Playback Controls ───────────────────────────────

    fun playSong(song: Song, songs: List<Song> = listOf(song), startIndex: Int = 0) {
        _playlist.postValue(songs)
        _currentSong.postValue(song)
        _currentIndex.postValue(startIndex)

        exoPlayer?.let { player ->
            player.clearMediaItems()
            songs.forEach { s ->
                val uri = if (!s.localFilePath.isNullOrEmpty()) s.localFilePath else s.absoluteStreamUrl
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

    fun play() { exoPlayer?.play() }
    fun pause() { exoPlayer?.pause() }

    fun playPause() {
        exoPlayer?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() {
        exoPlayer?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun previous() {
        exoPlayer?.let { player ->
            if (player.currentPosition > 3000) player.seekTo(0)
            else if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
        }
    }

    fun seekTo(position: Long) { exoPlayer?.seekTo(position) }
    fun getPlayer(): ExoPlayer? = exoPlayer

    // ─── Notifications ───────────────────────────────────

    private fun createNotification(): Notification {
        val song = _currentSong.value
        val isCurrentlyPlaying = exoPlayer?.isPlaying == true

        val contentIntent = PendingIntent.getActivity(
            this, 0,
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
            .addAction(R.drawable.ic_skip_previous, "Previous", createActionPendingIntent("PREVIOUS"))
            .addAction(
                if (isCurrentlyPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                if (isCurrentlyPlaying) "Pause" else "Play",
                createActionPendingIntent("PLAY_PAUSE")
            )
            .addAction(R.drawable.ic_skip_next, "Next", createActionPendingIntent("NEXT"))
            .build()
    }

    private fun updateNotification() {
        try {
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(Constants.PLAYER_NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
            // Notification update is non-critical; log and continue
            android.util.Log.w("MusicPlayerService", "Notification update failed", e)
        }
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, MusicPlayerService::class.java).apply { this.action = action }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // ─── Lifecycle ───────────────────────────────────────

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
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
        handler.removeCallbacks(positionUpdater) // Fix handler leak
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
