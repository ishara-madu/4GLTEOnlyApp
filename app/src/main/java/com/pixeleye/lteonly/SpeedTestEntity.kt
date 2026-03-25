package com.pixeleye.lteonly

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speed_test")
data class SpeedTestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val downloadSpeed: Double,
    val uploadSpeed: Double,
    val ping: Int,
    val networkType: String
)
