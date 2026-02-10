package com.github.sysmoon.wholphin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.sysmoon.wholphin.data.model.UserPreferencesEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserPreferencesDao {

    @Query("SELECT * FROM user_preferences WHERE userId = :userId LIMIT 1")
    suspend fun get(userId: Int): UserPreferencesEntity?

    @Query("SELECT * FROM user_preferences WHERE userId = :userId LIMIT 1")
    fun getFlow(userId: Int): Flow<UserPreferencesEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(entity: UserPreferencesEntity)
}
