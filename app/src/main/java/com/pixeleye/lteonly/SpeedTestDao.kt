package com.pixeleye.lteonly

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedTestDao {
    
    @Insert
    suspend fun insert(speedTest: SpeedTestEntity)
    
    @Query("SELECT * FROM speed_test ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSpeedTests(limit: Int): Flow<List<SpeedTestEntity>>
    
    @Query("SELECT * FROM speed_test ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSpeedTestsSync(limit: Int): List<SpeedTestEntity>
    
    @Query("SELECT * FROM speed_test WHERE timestamp >= :startTime ORDER BY timestamp DESC")
    fun getSpeedTestsSince(startTime: Long): Flow<List<SpeedTestEntity>>
    
    @Query("DELETE FROM speed_test WHERE timestamp < :beforeTime")
    suspend fun deleteOldEntries(beforeTime: Long)
    
    @Query("DELETE FROM speed_test WHERE id NOT IN (SELECT id FROM speed_test ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun deleteExcessEntries(limit: Int)
    
    @Query("DELETE FROM speed_test")
    suspend fun deleteAll()
}
