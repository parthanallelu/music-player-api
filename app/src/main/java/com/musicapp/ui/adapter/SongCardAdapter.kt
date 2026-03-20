package com.musicapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.musicapp.R
import com.musicapp.model.Song

class SongCardAdapter(
    private val onSongClick: (Song, Int) -> Unit
) : ListAdapter<Song, SongCardAdapter.SongViewHolder>(SongDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist_card, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivCover: ImageView = itemView.findViewById(R.id.ivCover)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        private val tvSubtitle: TextView = itemView.findViewById(R.id.tvSubtitle)

        fun bind(song: Song, position: Int) {
            tvTitle.text = song.title
            
            val info = StringBuilder(song.artist)
            if (song.album.isNotEmpty() && song.album != "Unknown Album") {
                info.append(" • ").append(song.album)
            }
            if (song.year > 0) {
                info.append(" • ").append(song.year)
            }
            if (song.genre != "unknown" && !song.genre.isNullOrEmpty()) {
                info.append(" • ").append(song.genre)
            }
            tvSubtitle.text = info.toString()
            
            if (song.absoluteAlbumArtUrl.isNotEmpty()) {
                ivCover.load(song.absoluteAlbumArtUrl) {
                    crossfade(true)
                    placeholder(R.drawable.bg_album_art_placeholder)
                    error(R.drawable.bg_album_art_placeholder)
                    transformations(RoundedCornersTransformation(16f))
                }
            } else {
                ivCover.setImageResource(R.drawable.bg_album_art_placeholder)
            }

            itemView.setOnClickListener {
                onSongClick(song, position)
            }
        }
    }

    class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Song, newItem: Song) = oldItem == newItem
    }
}
