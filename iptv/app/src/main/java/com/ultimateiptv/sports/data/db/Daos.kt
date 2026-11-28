package com.ultimateiptv.sports.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY createdAt")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY createdAt")
    suspend fun getAll(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun get(id: Long): SubscriptionEntity?

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    fun observe(id: Long): Flow<SubscriptionEntity?>

    @Insert
    suspend fun insert(entity: SubscriptionEntity): Long

    @Update
    suspend fun update(entity: SubscriptionEntity)

    @Query("UPDATE subscriptions SET status = :status, lastError = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: SyncStatus, error: String?)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM subscriptions")
    fun observeCount(): Flow<Int>
}

private const val LIST_COLUMNS = """
    c.uid, c.subscriptionId, c.name, c.logo, c.groupTitle, c.epgId, c.number, c.isSports,
    (f.uid IS NOT NULL) AS isFavorite
"""

@Dao
interface ChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE subscriptionId = :subscriptionId AND generation != :generation")
    suspend fun deleteStale(subscriptionId: Long, generation: Long): Int

    @Query("DELETE FROM channels WHERE subscriptionId = :subscriptionId")
    suspend fun deleteForSubscription(subscriptionId: Long)

    @Query("SELECT COUNT(*) FROM channels WHERE subscriptionId = :subscriptionId")
    suspend fun countFor(subscriptionId: Long): Int

    @Query("SELECT * FROM channels WHERE uid = :uid")
    suspend fun get(uid: String): ChannelEntity?

    @Query("SELECT $LIST_COLUMNS FROM channels c LEFT JOIN favorite_channels f ON f.uid = c.uid WHERE c.uid = :uid")
    fun observeItem(uid: String): Flow<ChannelListItem?>

    @Query(
        """SELECT $LIST_COLUMNS FROM channels c LEFT JOIN favorite_channels f ON f.uid = c.uid
        WHERE (:subscriptionId = 0 OR c.subscriptionId = :subscriptionId)
          AND (:group IS NULL OR c.groupTitle = :group)
          AND (:sportsOnly = 0 OR c.isSports = 1)
        ORDER BY c.subscriptionId, c.sortIndex"""
    )
    fun page(subscriptionId: Long, group: String?, sportsOnly: Boolean): PagingSource<Int, ChannelListItem>

    @Query(
        """SELECT c.uid FROM channels c
        WHERE (:subscriptionId = 0 OR c.subscriptionId = :subscriptionId)
          AND (:group IS NULL OR c.groupTitle = :group)
          AND (:sportsOnly = 0 OR c.isSports = 1)
        ORDER BY c.subscriptionId, c.sortIndex LIMIT 20000"""
    )
    suspend fun uids(subscriptionId: Long, group: String?, sportsOnly: Boolean): List<String>

    @Query(
        """SELECT groupTitle, COUNT(*) AS count FROM channels
        WHERE (:subscriptionId = 0 OR subscriptionId = :subscriptionId) AND (:sportsOnly = 0 OR isSports = 1)
        GROUP BY groupTitle ORDER BY MIN(subscriptionId), MIN(sortIndex)"""
    )
    fun observeGroups(subscriptionId: Long, sportsOnly: Boolean): Flow<List<GroupCount>>

    @Query(
        """SELECT $LIST_COLUMNS FROM channels c LEFT JOIN favorite_channels f ON f.uid = c.uid
        WHERE c.isSports = 1 ORDER BY c.subscriptionId, c.sortIndex LIMIT :limit"""
    )
    fun observeSports(limit: Int): Flow<List<ChannelListItem>>

    @Query(
        """SELECT $LIST_COLUMNS FROM channels c LEFT JOIN favorite_channels f ON f.uid = c.uid
        WHERE c.normalizedName LIKE '%' || :query || '%'
        ORDER BY (c.normalizedName LIKE :query || '%') DESC, c.isSports DESC, length(c.normalizedName) LIMIT :limit"""
    )
    suspend fun search(query: String, limit: Int): List<ChannelListItem>

    @Query(
        """SELECT $LIST_COLUMNS FROM channels c INNER JOIN favorite_channels f ON f.uid = c.uid
        ORDER BY f.addedAt DESC"""
    )
    fun observeFavorites(): Flow<List<ChannelListItem>>

    @Query("SELECT f.uid FROM favorite_channels f INNER JOIN channels c ON c.uid = f.uid ORDER BY f.addedAt DESC")
    suspend fun favoriteUids(): List<String>

    @Query(
        """SELECT $LIST_COLUMNS FROM channels c INNER JOIN history h ON h.uid = c.uid
        LEFT JOIN favorite_channels f ON f.uid = c.uid ORDER BY h.lastWatchedAt DESC LIMIT :limit"""
    )
    fun observeHistory(limit: Int): Flow<List<ChannelListItem>>

    @Query("SELECT h.uid FROM history h INNER JOIN channels c ON c.uid = h.uid ORDER BY h.lastWatchedAt DESC LIMIT 500")
    suspend fun historyUids(): List<String>

    @Query("SELECT $LIST_COLUMNS FROM channels c LEFT JOIN favorite_channels f ON f.uid = c.uid WHERE c.uid IN (:uids)")
    suspend fun items(uids: List<String>): List<ChannelListItem>

    @Query("SELECT DISTINCT epgId FROM channels WHERE subscriptionId = :subscriptionId AND epgId IS NOT NULL")
    suspend fun epgIds(subscriptionId: Long): List<String>

    @Query("SELECT uid, normalizedName FROM channels WHERE subscriptionId = :subscriptionId AND epgId IS NULL")
    suspend fun unmatchedForEpg(subscriptionId: Long): List<UidName>

    @Query("UPDATE channels SET epgId = :epgId WHERE uid = :uid")
    suspend fun setEpgId(uid: String, epgId: String)

    @Query("SELECT COUNT(*) FROM channels")
    fun observeTotal(): Flow<Int>

    // Favourites
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addFavorite(f: FavoriteChannelEntity)

    @Query("DELETE FROM favorite_channels WHERE uid = :uid")
    suspend fun removeFavorite(uid: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_channels WHERE uid = :uid)")
    suspend fun isFavorite(uid: String): Boolean

    // History
    @Query("INSERT OR REPLACE INTO history(uid, lastWatchedAt, watchCount) VALUES(:uid, :time, COALESCE((SELECT watchCount FROM history WHERE uid = :uid), 0) + 1)")
    suspend fun touchHistory(uid: String, time: Long)

    @Query("DELETE FROM history")
    suspend fun clearHistory()

    @Query("DELETE FROM history WHERE uid = :uid")
    suspend fun removeHistory(uid: String)
}

