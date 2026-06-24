package com.pixeleye.lteonly

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Date
 
object AdManager {
    private const val TAG = "AdManager"
    
    // Ad Unit IDs from BuildConfig
    private val REWARDED_AD_UNIT_ID = BuildConfig.ADMOB_REWARDED_ID
    private val INTERSTITIAL_AD_UNIT_ID = BuildConfig.ADMOB_INTERSTITIAL_ID
    val APP_OPEN_AD_UNIT_ID = BuildConfig.ADMOB_APP_OPEN_ID
    val BANNER_AD_UNIT_ID = BuildConfig.ADMOB_BANNER_ID
 
    private var rewardedAd: RewardedAd? = null
    private var isRewardedAdLoading = false
    
    private var interstitialAd: InterstitialAd? = null
    private var isInterstitialAdLoading = false
 
    private var appOpenAd: AppOpenAd? = null
    private var isAppOpenAdLoading = false
    private var isShowingAppOpenAd = false
    private var appOpenAdLoadTime: Long = 0
    private var ignoreNextAppOpenAd = false
    private var lastAppOpenAdShowTime: Long = 0
    private var lastInterstitialAdShowTime: Long = 0
    
    var force4GClickCount = 0
 
    private var isInitialized = false
    private var appContext: Context? = null
    
    private val _isConsentFlowComplete = MutableStateFlow(false)
    val isConsentFlowComplete: StateFlow<Boolean> = _isConsentFlowComplete.asStateFlow()
 
    private val _isConsentFormShowing = MutableStateFlow(false)
    val isConsentFormShowing: StateFlow<Boolean> = _isConsentFormShowing.asStateFlow()
 
    fun setConsentFlowComplete(complete: Boolean) {
        _isConsentFlowComplete.value = complete
    }
 
    fun setConsentFormShowing(showing: Boolean) {
        _isConsentFormShowing.value = showing
    }
    
    private var rewardedRetryAttempt = 0
    private var interstitialRetryAttempt = 0
    private var appOpenRetryAttempt = 0
 
    fun isAdMobInitialized(): Boolean {
        return isInitialized
    }
 
    /** Check if the user is currently a Pro subscriber. */
    private val isProUser: Boolean
        get() = ProStateManager.isPremiumPro.value
 
    fun initialize(context: Context) {
        if (isInitialized) return
        
        appContext = context.applicationContext
        // Trigger initialization asynchronously
        MobileAds.initialize(context) { status ->
            Log.d(TAG, "AdMob Initialized: $status")
        }
 
        // The SDK supports loading ads immediately after calling MobileAds.initialize()
        // without waiting for the callback to finish. We mark as initialized instantly
        // to cut splash latency.
        isInitialized = true
 
        if (!isProUser) {
            // Instantly trigger background ad pre-loading on startup
            loadInterstitial(context)
            loadRewarded(context) // Preload Rewarded Ad as well
            loadAppOpenAd(context)
        } else {
            Log.d(TAG, "Pro user — skipping ad preloads")
        }
    }
 
    /**
     * Called when the user upgrades to Pro mid-session.
     * Immediately releases all cached ad instances to prevent leakage.
     */
    fun clearAllAds() {
        Log.d(TAG, "Clearing all cached ads for Pro user")
        rewardedAd = null
        interstitialAd = null
        appOpenAd = null
        isRewardedAdLoading = false
        isInterstitialAdLoading = false
        isAppOpenAdLoading = false
        isShowingAppOpenAd = false
    }

