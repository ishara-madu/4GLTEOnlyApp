package com.pixeleye.lteonly

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SettingsManager private constructor(context: Context) {
    
    val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val _speedTestReminderFlow = MutableStateFlow(prefs.getBoolean(KEY_SPEED_TEST, true))
    val speedTestReminderFlow: StateFlow<Boolean> = _speedTestReminderFlow.asStateFlow()

    fun setSpeedTestReminder(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPEED_TEST, enabled).apply()
        _speedTestReminderFlow.value = enabled
    }

    companion object {
        private const val PREFS_NAME = "app_settings_prefs"
        private const val KEY_SPEED_TEST = "speed_test_reminder"
        
        @Volatile
        private var INSTANCE: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SettingsManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
