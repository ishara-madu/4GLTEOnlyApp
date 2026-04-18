package com.pixeleye.lteonly

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_pings")
data class GamePingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverIp: String,
    val pingMs: Int,
    val timestamp: Long
)
