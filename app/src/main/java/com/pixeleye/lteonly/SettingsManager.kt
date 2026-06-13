package com.pixeleye.lteonly

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsManager private constructor(private val context: Context) {
    
    val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val _speedTestReminderFlow = MutableStateFlow(true)
    val speedTestReminderFlow: StateFlow<Boolean> = _speedTestReminderFlow.asStateFlow()

    private val _customTargetIpFlow = MutableStateFlow("")
    val customTargetIpFlow: StateFlow<String> = _customTargetIpFlow.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            _speedTestReminderFlow.value = prefs.getBoolean(KEY_SPEED_TEST, true)
            _customTargetIpFlow.value = prefs.getString(KEY_CUSTOM_TARGET_IP, "") ?: ""
        }
    }

    fun setSpeedTestReminder(enabled: Boolean) {
        _speedTestReminderFlow.value = enabled
        CoroutineScope(Dispatchers.IO).launch {
            prefs.edit().putBoolean(KEY_SPEED_TEST, enabled).apply()
        }
    }

    fun setCustomTargetIp(ip: String) {
        _customTargetIpFlow.value = ip
        CoroutineScope(Dispatchers.IO).launch {
            prefs.edit().putString(KEY_CUSTOM_TARGET_IP, ip).apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "app_settings_prefs"
        private const val KEY_SPEED_TEST = "speed_test_reminder"
        private const val KEY_CUSTOM_TARGET_IP = "custom_target_ip"
        
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
