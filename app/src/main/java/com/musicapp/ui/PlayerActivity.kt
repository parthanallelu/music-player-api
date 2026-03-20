package com.musicapp.ui

import android.os.Bundle
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import coil.load
import coil.transform.RoundedCornersTransformation
import com.musicapp.R
import com.musicapp.databinding.ActivityPlayerBinding
import com.musicapp.player.DownloadWorker
import com.musicapp.player.PlayerManager
import com.musicapp.ui.viewmodel.PlayerViewModel
import com.musicapp.util.TimeUtils

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val viewModel: PlayerViewModel by viewModels()
    private var isUserSeeking = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        observePlayer()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnPlayPause.setOnClickListener { viewModel.playPause() }
        binding.btnNext.setOnClickListener { viewModel.next() }
        binding.btnPrevious.setOnClickListener { viewModel.previous() }

        binding.btnDownload.setOnClickListener {
            val song = PlayerManager.currentSong.value
            if (song != null && !song.isDownloaded) {
                DownloadWorker.enqueue(this, song)
                Toast.makeText(this, R.string.downloading, Toast.LENGTH_SHORT).show()
            } else if (song?.isDownloaded == true) {
                Toast.makeText(this, R.string.downloaded, Toast.LENGTH_SHORT).show()
            }
        }

        binding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    binding.tvCurrentTime.text = TimeUtils.formatDuration(progress.toLong())
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) { isUserSeeking = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                isUserSeeking = false
                seekBar?.progress?.let { viewModel.seekTo(it.toLong()) }
            }
        })
    }

    private fun observePlayer() {
        viewModel.currentSong.observe(this) { song ->
            song?.let {
                binding.tvSongTitle.text = it.title
                binding.tvArtist.text = it.artist
                
                // Enriched info: Album • Year • Genre
                val info = StringBuilder()
                if (it.album.isNotEmpty() && it.album != "Unknown Album") {
                    info.append(it.album)
                }
                if (it.year > 0) {
                    if (info.isNotEmpty()) info.append(" • ")
                    info.append(it.year)
                }
                if (it.genre != "unknown" && it.genre.isNotEmpty()) {
                    if (info.isNotEmpty()) info.append(" • ")
                    info.append(it.genre)
                }
                binding.tvEnrichedInfo.text = info.toString()

                // Album Art
                if (it.absoluteAlbumArtUrl.isNotEmpty()) {
                    binding.ivAlbumArt.load(it.absoluteAlbumArtUrl) {
                        crossfade(true)
                        placeholder(R.drawable.bg_album_art_placeholder)
                        error(R.drawable.bg_album_art_placeholder)
                        transformations(RoundedCornersTransformation(24f))
                    }
                }

                // Artist Image
                if (it.artistImage.isNotEmpty()) {
                    binding.ivArtist.load(it.artistImage) {
                        crossfade(true)
                        placeholder(R.drawable.bg_album_art_placeholder)
                        error(R.drawable.bg_album_art_placeholder)
                    }
                } else {
                    binding.ivArtist.setImageResource(R.drawable.bg_album_art_placeholder)
                }
            }
        }

        viewModel.isPlaying.observe(this) { playing ->
            binding.btnPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        viewModel.duration.observe(this) { dur ->
            binding.seekBar.max = dur.toInt()
            binding.tvTotalTime.text = TimeUtils.formatDuration(dur)
        }

        viewModel.currentPosition.observe(this) { pos ->
            if (!isUserSeeking) {
                binding.seekBar.progress = pos.toInt()
                binding.tvCurrentTime.text = TimeUtils.formatDuration(pos)
            }
        }
    }
}
