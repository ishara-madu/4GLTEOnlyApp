package com.pixeleye.lteonly

import android.content.Context
import android.util.Log
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getCustomerInfoWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for the "Force LTE Only Pro" entitlement state (Ad-Free purchase).
 * Exposes a [StateFlow] that the entire app can observe to conditionally
 * show/hide ads or display ad-removal upgrade prompts.
 */
object ProStateManager {

    private const val TAG = "ProStateManager"
    private const val ENTITLEMENT_ID = "Force LTE Only Pro"

    private val _isUserPro = MutableStateFlow(false)
    /** Pro / Ad-Free entitlement state from RevenueCat */
    val isPremiumPro: StateFlow<Boolean> = _isUserPro.asStateFlow()
    val isUserPro: StateFlow<Boolean> = _isUserPro.asStateFlow()

    fun checkEntitlement() {
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { error ->
                Log.e(TAG, "Error fetching customer info: ${error.message}")
            },
            onSuccess = { customerInfo ->
                val isPro = customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true
                _isUserPro.value = isPro
                Log.d(TAG, "Entitlement check: isPro=$isPro")
                if (isPro) {
                    Log.d(TAG, "Premium Pro active — clearing all cached ads")
                    AdManager.clearAllAds()
                }
            }
        )
    }

    fun initialize(context: Context) {
        // No-op or initial setup if needed
    }
}

