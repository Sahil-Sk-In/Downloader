package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        initEngine(this)
    }

    companion object {
        private const val TAG = "MediaVaultApp"
        lateinit var instance: App
            private set

        @Volatile
        var isEngineReady: Boolean = false
            private set

        @Volatile
        var initError: String? = null
            private set

        fun initEngine(context: Context) {
            synchronized(this) {
                if (isEngineReady) return

                val appContext = context.applicationContext
                try {
                    YoutubeDL.getInstance().init(appContext)
                    Log.i(TAG, "YoutubeDL engine initialized successfully")
                    isEngineReady = true
                    initError = null
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to initialize YoutubeDL: ${e.message}", e)
                    initError = e.message ?: "Native library initialization failed"
                }

                try {
                    FFmpeg.getInstance().init(appContext)
                    Log.i(TAG, "FFmpeg engine initialized successfully")
                } catch (e: Exception) {
                    Log.w(TAG, "FFmpeg init warning: ${e.message}", e)
                }
            }
        }

        fun ensureEngineReady(context: Context): Boolean {
            if (!isEngineReady) {
                initEngine(context)
            }
            return isEngineReady
        }
    }
}
