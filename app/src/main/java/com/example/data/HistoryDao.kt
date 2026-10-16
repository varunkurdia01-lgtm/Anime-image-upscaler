package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history_table ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(history: HistoryEntity)

    @Update
    suspend fun update(history: HistoryEntity)
    
    @Query("DELETE FROM history_table")
    suspend fun clearHistory()

    @Query("DELETE FROM history_table WHERE id = :id")
    suspend fun deleteById(id: Int)
}
