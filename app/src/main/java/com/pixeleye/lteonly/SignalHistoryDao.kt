package com.pixeleye.lteonly

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SignalHistoryDao {
    
    @Insert
    suspend fun insert(signal: SignalHistoryEntity)
    
    @Query("SELECT * FROM signal_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistory(limit: Int): Flow<List<SignalHistoryEntity>>
    
    @Query("SELECT * FROM signal_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentHistorySync(limit: Int): List<SignalHistoryEntity>
    
    @Query("SELECT * FROM signal_history WHERE timestamp >= :startTime ORDER BY timestamp ASC")
    fun getHistorySince(startTime: Long): Flow<List<SignalHistoryEntity>>
    
    @Query("SELECT * FROM signal_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastEntry(): SignalHistoryEntity?
    
    @Query("DELETE FROM signal_history WHERE timestamp < :beforeTime")
    suspend fun deleteOldEntries(beforeTime: Long)
    
    @Query("DELETE FROM signal_history WHERE id NOT IN (SELECT id FROM signal_history ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun deleteExcessEntries(limit: Int)
    
    @Query("SELECT COUNT(*) FROM signal_history")
    suspend fun getCount(): Int
    
    @Query("DELETE FROM signal_history")
    suspend fun deleteAll()
}