data class UidName(val uid: String, val normalizedName: String)

@Dao
interface EpgDao {
    @Insert
    suspend fun insertAll(items: List<EpgProgrammeEntity>)

    @Query("DELETE FROM epg_programmes WHERE subscriptionId = :subscriptionId")
    suspend fun deleteFor(subscriptionId: Long)

    @Query("DELETE FROM epg_programmes WHERE stopMillis < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query(
        """SELECT * FROM epg_programmes WHERE subscriptionId = :subscriptionId AND epgChannelId = :epgId
        AND stopMillis > :now ORDER BY startMillis LIMIT 2"""
    )
    fun observeNowNext(subscriptionId: Long, epgId: String, now: Long): Flow<List<EpgProgrammeEntity>>

    @Query(
        """SELECT * FROM epg_programmes WHERE subscriptionId = :subscriptionId AND epgChannelId = :epgId
        AND stopMillis > :now ORDER BY startMillis LIMIT 2"""
    )
    suspend fun nowNext(subscriptionId: Long, epgId: String, now: Long): List<EpgProgrammeEntity>

    @Query(
        """SELECT * FROM epg_programmes WHERE subscriptionId = :subscriptionId AND epgChannelId = :epgId
        AND stopMillis > :from AND startMillis < :to ORDER BY startMillis"""
    )
    fun observeSchedule(subscriptionId: Long, epgId: String, from: Long, to: Long): Flow<List<EpgProgrammeEntity>>

    @Query(
        """SELECT p.id, p.subscriptionId, p.epgChannelId, p.startMillis, p.stopMillis, p.title, p.description,
           c.uid AS channelUid, c.name AS channelName, c.logo AS channelLogo
        FROM epg_programmes p
        LEFT JOIN channels c ON c.uid = (SELECT uid FROM channels WHERE subscriptionId = p.subscriptionId AND epgId = p.epgChannelId LIMIT 1)
        WHERE p.stopMillis > :from AND p.startMillis < :to AND p.title LIKE '%' || :query || '%'
        ORDER BY p.startMillis LIMIT :limit"""
    )
    suspend fun search(query: String, from: Long, to: Long, limit: Int): List<ProgrammeWithChannel>

