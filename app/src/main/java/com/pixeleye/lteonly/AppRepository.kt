package com.pixeleye.lteonly

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppRepositoryConfig {
    const val TAG = "AppRepository"
}

class AppRepository(private val context: Context) {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
    private val GENERAL_LIMIT = 20
    private val SPEED_TEST_LIMIT = 15
    
    private val _recentSignalHistory = MutableStateFlow<List<SignalHistoryEntity>>(emptyList())
    val recentSignalHistory: StateFlow<List<SignalHistoryEntity>> = _recentSignalHistory.asStateFlow()
    
    private val _recentDataUsage = MutableStateFlow<List<DataUsageEntity>>(emptyList())
    val recentDataUsage: StateFlow<List<DataUsageEntity>> = _recentDataUsage.asStateFlow()
    
    private val _recentSpeedTests = MutableStateFlow<List<SpeedTestEntity>>(emptyList())
    val recentSpeedTests: StateFlow<List<SpeedTestEntity>> = _recentSpeedTests.asStateFlow()
    
    private val _recentPingTests = MutableStateFlow<List<PingTestEntity>>(emptyList())
    val recentPingTests: StateFlow<List<PingTestEntity>> = _recentPingTests.asStateFlow()
    
    init {
        scope.launch {
            refreshAllData()
        }
    }
    
    private suspend fun refreshAllData() {
        try {
            val database = AppDatabase.getDatabase(context)
            cleanupOldEntries(database)
            
            _recentSignalHistory.value = database.signalHistoryDao().getRecentHistorySync(500)
            _recentDataUsage.value = database.dataUsageDao().getRecentDataUsageSync(100)
            _recentSpeedTests.value = database.speedTestDao().getRecentSpeedTestsSync(50)
            _recentPingTests.value = database.pingTestDao().getRecentPingTestsSync(100)
            
            startCollecting()
        } catch (e: Exception) {
            Log.e(AppRepositoryConfig.TAG, "refreshAllData failed: ${e.message}", e)
        }
    }
    
    private suspend fun startCollecting() {
        val database = AppDatabase.getDatabase(context)
        
        scope.launch {
            database.signalHistoryDao().getRecentHistory(500).collect {
                _recentSignalHistory.value = it
            }
        }
        scope.launch {
            database.dataUsageDao().getRecentDataUsage(100).collect {
                _recentDataUsage.value = it
            }
        }
        scope.launch {
            database.speedTestDao().getRecentSpeedTests(50).collect {
                _recentSpeedTests.value = it
            }
        }
        scope.launch {
            database.pingTestDao().getRecentPingTests(100).collect {
                _recentPingTests.value = it
            }
        }
    }
    
    private fun cleanupOldEntries(database: AppDatabase) {
        scope.launch {
            try {
                // Time-based cleanup (7 days)
                database.signalHistoryDao().deleteOldEntries(sevenDaysAgo)
                database.dataUsageDao().deleteOldEntries(sevenDaysAgo)
                database.speedTestDao().deleteOldEntries(sevenDaysAgo)
                database.pingTestDao().deleteOldEntries(sevenDaysAgo)
                
                // Count-based cleanup
                database.signalHistoryDao().deleteExcessEntries(GENERAL_LIMIT)
                database.dataUsageDao().deleteExcessEntries(GENERAL_LIMIT)
                database.pingTestDao().deleteExcessEntries(GENERAL_LIMIT)
                database.speedTestDao().deleteExcessEntries(SPEED_TEST_LIMIT)
            } catch (e: Exception) {
                Log.e(AppRepositoryConfig.TAG, "cleanupOldEntries failed: ${e.message}", e)
            }
        }
    }
    
    fun getSignalHistorySince(startTime: Long) = _recentSignalHistory
    
