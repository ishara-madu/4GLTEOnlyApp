package com.pixeleye.lteonly

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DataUsageDao {
    
    @Insert
    suspend fun insert(dataUsage: DataUsageEntity)
    
    @Query("SELECT * FROM data_usage ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentDataUsage(limit: Int): Flow<List<DataUsageEntity>>
    
    @Query("SELECT * FROM data_usage ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentDataUsageSync(limit: Int): List<DataUsageEntity>
    
    @Query("SELECT * FROM data_usage WHERE date = :date")
    fun getDataUsageByDate(date: String): Flow<List<DataUsageEntity>>
    
    @Query("DELETE FROM data_usage WHERE timestamp < :beforeTime")
    suspend fun deleteOldEntries(beforeTime: Long)
    
    @Query("DELETE FROM data_usage WHERE id NOT IN (SELECT id FROM data_usage ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun deleteExcessEntries(limit: Int)
    
    @Query("DELETE FROM data_usage")
    suspend fun deleteAll()
}
