package com.example.ui

import android.app.Application
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.App
import com.example.data.AppDatabase
import com.example.data.DownloadFormat
import com.example.data.DownloadItem
import com.example.data.DownloadStatus
import com.example.service.DownloadManagerHelper
import com.example.service.DownloadService
import com.example.service.MediaDownloadWorker
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class NavTab {
    HOME,
    DOWNLOADS,
    HISTORY,
    SETTINGS
}

enum class HistoryFilter {
    ALL,
    VIDEO,
    AUDIO
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val downloadDao = database.downloadDao()

    private val _currentTab = MutableStateFlow(NavTab.HOME)
    val currentTab: StateFlow<NavTab> = _currentTab.asStateFlow()

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _selectedFormat = MutableStateFlow(DownloadFormat.VIDEO_BEST)
    val selectedFormat: StateFlow<DownloadFormat> = _selectedFormat.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analyzedInfo = MutableStateFlow<VideoInfo?>(null)
    val analyzedInfo: StateFlow<VideoInfo?> = _analyzedInfo.asStateFlow()

    private val _showFormatSheet = MutableStateFlow(false)
    val showFormatSheet: StateFlow<Boolean> = _showFormatSheet.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    val historyFilter: StateFlow<HistoryFilter> = _historyFilter.asStateFlow()

    private val _snackbarEvent = MutableSharedFlow<String>()
    val snackbarEvent: SharedFlow<String> = _snackbarEvent.asSharedFlow()

    private val _currentlyPlayingItem = MutableStateFlow<DownloadItem?>(null)
    val currentlyPlayingItem: StateFlow<DownloadItem?> = _currentlyPlayingItem.asStateFlow()

    private val _ytDlpVersion = MutableStateFlow("yt-dlp 2025.01.15")
    val ytDlpVersion: StateFlow<String> = _ytDlpVersion.asStateFlow()

    private val _isUpdatingEngine = MutableStateFlow(false)
    val isUpdatingEngine: StateFlow<Boolean> = _isUpdatingEngine.asStateFlow()

