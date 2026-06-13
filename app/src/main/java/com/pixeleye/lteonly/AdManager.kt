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
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

object AdManager {
    private const val TAG = "AdManager"
    
    // Ad Unit IDs from BuildConfig
    private val REWARDED_AD_UNIT_ID = BuildConfig.ADMOB_REWARDED_ID
    private val REWARDED_INTERSTITIAL_AD_UNIT_ID = BuildConfig.ADMOB_REWARDED_INTERSTITIAL_ID
    val APP_OPEN_AD_UNIT_ID = BuildConfig.ADMOB_APP_OPEN_ID
    val BANNER_AD_UNIT_ID = BuildConfig.ADMOB_BANNER_ID

    private var rewardedAd: RewardedAd? = null
    private var isRewardedAdLoading = false
    
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    private var isRewardedInterstitialAdLoading = false

    private var appOpenAd: AppOpenAd? = null
    private var isAppOpenAdLoading = false
    private var isShowingAppOpenAd = false
    private var appOpenAdLoadTime: Long = 0
    private var ignoreNextAppOpenAd = false

    private var cachedBannerAdView: AdView? = null

    private var isInitialized = false
    private var appContext: Context? = null
    
    private var rewardedRetryAttempt = 0
    private var rewardedInterstitialRetryAttempt = 0
    private var appOpenRetryAttempt = 0
    private var bannerRetryAttempt = 0

