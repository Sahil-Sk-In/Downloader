package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class App : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize engines in background to avoid blocking main thread and preventing ANR/crashes
        appScope.launch {
            initEngine(this@App)
        }
    }

    companion object {
        private const val TAG = "MediaVaultApp"
        lateinit var instance: App
            private set

        private val _isEngineReady = MutableStateFlow(false)
        val isEngineReadyFlow: StateFlow<Boolean> = _isEngineReady.asStateFlow()

        @Volatile
        var isEngineReady: Boolean = false
            private set

        @Volatile
        var initError: String? = null
            private set

        private val initLock = Any()

        fun initEngine(context: Context): Boolean {
            synchronized(initLock) {
                if (isEngineReady) return true

                val appContext = context.applicationContext
                try {
                    Log.i(TAG, "Initializing YoutubeDL core...")
                    YoutubeDL.getInstance().init(appContext)
                    Log.i(TAG, "YoutubeDL engine initialized successfully")
                    isEngineReady = true
                    _isEngineReady.value = true
                    initError = null
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize YoutubeDL: ${e.message}", e)
                    initError = e.message ?: "Native library initialization failed"
                    isEngineReady = false
                    _isEngineReady.value = false
                }

                try {
                    Log.i(TAG, "Initializing FFmpeg core...")
                    FFmpeg.getInstance().init(appContext)
                    Log.i(TAG, "FFmpeg engine initialized successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "FFmpeg init notice: ${e.message}", e)
                }

                return isEngineReady
            }
        }

        fun ensureEngineReady(context: Context): Boolean {
            if (!isEngineReady) {
                return initEngine(context)
            }
            return isEngineReady
        }
    }
}
