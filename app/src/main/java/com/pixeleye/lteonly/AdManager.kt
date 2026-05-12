package com.pixeleye.lteonly

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

object AdManager {
    private const val TAG = "AdManager"
    
    // Ad Unit IDs from BuildConfig
    private val INTERSTITIAL_AD_UNIT_ID = BuildConfig.ADMOB_INTERSTITIAL_ID
    val APP_OPEN_AD_UNIT_ID = BuildConfig.ADMOB_APP_OPEN_ID
    val BANNER_AD_UNIT_ID = BuildConfig.ADMOB_BANNER_ID

    private var interstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    
    private var appOpenAd: AppOpenAd? = null
    private var isAppOpenAdLoading = false
    private var isShowingAppOpenAd = false
    private var appOpenAdLoadTime: Long = 0

    /** Check if the user is currently a Pro subscriber. */
    private val isProUser: Boolean
        get() = ProStateManager.isUserPro.value

    fun initialize(context: Context) {
        MobileAds.initialize(context) { status ->
            Log.d(TAG, "AdMob Initialized: $status")
            if (!isProUser) {
                // Instantly trigger background ad pre-loading on startup
                loadInterstitial(context)
                loadAppOpenAd(context)
            } else {
                Log.d(TAG, "Pro user — skipping ad preloads")
            }
        }
    }

    /**
     * Called when the user upgrades to Pro mid-session.
     * Immediately releases all cached ad instances to prevent leakage.
     */
    fun clearAllAds() {
        Log.d(TAG, "Clearing all cached ads for Pro user")
        interstitialAd = null
        appOpenAd = null
        isAdLoading = false
        isAppOpenAdLoading = false
        isShowingAppOpenAd = false
    }

    private fun loadInterstitial(context: Context) {
        if (isProUser) {
            Log.d(TAG, "Pro user — skipping interstitial load")
            return
        }
        // Requirement 3: Avoid Redundant Requests
        if (interstitialAd != null || isAdLoading) {
            Log.d(TAG, "Interstitial ad already loaded or loading — skipping redundant request")
            return
        }

        isAdLoading = true
        CoroutineScope(Dispatchers.Main).launch {
            try {
                if (isProUser) {
                    isAdLoading = false
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
                            isAdLoading = false
                        }

                        override fun onAdLoaded(ad: InterstitialAd) {
                            Log.d(TAG, "Interstitial ad loaded successfully")
                            // Double-check: if user became Pro while loading, discard immediately
                            if (isProUser) {
                                Log.d(TAG, "Pro user detected after interstitial load — discarding")
                                isAdLoading = false
                                return
                            }
                            interstitialAd = ad
                            isAdLoading = false
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading Interstitial ad asynchronously", e)
                isAdLoading = false
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

    fun showInterstitial(activity: Activity, onAdDismissed: () -> Unit = {}) {
        // Strict Pro guardrail
        if (isProUser) {
            interstitialAd = null
            Log.d(TAG, "Pro user — blocking interstitial ad")
            onAdDismissed()
            return
        }

        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Ad dismissed")
                    interstitialAd = null
                    onAdDismissed()
                    loadInterstitial(activity) // Load the next one
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Ad failed to show: ${adError.message}")
                    interstitialAd = null
                    onAdDismissed()
                    loadInterstitial(activity)
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Ad showed full screen content — instantly loading next ad")
                    interstitialAd = null
                    // Instantly trigger background ad pre-loading while showing current ad
                    loadInterstitial(activity)
                }
            }
            // Show cached ad instantly
            interstitialAd?.show(activity)
        } else {
            Log.d(TAG, "Ad not ready yet")
            onAdDismissed()
            loadInterstitial(activity)
        }
    }
}
