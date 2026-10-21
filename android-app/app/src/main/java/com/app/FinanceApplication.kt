package com.app

import android.app.Application
import com.app.data.local.TokenManager
import com.app.data.remote.websocket.WebSocketManager
import com.app.utils.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FinanceApplication : Application(), ImageLoaderFactory {

    @Inject lateinit var tokenManager: TokenManager
    @Inject lateinit var webSocketManager: WebSocketManager
    @Inject lateinit var notificationHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                if (tokenManager.isLoggedIn) {
                    webSocketManager.connect()
                }
                com.app.data.sync.SyncWorkScheduler.schedulePeriodicSync(this@FinanceApplication)
                com.app.data.watchdog.WatchdogWorkScheduler.schedulePeriodicWatchdog(this@FinanceApplication)
            } catch (e: Exception) {
                android.util.Log.e("FinanceApplication", "Background initialization failed", e)
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
