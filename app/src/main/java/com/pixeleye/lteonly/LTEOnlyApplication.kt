package com.pixeleye.lteonly

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration

/**
 * Main Application class for the LTE Only app.
 * This class is used to initialize global state, provide access to the Room database,
 * manage App Open Ads, and initialize RevenueCat for in-app purchases.
 */
class LTEOnlyApplication : Application(), Application.ActivityLifecycleCallbacks, DefaultLifecycleObserver {
    
    // Lazy initialization of the Room Database instance
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
    
    companion object {
        private var _currentActivity: Activity? = null
        val currentActivity: Activity?
            get() = _currentActivity
    }

    override fun onCreate() {
        super<Application>.onCreate()
        // Initialize AdMob AdManager
        AdManager.initialize(this)

        // Initialize RevenueCat
        Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(
            PurchasesConfiguration.Builder(this, BuildConfig.REVENUECAT_API_KEY).build()
        )

        // Initialize ProStateManager for temporary Pro access
        ProStateManager.initialize(this)

        // Check entitlement at launch so the pro state is available immediately
        ProStateManager.checkEntitlement()

        // Temporarily log Firebase Installation ID for In-App Messaging testing
        com.google.firebase.installations.FirebaseInstallations.getInstance().id.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                android.util.Log.d("FIREBASE_FID", "==================================================")
                android.util.Log.d("FIREBASE_FID", "Your Firebase Installation ID is: ${task.result}")
                android.util.Log.d("FIREBASE_FID", "==================================================")
            } else {
                android.util.Log.e("FIREBASE_FID", "Failed to get Firebase Installation ID", task.exception)
            }
        }
        
        // Register lifecycle callbacks
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    private var isFirstResume = true

    override fun onResume(owner: LifecycleOwner) {
        super<DefaultLifecycleObserver>.onResume(owner)
        // Refresh entitlement status whenever app returns to foreground
        ProStateManager.checkEntitlement()
        ProStateManager.checkTempProExpiry(this)

        // Skip App Open Ad entirely for Premium Pro users
        if (ProStateManager.isPremiumPro.value) return

        // Bypass the first resume on cold start because the Splash screen manages it
        if (isFirstResume) {
            isFirstResume = false
            return
        }

        // Show App Open Ad when app returns to foreground
        currentActivity?.let {
            AdManager.showAppOpenAdIfAvailable(it)
        }
    }

    // ActivityLifecycleCallbacks implementation
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        _currentActivity = activity
    }
    override fun onActivityResumed(activity: Activity) {
        _currentActivity = activity
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (_currentActivity == activity) {
            _currentActivity = null
        }
    }
}
