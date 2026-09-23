package com.example.service

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import java.io.File
import java.text.DecimalFormat
import java.util.Locale

object DownloadManagerHelper {

    private const val TAG = "DownloadHelper"

    fun getStorageDirectory(isAudio: Boolean, context: Context? = null): File {
        val targetDir = if (isAudio) {
            val musicPublic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            File(musicPublic, "MediaVault")
        } else {
            val moviesPublic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            File(moviesPublic, "MediaVault")
        }

        if (!targetDir.exists()) {
            val created = targetDir.mkdirs()
            if (!created && context != null) {
                val fallbackType = if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
                val fallback = context.getExternalFilesDir(fallbackType) ?: context.filesDir
                val fallbackVault = File(fallback, "MediaVault")
                fallbackVault.mkdirs()
                return fallbackVault
            }
        }
        return targetDir
    }

    fun scanMediaFile(context: Context, filePath: String, mimeType: String? = null) {
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(filePath),
                if (mimeType != null) arrayOf(mimeType) else null
            ) { path, uri ->
                Log.d(TAG, "Scanned $path -> MediaStore URI: $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning media file: ${e.message}", e)
        }
    }

    fun detectPlatform(url: String): String {
        val lower = url.lowercase(Locale.getDefault())
        return when {
            lower.contains("youtube.com") || lower.contains("youtu.be") -> "YouTube"
            lower.contains("instagram.com") -> "Instagram"
            lower.contains("tiktok.com") -> "TikTok"
            lower.contains("twitter.com") || lower.contains("x.com") -> "Twitter / X"
            lower.contains("facebook.com") || lower.contains("fb.watch") -> "Facebook"
            lower.contains("soundcloud.com") -> "SoundCloud"
            lower.contains("reddit.com") -> "Reddit"
            lower.contains("pinterest.com") || lower.contains("pin.it") -> "Pinterest"
            lower.contains("vimeo.com") -> "Vimeo"
            lower.contains("dailymotion.com") -> "Dailymotion"
            else -> "Web Media"
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        val format = DecimalFormat("#,##0.#")
        val index = digitGroups.coerceIn(0, units.size - 1)
        return "${format.format(bytes / Math.pow(1024.0, index.toDouble()))} ${units[index]}"
    }

    fun formatDuration(seconds: Long): String {
        if (seconds <= 0) return "--:--"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val remainingSeconds = seconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, remainingSeconds)
        }
    }

    fun extractUrlFromSharedText(text: String): String {
        val urlRegex = "(https?://[\\w\\d:#@%/;$()~_?\\+-=\\\\\\.&]+)".toRegex()
        val match = urlRegex.find(text)
        return match?.value ?: text.trim()
    }
}
