package com.ultimateiptv.sports.di

import com.ultimateiptv.sports.data.settings.SportsProviderId
import com.ultimateiptv.sports.data.sports.ApiFootballProvider
import com.ultimateiptv.sports.data.sports.ApiFootballService
import com.ultimateiptv.sports.data.sports.DemoSportsProvider
import com.ultimateiptv.sports.data.sports.FootballDataProvider
import com.ultimateiptv.sports.data.sports.FootballDataService
import com.ultimateiptv.sports.data.sports.SportsProvider
import dagger.MapKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import javax.inject.Singleton

@MapKey
annotation class ProviderKey(val value: SportsProviderId)

@Module
@InstallIn(SingletonComponent::class)
object SportsModule {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true; explicitNulls = false }

    private fun retrofit(base: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(base)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton
    fun apiFootball(client: OkHttpClient): ApiFootballService = retrofit(ApiFootballService.BASE_URL, client).create(ApiFootballService::class.java)

    @Provides @Singleton
    fun footballData(client: OkHttpClient): FootballDataService = retrofit(FootballDataService.BASE_URL, client).create(FootballDataService::class.java)

    @Provides @Singleton
    fun apiFootballProvider(s: ApiFootballService) = ApiFootballProvider(s)

    @Provides @IntoMap @ProviderKey(SportsProviderId.API_FOOTBALL)
    fun af(p: ApiFootballProvider): SportsProvider = p

    @Provides @IntoMap @ProviderKey(SportsProviderId.FOOTBALL_DATA)
    fun fd(s: FootballDataService): SportsProvider = FootballDataProvider(s)

    @Provides @IntoMap @ProviderKey(SportsProviderId.DEMO)
    fun demo(): SportsProvider = DemoSportsProvider()
}
