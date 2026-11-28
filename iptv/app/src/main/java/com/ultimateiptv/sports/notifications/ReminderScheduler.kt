package com.ultimateiptv.sports.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.getSystemService
import com.ultimateiptv.sports.data.db.MatchEntity
import com.ultimateiptv.sports.data.db.MatchReminderEntity
import com.ultimateiptv.sports.data.settings.SettingsRepository
import com.ultimateiptv.sports.data.sports.MatchRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Schedules "starts in N minutes" and "kick-off" alarms for match reminders. */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val matches: MatchRepository,
    private val settings: SettingsRepository,
) {
    private val alarm: AlarmManager? get() = context.getSystemService()

    suspend fun add(match: MatchEntity, automatic: Boolean = false) {
        val s = settings.current()
        val r = MatchReminderEntity(
            matchId = match.id,
            kickoff = match.kickoff,
            title = "${match.homeName} – ${match.awayName}",
            competition = match.competitionName,
            minutesBefore = s.reminderMinutes,
            automatic = automatic,
        )
        matches.saveReminder(r)
        schedule(r, s.notifyKickoff)
    }

    suspend fun remove(matchId: String) {
        matches.reminder(matchId)?.let { cancel(it) }
        matches.deleteReminder(matchId)
    }

    suspend fun toggle(match: MatchEntity): Boolean {
        val exists = matches.reminder(match.id) != null
        if (exists) remove(match.id) else add(match)
        return !exists
    }

    suspend fun rescheduleAll() {
        matches.cleanupReminders()
        val kickoff = settings.current().notifyKickoff
        matches.futureReminders().forEach { schedule(it, kickoff) }
    }

    fun schedule(r: MatchReminderEntity, notifyKickoff: Boolean) {
        val now = System.currentTimeMillis()
        val before = r.kickoff - TimeUnit.MINUTES.toMillis(r.minutesBefore.toLong())
        if (before > now) set(before, pending(r.matchId, ReminderReceiver.KIND_BEFORE))
        if (notifyKickoff && r.kickoff > now) set(r.kickoff, pending(r.matchId, ReminderReceiver.KIND_KICKOFF))
    }

    private fun cancel(r: MatchReminderEntity) {
        alarm?.cancel(pending(r.matchId, ReminderReceiver.KIND_BEFORE))
        alarm?.cancel(pending(r.matchId, ReminderReceiver.KIND_KICKOFF))
    }

    private fun set(at: Long, pi: PendingIntent) {
        val am = alarm ?: return
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        if (exactAllowed) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            // Without the exact-alarm permission Android may delay the alarm by a few minutes.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(matchId: String, kind: Int): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION
            putExtra(ReminderReceiver.EXTRA_MATCH_ID, matchId)
            putExtra(ReminderReceiver.EXTRA_KIND, kind)
        }
        return PendingIntent.getBroadcast(context, matchId.hashCode() * 2 + kind, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
