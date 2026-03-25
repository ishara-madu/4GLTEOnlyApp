package com.pixeleye.lteonly

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PingTestDao {
    
    @Insert
    suspend fun insert(pingTest: PingTestEntity)
    
    @Query("SELECT * FROM ping_test ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentPingTests(limit: Int): Flow<List<PingTestEntity>>
    
    @Query("SELECT * FROM ping_test ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentPingTestsSync(limit: Int): List<PingTestEntity>
    
    @Query("SELECT * FROM ping_test WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    fun getPingTestsSince(startTime: Long): Flow<List<PingTestEntity>>
    
    @Query("DELETE FROM ping_test WHERE timestamp < :beforeTime")
    suspend fun deleteOldEntries(beforeTime: Long)
    
    @Query("DELETE FROM ping_test WHERE id NOT IN (SELECT id FROM ping_test ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun deleteExcessEntries(limit: Int)
    
    @Query("DELETE FROM ping_test")
    suspend fun deleteAll()
}
