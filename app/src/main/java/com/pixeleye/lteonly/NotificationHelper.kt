package com.pixeleye.lteonly

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val CHANNEL_SIGNAL = "signal_alerts"
    const val CHANNEL_REMINDER = "speed_test_reminders"
    const val CHANNEL_MONITOR = "service_monitor"
    
    const val NOTIFICATION_ID_SIGNAL = 101
    const val NOTIFICATION_ID_REMINDER = 102
    const val NOTIFICATION_ID_MONITOR = 103

    fun sendLowSignalNotification(context: Context, dbm: Int) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SIGNAL)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Low Signal Warning")
            .setContentText("Your current signal is weak ($dbm dBm). Consider moving to a better area.")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_SIGNAL, notification)
    }

    fun sendSpeedTestReminder(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("target_tab", 2) // Tools Tab
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 1, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Speed Test Reminder")
            .setContentText("It's been a while since your last speed test. Check your network performance now!")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_REMINDER, notification)
    }

    fun createMonitorNotification(context: Context): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 2, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Signal Monitoring Active")
            .setContentText("Running in the background to alert you of low signal.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
