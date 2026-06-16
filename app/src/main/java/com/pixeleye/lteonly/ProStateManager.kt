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

    private val _isUserPro = MutableStateFlow(false)
    /** Permanent Pro state from RevenueCat */
    val isPremiumPro: StateFlow<Boolean> = _isUserPro.asStateFlow()

    private val _isTempProActive = MutableStateFlow(false)
    /** Temporary Pro state active after watching a rewarded ad */
    val isTempProActive: StateFlow<Boolean> = _isTempProActive.asStateFlow()

    private val _isProAccessActive = MutableStateFlow(false)
    /** Combined Pro state: true if user is Premium OR Temp Pro is active */
    val isUserPro: StateFlow<Boolean> = _isProAccessActive.asStateFlow()

    // Remaining passes removed (infinite passes per session)

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
        // Removed 3-pass daily limit and 5-minute expiry logic
        // Session-based unlock: isTempProActive is false on cold start
        _isTempProActive.value = false
        updateCombinedState()
    }

    fun activateTempPro(context: Context) {
        // Unlock Pro features for the current session
        _isTempProActive.value = true
        updateCombinedState()
        Log.d(TAG, "Session-based Temp Pro activated via Rewarded Ad")
    }

    fun checkTempProExpiry(context: Context) {
        // No-op: Temp Pro lasts for the entire app session
    }
}
