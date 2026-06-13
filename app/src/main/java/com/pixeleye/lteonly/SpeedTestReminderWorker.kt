package com.pixeleye.lteonly

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class SpeedTestReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val settingsManager = SettingsManager.getInstance(applicationContext)
        // Check if reminder is still enabled
        if (settingsManager.prefs.getBoolean("speed_test_reminder", true)) {
            val lastReminderTime = settingsManager.prefs.getLong("last_reminder_time", 0)
            val currentTime = System.currentTimeMillis()
            // Ensure at least 20 hours have passed since the last reminder to prevent drift from skipping days
            if (currentTime - lastReminderTime >= 20L * 60 * 60 * 1000) {
                NotificationHelper.sendSpeedTestReminder(applicationContext)
                settingsManager.prefs.edit().putLong("last_reminder_time", currentTime).apply()
            }
        }
        return Result.success()
    }
}
