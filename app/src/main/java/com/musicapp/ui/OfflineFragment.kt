package com.musicapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicapp.databinding.FragmentOfflineBinding
import com.musicapp.ui.adapter.SongAdapter
import com.musicapp.ui.viewmodel.LibraryViewModel
import com.musicapp.ui.viewmodel.PlayerViewModel

class OfflineFragment : Fragment() {

    private var _binding: FragmentOfflineBinding? = null
    private val binding get() = _binding!!
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private val playerViewModel: PlayerViewModel by activityViewModels()
    private lateinit var adapter: SongAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentOfflineBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = SongAdapter(
            onSongClick = { song, index ->
                val songs = libraryViewModel.downloadedSongs.value ?: emptyList()
                playerViewModel.playSong(song, songs, index)
            }
        )
        binding.rvDownloadedSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvDownloadedSongs.adapter = adapter

        binding.btnBack.setOnClickListener {
            findNavController().navigateUp()
        }

        libraryViewModel.downloadedSongs.observe(viewLifecycleOwner) { songs ->
            adapter.submitList(songs)
            binding.tvNoDownloads.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
