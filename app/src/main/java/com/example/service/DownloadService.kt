package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.example.App
import com.example.MainActivity
import com.example.data.AppDatabase
import com.example.data.DownloadFormat
import com.example.data.DownloadStatus
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var database: AppDatabase
    private lateinit var notificationManager: NotificationManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val activeJobs = ConcurrentHashMap<String, Job>()

    companion object {
        private const val TAG = "DownloadService"
        const val CHANNEL_ID = "media_vault_downloads"
        const val CHANNEL_NAME = "MediaVault File Downloads"
        const val NOTIFICATION_ID = 9001

        const val ACTION_START_DOWNLOAD = "com.example.action.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.example.action.CANCEL_DOWNLOAD"

        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FORMAT_ID = "extra_format_id"

        fun startDownload(
            context: Context,
            downloadId: String,
            url: String,
            title: String,
            formatId: String
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FORMAT_ID, formatId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelDownload(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MediaVault:DownloadWakeLock").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_NOT_STICKY
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Downloading Media"
                val formatId = intent.getStringExtra(EXTRA_FORMAT_ID) ?: DownloadFormat.VIDEO_BEST.formatId

                startForegroundWithNotification(title, "Starting download...")
                wakeLock?.acquire(30 * 60 * 1000L)

                val job = serviceScope.launch {
                    processDownload(downloadId, url, title, formatId)
                }
                activeJobs[downloadId] = job
            }

            ACTION_CANCEL_DOWNLOAD -> {
                val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_NOT_STICKY
                handleCancel(downloadId)
            }
        }

        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification(title: String, message: String) {
        val notification = buildProgressNotification(title, message, 0f, true)
        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundServiceType)
    }

    private suspend fun processDownload(
        downloadId: String,
        url: String,
        initialTitle: String,
        formatId: String
    ) {
        val format = DownloadFormat.fromId(formatId)
        val downloadDao = database.downloadDao()

        if (!App.ensureEngineReady(this)) {
            val errorMsg = App.initError ?: "Native yt-dlp core failed to initialize"
            Log.e(TAG, "Engine not ready: $errorMsg")
            downloadDao.updateStatus(downloadId, DownloadStatus.FAILED, errorMsg)
            checkAllJobsCompleted()
            return
        }

        val destinationDir = DownloadManagerHelper.getStorageDirectory(format.isAudioOnly, this)
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
                if (now - lastProgressUpdate > 500) {
                    lastProgressUpdate = now
                    val progressFloat = progress.coerceIn(0f, 100f)
                    val etaStr = if (etaInSeconds > 0) "${etaInSeconds}s" else ""
                    val speedStr = parseSpeedFromLine(line)

                    serviceScope.launch {
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

                    val notif = buildProgressNotification(
                        title = initialTitle,
                        message = "${progressFloat.toInt()}% • $speedStr",
                        progress = progressFloat,
                        indeterminate = false
                    )
                    notificationManager.notify(NOTIFICATION_ID, notif)
                }
            }

            val completedFile = findLatestDownloadedFile(destinationDir, startTime)
            val finalPath = completedFile?.absolutePath ?: "${destinationDir.absolutePath}/$initialTitle.${format.extension}"

            downloadDao.markCompleted(downloadId, finalPath)

            DownloadManagerHelper.scanMediaFile(
                context = this,
                filePath = finalPath,
                mimeType = if (format.isAudioOnly) "audio/*" else "video/*"
            )

            showCompletionNotification(initialTitle, finalPath)

        } catch (e: Exception) {
            Log.e(TAG, "Download failed for $downloadId: ${e.message}", e)
            val errorMsg = e.message ?: "Download failed"
            downloadDao.updateStatus(
                id = downloadId,
                status = if (errorMsg.contains("cancel", ignoreCase = true)) DownloadStatus.CANCELLED else DownloadStatus.FAILED,
                errorMessage = errorMsg
            )
        } finally {
            activeJobs.remove(downloadId)
            checkAllJobsCompleted()
        }
    }

    private fun handleCancel(downloadId: String) {
        serviceScope.launch {
            try {
                YoutubeDL.getInstance().destroyProcessById(downloadId)
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying process for $downloadId: ${e.message}")
            }
            activeJobs[downloadId]?.cancel()
            activeJobs.remove(downloadId)
            database.downloadDao().updateStatus(downloadId, DownloadStatus.CANCELLED, "Download cancelled by user")
            checkAllJobsCompleted()
        }
    }

    private fun checkAllJobsCompleted() {
        if (activeJobs.isEmpty()) {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
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
        title: String,
        message: String,
        progress: Float,
        indeterminate: Boolean
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
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

    private fun showCompletionNotification(title: String, filePath: String) {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Download Complete")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notif)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live download progress for MediaVault transfers"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
