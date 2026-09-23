package com.example.data

import kotlinx.coroutines.flow.Flow

class DownloadRepository(private val downloadDao: DownloadDao) {

    val allDownloads: Flow<List<DownloadItem>> = downloadDao.getAllDownloads()
    val completedDownloads: Flow<List<DownloadItem>> = downloadDao.getCompletedDownloads()
    val activeDownloads: Flow<List<DownloadItem>> = downloadDao.getActiveDownloads()

    suspend fun getDownloadById(id: String): DownloadItem? {
        return downloadDao.getDownloadById(id)
    }

    fun observeDownloadById(id: String): Flow<DownloadItem?> {
        return downloadDao.observeDownloadById(id)
    }

    suspend fun insertOrUpdate(item: DownloadItem) {
        downloadDao.insertOrUpdate(item)
    }

    suspend fun updateProgress(
        id: String,
        progress: Float,
        speed: String,
        eta: String,
        downloadedBytes: Long,
        totalBytes: Long,
        status: DownloadStatus
    ) {
        downloadDao.updateProgress(id, progress, speed, eta, downloadedBytes, totalBytes, status)
    }

    suspend fun updateStatus(id: String, status: DownloadStatus, errorMessage: String? = null) {
        downloadDao.updateStatus(id, status, errorMessage)
    }

    suspend fun markCompleted(id: String, filePath: String, completedAt: Long = System.currentTimeMillis()) {
        downloadDao.markCompleted(id, filePath, completedAt)
    }

    suspend fun delete(item: DownloadItem) {
        downloadDao.delete(item)
    }

    suspend fun deleteById(id: String) {
        downloadDao.deleteById(id)
    }

    suspend fun clearCompleted() {
        downloadDao.clearCompleted()
    }
}
