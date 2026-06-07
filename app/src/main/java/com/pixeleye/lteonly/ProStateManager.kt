package com.pixeleye.lteonly

import android.content.Context
import android.util.Log
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getCustomerInfoWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

/**
 * Singleton manager for the "Force LTE Only Pro" entitlement state.
 * Exposes a [StateFlow] that the entire app can observe to conditionally
 * show/hide ads, unlock features, or display upgrade prompts.
 * Also supports temporary Pro access for free users by watching Rewarded Ads.
 */
object ProStateManager {

    private const val TAG = "ProStateManager"
    private const val ENTITLEMENT_ID = "Force LTE Only Pro"
    private const val PREFS_NAME = "temp_pro_prefs"
    private const val KEY_PASS_COUNT = "pass_count"
    private const val KEY_LAST_DATE = "last_date"
    private const val KEY_EXPIRY_TIME = "temp_pro_expiry"

    private val _isUserPro = MutableStateFlow(false)
    /** Permanent Pro state from RevenueCat */
    val isPremiumPro: StateFlow<Boolean> = _isUserPro.asStateFlow()

    private val _isTempProActive = MutableStateFlow(false)
    /** Temporary Pro state active after watching a rewarded ad */
    val isTempProActive: StateFlow<Boolean> = _isTempProActive.asStateFlow()

    private val _isProAccessActive = MutableStateFlow(false)
    /** Combined Pro state: true if user is Premium OR Temp Pro is active */
    val isUserPro: StateFlow<Boolean> = _isProAccessActive.asStateFlow()

    private val _remainingPasses = MutableStateFlow(3)
    /** Remaining free Pro preview passes left today (max 3/day) */
    val remainingPasses: StateFlow<Int> = _remainingPasses.asStateFlow()

    private fun updateCombinedState() {
        val active = _isUserPro.value || _isTempProActive.value
        _isProAccessActive.value = active
        if (_isUserPro.value) {
            Log.d(TAG, "Premium Pro active — clearing all cached ads")
            AdManager.clearAllAds()
        }
    }

    fun checkEntitlement() {
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { error ->
                Log.e(TAG, "Error fetching customer info: ${error.message}")
            },
            onSuccess = { customerInfo ->
                val isPro = customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true
                _isUserPro.value = isPro
                Log.d(TAG, "Entitlement check: isPro=$isPro")
                updateCombinedState()
            }
        )
    }

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(KEY_LAST_DATE, "")
        val today = getTodayDateString()

        if (lastDate != today) {
            prefs.edit().putString(KEY_LAST_DATE, today).putInt(KEY_PASS_COUNT, 0).apply()
            _remainingPasses.value = 3
        } else {
            val count = prefs.getInt(KEY_PASS_COUNT, 0)
            _remainingPasses.value = (3 - count).coerceAtLeast(0)
        }

        // Check if temporary pro is still active on launch
        val expiryTime = prefs.getLong(KEY_EXPIRY_TIME, 0)
        if (System.currentTimeMillis() < expiryTime) {
            _isTempProActive.value = true
            updateCombinedState()
        }
    }

    fun activateTempPro(context: Context, durationMinutes: Int = 5) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentCount = prefs.getInt(KEY_PASS_COUNT, 0)
        prefs.edit().putInt(KEY_PASS_COUNT, currentCount + 1).apply()
        _remainingPasses.value = (3 - (currentCount + 1)).coerceAtLeast(0)

        val expiryTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000)
        prefs.edit().putLong(KEY_EXPIRY_TIME, expiryTime).apply()

        _isTempProActive.value = true
        updateCombinedState()
    }

    fun checkTempProExpiry(context: Context) {
        if (!_isTempProActive.value) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val expiryTime = prefs.getLong(KEY_EXPIRY_TIME, 0)
        if (System.currentTimeMillis() >= expiryTime) {
            _isTempProActive.value = false
            updateCombinedState()
            Log.d(TAG, "Temporary Pro has expired")
        }
    }

    private fun getTodayDateString(): String {
        val cal = Calendar.getInstance()
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}-${cal.get(Calendar.DAY_OF_MONTH)}"
    }
}
