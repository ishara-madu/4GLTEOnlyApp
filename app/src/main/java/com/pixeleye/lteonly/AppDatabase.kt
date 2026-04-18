package com.pixeleye.lteonly

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SignalHistoryEntity::class,
        DataUsageEntity::class,
        SpeedTestEntity::class,
        PingTestEntity::class,
        GamePingEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun signalHistoryDao(): SignalHistoryDao
    abstract fun dataUsageDao(): DataUsageDao
    abstract fun speedTestDao(): SpeedTestDao
    abstract fun pingTestDao(): PingTestDao
    abstract fun gamePingDao(): GamePingDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lte_only_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
