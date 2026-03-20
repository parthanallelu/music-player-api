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
import androidx.media3.session.MediaSession
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.request.ImageRequest
import android.net.Uri
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
    private var isManualSongChange = false
    private var mediaSession: MediaSession? = null
    private var currentBitmap: Bitmap? = null
    private lateinit var imageLoader: ImageLoader

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
        imageLoader = ImageLoader(this)
        initializePlayer()
    }

    private fun initializePlayer() {
        val httpDataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("MusicPlayer/1.0 (Android)")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(15000)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)
        
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            
        exoPlayer = player
        mediaSession = MediaSession.Builder(this, player).build()

        player.apply {
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    _isPlaying.postValue(playing)
                    if (playing) updateNotification()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val index = exoPlayer?.currentMediaItemIndex ?: 0
                    _currentIndex.value = index
                    val songs = _playlist.value ?: return
                    if (index in songs.indices) {
                        val song = songs[index]
                        _currentSong.value = song
                        android.util.Log.d("PLAYER_DEBUG", "Transitioned to: ${song.title} (${song.source}) - URL: ${song.absoluteStreamUrl}")
                        updateNotification()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    android.util.Log.d("PLAYER_DEBUG", "Playback state changed: $playbackState")
                    when (playbackState) {
                        Player.STATE_READY -> {
                            _duration.postValue(exoPlayer?.duration ?: 0L)
                            isManualSongChange = false
                        }
                        Player.STATE_BUFFERING -> {
                            isManualSongChange = false
                        }
                        Player.STATE_ENDED -> {
                            _isPlaying.postValue(false)
                            val current = _currentSong.value
                            if (current != null && exoPlayer?.hasNextMediaItem() == false && !isManualSongChange) {
                                serviceScope.launch { handleAutoRadio(current) }
                            }
                        }
                        else -> {}
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    val currentTrack = _currentSong.value
                    android.util.Log.e("PLAYER_ERROR", "ExoPlayer Error for: ${currentTrack?.title} [Source: ${currentTrack?.source}]", error)
                    android.util.Log.e("PLAYER_ERROR", "Error URL: ${currentTrack?.absoluteStreamUrl}")
                    android.util.Log.e("PLAYER_ERROR", "Error Code: ${error.errorCode} (${error.errorCodeName})")
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
                handler.post { playSong(next, listOf(next), 0, isManual = false) }
                return true
            }
        }
        return false
    }

    // ─── Playback Controls ───────────────────────────────

    fun playSong(song: Song, songs: List<Song> = listOf(song), startIndex: Int = 0, isManual: Boolean = true) {
        isManualSongChange = isManual
        _playlist.value = songs
        _currentSong.value = song
        _currentIndex.value = startIndex

        exoPlayer?.let { player ->
            player.clearMediaItems()
            songs.forEach { s ->
                val uri = if (!s.localFilePath.isNullOrEmpty()) s.localFilePath else s.absoluteStreamUrl
                android.util.Log.d("PLAYER_DEBUG", "Adding to playlist: ${s.title} [Source: ${s.source}] URI: $uri")
                
                val mediaItem = MediaItem.Builder()
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(s.title)
                            .setArtist(s.artist)
                            .setAlbumTitle(s.album)
                            .setArtworkUri(Uri.parse(s.absoluteAlbumArtUrl))
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
            Intent(this, PlayerActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, Constants.PLAYER_NOTIFICATION_CHANNEL_ID)
            .setContentTitle(song?.title ?: "Music Player")
            .setContentText(song?.artist ?: "")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(contentIntent)
            .setOngoing(isCurrentlyPlaying)
            .setLargeIcon(currentBitmap)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession?.sessionCompatToken)
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
        val song = _currentSong.value
        if (song != null && !song.albumArtUrl.isNullOrEmpty()) {
            serviceScope.launch {
                val request = ImageRequest.Builder(this@MusicPlayerService)
                    .data(song.absoluteAlbumArtUrl)
                    .build()
                val result = imageLoader.execute(request)
                val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != currentBitmap) {
                    currentBitmap = bitmap
                    handler.post { performNotificationUpdate() }
                } else {
                    handler.post { performNotificationUpdate() }
                }
            }
        } else {
            currentBitmap = null
            performNotificationUpdate()
        }
    }

    private fun performNotificationUpdate() {
        try {
            val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            manager.notify(Constants.PLAYER_NOTIFICATION_ID, createNotification())
        } catch (e: Exception) {
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
        handler.removeCallbacks(positionUpdater)
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }
}
