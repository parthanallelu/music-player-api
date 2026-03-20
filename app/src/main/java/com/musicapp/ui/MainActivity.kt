package com.musicapp.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import coil.load
import coil.transform.RoundedCornersTransformation
import com.musicapp.R
import com.musicapp.databinding.ActivityMainBinding
import com.musicapp.player.PlayerManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Bind player service
        PlayerManager.bindService(this)

        setupNavigation()
        setupMiniPlayer()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)
    }

    private fun setupMiniPlayer() {
        PlayerManager.currentSong.observe(this) { song ->
            if (song != null) {
                binding.miniPlayerContainer.visibility = View.VISIBLE
                binding.miniTitle.text = song.title
                binding.miniArtist.text = song.artist
                if (song.absoluteAlbumArtUrl.isNotEmpty()) {
                    binding.miniAlbumArt.load(song.absoluteAlbumArtUrl) {
                        crossfade(true)
                        placeholder(R.drawable.bg_album_art_placeholder)
                        error(R.drawable.bg_album_art_placeholder)
                        transformations(RoundedCornersTransformation(8f))
                    }
                }
            } else {
                binding.miniPlayerContainer.visibility = View.GONE
            }
        }

        PlayerManager.isPlaying.observe(this) { playing ->
            binding.miniPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play
            )
        }

        binding.miniPlayPause.setOnClickListener {
            PlayerManager.playPause()
        }

        binding.miniPlayerContainer.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
        }
    }

    override fun onDestroy() {
        PlayerManager.unbindService(this)
        super.onDestroy()
    }
}