    val allDownloads: StateFlow<List<DownloadItem>> = downloadDao.getAllDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeDownloads: StateFlow<List<DownloadItem>> = downloadDao.getActiveDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rawCompletedDownloads: StateFlow<List<DownloadItem>> = downloadDao.getCompletedDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredCompletedDownloads: StateFlow<List<DownloadItem>> = combine(
        rawCompletedDownloads,
        _searchQuery,
        _historyFilter
    ) { list, query, filter ->
        list.filter { item ->
            val matchesFilter = when (filter) {
                HistoryFilter.ALL -> true
                HistoryFilter.VIDEO -> !item.isAudioOnly
                HistoryFilter.AUDIO -> item.isAudioOnly
            }
            val matchesQuery = query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    item.uploader.contains(query, ignoreCase = true) ||
                    item.platformName.contains(query, ignoreCase = true) ||
                    item.fileExtension.contains(query, ignoreCase = true)

            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        loadEngineVersion()
    }

    private fun loadEngineVersion() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (App.ensureEngineReady(getApplication())) {
                    val version = YoutubeDL.getInstance().version(getApplication())
                    if (!version.isNullOrBlank()) {
                        _ytDlpVersion.value = "yt-dlp $version"
                    }
                }
            } catch (e: Exception) {
                Log.w("MainViewModel", "Could not fetch engine version: ${e.message}")
            }
        }
    }

    fun updateYtDlpEngine(context: Context) {
        viewModelScope.launch {
            _isUpdatingEngine.value = true
            try {
                withContext(Dispatchers.IO) {
                    val status = YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext)
                    _snackbarEvent.emit("Engine update: ${status?.name ?: "Up to date"}")
                    loadEngineVersion()
                }
            } catch (e: Exception) {
                _snackbarEvent.emit("Engine update: ${e.message ?: "Failed"}")
            } finally {
                _isUpdatingEngine.value = false
            }
        }
    }

    fun setTab(tab: NavTab) {
        _currentTab.value = tab
    }

    fun setUrlInput(url: String) {
        _urlInput.value = url
    }

    fun clearUrl() {
        _urlInput.value = ""
        _analyzedInfo.value = null
    }

    fun selectFormat(format: DownloadFormat) {
        _selectedFormat.value = format
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun closeFormatSheet() {
        _showFormatSheet.value = false
    }

    fun playMedia(item: DownloadItem) {
        _currentlyPlayingItem.value = item
    }

    fun dismissMediaPlayer() {
        _currentlyPlayingItem.value = null
    }

    fun pasteFromClipboard(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
                val item = clipboard.primaryClip?.getItemAt(0)
                val rawText = item?.text?.toString() ?: ""
                val extractedUrl = DownloadManagerHelper.extractUrlFromSharedText(rawText)
                if (extractedUrl.isNotBlank()) {
                    _urlInput.value = extractedUrl
                    fetchMetadataAndShowSheet(extractedUrl)
                } else {
                    emitSnackbar("Clipboard does not contain a valid link")
                }
            } else {
                emitSnackbar("Clipboard is empty")
            }
        } catch (e: Exception) {
            emitSnackbar("Could not access clipboard")
        }
    }

    fun handleSharedIntentText(sharedText: String) {
        val extractedUrl = DownloadManagerHelper.extractUrlFromSharedText(sharedText)
        if (extractedUrl.isNotBlank()) {
            _urlInput.value = extractedUrl
            _currentTab.value = NavTab.HOME
            fetchMetadataAndShowSheet(extractedUrl)
        }
    }

    fun fetchMetadataAndShowSheet(url: String) {
        _showFormatSheet.value = true
        _isAnalyzing.value = true
        _analyzedInfo.value = null

        viewModelScope.launch(Dispatchers.IO) {
            try {
                App.ensureEngineReady(getApplication())
                val info = YoutubeDL.getInstance().getInfo(url)
                _analyzedInfo.value = info
            } catch (e: Exception) {
                Log.w("MainViewModel", "Metadata extraction note: ${e.message}")
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    fun startDownload(context: Context, format: DownloadFormat) {
        val url = _urlInput.value.trim()
        if (url.isBlank()) {
            emitSnackbar("Please enter a media URL")
            return
        }

        val downloadId = UUID.randomUUID().toString()
        val info = _analyzedInfo.value
        val title = info?.title?.takeIf { it.isNotBlank() } ?: "Media_${System.currentTimeMillis() % 100000}"
        val uploader = info?.uploader ?: ""
        val duration = info?.duration?.toLong() ?: 0L
        val thumbnail = info?.thumbnail ?: ""
        val platform = DownloadManagerHelper.detectPlatform(url)

        val item = DownloadItem(
            id = downloadId,
            url = url,
            title = title,
            uploader = uploader,
            durationSeconds = duration,
            thumbnailUrl = thumbnail,
            formatId = format.formatId,
            formatDisplayName = format.displayName,
            fileExtension = format.extension,
            isAudioOnly = format.isAudioOnly,
            status = DownloadStatus.QUEUED,
            platformName = platform
        )

        viewModelScope.launch(Dispatchers.IO) {
            downloadDao.insertOrUpdate(item)
            MediaDownloadWorker.enqueueDownload(context, downloadId, url, title, format.formatId)
            _showFormatSheet.value = false
            _urlInput.value = ""
            _analyzedInfo.value = null
            _currentTab.value = NavTab.DOWNLOADS
            emitSnackbar("Started downloading: $title")
        }
    }

    fun retryDownload(context: Context, item: DownloadItem) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = item.copy(
                status = DownloadStatus.QUEUED,
                progressPercent = 0f,
                errorMessage = null
            )
            downloadDao.insertOrUpdate(updated)
            MediaDownloadWorker.enqueueDownload(context, item.id, item.url, item.title, item.formatId)
            emitSnackbar("Retrying: ${item.title}")
        }
    }

    fun cancelDownload(context: Context, downloadId: String) {
        MediaDownloadWorker.cancelDownload(context, downloadId)
        DownloadService.cancelDownload(context, downloadId)
        emitSnackbar("Download cancelled")
    }

    fun deleteItem(item: DownloadItem, deleteLocalFile: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (deleteLocalFile && item.localFilePath.isNotBlank()) {
                try {
                    val file = File(item.localFilePath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.e("MainViewModel", "Error deleting local file: ${e.message}")
                }
            }
            downloadDao.delete(item)
            emitSnackbar("Removed: ${item.title}")
        }
    }

    fun clearCompletedHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            downloadDao.clearCompleted()
            emitSnackbar("Cleared completed history")
        }
    }

    fun shareMediaFile(context: Context, item: DownloadItem) {
        try {
            val file = File(item.localFilePath)
            if (!file.exists()) {
                emitSnackbar("File not found on device")
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = if (item.isAudioOnly) "audio/*" else "video/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, item.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share ${item.title}"))
        } catch (e: Exception) {
            emitSnackbar("Failed to share file: ${e.message}")
        }
    }

    fun openInExternalApp(context: Context, item: DownloadItem) {
        try {
            val file = File(item.localFilePath)
            if (!file.exists()) {
                emitSnackbar("File not found")
                return
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (item.isAudioOnly) "audio/*" else "video/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
        } catch (e: Exception) {
            emitSnackbar("No app found to play this file")
        }
    }

    private fun emitSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarEvent.emit(message)
        }
    }
}
