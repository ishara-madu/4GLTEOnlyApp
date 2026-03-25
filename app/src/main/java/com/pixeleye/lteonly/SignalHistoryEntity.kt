package com.pixeleye.lteonly

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_history")
data class SignalHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val rsrp: Int,
    val rsrq: Int,
    val rssi: Int,
    val sinr: Int,
    val carrierName: String,
    val networkType: String,
    val band: String
)
