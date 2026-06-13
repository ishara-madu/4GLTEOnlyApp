package com.pixeleye.lteonly

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.InputStreamReader

class PingStabilizerService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    
    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "ping_stabilizer_channel"
        private const val TAG = "PingStabilizerService"
        
        var isRunning = false
            private set
            
        private val _pingHistory = MutableStateFlow<List<Int>>(emptyList())
        val pingHistory: StateFlow<List<Int>> = _pingHistory.asStateFlow()
        
        fun clearHistory() {
            _pingHistory.value = emptyList()
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    @Volatile
    private var currentTargetIp = "1.1.1.1"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }
        
        intent?.getStringExtra("TARGET_IP")?.let {
            currentTargetIp = if (it.isNotBlank()) it else "1.1.1.1"
        }

        val notification = createNotification()
        
        try {
            startForeground(NOTIFICATION_ID, notification)
            if (!isRunning) {
                isRunning = true
                startPingLoop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
        }

        return START_STICKY
    }

    private fun startPingLoop() {
        serviceScope.launch {
            Log.d(TAG, "Ping stabilizer loop started")
            while (isActive) {
                try {
                    // Send a single ICMP echo request to a highly reliable server or custom IP
                    // -c 1: one packet
                    // -W 1: wait 1 second for a reply
                    val process = Runtime.getRuntime().exec("ping -c 1 -W 1 $currentTargetIp")
                    
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    val output = StringBuilder()
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }
                    
                    val exitCode = process.waitFor()
                    if (exitCode == 0) {
                        Log.d(TAG, "Stabilizer Ping SUCCESS")
                        // Parse time from output
                        val timeMatch = Regex("time=([0-9.]+) ms").find(output.toString())
                        val latency = timeMatch?.groups?.get(1)?.value?.toDoubleOrNull()?.toInt() ?: -1
                        if (latency >= 0) {
                            val currentList = _pingHistory.value.toMutableList()
                            currentList.add(latency)
                            if (currentList.size > 100) {
                                currentList.removeAt(0)
                            }
                            _pingHistory.value = currentList
                        }
                    } else {
                        Log.d(TAG, "Stabilizer Ping FAILED")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in ping loop", e)
                }
                
                // Wait 2 seconds before the next ping.
                // This is frequent enough to keep the radio active without consuming significant data.
                delay(2000)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Ping Stabilizer"
            val descriptionText = "Keeps connection active to stabilize gaming ping"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, PingStabilizerService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent: PendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Ping Stabilizer Active")
            .setContentText("Keeping network alive for better gaming")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Ping stabilizer service destroyed")
        isRunning = false
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
