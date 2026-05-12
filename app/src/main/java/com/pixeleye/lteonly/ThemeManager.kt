package com.pixeleye.lteonly

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.pixeleye.lteonly.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppTheme {
    LIGHT, DARK, SYSTEM
}

class ThemeManager private constructor(private val context: Context) {
    
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    private val _themeFlow = MutableStateFlow(AppTheme.SYSTEM)
    val themeFlow: StateFlow<AppTheme> = _themeFlow.asStateFlow()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            _themeFlow.value = getSavedTheme()
        }
    }
    
    var currentTheme: AppTheme
        get() = _themeFlow.value
        set(value) {
            _themeFlow.value = value
            CoroutineScope(Dispatchers.IO).launch {
                prefs.edit().putString(KEY_THEME, value.name).apply()
            }
        }
        
    private fun getSavedTheme(): AppTheme {
        val value = prefs.getString(KEY_THEME, AppTheme.SYSTEM.name) ?: AppTheme.SYSTEM.name
        return try {
            AppTheme.valueOf(value)
        } catch (e: Exception) {
            AppTheme.SYSTEM
        }
    }
    
    val isDarkTheme: Boolean
        get() = when (currentTheme) {
            AppTheme.LIGHT -> false
            AppTheme.DARK -> true
            AppTheme.SYSTEM -> false
        }
    
    companion object {
        private const val PREFS_NAME = "theme_prefs"
        private const val KEY_THEME = "app_theme"
        
        @Volatile
        private var INSTANCE: ThemeManager? = null
        
        fun getInstance(context: Context): ThemeManager {
            return INSTANCE ?: synchronized(this) {
                val instance = ThemeManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