    fun preloadBannerAd(context: Context) {
        if (isProUser) return
        if (cachedBannerAdView != null) return

        cachedBannerAdView = AdView(context.applicationContext).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = BANNER_AD_UNIT_ID
            adListener = object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Log.d(TAG, "Banner ad failed to load: ${adError.message}")
                    val delayMs = (Math.pow(2.0, bannerRetryAttempt.toDouble()) * 2000).toLong().coerceAtMost(60000)
                    bannerRetryAttempt++
                    Log.d(TAG, "Scheduling banner ad retry in ${delayMs}ms (Attempt $bannerRetryAttempt)")
                    CoroutineScope(Dispatchers.Main).launch {
                        delay(delayMs)
                        loadAd(AdRequest.Builder().build())
                    }
                }
                override fun onAdLoaded() {
                    Log.d(TAG, "Banner ad loaded successfully")
                    bannerRetryAttempt = 0
                }
            }
            loadAd(AdRequest.Builder().build())
        }
    }

    fun getOrCreateBannerAdView(context: Context): AdView {
        if (isProUser) {
            return AdView(context)
        }
        if (cachedBannerAdView == null) {
            preloadBannerAd(context)
        }
        return cachedBannerAdView ?: AdView(context)
    }

    fun isAdMobInitialized(): Boolean {
        return isInitialized
    }

    /** Check if the user is currently a Pro subscriber. */
    private val isProUser: Boolean
        get() = ProStateManager.isPremiumPro.value

    fun initialize(context: Context) {
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
            loadRewardedInterstitial(context) // ONLY preload Rewarded Interstitial
            loadAppOpenAd(context)
            preloadBannerAd(context)
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
        rewardedInterstitialAd = null
        appOpenAd = null
        cachedBannerAdView?.destroy()
        cachedBannerAdView = null
        isRewardedAdLoading = false
        isRewardedInterstitialAdLoading = false
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
                            val delayMs = (Math.pow(2.0, rewardedRetryAttempt.toDouble()) * 2000).toLong().coerceAtMost(60000)
                            rewardedRetryAttempt++
                            Log.d(TAG, "Scheduling rewarded ad retry in ${delayMs}ms (Attempt $rewardedRetryAttempt)")
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(delayMs)
                                appContext?.let { loadRewarded(it) }
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

    fun loadRewardedInterstitial(context: Context) {
        if (isProUser) {
            Log.d(TAG, "Pro user — skipping rewarded interstitial load")
            return
        }
        // Requirement 3: Avoid Redundant Requests
        if (rewardedInterstitialAd != null || isRewardedInterstitialAdLoading) {
            Log.d(TAG, "Rewarded interstitial ad already loaded or loading — skipping redundant request")
            return
        }

        isRewardedInterstitialAdLoading = true
        val currentAttempt = rewardedInterstitialRetryAttempt

        // Timeout guard: if the ad request gets stuck for 15 seconds, reset state and retry.
        CoroutineScope(Dispatchers.Main).launch {
            delay(15000)
            if (isRewardedInterstitialAdLoading && rewardedInterstitialAd == null && currentAttempt == rewardedInterstitialRetryAttempt) {
                Log.d(TAG, "Rewarded interstitial ad load timed out after 15s. Resetting state and retrying.")
                isRewardedInterstitialAdLoading = false
                val delayMs = (Math.pow(2.0, rewardedInterstitialRetryAttempt.toDouble()) * 2000).toLong().coerceAtMost(60000)
                rewardedInterstitialRetryAttempt++
                CoroutineScope(Dispatchers.Main).launch {
                    delay(delayMs)
                    appContext?.let { loadRewardedInterstitial(it) }
                }
            }
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (isProUser) {
                    isRewardedInterstitialAdLoading = false
                    return@launch
                }
                val adRequest = withContext(Dispatchers.Default) {
                    AdRequest.Builder().build()
                }

                RewardedInterstitialAd.load(
                    context,
                    REWARDED_INTERSTITIAL_AD_UNIT_ID,
                    adRequest,
                    object : RewardedInterstitialAdLoadCallback() {
                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            Log.d(TAG, "Rewarded interstitial ad failed to load: ${adError.message}")
                            rewardedInterstitialAd = null
                            isRewardedInterstitialAdLoading = false

                            // Retry with exponential backoff (for preloaded ad)
                            val delayMs = (Math.pow(2.0, rewardedInterstitialRetryAttempt.toDouble()) * 2000).toLong().coerceAtMost(60000)
                            rewardedInterstitialRetryAttempt++
                            Log.d(TAG, "Scheduling rewarded interstitial ad retry in ${delayMs}ms (Attempt $rewardedInterstitialRetryAttempt)")
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(delayMs)
                                appContext?.let { loadRewardedInterstitial(it) }
                            }
                        }

                        override fun onAdLoaded(ad: RewardedInterstitialAd) {
                            Log.d(TAG, "Rewarded interstitial ad loaded successfully")
                            rewardedInterstitialRetryAttempt = 0 // Reset attempt counter
                            // Double-check: if user became Pro while loading, discard immediately
                            if (isProUser) {
                                Log.d(TAG, "Pro user detected after rewarded interstitial load — discarding")
                                isRewardedInterstitialAdLoading = false
                                return
                            }
                            rewardedInterstitialAd = ad
                            isRewardedInterstitialAdLoading = false
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading Rewarded interstitial ad asynchronously", e)
                isRewardedInterstitialAdLoading = false
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

        // Timeout guard: if the ad request gets stuck for 15 seconds, reset state and retry.
        CoroutineScope(Dispatchers.Main).launch {
            delay(15000)
            if (isAppOpenAdLoading && appOpenAd == null && currentAttempt == appOpenRetryAttempt) {
                Log.d(TAG, "App Open Ad load timed out after 15s. Resetting state and retrying.")
                isAppOpenAdLoading = false
                val delayMs = (Math.pow(2.0, appOpenRetryAttempt.toDouble()) * 2000).toLong().coerceAtMost(60000)
                appOpenRetryAttempt++
                CoroutineScope(Dispatchers.Main).launch {
                    delay(delayMs)
                    appContext?.let { loadAppOpenAd(it) }
                }
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
                            val delayMs = (Math.pow(2.0, appOpenRetryAttempt.toDouble()) * 2000).toLong().coerceAtMost(60000)
                            appOpenRetryAttempt++
                            Log.d(TAG, "Scheduling app open ad retry in ${delayMs}ms (Attempt $appOpenRetryAttempt)")
                            CoroutineScope(Dispatchers.Main).launch {
                                delay(delayMs)
                                appContext?.let { loadAppOpenAd(it) }
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
                loadAppOpenAd(activity)
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                appOpenAd = null
                isShowingAppOpenAd = false
                loadAppOpenAd(activity)
            }

            override fun onAdShowedFullScreenContent() {
                isShowingAppOpenAd = true
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

    fun isRewardedInterstitialAdAvailable(): Boolean {
        return rewardedInterstitialAd != null && !isProUser
    }

    fun isRewardedInterstitialAdLoading(): Boolean {
        return isRewardedInterstitialAdLoading && !isProUser
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
                    loadAppOpenAd(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    appOpenAd = null
                    isShowingAppOpenAd = false
                    onComplete()
                    loadAppOpenAd(activity)
                }

                override fun onAdShowedFullScreenContent() {
                    isShowingAppOpenAd = true
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
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Rewarded ad failed to show: ${adError.message}")
                    rewardedAd = null
                    onAdShowed()
                    onRewardEarned()
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

    fun showRewardedInterstitial(
        activity: Activity,
        onAdShowed: () -> Unit = {},
        onRewardEarned: () -> Unit = {}
    ) {
        if (isProUser) {
            rewardedInterstitialAd = null
            Log.d(TAG, "Pro user — blocking rewarded interstitial ad")
            onAdShowed()
            onRewardEarned()
            return
        }

        if (rewardedInterstitialAd != null) {
            ignoreNextAppOpenAd = true
            var rewardEarned = false
            rewardedInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Rewarded interstitial ad dismissed")
                    rewardedInterstitialAd = null
                    if (rewardEarned) {
                        onRewardEarned()
                    } else {
                        Toast.makeText(activity, "Watch the full video to access settings", Toast.LENGTH_LONG).show()
                    }
                    loadRewardedInterstitial(activity)
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Rewarded interstitial ad failed to show: ${adError.message}")
                    rewardedInterstitialAd = null
                    onAdShowed()
                    onRewardEarned()
                    loadRewardedInterstitial(activity)
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Rewarded interstitial ad showed full screen")
                    onAdShowed()
                }
            }
            rewardedInterstitialAd?.show(activity) { rewardItem ->
                Log.d(TAG, "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
                rewardEarned = true
            }
        } else {
            Log.d(TAG, "Rewarded interstitial ad not ready yet")
            onAdShowed()
            onRewardEarned()
            loadRewardedInterstitial(activity)
        }
    }
}
