package com.pixeleye.lteonly

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

class LteOnlyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AdManager.initialize(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val signalChannel = NotificationChannel(
                NotificationHelper.CHANNEL_SIGNAL,
                "Network Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for low signal strength"
            }

            val reminderChannel = NotificationChannel(
                NotificationHelper.CHANNEL_REMINDER,
                "Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Periodic speed test reminders"
            }

            val monitorChannel = NotificationChannel(
                NotificationHelper.CHANNEL_MONITOR,
                "Network Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing background signal monitoring"
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(signalChannel)
            notificationManager.createNotificationChannel(reminderChannel)
            notificationManager.createNotificationChannel(monitorChannel)
        }
    }
}
