package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_items ORDER BY createdAtTimestamp DESC")
    fun getAllDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE status = 'COMPLETED' ORDER BY completedAtTimestamp DESC, createdAtTimestamp DESC")
    fun getCompletedDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE status IN ('QUEUED', 'ANALYZING', 'DOWNLOADING', 'PROCESSING') ORDER BY createdAtTimestamp DESC")
    fun getActiveDownloads(): Flow<List<DownloadItem>>

    @Query("SELECT * FROM download_items WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadItem?

    @Query("SELECT * FROM download_items WHERE id = :id LIMIT 1")
    fun observeDownloadById(id: String): Flow<DownloadItem?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: DownloadItem)

    @Update
    suspend fun update(item: DownloadItem)

    @Query("UPDATE download_items SET progressPercent = :progress, speedText = :speed, etaText = :eta, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, status = :status WHERE id = :id")
    suspend fun updateProgress(
        id: String,
        progress: Float,
        speed: String,
        eta: String,
        downloadedBytes: Long,
        totalBytes: Long,
        status: DownloadStatus
    )

    @Query("UPDATE download_items SET status = :status, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, errorMessage: String? = null)

    @Query("UPDATE download_items SET status = 'COMPLETED', localFilePath = :filePath, completedAtTimestamp = :completedAt, progressPercent = 100.0, errorMessage = null WHERE id = :id")
    suspend fun markCompleted(id: String, filePath: String, completedAt: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(item: DownloadItem)

    @Query("DELETE FROM download_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM download_items WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()
}
