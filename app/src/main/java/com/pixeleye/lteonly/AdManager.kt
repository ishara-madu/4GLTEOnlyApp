package com.pixeleye.lteonly

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import java.util.Date

object AdManager {
    private const val TAG = "AdManager"
    
    // Test Ad Unit IDs
    private const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-LOCAL_ID_HERE"
    const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
    const val BANNER_AD_UNIT_ID = "ca-app-pub-LOCAL_ID_HERE"

    private var interstitialAd: InterstitialAd? = null
    private var isAdLoading = false
    
    private var appOpenAd: AppOpenAd? = null
    private var isShowingAppOpenAd = false
    private var appOpenAdLoadTime: Long = 0

    fun initialize(context: Context) {
        MobileAds.initialize(context) { status ->
            Log.d(TAG, "AdMob Initialized: $status")
            loadInterstitial(context)
            loadAppOpenAd(context)
        }
    }

    private fun loadInterstitial(context: Context) {
        if (interstitialAd != null || isAdLoading) return

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()

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
                    interstitialAd = ad
                    isAdLoading = false
                }
            }
        )
    }

    fun loadAppOpenAd(context: Context) {
        val request = AdRequest.Builder().build()
        AppOpenAd.load(
            context,
            APP_OPEN_AD_UNIT_ID,
            request,
            object : AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    appOpenAdLoadTime = Date().time
                    Log.d(TAG, "App Open Ad loaded")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    Log.d(TAG, "App Open Ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    private fun wasLoadTimeLessThanNHoursAgo(numHours: Long): Boolean {
        val dateDifference: Long = Date().time - appOpenAdLoadTime
        val numMilliSecondsPerHour: Long = 3600000
        return dateDifference < numMilliSecondsPerHour * numHours
    }

    fun showAppOpenAdIfAvailable(activity: Activity) {
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
        if (interstitialAd != null) {
            interstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Ad dismissed")
                    interstitialAd = null
                    loadInterstitial(activity) // Load the next one
                    onAdDismissed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.d(TAG, "Ad failed to show: ${adError.message}")
                    interstitialAd = null
                    onAdDismissed()
                }
            }
            interstitialAd?.show(activity)
        } else {
            Log.d(TAG, "Ad not ready yet")
            onAdDismissed()
            loadInterstitial(activity)
        }
    }
}
