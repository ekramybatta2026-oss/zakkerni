package com.ultimateiptv.sports.di

import android.content.Context
import androidx.room.Room
import com.ultimateiptv.sports.BuildConfig
import com.ultimateiptv.sports.data.db.AppDatabase
import com.ultimateiptv.sports.data.db.ChannelDao
import com.ultimateiptv.sports.data.db.EpgDao
import com.ultimateiptv.sports.data.db.MatchDao
import com.ultimateiptv.sports.data.db.SubscriptionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "ultimate_iptv.db")
            .fallbackToDestructiveMigrationOnDowngrade(true)
            .build()

    @Provides fun subscriptionDao(db: AppDatabase): SubscriptionDao = db.subscriptionDao()
    @Provides fun channelDao(db: AppDatabase): ChannelDao = db.channelDao()
    @Provides fun epgDao(db: AppDatabase): EpgDao = db.epgDao()
    @Provides fun matchDao(db: AppDatabase): MatchDao = db.matchDao()

    /**
     * One shared client: playlist downloads, EPG, sports APIs and the video player reuse the
     * same connection pool, which noticeably speeds up channel zapping on the same host.
     */
    @Provides
    @Singleton
    fun okHttp(@ApplicationContext context: Context): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC never logs headers or bodies, so credentials in query strings only show in debug builds.
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
            redactHeader("X-Auth-Token")
            redactHeader("x-apisports-key")
        }
        return OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .dispatcher(Dispatcher().apply { maxRequestsPerHost = 8 })
            .cache(Cache(File(context.cacheDir, "http"), 20L * 1024 * 1024))
            .addInterceptor(logging)
            .build()
    }
}
