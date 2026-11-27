package com.ultimateiptv.sports.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun subType(v: SubscriptionType): String = v.name
    @TypeConverter fun subType(v: String): SubscriptionType = SubscriptionType.valueOf(v)
    @TypeConverter fun sync(v: SyncStatus): String = v.name
    @TypeConverter fun sync(v: String): SyncStatus = SyncStatus.valueOf(v)
    @TypeConverter fun match(v: MatchStatus): String = v.name
    @TypeConverter fun match(v: String): MatchStatus = runCatching { MatchStatus.valueOf(v) }.getOrDefault(MatchStatus.UNKNOWN)
}

@Database(
    entities = [
        SubscriptionEntity::class, ChannelEntity::class, FavoriteChannelEntity::class, HistoryEntity::class,
        EpgProgrammeEntity::class, MatchEntity::class, FavoriteTeamEntity::class, MatchReminderEntity::class,
        CompetitionChannelEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun channelDao(): ChannelDao
    abstract fun epgDao(): EpgDao
    abstract fun matchDao(): MatchDao
}