    suspend fun saveSignalData(
        rsrp: Int,
        rsrq: Int,
        rssi: Int,
        sinr: Int,
        carrierName: String,
        networkType: String,
        band: String
    ) = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(context)
            val entity = SignalHistoryEntity(
                timestamp = System.currentTimeMillis(),
                rsrp = rsrp,
                rsrq = rsrq,
                rssi = rssi,
                sinr = sinr,
                carrierName = carrierName,
                networkType = networkType,
                band = band
            )
            database.signalHistoryDao().insert(entity)
            database.signalHistoryDao().deleteOldEntries(sevenDaysAgo)
            database.signalHistoryDao().deleteExcessEntries(GENERAL_LIMIT)
        } catch (e: Exception) {
            Log.e(AppRepositoryConfig.TAG, "saveSignalData failed: ${e.message}", e)
        }
    }
    
    suspend fun saveDataUsage(mobileBytes: Long, wifiBytes: Long) = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(context)
            val now = System.currentTimeMillis()
            val date = dateFormat.format(Date(now))
            val entity = DataUsageEntity(
                timestamp = now,
                mobileBytes = mobileBytes,
                wifiBytes = wifiBytes,
                date = date
            )
            database.dataUsageDao().insert(entity)
            database.dataUsageDao().deleteOldEntries(sevenDaysAgo)
            database.dataUsageDao().deleteExcessEntries(GENERAL_LIMIT)
        } catch (e: Exception) {
            Log.e(AppRepositoryConfig.TAG, "saveDataUsage failed: ${e.message}", e)
        }
    }
    
    suspend fun saveSpeedTest(downloadSpeed: Double, uploadSpeed: Double, ping: Int, networkType: String) = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(context)
            val entity = SpeedTestEntity(
                timestamp = System.currentTimeMillis(),
                downloadSpeed = downloadSpeed,
                uploadSpeed = uploadSpeed,
                ping = ping,
                networkType = networkType
            )
            database.speedTestDao().insert(entity)
            database.speedTestDao().deleteOldEntries(sevenDaysAgo)
            database.speedTestDao().deleteExcessEntries(SPEED_TEST_LIMIT)
        } catch (e: Exception) {
            Log.e(AppRepositoryConfig.TAG, "saveSpeedTest failed: ${e.message}", e)
        }
    }
    
    suspend fun savePingTest(latency: Int, host: String, success: Boolean) = withContext(Dispatchers.IO) {
        try {
            val database = AppDatabase.getDatabase(context)
            val entity = PingTestEntity(
                timestamp = System.currentTimeMillis(),
                latency = latency,
                host = host,
                success = success
            )
            database.pingTestDao().insert(entity)
            database.pingTestDao().deleteOldEntries(sevenDaysAgo)
            database.pingTestDao().deleteExcessEntries(GENERAL_LIMIT)
        } catch (e: Exception) {
            Log.e(AppRepositoryConfig.TAG, "savePingTest failed: ${e.message}", e)
        }
    }
    
        suspend fun clearAllSignalHistory() = withContext(Dispatchers.IO) {
        try {
            AppDatabase.getDatabase(context).signalHistoryDao().deleteAll()
        } catch (e: Exception) {
            Log.e(AppRepositoryConfig.TAG, "clearAllSignalHistory failed: ${e.message}", e)
        }
    }
    
    suspend fun clearAllDataUsage() = withContext(Dispatchers.IO) {
        try {
            AppDatabase.getDatabase(context).dataUsageDao().deleteAll()
        } catch (e: Exception) {
            Log.e(AppRepositoryConfig.TAG, "clearAllDataUsage failed: ${e.message}", e)
        }
    }
    
    suspend fun clearAllSpeedTests() = withContext(Dispatchers.IO) {
        try {
            AppDatabase.getDatabase(context).speedTestDao().deleteAll()
        } catch (e: Exception) {
            Log.e(AppRepositoryConfig.TAG, "clearAllSpeedTests failed: ${e.message}", e)
        }
    }
    
    suspend fun clearAllPingTests() = withContext(Dispatchers.IO) {
        try {
            AppDatabase.getDatabase(context).pingTestDao().deleteAll()
        } catch (e: Exception) {
            Log.e(AppRepositoryConfig.TAG, "clearAllPingTests failed: ${e.message}", e)
        }
    }
    
    suspend fun clearAllData() {
        clearAllSignalHistory()
        clearAllDataUsage()
        clearAllSpeedTests()
        clearAllPingTests()
    }
}
