package com.github.sysmoon.wholphin.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores per-user UI/UX preferences as a serialized [UserPreferencesProto] blob.
 * Keyed by [userId] (JellyfinUser.rowId).
 */
@Entity(
    tableName = "user_preferences",
    foreignKeys = [
        ForeignKey(
            entity = JellyfinUser::class,
            parentColumns = arrayOf("rowId"),
            childColumns = arrayOf("userId"),
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("userId", unique = true)],
)
data class UserPreferencesEntity(
    @PrimaryKey
    val userId: Int,
    val preferencesBlob: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UserPreferencesEntity
        if (userId != other.userId) return false
        if (!preferencesBlob.contentEquals(other.preferencesBlob)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = userId
        result = 31 * result + preferencesBlob.contentHashCode()
        return result
    }
}