    @Query(
        """SELECT p.id, p.subscriptionId, p.epgChannelId, p.startMillis, p.stopMillis, p.title, p.description,
           c.uid AS channelUid, c.name AS channelName, c.logo AS channelLogo
        FROM epg_programmes p
        LEFT JOIN channels c ON c.uid = (SELECT uid FROM channels WHERE subscriptionId = p.subscriptionId AND epgId = p.epgChannelId LIMIT 1)
        WHERE p.stopMillis > :from AND p.startMillis < :to
        ORDER BY p.startMillis LIMIT 2000"""
    )
    suspend fun inWindow(from: Long, to: Long): List<ProgrammeWithChannel>

    @Query("SELECT COUNT(*) FROM epg_programmes WHERE subscriptionId = :subscriptionId")
    suspend fun countFor(subscriptionId: Long): Int
}

@Dao
interface MatchDao {
    @Upsert
    suspend fun upsertAll(items: List<MatchEntity>)

    @Query("SELECT * FROM matches WHERE kickoff >= :from AND kickoff < :to AND provider = :provider ORDER BY kickoff")
    fun observeRange(provider: String, from: Long, to: Long): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE provider = :provider AND competitionKey = :key AND kickoff >= :from AND kickoff < :to ORDER BY kickoff")
    fun observeCompetition(provider: String, key: String, from: Long, to: Long): Flow<List<MatchEntity>>

    @Query("SELECT * FROM matches WHERE id = :id")
    suspend fun get(id: String): MatchEntity?

    @Query("SELECT * FROM matches WHERE id = :id")
    fun observe(id: String): Flow<MatchEntity?>

    @Query("SELECT MIN(fetchedAt) FROM matches WHERE provider = :provider AND kickoff >= :from AND kickoff < :to")
    suspend fun oldestFetch(provider: String, from: Long, to: Long): Long?

    @Query("DELETE FROM matches WHERE provider = :provider AND kickoff >= :from AND kickoff < :to")
    suspend fun deleteRange(provider: String, from: Long, to: Long)

    @Query("DELETE FROM matches WHERE kickoff < :before")
    suspend fun deleteOlderThan(before: Long)

    @Transaction
    suspend fun replaceRange(provider: String, from: Long, to: Long, items: List<MatchEntity>) {
        deleteRange(provider, from, to)
        upsertAll(items)
    }

    @Query("SELECT * FROM matches WHERE provider = :provider AND (homeName LIKE '%' || :q || '%' OR awayName LIKE '%' || :q || '%' OR competitionName LIKE '%' || :q || '%') ORDER BY kickoff DESC LIMIT 50")
    suspend fun search(provider: String, q: String): List<MatchEntity>

    // Favourite teams
    @Query("SELECT * FROM favorite_teams ORDER BY name")
    fun observeFavoriteTeams(): Flow<List<FavoriteTeamEntity>>

    @Query("SELECT * FROM favorite_teams")
    suspend fun favoriteTeams(): List<FavoriteTeamEntity>

    @Upsert
    suspend fun addFavoriteTeam(team: FavoriteTeamEntity)

    @Query("DELETE FROM favorite_teams WHERE `key` = :key")
    suspend fun removeFavoriteTeam(key: String)

    // Reminders
    @Query("SELECT * FROM match_reminders ORDER BY kickoff")
    fun observeReminders(): Flow<List<MatchReminderEntity>>

    @Query("SELECT * FROM match_reminders WHERE kickoff > :now")
    suspend fun futureReminders(now: Long): List<MatchReminderEntity>

    @Query("SELECT * FROM match_reminders WHERE matchId = :id")
    suspend fun reminder(id: String): MatchReminderEntity?

    @Upsert
    suspend fun upsertReminder(r: MatchReminderEntity)

    @Query("DELETE FROM match_reminders WHERE matchId = :id")
    suspend fun deleteReminder(id: String)

    @Query("DELETE FROM match_reminders WHERE kickoff < :before")
    suspend fun deleteOldReminders(before: Long)

    // Broadcaster mapping
    @Query("SELECT channelUid FROM competition_channels WHERE competitionKey = :key")
    fun observeCompetitionChannels(key: String): Flow<List<String>>

    @Query("SELECT channelUid FROM competition_channels WHERE competitionKey = :key")
    suspend fun competitionChannels(key: String): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addCompetitionChannel(e: CompetitionChannelEntity)

    @Query("DELETE FROM competition_channels WHERE competitionKey = :key AND channelUid = :uid")
    suspend fun removeCompetitionChannel(key: String, uid: String)
}
