package com.musicapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.musicapp.R
import com.musicapp.databinding.FragmentHomeBinding
import com.musicapp.ui.adapter.SongCardAdapter
import com.musicapp.ui.viewmodel.HomeViewModel
import com.musicapp.ui.viewmodel.PlayerViewModel
import java.util.Calendar

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val homeViewModel: HomeViewModel by activityViewModels()
    private val playerViewModel: PlayerViewModel by activityViewModels()
    private lateinit var trendingAdapter: SongCardAdapter
    private lateinit var hindiAdapter: SongCardAdapter
    private lateinit var englishAdapter: SongCardAdapter
    private lateinit var technoAdapter: SongCardAdapter
    private lateinit var recentAdapter: SongCardAdapter
    private lateinit var recommendedAdapter: SongCardAdapter

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
        val onSongClick = { song: com.musicapp.model.Song, currentList: List<com.musicapp.model.Song>, index: Int ->
            playerViewModel.playSong(song, currentList, index)
        }

        trendingAdapter = SongCardAdapter { song, index -> 
            onSongClick(song, homeViewModel.trendingSongs.value ?: emptyList(), index) 
        }
        binding.rvTrending.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvTrending.adapter = trendingAdapter

        hindiAdapter = SongCardAdapter { song, index -> 
            onSongClick(song, homeViewModel.hindiSongs.value ?: emptyList(), index) 
        }
        binding.rvHindiHits.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvHindiHits.adapter = hindiAdapter

        englishAdapter = SongCardAdapter { song, index -> 
            onSongClick(song, homeViewModel.englishSongs.value ?: emptyList(), index) 
        }
        binding.rvEnglishHits.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvEnglishHits.adapter = englishAdapter

        technoAdapter = SongCardAdapter { song, index -> 
            onSongClick(song, homeViewModel.technoSongs.value ?: emptyList(), index) 
        }
        binding.rvTechnoHits.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvTechnoHits.adapter = technoAdapter

        recentAdapter = SongCardAdapter { song, index ->
            onSongClick(song, homeViewModel.recentlyPlayedSongs.value ?: emptyList(), index)
        }
        binding.rvRecentlyPlayed.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecentlyPlayed.adapter = recentAdapter

        recommendedAdapter = SongCardAdapter { song, index -> 
            onSongClick(song, homeViewModel.recommendedSongs.value ?: emptyList(), index) 
        }
        binding.rvRecommended.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.rvRecommended.adapter = recommendedAdapter
    }

    private fun observeData() {
        homeViewModel.trendingSongs.observe(viewLifecycleOwner) { songs ->
            trendingAdapter.submitList(songs)
            checkRefreshState()
        }
        homeViewModel.hindiSongs.observe(viewLifecycleOwner) { songs ->
            hindiAdapter.submitList(songs)
            checkRefreshState()
        }
        homeViewModel.englishSongs.observe(viewLifecycleOwner) { songs ->
            englishAdapter.submitList(songs)
            checkRefreshState()
        }
        homeViewModel.technoSongs.observe(viewLifecycleOwner) { songs ->
            technoAdapter.submitList(songs)
            checkRefreshState()
        }

        homeViewModel.recentlyPlayedSongs.observe(viewLifecycleOwner) { songs ->
            if (songs.isNotEmpty()) {
                binding.tvRecentlyPlayedLabel.visibility = View.VISIBLE
                binding.rvRecentlyPlayed.visibility = View.VISIBLE
                recentAdapter.submitList(songs)
            } else {
                binding.tvRecentlyPlayedLabel.visibility = View.GONE
                binding.rvRecentlyPlayed.visibility = View.GONE
            }
        }

        homeViewModel.recommendedSongs.observe(viewLifecycleOwner) { songs ->
            if (songs.isNotEmpty()) {
                binding.tvRecommendedLabel.visibility = View.VISIBLE
                binding.rvRecommended.visibility = View.VISIBLE
                recommendedAdapter.submitList(songs)
            } else {
                binding.tvRecommendedLabel.visibility = View.GONE
                binding.rvRecommended.visibility = View.GONE
            }
            checkRefreshState()
        }

        homeViewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
            checkRefreshState()
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

    private fun checkRefreshState() {
        val isLoading = homeViewModel.isLoading.value ?: false
        if (!isLoading) {
            binding.swipeRefresh.isRefreshing = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
