package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandDao {
    @Query("SELECT * FROM saved_commands ORDER BY isFavorite DESC, createdAtMs DESC")
    fun observeSavedCommands(): Flow<List<SavedCommandEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedCommand(command: SavedCommandEntity)

    @Update
    suspend fun updateSavedCommand(command: SavedCommandEntity)

    @Query("DELETE FROM saved_commands WHERE id = :id")
    suspend fun deleteSavedCommandById(id: Int)

    @Query("UPDATE saved_commands SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Int, favorite: Boolean)

    @Query("SELECT * FROM command_logs ORDER BY timestampMs DESC LIMIT 50")
    fun observeRecentLogs(): Flow<List<CommandLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: CommandLogEntity)

    @Query("DELETE FROM command_logs")
    suspend fun clearLogs()
}
