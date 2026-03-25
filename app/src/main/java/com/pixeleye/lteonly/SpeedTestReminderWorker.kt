package com.pixeleye.lteonly

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class SpeedTestReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val settingsManager = SettingsManager.getInstance(applicationContext)
        // Check if reminder is still enabled
        if (settingsManager.prefs.getBoolean("speed_test_reminder", true)) {
            NotificationHelper.sendSpeedTestReminder(applicationContext)
        }
        return Result.success()
    }
}
