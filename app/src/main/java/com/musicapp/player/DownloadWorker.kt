package com.musicapp.player

import android.app.NotificationManager
import android.content.Context
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.musicapp.R
import com.musicapp.database.AppDatabase
import com.musicapp.util.Constants
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class DownloadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val songId = inputData.getString("song_id") ?: return Result.failure()
        val songTitle = inputData.getString("song_title") ?: "Unknown"
        val downloadUrl = inputData.getString("download_url") ?: return Result.failure()

        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        try {
            // Show progress notification
            val notification = NotificationCompat.Builder(applicationContext, Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Downloading")
                .setContentText(songTitle)
                .setSmallIcon(R.drawable.ic_download)
                .setProgress(100, 0, false)
                .setOngoing(true)
                .build()

            notificationManager.notify(songId.hashCode(), notification)

            // Download file
            val musicDir = File(
                applicationContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
                Constants.DOWNLOAD_DIR
            )
            if (!musicDir.exists()) musicDir.mkdirs()

            val fileName = "${songTitle.replace("[^a-zA-Z0-9]".toRegex(), "_")}_$songId.mp3"
            val outputFile = File(musicDir, fileName)

            val url = URL(downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connect()

            val totalBytes = connection.contentLength
            var downloadedBytes = 0

            connection.inputStream.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (totalBytes > 0) {
                            val progress = (downloadedBytes * 100 / totalBytes)
                            setProgress(workDataOf("progress" to progress))

                            val progressNotification = NotificationCompat.Builder(
                                applicationContext,
                                Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID
                            )
                                .setContentTitle("Downloading")
                                .setContentText("$songTitle - $progress%")
                                .setSmallIcon(R.drawable.ic_download)
                                .setProgress(100, progress, false)
                                .setOngoing(true)
                                .build()
                            notificationManager.notify(songId.hashCode(), progressNotification)
                        }
                    }
                }
            }

            // Update database
            val db = AppDatabase.getInstance(applicationContext)
            db.songDao().markAsDownloaded(songId, outputFile.absolutePath)

            // Complete notification
            val completeNotification = NotificationCompat.Builder(
                applicationContext,
                Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID
            )
                .setContentTitle("Download Complete")
                .setContentText(songTitle)
                .setSmallIcon(R.drawable.ic_download)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(songId.hashCode(), completeNotification)

            return Result.success(workDataOf("file_path" to outputFile.absolutePath))

        } catch (e: Exception) {
            // Error notification
            val errorNotification = NotificationCompat.Builder(
                applicationContext,
                Constants.DOWNLOAD_NOTIFICATION_CHANNEL_ID
            )
                .setContentTitle("Download Failed")
                .setContentText(songTitle)
                .setSmallIcon(R.drawable.ic_download)
                .setAutoCancel(true)
                .build()
            notificationManager.notify(songId.hashCode(), errorNotification)

            return Result.failure()
        }
    }

    companion object {
        fun enqueue(context: Context, song: com.musicapp.model.Song) {
            val inputData = workDataOf(
                "song_id" to song.id,
                "song_title" to song.title,
                "download_url" to song.absoluteStreamUrl
            )

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(Constants.DOWNLOAD_WORK_TAG)
                .addTag(song.id)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    "download_${song.id}",
                    ExistingWorkPolicy.KEEP,
                    downloadRequest
                )
        }
    }
}
