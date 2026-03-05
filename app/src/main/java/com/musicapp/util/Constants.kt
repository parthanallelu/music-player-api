package com.musicapp.util

object Constants {
    // Backend API URL
    // For emulator testing: "http://10.0.2.2:3000/v1/"
    const val BASE_URL = "https://www.saavn.com/"

    // Download directory name
    const val DOWNLOAD_DIR = "MusicPlayer"

    // Notification
    const val PLAYER_NOTIFICATION_ID = 1
    const val PLAYER_NOTIFICATION_CHANNEL_ID = "music_player_channel"
    const val PLAYER_NOTIFICATION_CHANNEL_NAME = "Music Player"

    const val DOWNLOAD_NOTIFICATION_CHANNEL_ID = "download_channel"
    const val DOWNLOAD_NOTIFICATION_CHANNEL_NAME = "Downloads"

    // WorkManager tags
    const val DOWNLOAD_WORK_TAG = "download_work"
}
