package com.ultimateiptv.sports.ui.screens.matches

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ultimateiptv.sports.R
import com.ultimateiptv.sports.data.db.MatchEntity
import com.ultimateiptv.sports.data.db.MatchStatus
import com.ultimateiptv.sports.data.sports.Competitions
import com.ultimateiptv.sports.data.sports.Teams
import com.ultimateiptv.sports.data.sports.isLive
import com.ultimateiptv.sports.ui.components.ChannelLogo
import com.ultimateiptv.sports.ui.components.FocusCard
import com.ultimateiptv.sports.ui.components.LiveBadge
import com.ultimateiptv.sports.ui.components.Tag
import com.ultimateiptv.sports.ui.components.formatTime
import com.ultimateiptv.sports.ui.components.tvFocusBorder
import com.ultimateiptv.sports.ui.theme.LiveRed
import com.ultimateiptv.sports.ui.theme.Pitch
import java.util.Locale

fun isArabic(): Boolean = Locale.getDefault().language == "ar"

/** Team name in the app language (Arabic names for known clubs). */
fun teamLabel(name: String): String = Teams.displayName(name, isArabic())

/** Localised competition name: catalog translation when known, provider name otherwise. */
fun competitionLabel(m: MatchEntity): String = Competitions.get(m.competitionKey)?.name(isArabic()) ?: m.competitionName

@Composable
fun statusText(m: MatchEntity): String = when (m.status) {
    MatchStatus.LIVE -> m.minute?.let { "$it'" } ?: stringResource(R.string.live)
    MatchStatus.HALFTIME -> stringResource(R.string.match_halftime)
    MatchStatus.FINISHED -> stringResource(R.string.match_finished)
    MatchStatus.POSTPONED -> stringResource(R.string.match_postponed)
    MatchStatus.CANCELLED -> stringResource(R.string.match_cancelled)
    else -> formatTime(m.kickoff)
}

@Composable
fun MatchCard(
    m: MatchEntity,
    hasReminder: Boolean,
    onClick: () -> Unit,
    onReminder: (() -> Unit)?,
    modifier: Modifier = Modifier,
    showCompetition: Boolean = false,
    isDemo: Boolean = false,
) {
    FocusCard(onClick = onClick, modifier = modifier.fillMaxWidth().testTag("match_card")) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            if (showCompetition || isDemo) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showCompetition) {
                        ChannelLogo(m.competitionLogo, m.competitionName, size = 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text(competitionLabel(m), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    } else Spacer(Modifier.weight(1f))
                    if (isDemo) Tag(stringResource(R.string.demo))
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Team(teamLabel(m.homeName), m.homeLogo, Modifier.weight(1f), alignEnd = true)
                Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val showScore = m.homeScore != null && m.status != MatchStatus.SCHEDULED
                    if (showScore) {
                        Text(
                            "${m.homeScore} - ${m.awayScore}",
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                            color = if (m.isLive()) Pitch else MaterialTheme.colorScheme.onSurface,
                        )
                    } else {
                        Text(formatTime(m.kickoff), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    if (m.isLive()) LiveBadge(statusText(m))
                    else if (showScore || m.status != MatchStatus.SCHEDULED) Text(statusText(m), style = MaterialTheme.typography.labelSmall, color = if (m.status == MatchStatus.CANCELLED) LiveRed else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Team(teamLabel(m.awayName), m.awayLogo, Modifier.weight(1f), alignEnd = false)
                if (onReminder != null && m.status == MatchStatus.SCHEDULED && m.kickoff > System.currentTimeMillis()) {
                    IconButton(onClick = onReminder, modifier = Modifier.tvFocusBorder(CircleShape)) {
                        Icon(
                            if (hasReminder) Icons.Filled.Notifications else Icons.Filled.NotificationsNone,
                            contentDescription = stringResource(R.string.match_remind),
                            tint = if (hasReminder) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (onReminder != null) {
                    Spacer(Modifier.width(48.dp))
                }
            }
        }
    }
}

@Composable
private fun Team(name: String, logo: String?, modifier: Modifier, @Suppress("UNUSED_PARAMETER") alignEnd: Boolean) {
    // Logo above the name, centred: long names wrap on word boundaries instead of being cut mid-word.
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        ChannelLogo(logo, name, size = 34.dp, shape = CircleShape)
        Spacer(Modifier.height(4.dp))
        Text(
            name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, minLines = 2,
        )
    }
}
