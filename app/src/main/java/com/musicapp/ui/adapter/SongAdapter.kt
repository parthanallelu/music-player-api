package com.musicapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.musicapp.R
import com.musicapp.model.Song
import com.musicapp.util.TimeUtils

class SongAdapter(
    private val onSongClick: (Song, Int) -> Unit,
    private val onDownloadClick: ((Song) -> Unit)? = null
) : ListAdapter<Song, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val albumArt: ImageView = itemView.findViewById(R.id.ivAlbumArt)
        private val title: TextView = itemView.findViewById(R.id.tvTitle)
        private val artist: TextView = itemView.findViewById(R.id.tvArtist)
        private val duration: TextView = itemView.findViewById(R.id.tvDuration)
        private val downloadBtn: ImageButton = itemView.findViewById(R.id.btnDownload)

        fun bind(song: Song, position: Int) {
            title.text = song.title
            
            val info = StringBuilder(song.artist)
            if (song.album.isNotEmpty() && song.album != "Unknown Album") {
                info.append(" • ").append(song.album)
            }
            if (song.year > 0) {
                info.append(" • ").append(song.year)
            }
            if (song.genre != "unknown" && song.genre.isNotEmpty()) {
                info.append(" • ").append(song.genre)
            }
            artist.text = info.toString()
            
            duration.text = TimeUtils.formatDuration(song.duration)

            if (song.absoluteAlbumArtUrl.isNotEmpty()) {
                albumArt.load(song.absoluteAlbumArtUrl) {
                    crossfade(true)
                    placeholder(R.drawable.bg_album_art_placeholder)
                    error(R.drawable.bg_album_art_placeholder)
                    transformations(RoundedCornersTransformation(16f))
                }
            } else {
                albumArt.setImageResource(R.drawable.ic_music_note)
                albumArt.setBackgroundResource(R.drawable.bg_album_art_placeholder)
            }

            if (song.isDownloaded) {
                downloadBtn.visibility = View.GONE
            } else {
                downloadBtn.visibility = if (onDownloadClick != null) View.VISIBLE else View.GONE
            }

            downloadBtn.setOnClickListener {
                onDownloadClick?.invoke(song)
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
