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
    
    private var currentActivity: Activity? = null

    override fun onCreate() {
        super<Application>.onCreate()
        // Initialize AdMob AdManager
        AdManager.initialize(this)

        // Initialize RevenueCat
        Purchases.logLevel = LogLevel.DEBUG
        Purchases.configure(
            PurchasesConfiguration.Builder(this, BuildConfig.REVENUECAT_API_KEY).build()
        )

        // Check entitlement at launch so the pro state is available immediately
        ProStateManager.checkEntitlement()
        
        // Register lifecycle callbacks
        registerActivityLifecycleCallbacks(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        super<DefaultLifecycleObserver>.onStart(owner)
        // Show App Open Ad when app returns to foreground
        currentActivity?.let {
            AdManager.showAppOpenAdIfAvailable(it)
        }
    }

    // ActivityLifecycleCallbacks implementation
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityResumed(activity: Activity) {
        currentActivity = activity
    }
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
}