    fun loadRewarded(context: Context) {
        if (isProUser) {
            Log.d(TAG, "Pro user — skipping rewarded load")
            return
        }
        // Requirement 3: Avoid Redundant Requests
        if (rewardedAd != null || isRewardedAdLoading) {
            Log.d(TAG, "Rewarded ad already loaded or loading — skipping redundant request")
            return
        }

        isRewardedAdLoading = true
        val currentAttempt = rewardedRetryAttempt

        // Timeout guard: if the ad request gets stuck for 15 seconds, reset state and retry.
        CoroutineScope(Dispatchers.Main).launch {
            delay(15000)
            if (isRewardedAdLoading && rewardedAd == null && currentAttempt == rewardedRetryAttempt) {
                Log.d(TAG, "Rewarded ad load timed out after 15s. Resetting state.")
                isRewardedAdLoading = false
                // Note: since this is loaded on demand, we don't automatically retry on timeout
                // to prevent background requests when user has already given up.
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (isProUser) {
                    isRewardedAdLoading = false
                    return@launch
                }
                val adRequest = withContext(Dispatchers.Default) {
                    AdRequest.Builder().build()
                }

                RewardedAd.load(
                    context,
                    REWARDED_AD_UNIT_ID,
                    adRequest,
                    object : RewardedAdLoadCallback() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            Log.d(TAG, "Rewarded ad failed to load: ${adError.message}")
                            rewardedAd = null
                            isRewardedAdLoading = false
                            
                            // Retry with exponential backoff (Policy Compliant)
                            if (rewardedRetryAttempt < 3) {
                                val delayMs = (Math.pow(2.0, rewardedRetryAttempt.toDouble()) * 2000).toLong().coerceAtMost(60000)
                                rewardedRetryAttempt++
                                Log.d(TAG, "Scheduling rewarded ad retry in ${delayMs}ms (Attempt $rewardedRetryAttempt)")
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(delayMs)
                                    appContext?.let { loadRewarded(it) }
                                }
                            } else {
                                Log.d(TAG, "Max retries reached for rewarded ad")
                            }
                        }

                        override fun onAdLoaded(ad: RewardedAd) {
                            Log.d(TAG, "Rewarded ad loaded successfully")
                            rewardedRetryAttempt = 0
                            if (isProUser) {
                                Log.d(TAG, "Pro user detected after rewarded load — discarding")
                                isRewardedAdLoading = false
                                return
                            }
                            rewardedAd = ad
                            isRewardedAdLoading = false
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading Rewarded ad asynchronously", e)
                isRewardedAdLoading = false
            }
        }
    }

    fun loadInterstitial(context: Context) {
        if (isProUser) {
            Log.d(TAG, "Pro user — skipping interstitial load")
            return
        }
        // Requirement 3: Avoid Redundant Requests
        if (interstitialAd != null || isInterstitialAdLoading) {
            Log.d(TAG, "Interstitial ad already loaded or loading — skipping redundant request")
            return
        }

        isInterstitialAdLoading = true
        val currentAttempt = interstitialRetryAttempt

        // Timeout guard: if the ad request gets stuck for 15 seconds, reset state.
        CoroutineScope(Dispatchers.Main).launch {
            delay(15000)
            if (isInterstitialAdLoading && interstitialAd == null && currentAttempt == interstitialRetryAttempt) {
                Log.d(TAG, "Interstitial ad load timed out after 15s. Resetting state.")
                isInterstitialAdLoading = false
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (isProUser) {
                    isInterstitialAdLoading = false
                    return@launch
                }
                val adRequest = withContext(Dispatchers.Default) {
                    AdRequest.Builder().build()
                }

                InterstitialAd.load(
                    context,
                    INTERSTITIAL_AD_UNIT_ID,
                    adRequest,
                    object : InterstitialAdLoadCallback() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            Log.d(TAG, "Interstitial ad failed to load: ${adError.message}")
                            interstitialAd = null
                            isInterstitialAdLoading = false

                            // Retry with exponential backoff (for preloaded ad)
                            if (interstitialRetryAttempt < 3) {
                                val delayMs = (Math.pow(2.0, interstitialRetryAttempt.toDouble()) * 2000).toLong().coerceAtMost(60000)
                                interstitialRetryAttempt++
                                Log.d(TAG, "Scheduling interstitial ad retry in ${delayMs}ms (Attempt $interstitialRetryAttempt)")
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(delayMs)
                                    appContext?.let { loadInterstitial(it) }
                                }
                            } else {
                                Log.d(TAG, "Max retries reached for interstitial ad")
                            }
                        }

                        override fun onAdLoaded(ad: InterstitialAd) {
                            Log.d(TAG, "Interstitial ad loaded successfully")
                            interstitialRetryAttempt = 0 // Reset attempt counter
                            // Double-check: if user became Pro while loading, discard immediately
                            if (isProUser) {
                                Log.d(TAG, "Pro user detected after interstitial ad load — discarding")
                                isInterstitialAdLoading = false
                                return
                            }
                            interstitialAd = ad
                            isInterstitialAdLoading = false
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading Interstitial ad asynchronously", e)
                isInterstitialAdLoading = false
            }
        }
    }

