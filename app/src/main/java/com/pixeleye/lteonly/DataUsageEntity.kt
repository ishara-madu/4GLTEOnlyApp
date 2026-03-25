package com.pixeleye.lteonly

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "data_usage")
data class DataUsageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val mobileBytes: Long,
    val wifiBytes: Long,
    val date: String
)
