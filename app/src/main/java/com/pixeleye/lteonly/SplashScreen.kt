package com.pixeleye.lteonly

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.provider.Settings
import android.util.Log
import android.widget.ImageView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pixeleye.lteonly.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onSplashComplete: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isPremiumPro by ProStateManager.isPremiumPro.collectAsState()

    // Animation states
    var startPulse by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (startPulse) 1.08f else 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        startPulse = true
        
        // If user is Premium Pro, skip ad waiting completely
        if (isPremiumPro) {
            delay(1000) // Brief delay for visual brand presence
            onSplashComplete()
            return@LaunchedEffect
        }

        // Wait for GDPR consent flow status update
        var initWaitTime = 0
        val maxInitWait = 5000 // 5 seconds max wait for consent completion
        while (!AdManager.isConsentFlowComplete.value && !AdManager.isConsentFormShowing.value && initWaitTime < maxInitWait) {
            delay(200)
            initWaitTime += 200
        }

        // If the consent form is actively showing, wait indefinitely until it's dismissed
        if (AdManager.isConsentFormShowing.value) {
            while (AdManager.isConsentFormShowing.value) {
                delay(200)
            }
        }

        // If initialized and consent allows ads, load and show
        if (AdManager.isAdMobInitialized()) {
            // Trigger ad load if it isn't already loading or cached
            if (!AdManager.isAppOpenAdAvailable() && !AdManager.isAppOpenAdLoading()) {
                AdManager.loadAppOpenAd(context.applicationContext)
            }

            // Wait up to remaining time of total 8s limit for the ad to load
            var waitTime = 0
            val remainingWaitLimit = 8000 - initWaitTime
            while (!AdManager.isAppOpenAdAvailable() && waitTime < remainingWaitLimit) {
                delay(500)
                waitTime += 500
            }

            if (AdManager.isAppOpenAdAvailable()) {
                // Show the ad and wait for it to be dismissed before proceeding
                AdManager.showAppOpenAdOnSplash(activity ?: return@LaunchedEffect) {
                    onSplashComplete()
                }
                return@LaunchedEffect
            }
        }

        // Proceed without ad if it timed out or initialization failed
        Log.w("SplashScreen", "App Open Ad timed out or not available, proceeding to app")
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeumorphicBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight(0.7f)
        ) {
            // The real app launcher icon safely using AndroidView with scale animation
            AndroidView(
                factory = { ctx ->
                    ImageView(ctx).apply {
                        val pm = ctx.packageManager
                        val icon = pm.getApplicationIcon(ctx.packageName)
                        setImageDrawable(icon)
                    }
                },
                modifier = Modifier
                    .size(110.dp)
                    .scale(scale)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // App Name
            Text(
                text = "Force LTE Only",
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Optimize Your Network",
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                color = TextSecondary
            )
        }

        // Bottom Loading Section
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = TextTeal,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Checking connection...",
                fontFamily = PoppinsFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

    }
}
