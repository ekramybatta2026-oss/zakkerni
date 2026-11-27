package com.ultimateiptv.sports.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ultimateiptv.sports.data.iptv.EpgRepository
import com.ultimateiptv.sports.data.iptv.SubscriptionRepository
import com.ultimateiptv.sports.data.settings.SettingsRepository
import com.ultimateiptv.sports.data.sports.MatchRepository
import com.ultimateiptv.sports.notifications.ReminderScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Refreshes subscriptions whose auto-refresh interval elapsed, and their EPG. */
@HiltWorker
class SubscriptionRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val subscriptions: SubscriptionRepository,
    private val epg: EpgRepository,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val only = inputData.getLong(KEY_ID, 0)
        val epgHours = settings.current().epgRefreshHours
        val now = System.currentTimeMillis()
        var failed = false
        subscriptions.observeAll().first().filter { it.enabled && (only == 0L || it.id == only) }.forEach { sub ->
            val due = only != 0L || (sub.autoRefreshHours > 0 &&
                (sub.lastRefreshAt == null || now - sub.lastRefreshAt > TimeUnit.HOURS.toMillis(sub.autoRefreshHours.toLong())))
            if (due) {
                if (subscriptions.refresh(sub.id).isFailure) failed = true
            } else if (epgHours > 0 && (sub.lastEpgRefreshAt == null || now - sub.lastEpgRefreshAt > TimeUnit.HOURS.toMillis(epgHours.toLong()))) {
                epg.refresh(sub, subscriptions.secretsOf(sub))
            }
        }
        return if (failed && runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        const val KEY_ID = "subscription_id"
        private const val PERIODIC = "subscription-refresh"

        fun schedulePeriodic(context: Context) {
            val req = PeriodicWorkRequestBuilder<SubscriptionRefreshWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresStorageNotLow(true).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun refreshNow(context: Context, id: Long) {
            val req = OneTimeWorkRequestBuilder<SubscriptionRefreshWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork("refresh-$id", ExistingWorkPolicy.KEEP, req)
        }
    }
}

/** Syncs fixtures for the next days and creates automatic reminders for favourite teams. */
@HiltWorker
class FavoriteTeamsWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val matches: MatchRepository,
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val s = settings.current()
        if (!s.notificationsEnabled || !s.notifyFavoriteTeams || !s.hasSportsApiKey) return Result.success()
        val favorites = matches.favoriteTeamKeys()
        if (favorites.isEmpty()) return Result.success()
        val today = LocalDate.now()
        val now = System.currentTimeMillis()
        for (d in 0L..2L) {
            val date = today.plusDays(d)
            matches.refreshDay(date)
            val (from, to) = matches.dayBounds(date)
            matches.observeRange(from, to).first()
                .filter { it.kickoff > now && (MatchRepository.teamKey(it.homeName) in favorites || MatchRepository.teamKey(it.awayName) in favorites) }
                .forEach { m -> if (matches.reminder(m.id) == null) scheduler.add(m, automatic = true) }
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<FavoriteTeamsWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("favorite-teams", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
