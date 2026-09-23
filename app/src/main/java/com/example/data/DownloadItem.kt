package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED,
    ANALYZING,
    DOWNLOADING,
    PROCESSING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(tableName = "download_items")
data class DownloadItem(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val uploader: String = "",
    val durationSeconds: Long = 0,
    val thumbnailUrl: String = "",
    val formatId: String,
    val formatDisplayName: String,
    val fileExtension: String,
    val isAudioOnly: Boolean,
    val localFilePath: String = "",
    val totalBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val progressPercent: Float = 0f,
    val speedText: String = "",
    val etaText: String = "",
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val errorMessage: String? = null,
    val createdAtTimestamp: Long = System.currentTimeMillis(),
    val completedAtTimestamp: Long? = null,
    val platformName: String = ""
)
