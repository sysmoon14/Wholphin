package com.github.sysmoon.wholphin.data

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.github.sysmoon.wholphin.data.model.GetItemsFilter
import com.github.sysmoon.wholphin.data.model.ItemPlayback
import com.github.sysmoon.wholphin.data.model.ItemTrackModification
import com.github.sysmoon.wholphin.data.model.JellyfinServer
import com.github.sysmoon.wholphin.data.model.JellyfinUser
import com.github.sysmoon.wholphin.data.model.LibraryDisplayInfo
import com.github.sysmoon.wholphin.data.model.NavDrawerPinnedItem
import com.github.sysmoon.wholphin.data.model.PlaybackLanguageChoice
import com.github.sysmoon.wholphin.data.model.SeerrServer
import com.github.sysmoon.wholphin.data.model.SeerrUser
import com.github.sysmoon.wholphin.data.model.UserPreferencesEntity
import com.github.sysmoon.wholphin.ui.components.ViewOptions
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber
import java.util.UUID

@Database(
    entities = [
        JellyfinServer::class,
        JellyfinUser::class,
        ItemPlayback::class,
        NavDrawerPinnedItem::class,
        LibraryDisplayInfo::class,
        PlaybackLanguageChoice::class,
        ItemTrackModification::class,
        SeerrServer::class,
        SeerrUser::class,
        UserPreferencesEntity::class,
    ],
    version = 22,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(3, 4),
        AutoMigration(4, 5),
        AutoMigration(5, 6),
        AutoMigration(6, 7),
        AutoMigration(7, 8),
        AutoMigration(8, 9),
        AutoMigration(9, 10),
        AutoMigration(10, 11),
        AutoMigration(11, 12),
        AutoMigration(12, 20),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): JellyfinServerDao

    abstract fun itemPlaybackDao(): ItemPlaybackDao

    abstract fun serverPreferencesDao(): ServerPreferencesDao

    abstract fun libraryDisplayInfoDao(): LibraryDisplayInfoDao

    abstract fun playbackLanguageChoiceDao(): PlaybackLanguageChoiceDao

    abstract fun seerrServerDao(): SeerrServerDao

    abstract fun userPreferencesDao(): UserPreferencesDao
}

class Converters {
    @TypeConverter
    fun convertToString(id: UUID): String = id.toString().replace("-", "")

    @TypeConverter
    fun convertToUUID(str: String): UUID = str.toUUID()

    @TypeConverter
    fun convertItemSortBy(sort: ItemSortBy): String = sort.serialName

    @TypeConverter
    fun convertItemSortBy(sort: String): ItemSortBy? = ItemSortBy.fromNameOrNull(sort)

    @TypeConverter
    fun convertSortOrder(sort: SortOrder): String = sort.serialName

    @TypeConverter
    fun convertSortOrder(sort: String): SortOrder? = SortOrder.fromNameOrNull(sort)

    @TypeConverter
    fun convertGetItemsFilter(filter: GetItemsFilter): String = Json.encodeToString(filter)

    @TypeConverter
    fun convertGetItemsFilter(filter: String): GetItemsFilter =
        try {
            Json.decodeFromString(filter)
        } catch (ex: Exception) {
            Timber.e(ex, "Error parsing filter")
            GetItemsFilter()
        }

    @TypeConverter
    fun convertViewOptions(viewOptions: ViewOptions?): String? = viewOptions?.let { Json.encodeToString(viewOptions) }

    @TypeConverter
    fun convertViewOptions(viewOptions: String?): ViewOptions? =
        try {
            viewOptions?.let { Json.decodeFromString(viewOptions) }
        } catch (ex: Exception) {
            Timber.e(ex, "Error parsing view options")
            null
        }
}

object Migrations {
    val Migrate2to3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users RENAME TO users_old")
                db.execSQL(
                    """
                    CREATE TABLE "users" (
                      rowId integer PRIMARY KEY AUTOINCREMENT NOT NULL,
                      id text NOT NULL,
                      name text,
                      serverId text NOT NULL,
                      accessToken text,
                      FOREIGN KEY (serverId) REFERENCES "servers" (id) ON DELETE CASCADE ON UPDATE NO ACTION
                    )
                    """.trimIndent(),
                )
                db.execSQL("UPDATE servers SET id = REPLACE(id, '-', '')")
                db.execSQL(
                    """
                    INSERT INTO users (id, name, serverId, accessToken)
                        SELECT REPLACE(id, '-', ''), name, REPLACE(serverId, '-', ''), accessToken
                        FROM users_old
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE users_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_users_serverId ON users (serverId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_users_id ON users (id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_id_serverId ON users (id, serverId)")
            }
        }

    val Migrate20to21 =
        object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Ensure seerr_servers table exists with correct structure
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `seerr_servers` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `url` TEXT NOT NULL,
                        `name` TEXT,
                        `version` TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_seerr_servers_url` ON `seerr_servers` (`url`)
                    """.trimIndent(),
                )

                // Try to preserve data from seerr_users if it exists with compatible structure
                try {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `seerr_users_backup` (
                            `jellyfinUserRowId` INTEGER NOT NULL,
                            `serverId` INTEGER NOT NULL,
                            `authMethod` TEXT NOT NULL,
                            `username` TEXT,
                            `password` TEXT,
                            `credential` TEXT
                        )
                        """.trimIndent(),
                    )
                    db.execSQL(
                        """
                        INSERT INTO `seerr_users_backup` 
                        SELECT `jellyfinUserRowId`, `serverId`, `authMethod`, `username`, `password`, `credential`
                        FROM `seerr_users`
                        """.trimIndent(),
                    )
                } catch (e: Exception) {
                    // Table doesn't exist or structure is incompatible - that's okay, we'll start fresh
                    Timber.d(e, "Could not backup seerr_users data (table may not exist or structure incompatible)")
                }

                // Drop and recreate seerr_users table with correct structure
                db.execSQL("DROP TABLE IF EXISTS `seerr_users`")
                db.execSQL(
                    """
                    CREATE TABLE `seerr_users` (
                        `jellyfinUserRowId` INTEGER NOT NULL,
                        `serverId` INTEGER NOT NULL,
                        `authMethod` TEXT NOT NULL,
                        `username` TEXT,
                        `password` TEXT,
                        `credential` TEXT,
                        PRIMARY KEY(`jellyfinUserRowId`, `serverId`),
                        FOREIGN KEY(`serverId`) REFERENCES `seerr_servers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`jellyfinUserRowId`) REFERENCES `users`(`rowId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )

                // Restore data if we backed it up
                try {
                    db.execSQL(
                        """
                        INSERT INTO `seerr_users` 
                        SELECT `jellyfinUserRowId`, `serverId`, `authMethod`, `username`, `password`, `credential`
                        FROM `seerr_users_backup`
                        """.trimIndent(),
                    )
                } catch (e: Exception) {
                    Timber.d(e, "Could not restore seerr_users data (backup may be empty or incompatible)")
                }

                db.execSQL("DROP TABLE IF EXISTS `seerr_users_backup`")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_seerr_users_serverId` ON `seerr_users` (`serverId`)
                    """.trimIndent(),
                )
            }
        }

    val Migrate21to22 =
        object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `user_preferences` (
                        `userId` INTEGER NOT NULL PRIMARY KEY,
                        `preferencesBlob` BLOB NOT NULL,
                        FOREIGN KEY(`userId`) REFERENCES `users`(`rowId`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_user_preferences_userId` ON `user_preferences` (`userId`)
                    """.trimIndent(),
                )
            }
        }
}
