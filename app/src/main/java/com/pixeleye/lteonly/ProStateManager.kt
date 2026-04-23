package com.pixeleye.lteonly

import android.util.Log
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getCustomerInfoWith
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for the "Force LTE Only Pro" entitlement state.
 * Exposes a [StateFlow] that the entire app can observe to conditionally
 * show/hide ads, unlock features, or display upgrade prompts.
 */
object ProStateManager {

    private const val TAG = "ProStateManager"
    private const val ENTITLEMENT_ID = "Force LTE Only Pro"

    private val _isUserPro = MutableStateFlow(false)

    /** Observable pro state — true when the user has an active "Force LTE Only Pro" entitlement. */
    val isUserPro: StateFlow<Boolean> = _isUserPro.asStateFlow()

    /**
     * Queries RevenueCat for the latest customer info and updates [isUserPro].
     * Safe to call from anywhere; the SDK handles threading internally.
     */
    fun checkEntitlement() {
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { error ->
                Log.e(TAG, "Error fetching customer info: ${error.message}")
                // Keep the current state on error — don't lock users out
            },
            onSuccess = { customerInfo ->
                val isPro = customerInfo.entitlements[ENTITLEMENT_ID]?.isActive == true
                _isUserPro.value = isPro
                Log.d(TAG, "Entitlement check: isPro=$isPro")
            }
        )
    }
}
