package com.ultimateiptv.sports

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.SvgDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.ultimateiptv.sports.notifications.Notifier
import com.ultimateiptv.sports.notifications.ReminderScheduler
import com.ultimateiptv.sports.work.FavoriteTeamsWorker
import com.ultimateiptv.sports.work.SubscriptionRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var okHttp: OkHttpClient
    @Inject lateinit var reminders: ReminderScheduler

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        SubscriptionRefreshWorker.schedulePeriodic(this)
        FavoriteTeamsWorker.schedule(this)
        appScope.launch { runCatching { reminders.rescheduleAll() } }
    }

    /** Coil shares the app OkHttp pool; SVG support is needed for football-data.org crests. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient(okHttp)
        .components { add(SvgDecoder.Factory()) }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.15).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(80L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}
