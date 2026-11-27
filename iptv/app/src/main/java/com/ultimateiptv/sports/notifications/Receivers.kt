package com.ultimateiptv.sports.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.data.settings.SettingsRepository
import com.ultimateiptv.sports.data.sports.MatchRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var matches: MatchRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifier: Notifier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val id = intent.getStringExtra(EXTRA_MATCH_ID) ?: return
        val kind = intent.getIntExtra(EXTRA_KIND, KIND_BEFORE)
        val pending = goAsync()
        receiverScope.launch {
            try {
                if (!settings.current().notificationsEnabled) return@launch
                val r = matches.reminder(id) ?: return@launch
                val text = if (kind == KIND_BEFORE) context.getString(R.string.notif_starts_in, r.minutesBefore, r.competition)
                else context.getString(R.string.notif_kickoff, r.competition)
                notifier.showMatch(id, r.title, text)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.ultimateiptv.sports.MATCH_REMINDER"
        const val EXTRA_MATCH_ID = "match_id"
        const val EXTRA_KIND = "kind"
        const val KIND_BEFORE = 0
        const val KIND_KICKOFF = 1
    }
}

/** Alarms are cleared on reboot / app update; restore them. */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: ReminderScheduler

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> {
                val pending = goAsync()
                receiverScope.launch {
                    try { scheduler.rescheduleAll() } finally { pending.finish() }
                }
            }
        }
    }
}
