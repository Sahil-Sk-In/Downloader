package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.App
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.DownloadFormat
import com.example.data.DownloadStatus
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaDownloadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    private val database = AppDatabase.getDatabase(context)
    private val downloadDao = database.downloadDao()
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        private const val TAG = "MediaDownloadWorker"
        const val KEY_DOWNLOAD_ID = "key_download_id"
        const val KEY_URL = "key_url"
        const val KEY_TITLE = "key_title"
        const val KEY_FORMAT_ID = "key_format_id"

        const val CHANNEL_ID = "media_vault_work_channel"
        const val CHANNEL_NAME = "MediaVault Work Tasks"
        const val NOTIFICATION_ID_BASE = 10000

        fun enqueueDownload(
            context: Context,
            downloadId: String,
            url: String,
            title: String,
            formatId: String
        ) {
            val inputData = workDataOf(
                KEY_DOWNLOAD_ID to downloadId,
                KEY_URL to url,
                KEY_TITLE to title,
                KEY_FORMAT_ID to formatId
            )

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("download_$downloadId")
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "work_$downloadId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }

        fun cancelDownload(context: Context, downloadId: String) {
            WorkManager.getInstance(context).cancelUniqueWork("work_$downloadId")
            try {
                YoutubeDL.getInstance().destroyProcessById(downloadId)
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying process: ${e.message}")
            }
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadId = inputData.getString(KEY_DOWNLOAD_ID) ?: return@withContext Result.failure()
        val url = inputData.getString(KEY_URL) ?: return@withContext Result.failure()
        val initialTitle = inputData.getString(KEY_TITLE) ?: "Media Download"
        val formatId = inputData.getString(KEY_FORMAT_ID) ?: DownloadFormat.VIDEO_BEST.formatId

        val format = DownloadFormat.fromId(formatId)
        val notificationId = NOTIFICATION_ID_BASE + (downloadId.hashCode() and 0xFFFF)

        createNotificationChannel()

        // Establish foreground service via WorkManager
        val initialNotification = buildProgressNotification(
            notificationId = notificationId,
            title = initialTitle,
            message = "Initializing engine...",
            progress = 0f,
            indeterminate = true,
            downloadId = downloadId
        )

        val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, initialNotification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, initialNotification)
        }

        try {
            setForeground(foregroundInfo)
        } catch (e: Exception) {
            Log.w(TAG, "Could not set foreground service: ${e.message}")
        }

        if (!App.ensureEngineReady(context)) {
            val err = App.initError ?: "Native yt-dlp core failed to load"
            downloadDao.updateStatus(downloadId, DownloadStatus.FAILED, err)
            return@withContext Result.failure(workDataOf("error" to err))
        }

        val destinationDir = DownloadManagerHelper.getStorageDirectory(format.isAudioOnly, context)
        val outputTemplate = "${destinationDir.absolutePath}/%(title).100B-%(id)s.%(ext)s"

        val request = YoutubeDLRequest(url).apply {
            addOption("-o", outputTemplate)
            addOption("-f", format.ytDlpFormatSpec)
            addOption("--no-mtime")
            addOption("--no-playlist")
            addOption("--socket-timeout", "30")
            addOption("--retries", "10")
            addOption("--fragment-retries", "10")

            for (arg in format.extraArgs) {
                addOption(arg)
            }
        }

        try {
            downloadDao.updateStatus(downloadId, DownloadStatus.DOWNLOADING)
            var lastProgressUpdate = 0L
            val startTime = System.currentTimeMillis()

            YoutubeDL.getInstance().execute(request, downloadId) { progress, etaInSeconds, line ->
                val now = System.currentTimeMillis()
                if (now - lastProgressUpdate > 400) {
                    lastProgressUpdate = now
                    val progressFloat = progress.coerceIn(0f, 100f)
                    val etaStr = if (etaInSeconds > 0) "${etaInSeconds}s" else ""
                    val speedStr = parseSpeedFromLine(line)

                    kotlinx.coroutines.runBlocking {
                        downloadDao.updateProgress(
                            id = downloadId,
                            progress = progressFloat,
                            speed = speedStr,
                            eta = etaStr,
                            downloadedBytes = 0L,
                            totalBytes = 0L,
                            status = if (progressFloat >= 99f) DownloadStatus.PROCESSING else DownloadStatus.DOWNLOADING
                        )
                    }

                    val updatedNotif = buildProgressNotification(
                        notificationId = notificationId,
                        title = initialTitle,
                        message = "${progressFloat.toInt()}% • $speedStr",
                        progress = progressFloat,
                        indeterminate = false,
                        downloadId = downloadId
                    )
                    notificationManager.notify(notificationId, updatedNotif)
                }
            }

            val completedFile = findLatestDownloadedFile(destinationDir, startTime)
            val finalPath = completedFile?.absolutePath ?: "${destinationDir.absolutePath}/$initialTitle.${format.extension}"

            downloadDao.markCompleted(downloadId, finalPath)

            DownloadManagerHelper.scanMediaFile(
                context = context,
                filePath = finalPath,
                mimeType = if (format.isAudioOnly) "audio/*" else "video/*"
            )

            showCompletionNotification(notificationId, initialTitle)

            Result.success(workDataOf("filePath" to finalPath))
        } catch (e: Exception) {
            Log.e(TAG, "Worker execution failed for $downloadId: ${e.message}", e)
            val errorMsg = e.message ?: "Task error"
            val isCancelled = isStopped || errorMsg.contains("cancel", ignoreCase = true)
            downloadDao.updateStatus(
                id = downloadId,
                status = if (isCancelled) DownloadStatus.CANCELLED else DownloadStatus.FAILED,
                errorMessage = errorMsg
            )
            if (isCancelled) {
                Result.failure(workDataOf("cancelled" to true))
            } else {
                Result.failure(workDataOf("error" to errorMsg))
            }
        } finally {
            notificationManager.cancel(notificationId)
        }
    }

    private fun findLatestDownloadedFile(directory: File, afterTimestamp: Long): File? {
        return try {
            directory.listFiles()
                ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") && it.lastModified() >= afterTimestamp - 10000 }
                ?.maxByOrNull { it.lastModified() }
        } catch (e: Exception) {
            null
        }
    }

    private fun parseSpeedFromLine(line: String?): String {
        if (line == null) return ""
        val speedMatch = Regex("at\\s+([\\d\\.]+\\s*[KMG]i?B/s)").find(line)
        return speedMatch?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun buildProgressNotification(
        notificationId: Int,
        title: String,
        message: String,
        progress: Float,
        indeterminate: Boolean,
        downloadId: String
    ): Notification {
        val launchIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress.toInt(), indeterminate)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    private fun showCompletionNotification(notificationId: Int, title: String) {
        val launchIntent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(notificationId + 1, notif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of background media downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
}
