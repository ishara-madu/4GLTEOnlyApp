package com.pixeleye.lteonly

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ping_test")
data class PingTestEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val latency: Int,
    val host: String,
    val success: Boolean
)
