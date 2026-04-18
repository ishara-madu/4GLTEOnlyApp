package com.pixeleye.lteonly

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface GamePingDao {
    @Insert
    suspend fun insert(ping: GamePingEntity)

    @Query("SELECT pingMs FROM game_pings WHERE serverIp = :ip ORDER BY timestamp ASC LIMIT 50")
    suspend fun getRecentPings(ip: String): List<Int>

    @Query("DELETE FROM game_pings WHERE serverIp = :ip AND id NOT IN (SELECT id FROM game_pings WHERE serverIp = :ip ORDER BY timestamp DESC LIMIT 50)")
    suspend fun deleteOldPings(ip: String)
}
