package com.musicapp.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicapp.R
import com.musicapp.databinding.FragmentLibraryBinding
import com.musicapp.ui.adapter.PlaylistAdapter
import com.musicapp.ui.adapter.SongAdapter
import com.musicapp.ui.viewmodel.LibraryViewModel
import com.musicapp.ui.viewmodel.PlayerViewModel

class LibraryFragment : Fragment() {

    private var _binding: FragmentLibraryBinding? = null
    private val binding get() = _binding!!
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private val playerViewModel: PlayerViewModel by activityViewModels()
    private lateinit var playlistAdapter: PlaylistAdapter
    private lateinit var localMusicAdapter: SongAdapter

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            libraryViewModel.loadLocalMusic()
        } else {
            Toast.makeText(requireContext(), R.string.permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLibraryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerViews()
        setupListeners()
        observeData()
        requestAudioPermissionAndScan()
    }

    private fun setupRecyclerViews() {
        playlistAdapter = PlaylistAdapter(
            onPlaylistClick = { playlist ->
                if (playlist.id < 0) {
                    when (playlist.id) {
                        -1L -> libraryViewModel.recentlyPlayed.value?.let { songs ->
                            if (songs.isNotEmpty()) playerViewModel.playSong(songs.first(), songs, 0)
                        }
                        -2L -> libraryViewModel.mostPlayed.value?.let { songs ->
                            if (songs.isNotEmpty()) playerViewModel.playSong(songs.first(), songs, 0)
                        }
                        -3L -> Toast.makeText(requireContext(), "Generating Mix...", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Playlist: ${playlist.name}", Toast.LENGTH_SHORT).show()
                }
            },
            onPlaylistLongClick = { playlist ->
                AlertDialog.Builder(requireContext(), R.style.Theme_MusicPlayer)
                    .setTitle(R.string.delete)
                    .setMessage("Delete playlist \"${playlist.name}\"?")
                    .setPositiveButton(R.string.delete) { _, _ ->
                        libraryViewModel.deletePlaylist(playlist)
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
        )
        binding.rvPlaylists.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPlaylists.adapter = playlistAdapter

        localMusicAdapter = SongAdapter(
            onSongClick = { song, index ->
                val songs = libraryViewModel.localMusic.value ?: emptyList()
                playerViewModel.playSong(song, songs, index)
            }
        )
        binding.rvLocalMusic.layoutManager = LinearLayoutManager(requireContext())
        binding.rvLocalMusic.adapter = localMusicAdapter
    }

    private fun setupListeners() {
        binding.btnCreatePlaylist.setOnClickListener {
            showCreatePlaylistDialog()
        }

        binding.downloadsSection.setOnClickListener {
            findNavController().navigate(R.id.navigation_offline)
        }
    }

    private fun observeData() {
        libraryViewModel.playlists.observe(viewLifecycleOwner) { playlists ->
            playlistAdapter.submitList(playlists)
        }

        libraryViewModel.localMusic.observe(viewLifecycleOwner) { songs ->
            localMusicAdapter.submitList(songs)
            binding.tvNoLocalMusic.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun requestAudioPermissionAndScan() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(requireContext(), permission) == PackageManager.PERMISSION_GRANTED) {
            libraryViewModel.loadLocalMusic()
        } else {
            permissionLauncher.launch(permission)
        }
    }

    private fun showCreatePlaylistDialog() {
        val input = EditText(requireContext()).apply {
            hint = getString(R.string.playlist_name)
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary))
            setHintTextColor(ContextCompat.getColor(requireContext(), R.color.text_tertiary))
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(requireContext(), R.style.Theme_MusicPlayer)
            .setTitle(R.string.create_playlist)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    libraryViewModel.createPlaylist(name)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