    fun loadAppOpenAd(context: Context) {
        if (isProUser) {
            Log.d(TAG, "Pro user — skipping app open ad load")
            return
        }
        // Requirement 3: Avoid Redundant Requests
        if (appOpenAd != null || isAppOpenAdLoading) {
            Log.d(TAG, "App Open Ad already loaded or loading — skipping redundant request")
            return
        }

        isAppOpenAdLoading = true
        val currentAttempt = appOpenRetryAttempt

        // Timeout guard: if the ad request gets stuck for 15 seconds, reset state.
        CoroutineScope(Dispatchers.Main).launch {
            delay(15000)
            if (isAppOpenAdLoading && appOpenAd == null && currentAttempt == appOpenRetryAttempt) {
                Log.d(TAG, "App Open Ad load timed out after 15s. Resetting state.")
                isAppOpenAdLoading = false
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (isProUser) {
                    isAppOpenAdLoading = false
                    return@launch
                }
                val request = withContext(Dispatchers.Default) {
                    AdRequest.Builder().build()
                }
                AppOpenAd.load(
                    context,
                    APP_OPEN_AD_UNIT_ID,
                    request,
                    object : AppOpenAdLoadCallback() {
                        override fun onAdLoaded(ad: AppOpenAd) {
                            isAppOpenAdLoading = false
                            appOpenRetryAttempt = 0 // Reset attempt counter
                            // Double-check: if user became Pro while loading, discard immediately
                            if (isProUser) {
                                Log.d(TAG, "Pro user detected after app open ad load — discarding")
                                return
                            }
                            appOpenAd = ad
                            appOpenAdLoadTime = Date().time
                            Log.d(TAG, "App Open Ad loaded successfully")

                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            isAppOpenAdLoading = false
                            Log.d(TAG, "App Open Ad failed to load: ${loadAdError.message}")

                            // Retry with exponential backoff
                            if (appOpenRetryAttempt < 3) {
                                val delayMs = (Math.pow(2.0, appOpenRetryAttempt.toDouble()) * 2000).toLong().coerceAtMost(60000)
                                appOpenRetryAttempt++
                                Log.d(TAG, "Scheduling app open ad retry in ${delayMs}ms (Attempt $appOpenRetryAttempt)")
                                CoroutineScope(Dispatchers.Main).launch {
                                    delay(delayMs)
                                    appContext?.let { loadAppOpenAd(it) }
                                }
                            } else {
                                Log.d(TAG, "Max retries reached for app open ad")
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                isAppOpenAdLoading = false
                Log.e(TAG, "Error loading App Open Ad asynchronously", e)
            }
        }
    }

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference: Long = Date().time - appOpenAdLoadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    fun showAppOpenAdIfAvailable(activity: Activity) {
        // Strict Pro guardrail
        if (isProUser) {
            appOpenAd = null
            Log.d(TAG, "Pro user — blocking app open ad")
            return
        }

        if (isShowingAppOpenAd) return

        val timeSinceLastAd = Date().time - lastAppOpenAdShowTime
        if (timeSinceLastAd < 300000) { // 5 minutes cooldown
            Log.d(TAG, "Skipping App Open Ad due to 5-minute cooldown")
            return
        }

        if (ignoreNextAppOpenAd) {
            Log.d(TAG, "Skipping App Open Ad because another fullscreen ad is showing/was just shown")
            ignoreNextAppOpenAd = false
            return
        }

        if (appOpenAd == null || !wasLoadTimeLessThanNHoursAgo(4)) {
            loadAppOpenAd(activity)
            return
        }

        appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAppOpenAd = false
                loadAppOpenAd(activity.applicationContext)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowingAppOpenAd = false
                loadAppOpenAd(activity.applicationContext)
            }

            override fun onAdShowedFullScreenContent() {
                isShowingAppOpenAd = true
                lastAppOpenAdShowTime = Date().time
                Log.d(TAG, "App Open Ad showed")
            }
        }
        appOpenAd?.show(activity)
    }

    fun isAppOpenAdAvailable(): Boolean {
        return appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)
    }

    fun isAppOpenAdLoading(): Boolean {
        return isAppOpenAdLoading
    }

    fun isRewardedAdAvailable(): Boolean {
        return rewardedAd != null && !isProUser
    }

    fun isRewardedAdLoading(): Boolean {
        return isRewardedAdLoading && !isProUser
    }

    fun isInterstitialAdAvailable(): Boolean {
        return interstitialAd != null && !isProUser
    }
    
    fun canShowInterstitialAd(): Boolean {
        // Enforce a 3-minute cooldown between Interstitial Ads
        val timeSinceLastAd = Date().time - lastInterstitialAdShowTime
        return timeSinceLastAd > 180000 // 3 minutes in ms
    }

    fun isInterstitialAdLoading(): Boolean {
        return isInterstitialAdLoading && !isProUser
    }

    fun showAppOpenAdOnSplash(activity: Activity, onComplete: () -> Unit) {
        if (isProUser) {
            onComplete()
            return
        }

        if (ignoreNextAppOpenAd) {
            Log.d(TAG, "Skipping App Open Ad on splash because another fullscreen ad is active")
            ignoreNextAppOpenAd = false
            onComplete()
            return
        }

        if (appOpenAd != null && wasLoadTimeLessThanNHoursAgo(4)) {
            appOpenAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    appOpenAd = null
                    isShowingAppOpenAd = false
                    onComplete()
                    loadAppOpenAd(activity.applicationContext)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    appOpenAd = null
                    isShowingAppOpenAd = false
                    onComplete()
                    loadAppOpenAd(activity.applicationContext)
                }

                override fun onAdShowedFullScreenContent() {
                    isShowingAppOpenAd = true
                    lastAppOpenAdShowTime = Date().time
                    Log.d(TAG, "App Open Ad showed on splash")
                }
            }
            appOpenAd?.show(activity)
        } else {
            onComplete()
        }
    }

    fun showRewarded(
        activity: Activity,
        onAdShowed: () -> Unit = {},
        onRewardEarned: () -> Unit = {}
    ) {
        if (isProUser) {
            rewardedAd = null
            Log.d(TAG, "Pro user — blocking rewarded ad")
            onAdShowed()
            onRewardEarned()
            return
        }

        if (rewardedAd != null) {
            ignoreNextAppOpenAd = true
            var rewardEarned = false
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad dismissed")
                    rewardedAd = null
                    if (rewardEarned) {
                        onRewardEarned()
                    } else {
                        Toast.makeText(activity, "Watch the full video to access settings", Toast.LENGTH_LONG).show()
                    }
                    loadRewarded(activity.applicationContext) // Preload the next rewarded ad
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Rewarded ad failed to show: ${adError.message}")
                    rewardedAd = null
                    onAdShowed()
                    onRewardEarned()
                    loadRewarded(activity.applicationContext) // Preload again in case of failure
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Rewarded ad showed full screen")
                    onAdShowed()
                }
            }
            rewardedAd?.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                rewardEarned = true
            }
        } else {
            Log.d(TAG, "Rewarded ad not ready yet")
            onAdShowed()
            onRewardEarned()
        }
    }

    fun showInterstitial(
        activity: Activity,
        onAdShowed: () -> Unit = {},
        onAdDismissed: () -> Unit = {}
    ) {
        if (isProUser) {
            interstitialAd = null
            Log.d(TAG, "Pro user — blocking interstitial ad")
            onAdShowed()
            onAdDismissed()
            return
        }
        
        if (!canShowInterstitialAd()) {
            Log.d(TAG, "Cannot show interstitial ad yet due to cooldown")
            onAdShowed()
            onAdDismissed()
            return
        }

        if (interstitialAd != null) {
            ignoreNextAppOpenAd = true
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad dismissed")
                    interstitialAd = null
                    lastInterstitialAdShowTime = Date().time
                    onAdDismissed()
                    loadInterstitial(activity.applicationContext)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Interstitial ad failed to show: ${adError.message}")
                    interstitialAd = null
                    onAdShowed()
                    onAdDismissed()
                    loadInterstitial(activity.applicationContext)
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Interstitial ad showed full screen")
                    onAdShowed()
                }
            }
            interstitialAd?.show(activity)
        } else {
            Log.d(TAG, "Interstitial ad not ready yet")
            onAdShowed()
            onAdDismissed()
            loadInterstitial(activity)
        }
    }
}
