package com.musicapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicapp.R
import com.musicapp.databinding.FragmentHomeBinding
import com.musicapp.player.DownloadWorker
import com.musicapp.player.PlayerManager
import com.musicapp.ui.adapter.SongAdapter
import com.musicapp.ui.viewmodel.HomeViewModel
import com.musicapp.ui.viewmodel.PlayerViewModel
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val homeViewModel: HomeViewModel by activityViewModels()
    private val playerViewModel: PlayerViewModel by activityViewModels()
    private lateinit var songAdapter: SongAdapter
    private lateinit var recentAdapter: SongAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupGreeting()
        setupRecyclerViews()
        observeData()

        binding.swipeRefresh.setColorSchemeResources(R.color.green_primary)
        binding.swipeRefresh.setProgressBackgroundColorSchemeResource(R.color.background_surface)
        binding.swipeRefresh.setOnRefreshListener {
            homeViewModel.refresh()
        }

        binding.btnRetry.setOnClickListener {
            homeViewModel.refresh()
        }
    }

    private fun setupGreeting() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        binding.tvHeader.text = when {
            hour < 12 -> "Good Morning"
            hour < 18 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    private fun setupRecyclerViews() {
        songAdapter = SongAdapter(
            onSongClick = { song, index ->
                val songs = homeViewModel.songs.value ?: emptyList()
                playerViewModel.playSong(song, songs, index)
            },
            onDownloadClick = { song ->
                DownloadWorker.enqueue(requireContext(), song)
                Toast.makeText(requireContext(), R.string.downloading, Toast.LENGTH_SHORT).show()
            }
        )
        binding.rvSongs.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSongs.adapter = songAdapter

        recentAdapter = SongAdapter(
            onSongClick = { song, _ ->
                playerViewModel.playSong(song, listOf(song), 0)
            }
        )
        binding.rvRecentlyPlayed.layoutManager = LinearLayoutManager(
            requireContext(), LinearLayoutManager.HORIZONTAL, false
        )
        binding.rvRecentlyPlayed.adapter = recentAdapter
    }

    private fun observeData() {
        homeViewModel.songs.observe(viewLifecycleOwner) { songs ->
            songAdapter.submitList(songs)
            binding.swipeRefresh.isRefreshing = false
        }

        homeViewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        }

        homeViewModel.error.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                binding.errorContainer.visibility = View.VISIBLE
                binding.tvError.text = error
            } else {
                binding.errorContainer.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
