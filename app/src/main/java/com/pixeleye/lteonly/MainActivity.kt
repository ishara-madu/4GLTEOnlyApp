package com.pixeleye.lteonly

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.work.*
import com.pixeleye.lteonly.ui.theme.*
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getCustomerInfoWith
import com.google.android.play.core.review.ReviewManagerFactory
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import android.app.Activity.RESULT_OK
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.util.concurrent.TimeUnit
// Removed unused material icons imports
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.nativeCanvas
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

class MainActivity : ComponentActivity() {
    private lateinit var themeManager: ThemeManager
    private lateinit var appUpdateManager: AppUpdateManager
    private val updateType = AppUpdateType.FLEXIBLE

    private val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            android.util.Log.e("MainActivity", "Update flow failed! Result code: ${result.resultCode}")
            // If the update is cancelled or fails, you can request to start the update again.
        }
    }

    private lateinit var consentInformation: ConsentInformation

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeManager = ThemeManager.getInstance(this)
        appUpdateManager = AppUpdateManagerFactory.create(this)

        checkForAppUpdates()

        // Gather GDPR consent before initializing ads
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation = UserMessagingPlatform.getConsentInformation(this)
        consentInformation.requestConsentInfoUpdate(
            this,
            params,
            {
                val consentStatus = consentInformation.consentStatus
                val formRequired = consentStatus == ConsentInformation.ConsentStatus.REQUIRED
                if (formRequired) {
                    AdManager.setConsentFormShowing(true)
                }
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { _ ->
                    if (consentInformation.canRequestAds()) {
                        AdManager.initialize(applicationContext)
                    }
                    AdManager.setConsentFormShowing(false)
                    AdManager.setConsentFlowComplete(true)
                }
            },
            {
                if (consentInformation.canRequestAds()) {
                    AdManager.initialize(applicationContext)
                }
                AdManager.setConsentFlowComplete(true)
            }
        )
        if (consentInformation.canRequestAds()) {
            AdManager.initialize(applicationContext)
            AdManager.setConsentFlowComplete(true)
        }

        setContent {
            val theme by themeManager.themeFlow.collectAsStateWithLifecycle()
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemDark
            }

            com.pixeleye.lteonly.ui.theme.globalIsDarkTheme = darkTheme

            var showSplash by rememberSaveable { mutableStateOf(true) }

            ForceLTEOnlyTheme(darkTheme = darkTheme) {
                if (showSplash) {
                    SplashScreen(onSplashComplete = { showSplash = false })
                } else {
                    DashboardScreen(themeManager)
                }
            }
        }
    }

    private fun checkForAppUpdates() {
        val appUpdateInfoTask = appUpdateManager.appUpdateInfo
        appUpdateInfoTask.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
                && appUpdateInfo.isUpdateTypeAllowed(updateType)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(updateType).build()
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::appUpdateManager.isInitialized) {
            appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                    // If an in-app update is already running, resume the update.
                    appUpdateManager.startUpdateFlowForResult(
                        appUpdateInfo,
                        updateLauncher,
                        AppUpdateOptions.newBuilder(updateType).build()
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(themeManager: ThemeManager) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val context = LocalContext.current

    // Pro state from RevenueCat
    val isUserPro by ProStateManager.isUserPro.collectAsStateWithLifecycle()
    val isPremiumPro by ProStateManager.isPremiumPro.collectAsStateWithLifecycle()
    var showPaywall by remember { mutableStateOf(false) }

    var isWaitingForTempProAd by remember { mutableStateOf(false) }
    var showTempProConsentDialog by remember { mutableStateOf(false) }
    var showTempProLoadErrorDialog by remember { mutableStateOf(false) }
    val onWatchAdToUnlockClick = { showTempProConsentDialog = true }

    var hasPermission by remember { mutableStateOf(false) }
    var networkInfo by remember { mutableStateOf(NetworkInfo()) }
    var isSpeedTestRunning by remember { mutableStateOf(false) }
    var isPingTestRunning by remember { mutableStateOf(false) }
    var speedTestResult by remember { mutableStateOf<SpeedTestResult?>(null) }
    var speedTestPhase by remember { mutableStateOf("IDLE") }
    var pingTestResult by remember { mutableStateOf<PingTestResult?>(null) }
    var hasSavedInitialSignal by remember { mutableStateOf(false) }
    
    var isPingStabilizerEnabled by remember { mutableStateOf(PingStabilizerService.isRunning) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                isPingStabilizerEnabled = true
                val intent = Intent(context, PingStabilizerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }
    )

    var storedSignalHistory by remember { mutableStateOf<List<SignalHistoryEntity>>(emptyList()) }
    var storedDataUsage by remember { mutableStateOf<List<DataUsageEntity>>(emptyList()) }
    var storedSpeedTests by remember { mutableStateOf<List<SpeedTestEntity>>(emptyList()) }
    var storedPingTests by remember { mutableStateOf<List<PingTestEntity>>(emptyList()) }

    val gameServers = remember {
        mutableStateListOf(
            GameServer("Singapore", "203.126.118.38"),
            GameServer("India", "13.232.0.253"),
            GameServer("Middle East", "185.25.183.1"),
            GameServer("Europe", "146.66.152.1"),
            GameServer("US East", "208.78.164.1"),
            GameServer("Australia", "103.10.125.1"),
            GameServer("Japan", "155.133.239.1"),
            GameServer("Google DNS", "8.8.8.8")
        )
    }

    val telephonyService = remember { TelephonyService(context) }
    
    LaunchedEffect(Unit) {
        telephonyService.loadInitialGamePingHistory(gameServers)
    }
    val intent = (context as? ComponentActivity)?.intent

    LaunchedEffect(intent) {
        intent?.getIntExtra("target_tab", -1)?.let { tab ->
            if (tab != -1) {
                selectedTab = tab
                // Clear the extra so it doesn't trigger again on recomposition
                intent.removeExtra("target_tab")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions.values.all { it }
    }

    LaunchedEffect(Unit) {
        hasPermission = telephonyService.hasRequiredPermissions()
        if (!hasPermission) {
            permissionLauncher.launch(TelephonyService.REQUIRED_PERMISSIONS)
        }
    }

    LaunchedEffect(Unit) {
        telephonyService.getStoredSignalHistory().collect { storedSignalHistory = it }
    }

    LaunchedEffect(Unit) {
        telephonyService.getStoredDataUsage().collect { storedDataUsage = it }
    }

    LaunchedEffect(Unit) {
        telephonyService.getStoredSpeedTests().collect { storedSpeedTests = it }
    }

    LaunchedEffect(Unit) {
        telephonyService.getStoredPingTests().collect { storedPingTests = it }
    }

    val scope = rememberCoroutineScope()

    val settingsManager = remember { SettingsManager.getInstance(context) }
    val speedTestReminder by settingsManager.speedTestReminderFlow.collectAsStateWithLifecycle()


    var showHowToUseSheet by remember { mutableStateOf(false) }

    LaunchedEffect(speedTestReminder) {
        val workManager = WorkManager.getInstance(context)
        if (speedTestReminder) {
            val reminderRequest = PeriodicWorkRequestBuilder<SpeedTestReminderWorker>(6, TimeUnit.HOURS)
                .addTag("speed_test_reminder")
                .build()
            workManager.enqueueUniquePeriodicWork(
                "speed_test_reminder",
                ExistingPeriodicWorkPolicy.UPDATE,
                reminderRequest
            )
        } else {
            workManager.cancelAllWorkByTag("speed_test_reminder")
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            while (isActive) {
                networkInfo = telephonyService.getNetworkInfo()
                if (!hasSavedInitialSignal && networkInfo.signalStrength.rsrp != 0) {
                    telephonyService.saveSignalData(networkInfo)
                    hasSavedInitialSignal = true
                }
                delay(2000)
            }
        }
    }

    LaunchedEffect(selectedTab) {
        val analytics = com.google.firebase.analytics.FirebaseAnalytics.getInstance(context)
        when (selectedTab) {
            1 -> analytics.logEvent("analytics_tab_viewed", null)
            2 -> analytics.logEvent("tools_tab_viewed", null)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = NeumorphicBackground,
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            Column(
                modifier = Modifier
                    .background(NeumorphicBackground)
                    .navigationBarsPadding()
            ) {
                // Hide banner ad for pro users
                if (!isPremiumPro) {
                    BannerAd()
                }
                NeumorphicBottomBar(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it }
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
            0 -> HomeTab(
                networkInfo = networkInfo,
                telephonyService = telephonyService,
                context = context,
                hasPermission = hasPermission,
                isUserPro = isUserPro,
                onHowToUseClick = { showHowToUseSheet = true },
                onUpgradeClick = { showPaywall = true },
                onWatchAdToUnlockClick = onWatchAdToUnlockClick
            )
            1 -> AnalyticsTab(
                networkInfo = networkInfo,
                signalHistory = storedSignalHistory,
                speedTests = storedSpeedTests,
                pingTests = storedPingTests,
                gameServers = gameServers,
                isUserPro = isUserPro,
                onUpgradeClick = { showPaywall = true },
                onWatchAdToUnlockClick = onWatchAdToUnlockClick
            )
            2 -> ToolsTab(
                telephonyService = telephonyService,
                isSpeedTestRunning = isSpeedTestRunning,
                speedTestPhase = speedTestPhase,
                isPingTestRunning = isPingTestRunning,
                speedTestResult = speedTestResult,
                pingTestResult = pingTestResult,
                gameServers = gameServers,
                isUserPro = isUserPro,
                onUpgradeClick = { showPaywall = true },
                onWatchAdToUnlockClick = onWatchAdToUnlockClick,
                isPingStabilizerEnabled = isPingStabilizerEnabled,
                onTogglePingStabilizer = { enabled, targetIp ->
                    if (enabled) {
                        if (!isUserPro) {
                            showPaywall = true
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
                            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            isPingStabilizerEnabled = true
                            val intent = Intent(context, PingStabilizerService::class.java).apply {
                                putExtra("TARGET_IP", targetIp)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                context.startForegroundService(intent)
                            } else {
                                context.startService(intent)
                            }
                        }
                    } else {
                        isPingStabilizerEnabled = false
                        val intent = Intent(context, PingStabilizerService::class.java).apply {
                            action = "STOP_SERVICE"
                        }
                        context.startService(intent)
                    }
                },
                onSpeedTestClick = {
                    if (!isSpeedTestRunning) {
                        isSpeedTestRunning = true
                        speedTestResult = SpeedTestResult(0.0, 0.0, 0, networkInfo.networkType, false)

                        scope.launch {
                            val pingJob = launch {
                                while (isActive) {
                                    val currentPing = telephonyService.measurePing()
                                    if (currentPing > 0) {
                                        speedTestResult = speedTestResult?.copy(ping = currentPing)
                                    }
                                    delay(1000)
                                }
                            }

                            speedTestPhase = "DOWNLOAD"
                            val download = telephonyService.measureDownloadSpeed { progress ->
                                speedTestResult = speedTestResult?.copy(downloadSpeed = progress)
                            }
                            speedTestResult = speedTestResult?.copy(downloadSpeed = download)

                            speedTestPhase = "UPLOAD"
                            val upload = telephonyService.measureUploadSpeed { progress ->
                                speedTestResult = speedTestResult?.copy(uploadSpeed = progress)
                            }
                            speedTestResult = speedTestResult?.copy(uploadSpeed = upload, success = true)

                            speedTestPhase = "DONE"
                            isSpeedTestRunning = false
                            pingJob.cancel()

                            speedTestResult?.let {
                                telephonyService.saveSpeedTestRecord(it.downloadSpeed, it.uploadSpeed, it.ping)
                            }
                        }
                    }
                },
                onPingTestClick = {
                    if (!isPingTestRunning) {
                        isPingTestRunning = true
                        scope.launch {
                            val result = telephonyService.runPingTest()
                            pingTestResult = result
                            isPingTestRunning = false
                        }
                    }
                },
                context = context
            )
            3 -> SettingsTab(themeManager, context)
        }

        // Paywall dialog
        if (showPaywall) {
            PremiumUpgradeScreen(
                onDismiss = { showPaywall = false },
                onWatchAdClick = {
                    showPaywall = false
                    onWatchAdToUnlockClick()
                }
            )
        }

        if (showHowToUseSheet) {
            HowToUseBottomSheet(onDismiss = { showHowToUseSheet = false })
        }
        
        // --- TEMP PRO AD LOGIC ---
        if (isWaitingForTempProAd) {
            val activity = context as? android.app.Activity
            LaunchedEffect(Unit) {
                if (activity != null) {
                    if (!AdManager.isRewardedAdAvailable() && !AdManager.isRewardedAdLoading()) {
                        AdManager.loadRewarded(activity)
                    }
                    val startTime = System.currentTimeMillis()
                    var adShownSuccess = false
                    while (System.currentTimeMillis() - startTime < 20000) {
                        if (AdManager.isRewardedAdAvailable()) {
                            AdManager.showRewarded(
                                activity = activity,
                                onAdShowed = { isWaitingForTempProAd = false },
                                onRewardEarned = {
                                    adShownSuccess = true
                                    ProStateManager.activateTempPro(context)
                                    android.widget.Toast.makeText(context, "Pro Unlocked for this session!", android.widget.Toast.LENGTH_LONG).show()
                                }
                            )
                            return@LaunchedEffect
                        }
                        kotlinx.coroutines.delay(100)
                    }
                    isWaitingForTempProAd = false
                    if (!adShownSuccess) {
                        showTempProLoadErrorDialog = true
                    }
                }
            }

            androidx.compose.ui.window.Dialog(
                onDismissRequest = {},
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .shadow(12.dp, RoundedCornerShape(24.dp))
                            .background(NeumorphicBackground, RoundedCornerShape(24.dp))
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = TextTeal,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Text(
                            text = "Sponsored Access",
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Watch a short ad to continue, or upgrade to Pro for ad-free access.",
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeumorphicBackground, RoundedCornerShape(12.dp))
                                .clickable { isWaitingForTempProAd = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                fontFamily = PoppinsFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        if (showTempProConsentDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showTempProConsentDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showTempProConsentDialog = false },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .shadow(12.dp, RoundedCornerShape(24.dp))
                            .background(NeumorphicBackground, RoundedCornerShape(24.dp))
                            .clickable(enabled = false) {} // Prevent click-through
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Watch Video to Unlock",
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "To access advanced network settings, please watch a short video ad. This supports our free service.",
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Cancel Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NeumorphicBackground, RoundedCornerShape(12.dp))
                                    .clickable { showTempProConsentDialog = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Cancel",
                                    fontFamily = PoppinsFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = TextSecondary
                                )
                            }

                            // Watch Ad Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        brush = Brush.linearGradient(GradientColors),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable {
                                        showTempProConsentDialog = false
                                        isWaitingForTempProAd = true 
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Watch Video",
                                    fontFamily = PoppinsFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showTempProLoadErrorDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showTempProLoadErrorDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable { showTempProLoadErrorDialog = false },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .shadow(12.dp, RoundedCornerShape(24.dp))
                            .background(NeumorphicBackground, RoundedCornerShape(24.dp))
                            .clickable(enabled = false) {}
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Ads Available",
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Sorry, we couldn't load an ad right now. Please try again later.",
                            fontFamily = PoppinsFamily,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showTempProLoadErrorDialog = false },
                            colors = ButtonDefaults.buttonColors(containerColor = TextTeal),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Okay", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    }


}

@Composable
fun NeumorphicBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val navItems = listOf(
        NavItem(R.drawable.home, "Home"),
        NavItem(R.drawable.analytics, "Analytics"),
        NavItem(R.drawable.tools, "Tools"),
        NavItem(R.drawable.settings, "Settings")
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .shadow(8.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(NeumorphicBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, item ->
                val selected = selectedTab == index
                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.1f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "scale"
                )

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onTabSelected(index) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(scale)
                            .then(
                                if (selected) {
                                    Modifier
                                        .background(
                                            brush = Brush.linearGradient(GradientColors),
                                            shape = CircleShape
                                        )
                                } else Modifier
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = item.icon),
                            contentDescription = item.label,
                            modifier = Modifier.size(24.dp),
                            tint = if (selected) Color.White else TextSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = item.label,
                        style = Typography.labelSmall.copy(fontSize = 10.sp),
                        color = if (selected) GradientEnd else TextSecondary,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

data class NavItem(val icon: Int, val label: String)

// ---------------------------------------------------------
// REUSABLE COMPONENTS
// ---------------------------------------------------------

// ---------------------------------------------------------
// TAB 0: HOME
// ---------------------------------------------------------

@Composable
fun HomeTab(
    networkInfo: NetworkInfo,
    telephonyService: TelephonyService,
    context: Context,
    hasPermission: Boolean = true,
    isUserPro: Boolean = false,
    onHowToUseClick: () -> Unit = {},
    onUpgradeClick: () -> Unit = {},
    onWatchAdToUnlockClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    var showDeviceCodesSheet by remember { mutableStateOf(false) }
    var pendingReviewTrigger by remember { mutableStateOf(false) }
    var isWaitingForAd by remember { mutableStateOf(false) }
    var showLoadErrorDialog by remember { mutableStateOf(false) }

    val isPremiumPro by ProStateManager.isPremiumPro.collectAsStateWithLifecycle()
    val isTempProActive by ProStateManager.isTempProActive.collectAsStateWithLifecycle()
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ProStateManager.checkEntitlement()
                if (pendingReviewTrigger) {
                    pendingReviewTrigger = false
                    val activity = context as? android.app.Activity
                    if (activity != null) {
                        val manager = ReviewManagerFactory.create(context)
                        val request = manager.requestReviewFlow()
                        request.addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val reviewInfo = task.result
                                manager.launchReviewFlow(activity, reviewInfo)
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "4G LTE Switcher",
                    style = Typography.titleLarge.copy(fontSize = 28.sp),
                    color = TextPrimary
                )
                IconButton(
                    onClick = onHowToUseClick,
                    modifier = Modifier
                        .size(40.dp)
                        .neumorphic(cornerRadius = 12.dp, elevation = 4.dp)
                        .background(NeumorphicBackground, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.question),
                        contentDescription = "How to Use",
                        tint = GradientStart,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CarrierInfoCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    networkInfo = networkInfo,
                    telephonyService = telephonyService
                )
                SignalStrengthCard(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    networkInfo = networkInfo,
                    hasPermission = hasPermission
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            MainForce4GButton(onClick = {
                pendingReviewTrigger = true
                val activity = context as? android.app.Activity
                val openInfo = {
                    isWaitingForAd = false
                    try {
                        RadioInfoHelper.openRadioInfo(context)
                    } catch (e: Exception) {
                        showDeviceCodesSheet = true
                    }
                }

                if (activity != null && !isPremiumPro) {
                    if (AdManager.canShowInterstitialAd()) {
                        isWaitingForAd = true
                    } else {
                        openInfo()
                    }
                } else {
                    openInfo()
                }
            })

            Spacer(modifier = Modifier.height(32.dp))

            // Show countdown if temporary Pro is active
            if (isTempProActive && !isPremiumPro) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF00B0FF), Color(0xFF00E5FF)),
                                start = Offset.Zero,
                                end = Offset(400f, 0f)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "✨ Pro Unlocked for this Session",
                                style = Typography.titleMedium.copy(fontSize = 18.sp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "All premium tools unlocked until you close the app.",
                                style = Typography.bodyMedium.copy(fontSize = 13.sp),
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Upgrade to Pro / Watch ad to try Pro — hidden for permanent/temporary pro users
            if (!isUserPro) {
                // Upgrade to Pro Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF6C63FF), Color(0xFFE040FB)),
                                start = Offset.Zero,
                                end = Offset(400f, 0f)
                            )
                        )
                        .clickable { onUpgradeClick() }
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Upgrade to Pro",
                                style = Typography.titleMedium.copy(fontSize = 18.sp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Remove all ads & unlock premium features",
                                style = Typography.bodyMedium.copy(fontSize = 13.sp),
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.pro),
                            contentDescription = "Upgrade",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Watch Ad to Try Pro Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = GradientColors,
                                start = Offset.Zero,
                                end = Offset(400f, 0f)
                            )
                        )
                        .clickable {
                            onWatchAdToUnlockClick()
                        }
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Watch Ad to Unlock Pro",
                                style = Typography.titleMedium.copy(fontSize = 18.sp),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Get full access for this session for free.",
                                style = Typography.bodyMedium.copy(fontSize = 13.sp),
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.gift),
                            contentDescription = "Try Pro",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            DataSpeedCard(speedInfo = networkInfo.speedInfo, context = context)
            
            Spacer(modifier = Modifier.height(24.dp))
        }

        if (showDeviceCodesSheet) {
            DeviceCodesBottomSheet(onDismiss = { showDeviceCodesSheet = false })
        }

        if (isWaitingForAd) {
            val activity = context as? android.app.Activity
            LaunchedEffect(Unit) {
                if (activity != null) {
                    if (!AdManager.isRewardedInterstitialAdAvailable() && !AdManager.isRewardedInterstitialAdLoading()) {
                        AdManager.loadRewardedInterstitial(activity)
                    }
                }
                
                // Provide sufficient time (3.5s) for the user to read the intro screen and opt-out if desired.
                delay(3500)
                
                val startTime = System.currentTimeMillis()
                var adShownSuccess = false
                while (System.currentTimeMillis() - startTime < 16500) {
                    if (AdManager.isRewardedInterstitialAdAvailable()) {
                        if (activity != null) {
                            AdManager.showRewardedInterstitial(
                                activity = activity,
                                onAdShowed = { isWaitingForAd = false },
                                onRewardEarned = {
                                    adShownSuccess = true
                                    try {
                                        RadioInfoHelper.openRadioInfo(context)
                                    } catch (e: Exception) {
                                        showDeviceCodesSheet = true
                                    }
                                }
                            )
                        }
                        return@LaunchedEffect
                    }
                    if (!AdManager.isRewardedInterstitialAdLoading() && !AdManager.isRewardedInterstitialAdAvailable() && activity != null) {
                        AdManager.loadRewardedInterstitial(activity)
                    }
                    delay(200)
                }
                isWaitingForAd = false
                if (!adShownSuccess) {
                    try {
                        RadioInfoHelper.openRadioInfo(context)
                    } catch (e: Exception) {
                        showDeviceCodesSheet = true
                    }
                }
            }

            androidx.compose.ui.window.Dialog(
                onDismissRequest = { /* Prevent dismiss */ },
                properties = androidx.compose.ui.window.DialogProperties(
                    dismissOnBackPress = false,
                    dismissOnClickOutside = false,
                    usePlatformDefaultWidth = false
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .shadow(12.dp, RoundedCornerShape(24.dp))
                            .background(NeumorphicBackground, RoundedCornerShape(24.dp))
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = TextTeal,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        
                        Text(
                            text = "Sponsored Access",
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "Watch a short ad to continue, or upgrade to Pro for ad-free access.",
                            fontFamily = PoppinsFamily,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NeumorphicBackground, RoundedCornerShape(12.dp))
                                .clickable { isWaitingForAd = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Cancel",
                                fontFamily = PoppinsFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

    }
}

// ---------------------------------------------------------
// TAB 1: ANALYTICS
// ---------------------------------------------------------

@Composable
fun AnalyticsTab(
    networkInfo: NetworkInfo,
    signalHistory: List<SignalHistoryEntity>,
    speedTests: List<SpeedTestEntity>,
    pingTests: List<PingTestEntity>,
    gameServers: List<GameServer>,
    isUserPro: Boolean = false,
    onUpgradeClick: () -> Unit = {},
    onWatchAdToUnlockClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    val currentRsrp = networkInfo.signalStrength.rsrp
    val signalColor = getSignalLevelColor(networkInfo.signalStrength.level)
    val signalRsrpHistory = signalHistory.map { it.rsrp }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main analytics content — blurred when not pro
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState, enabled = isUserPro)
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                .then(if (!isUserPro) Modifier.blur(15.dp) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Analytics",
                style = Typography.titleLarge.copy(fontSize = 28.sp),
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))

            GlobalServerLatencyCard(
                gameServers = gameServers
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            PingStabilizerHistoryChart()
            
            Spacer(modifier = Modifier.height(16.dp))

            // Signal History Card
            AnalyticsCard(
                title = "SIGNAL HISTORY",
                subtitle = "Collected while app is running"
            ) {
                if (signalRsrpHistory.isNotEmpty()) {
                    SignalHistoryChart(
                        history = signalRsrpHistory,
                        modifier = Modifier.fillMaxWidth().height(100.dp),
                        color = signalColor
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Min: ${signalRsrpHistory.minOrNull() ?: 0} dBm", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                        Text("Avg: ${signalRsrpHistory.average().toInt()} dBm", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                        Text("Max: ${signalRsrpHistory.maxOrNull() ?: 0} dBm", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("Collecting signal data...", style = Typography.bodyMedium, color = TextSecondary)
                    }
                }
            }


            Spacer(modifier = Modifier.height(16.dp))

            // Speed Test History Card
            AnalyticsCard(
                title = "SPEED TEST HISTORY",
                subtitle = "From Tools page"
            ) {
                if (speedTests.isNotEmpty()) {
                    var selectedTest by remember { mutableStateOf<SpeedTestEntity?>(null) }

                    SpeedTestBarChart(
                        speedTests = speedTests.takeLast(10).reversed(),
                        onTestClick = { selectedTest = it }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${speedTests.size} tests", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                        Text("Best: ${String.format("%.1f", speedTests.maxOfOrNull { maxOf(it.downloadSpeed, it.uploadSpeed) } ?: 0.0)} Mbps", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                    }

                    if (selectedTest != null) {
                        SpeedTestDetailSheet(
                            test = selectedTest!!,
                            onDismiss = { selectedTest = null }
                        )
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("Run speed test in Tools", style = Typography.bodyMedium, color = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ping Test History Card
            AnalyticsCard(
                title = "PING TEST HISTORY",
                subtitle = "From Tools page"
            ) {
                if (pingTests.isNotEmpty()) {
                    PingHistoryChart(
                        history = pingTests.takeLast(30).map { it.latency },
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${pingTests.size} tests", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                        Text("Avg: ${pingTests.takeLast(30).map { it.latency }.average().toInt()} ms", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                        Text("Run ping test in Tools", style = Typography.bodyMedium, color = TextSecondary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Lock overlay for non-pro users
        if (!isUserPro) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 48.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF6C63FF), Color(0xFFE040FB))
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.subscription),
                            contentDescription = "Locked",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Pro Analytics Locked",
                        style = Typography.titleLarge.copy(fontSize = 22.sp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Upgrade to view detailed signal history, speed trends, and ping analytics.",
                        style = Typography.bodyMedium.copy(fontSize = 14.sp),
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF6C63FF), Color(0xFFE040FB))
                                )
                            )
                            .clickable { onUpgradeClick() }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Upgrade to Pro",
                            color = Color.White,
                            style = Typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Watch Ad to Unlock Alternative
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                            .clickable { onWatchAdToUnlockClick() }
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Watch Ad to Unlock",
                            color = Color.White,
                            style = Typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsCard(title: String, subtitle: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 24.dp, elevation = 6.dp)
            .background(NeumorphicBackground, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column {
            Text(title, style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary, letterSpacing = 1.sp)
            Text(subtitle, style = Typography.titleMedium.copy(fontSize = 16.sp), color = TextPrimary, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

// ---------------------------------------------------------
// TAB 2: TOOLS
// ---------------------------------------------------------

@Composable
private fun rememberAnimatedDots(): String {
    var dots by remember { mutableStateOf(".") }
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(500)
            dots = when (dots) {
                "." -> ".."
                ".." -> "..."
                else -> "."
            }
        }
    }
    return dots
}

@Composable
fun ToolsTab(
    telephonyService: TelephonyService,
    isSpeedTestRunning: Boolean,
    speedTestPhase: String,
    isPingTestRunning: Boolean,
    speedTestResult: SpeedTestResult?,
    pingTestResult: PingTestResult?,
    gameServers: List<GameServer>,
    isUserPro: Boolean = false,
    onUpgradeClick: () -> Unit = {},
    isPingStabilizerEnabled: Boolean = false,
    onTogglePingStabilizer: (Boolean, String) -> Unit = { _, _ -> },
    onSpeedTestClick: () -> Unit,
    onPingTestClick: () -> Unit,
    context: Context,
    onWatchAdToUnlockClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    
    var localIp by remember { mutableStateOf("Loading...") }
    var publicIp by remember { mutableStateOf("Loading...") }
    var wifiSpeed by remember { mutableStateOf("Loading...") }
    var showApnButton by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        localIp = NetworkInfoHelper.getLocalIpAddress()
        wifiSpeed = NetworkInfoHelper.getWifiLinkSpeed(context)
        publicIp = NetworkInfoHelper.getPublicIpAddress()
        
        // Check if APN Settings intent is resolvable
        showApnButton = RadioInfoHelper.canResolveIntent(context, Intent(Settings.ACTION_APN_SETTINGS))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Tools",
            style = Typography.titleLarge.copy(fontSize = 28.sp),
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        NetworkInfoCard(localIp = localIp, publicIp = publicIp, wifiSpeed = wifiSpeed)
        
        Spacer(modifier = Modifier.height(16.dp))
        
        val settingsManager = remember { SettingsManager.getInstance(context) }
        PingStabilizerCard(
            isPingStabilizerEnabled = isPingStabilizerEnabled,
            onTogglePingStabilizer = onTogglePingStabilizer,
            isUserPro = isUserPro,
            onUpgradeClick = onUpgradeClick,
            settingsManager = settingsManager
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        GameServersPingCard(gameServers, telephonyService, isUserPro, onUpgradeClick)

        Spacer(modifier = Modifier.height(16.dp))

        // Speed Test Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphic(cornerRadius = 24.dp, elevation = 6.dp)
                .background(NeumorphicBackground, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("SPEED TEST", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary, letterSpacing = 1.sp)
                Text("Network Speed", style = Typography.titleMedium.copy(fontSize = 18.sp), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(24.dp))

                SpeedometerProgress(
                    speed = if (!isSpeedTestRunning && (speedTestPhase == "DONE" || speedTestPhase == "IDLE")) {
                        0.0
                    } else {
                        when (speedTestPhase) {
                            "PING" -> (speedTestResult?.ping ?: 0).toDouble()
                            "UPLOAD" -> speedTestResult?.uploadSpeed ?: 0.0
                            else -> speedTestResult?.downloadSpeed ?: 0.0
                        }
                    },
                    isTesting = isSpeedTestRunning,
                    phase = speedTestPhase,
                    modifier = Modifier.size(200.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))

                val animatedDots = rememberAnimatedDots()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SpeedColumn(
                        icon = R.drawable.download,
                        label = "DOWNLOAD",
                        value = if (speedTestPhase == "IDLE" && speedTestResult != null) String.format("%.1f", speedTestResult?.downloadSpeed ?: 0.0)
                                else if (speedTestPhase == "DOWNLOAD") animatedDots
                                else if (speedTestResult?.downloadSpeed != null && speedTestResult.downloadSpeed > 0) String.format("%.1f", speedTestResult.downloadSpeed)
                        else "...",
                        unit = "Mbps",
                        modifier = Modifier.weight(1f)
                    )
                    SpeedColumn(
                        icon = R.drawable.upload,
                        label = "UPLOAD",
                        value = if (speedTestPhase == "IDLE" && speedTestResult != null) String.format("%.1f", speedTestResult?.uploadSpeed ?: 0.0)
                                else if (speedTestPhase == "UPLOAD") animatedDots
                                else if (speedTestResult?.uploadSpeed != null && speedTestResult.uploadSpeed > 0) String.format("%.1f", speedTestResult.uploadSpeed)
                        else "...",
                        unit = "Mbps",
                        modifier = Modifier.weight(1f)
                    )
                    SpeedColumn(
                        icon = R.drawable.ping,
                        label = "PING",
                        value = if (speedTestPhase == "IDLE" && speedTestResult != null) "${speedTestResult?.ping ?: 0}"
                                else if (speedTestPhase == "PING") animatedDots
                                else if (speedTestResult?.ping != null && speedTestResult.ping > 0) "${speedTestResult.ping}"
                        else "...",
                        unit = "ms",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(
                            if (isSpeedTestRunning) SolidColor(NeumorphicDarkShadow)
                            else Brush.linearGradient(GradientColors)
                        )
                        .clickable(enabled = !isSpeedTestRunning) { onSpeedTestClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSpeedTestRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("START SPEED TEST", style = Typography.labelMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Ping Test Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphic(cornerRadius = 24.dp, elevation = 6.dp)
                .background(NeumorphicBackground, RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column {
                Text("PING TEST", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary, letterSpacing = 1.sp)
                Text("Latency Check", style = Typography.titleMedium.copy(fontSize = 18.sp), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isPingTestRunning) "..." else "${pingTestResult?.latency ?: 0}",
                            style = Typography.titleLarge.copy(fontSize = 48.sp),
                            color = if ((pingTestResult?.latency ?: 0) < 50) TextTeal else if ((pingTestResult?.latency ?: 0) < 100) Color(0xFFFF9800) else Color.Red,
                            fontWeight = FontWeight.Bold
                        )
                        Text("ms", style = Typography.labelMedium.copy(fontSize = 14.sp), color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(
                            if (isPingTestRunning) SolidColor(NeumorphicDarkShadow)
                            else Brush.linearGradient(GradientColors)
                        )
                        .clickable(enabled = !isPingTestRunning) { onPingTestClick() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isPingTestRunning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("START PING TEST", style = Typography.labelMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // APN Settings Card
        if (showApnButton) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(cornerRadius = 24.dp, elevation = 6.dp)
                    .background(NeumorphicBackground, RoundedCornerShape(24.dp))
                    .padding(20.dp)
                    .clickable {
                        try {
                            val intent = Intent(Settings.ACTION_APN_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "APN settings blocked on this device", Toast.LENGTH_SHORT).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = androidx.compose.ui.res.painterResource(id = R.drawable.apn), contentDescription = null, tint = TextPrimary, modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("APN SETTINGS", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary, letterSpacing = 1.sp)
                            Text("Network Configuration", style = Typography.titleMedium.copy(fontSize = 16.sp), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Icon(androidx.compose.ui.res.painterResource(id = R.drawable.arrow_forward), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SpeedColumn(icon: Int, label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Icon(painter = androidx.compose.ui.res.painterResource(id = icon), contentDescription = null, modifier = Modifier.size(24.dp), tint = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = Typography.titleLarge.copy(fontSize = 22.sp), color = TextPrimary, fontWeight = FontWeight.Bold)
        Text(unit, style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
        Text(label, style = Typography.labelMedium.copy(fontSize = 9.sp), color = TextSecondary)
    }
}

@Composable
private fun PingHistoryChart(history: List<Int>, modifier: Modifier = Modifier) {
    if (history.isEmpty()) return

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val animationProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "PingChartAnimation"
    )

    BoxWithConstraints(modifier = modifier) {
        val scrollState = rememberScrollState()
        LaunchedEffect(scrollState.maxValue) {
            scrollState.scrollTo(scrollState.maxValue)
        }
        
        val minWidth = maxWidth
        val itemWidth = 30.dp
        val requiredWidth = maxOf(minWidth, itemWidth * history.size)
        
        val reversedHistory = history.reversed()

        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            Canvas(modifier = Modifier.width(requiredWidth).fillMaxHeight()) {
                val width = size.width
                val height = size.height
                val xPadding = if (reversedHistory.size > 1) 20f else width / 2f
                val chartWidth = if (reversedHistory.size > 1) width - (xPadding * 2) else width
                
                val maxPing = (reversedHistory.maxOrNull() ?: 100).coerceAtLeast(100).toFloat()
                val yRange = maxPing * 1.2f // Add 20% headroom

                val coords = reversedHistory.mapIndexed { index, ping ->
                    val x = if (reversedHistory.size == 1) xPadding else xPadding + (index.toFloat() / (reversedHistory.size - 1)) * chartWidth
                    val y = height - ((ping.toFloat() / yRange) * height)
                    Offset(x, y)
                }

                if (coords.size >= 2) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(coords[0].x, coords[0].y)
                        for (i in 0 until coords.size - 1) {
                            val p1 = coords[i]
                            val p2 = coords[i + 1]
                            val cp1x = (p1.x + p2.x) / 2f
                            val cp1y = p1.y
                            val cp2x = (p1.x + p2.x) / 2f
                            val cp2y = p2.y
                            cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                        }
                    }

                    clipRect(right = width * animationProgress) {
                        val fillPath = androidx.compose.ui.graphics.Path().apply {
                            addPath(path)
                            lineTo(coords.last().x, height)
                            lineTo(coords.first().x, height)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(androidx.compose.ui.graphics.Color(0xFF8E99F3).copy(alpha = 0.5f), Color.Transparent)
                            )
                        )

                        drawPath(
                            path = path,
                            color = androidx.compose.ui.graphics.Color(0xFF8E99F3),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 3.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )

                        coords.forEachIndexed { index, coord ->
                            val color = when {
                                history[index] < 50 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                                history[index] < 100 -> androidx.compose.ui.graphics.Color(0xFFFF9800)
                                else -> androidx.compose.ui.graphics.Color(0xFFFF5722)
                            }
                            drawCircle(color = color, radius = 5.dp.toPx(), center = coord)
                            drawCircle(
                                color = Color.White.copy(alpha = 0.4f),
                                radius = 8.dp.toPx(),
                                center = coord,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                } else if (coords.size == 1) {
                    val coord = coords[0]
                    clipRect(right = width * animationProgress) {
                        val color = when {
                            history[0] < 50 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                            history[0] < 100 -> androidx.compose.ui.graphics.Color(0xFFFF9800)
                            else -> androidx.compose.ui.graphics.Color(0xFFFF5722)
                        }
                        drawCircle(color = color, radius = 8.dp.toPx(), center = coord)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.4f),
                            radius = 12.dp.toPx(),
                            center = coord,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedHistoryChart(history: List<SpeedTestEntity>, modifier: Modifier = Modifier) {
    if (history.isEmpty()) return

    val maxDownload = history.maxOfOrNull { it.downloadSpeed }?.toFloat() ?: 1f
    val maxUpload = history.maxOfOrNull { it.uploadSpeed }?.toFloat() ?: 1f
    val maxSpeed = maxOf(maxDownload, maxUpload, 1f)

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val animationProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "SpeedChartAnimation"
    )

    BoxWithConstraints(modifier = modifier) {
        val scrollState = rememberScrollState()
        LaunchedEffect(scrollState.maxValue) {
            scrollState.scrollTo(scrollState.maxValue)
        }
        val minWidth = maxWidth
        val itemWidth = 40.dp
        val requiredWidth = maxOf(minWidth, itemWidth * history.size)
        
        val reversedHistory = history.reversed()

        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            Canvas(modifier = Modifier.width(requiredWidth).fillMaxHeight()) {
                val width = size.width
                val height = size.height
                val barWidth = width / reversedHistory.size

                reversedHistory.forEachIndexed { index, test ->
                    val targetDownHeight = height * ((test.downloadSpeed.toFloat() / maxSpeed).coerceIn(0.05f, 1f))
                    val targetUpHeight = height * ((test.uploadSpeed.toFloat() / maxSpeed).coerceIn(0.05f, 1f))
                    
                    val downHeight = targetDownHeight * animationProgress
                    val upHeight = targetUpHeight * animationProgress
                    val x = index * barWidth

                    val downGradient = Brush.verticalGradient(
                        colors = listOf(androidx.compose.ui.graphics.Color(0xFF8E99F3), androidx.compose.ui.graphics.Color(0xFF8E99F3).copy(alpha = 0.5f)),
                        startY = height - downHeight,
                        endY = height
                    )

                    val upGradient = Brush.verticalGradient(
                        colors = listOf(androidx.compose.ui.graphics.Color(0xFF5C6BC0), androidx.compose.ui.graphics.Color(0xFF5C6BC0).copy(alpha = 0.5f)),
                        startY = height - upHeight,
                        endY = height
                    )

                    val halfBarWidth = (barWidth / 2f) - 4f
                    
                    drawRoundRect(
                        brush = downGradient,
                        topLeft = androidx.compose.ui.geometry.Offset(x + 2f, height - downHeight),
                        size = androidx.compose.ui.geometry.Size(halfBarWidth, downHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(halfBarWidth / 2f, halfBarWidth / 2f)
                    )

                    drawRoundRect(
                        brush = upGradient,
                        topLeft = androidx.compose.ui.geometry.Offset(x + (barWidth / 2f) + 2f, height - upHeight),
                        size = androidx.compose.ui.geometry.Size(halfBarWidth, upHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(halfBarWidth / 2f, halfBarWidth / 2f)
                    )
                }
            }
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    if (timestamp == 0L) return "--"
    val sdf = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

// ---------------------------------------------------------
// TAB 3: SETTINGS
// ---------------------------------------------------------

@Composable
fun SettingsTab(themeManager: ThemeManager, context: Context) {
    val scrollState = rememberScrollState()
    var currentTheme by remember { mutableStateOf(themeManager.currentTheme) }
    var showAboutSheet by remember { mutableStateOf(false) }
    val settingsManager = remember { SettingsManager.getInstance(context) }

    val speedTestReminder by settingsManager.speedTestReminderFlow.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Settings",
            style = Typography.titleLarge.copy(fontSize = 28.sp),
            color = TextPrimary,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))

        // App Theme
        SettingsCard(title = "App Theme", icon = R.drawable.theme) {
            Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(NeumorphicDarkShadow.copy(alpha=0.3f)).padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                ThemeOptionButton(
                    text = "Light",
                    isSelected = currentTheme == AppTheme.LIGHT,
                    onClick = {
                        currentTheme = AppTheme.LIGHT
                        themeManager.currentTheme = AppTheme.LIGHT
                    }
                )
                ThemeOptionButton(
                    text = "Dark",
                    isSelected = currentTheme == AppTheme.DARK,
                    onClick = {
                        currentTheme = AppTheme.DARK
                        themeManager.currentTheme = AppTheme.DARK
                    }
                )
                ThemeOptionButton(
                    text = "Auto",
                    isSelected = currentTheme == AppTheme.SYSTEM,
                    onClick = {
                        currentTheme = AppTheme.SYSTEM
                        themeManager.currentTheme = AppTheme.SYSTEM
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Notifications
        SettingsCard(title = "Notifications", icon = R.drawable.notification) {
            ToggleRow("Speed Test Reminder", speedTestReminder, onToggleChange = { settingsManager.setSpeedTestReminder(it) })
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Help & Support
        SettingsCard(
            title = "Help & Support",
            icon = R.drawable.support,
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Manage Subscription (RevenueCat Customer Center)
        SettingsCard(
            title = "Manage Subscription",
            icon = R.drawable.subscription,
            onClick = {
                Purchases.sharedInstance.getCustomerInfoWith(
                    onError = {
                        Toast.makeText(context, "Unable to load subscription info", Toast.LENGTH_SHORT).show()
                    },
                    onSuccess = { customerInfo ->
                        val url = customerInfo.managementURL?.toString()
                            ?: "https://play.google.com/store/account/subscriptions"
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to open subscriptions", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard(
            title = "About",
            icon = R.drawable.about,
            onClick = { showAboutSheet = true }
        )

        if (showAboutSheet) {
            AboutBottomSheet(onDismiss = { showAboutSheet = false })
        }
    }
}

@Composable
fun SettingsActionRow(title: String, trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = Typography.bodyMedium, color = TextPrimary)
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        } else {
            Icon(androidx.compose.ui.res.painterResource(id = R.drawable.arrow_forward), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun RowScope.ThemeOptionButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) NeumorphicBackground else Color.Transparent)
            .clickable { onClick() }
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = Typography.labelMedium,
            color = if (isSelected) GradientStart else TextSecondary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun SettingsCard(
    title: String,
    icon: Int,
    onClick: (() -> Unit)? = null,
    content: @Composable (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .neumorphic(cornerRadius = 20.dp, elevation = 6.dp)
            .background(NeumorphicBackground, RoundedCornerShape(20.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(20.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painter = androidx.compose.ui.res.painterResource(id = icon), contentDescription = null, tint = GradientStart, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(title, style = Typography.titleMedium.copy(fontSize = 16.sp), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                if (onClick != null) {
                    Icon(androidx.compose.ui.res.painterResource(id = R.drawable.arrow_forward), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
            if (content != null) {
                Spacer(modifier = Modifier.height(16.dp))
                content()
            }
        }
    }
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onToggleChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Typography.bodyMedium, color = TextPrimary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggleChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GradientStart)
        )
    }
}

// ---------------------------------------------------------
// REUSED TAB 0 COMPONENTS
// ---------------------------------------------------------

@Composable
fun CarrierInfoCard(
    modifier: Modifier = Modifier,
    networkInfo: NetworkInfo,
    telephonyService: TelephonyService
) {
    val band = remember(networkInfo) { telephonyService.getBand() }
    val cellId = remember(networkInfo) { telephonyService.getCellId() }

    Box(
        modifier = modifier
            .neumorphic(cornerRadius = 20.dp, elevation = 6.dp)
            .background(NeumorphicBackground, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("CARRIER", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                    Text(
                        text = networkInfo.carrierName,
                        style = Typography.titleMedium.copy(fontSize = 16.sp),
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Box(
                    modifier = Modifier
                        .background(Brush.linearGradient(GradientColors), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (networkInfo.networkType.contains("LTE")) "LTE"
                               else if (networkInfo.networkType.contains("5G")) "5G"
                               else "4G",
                        style = Typography.labelMedium.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(GradientStart.copy(alpha = 0.1f), Color.Transparent)), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(if (networkInfo.isConnected) TextTeal else Color.Red.copy(alpha = 0.7f), CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (networkInfo.isConnected) "Connected" else "Disconnected",
                    style = Typography.bodyMedium.copy(fontSize = 12.sp),
                    color = if (networkInfo.isConnected) TextTeal else Color.Red.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (networkInfo.isLocationEnabled) TextTeal.copy(alpha = 0.1f)
                        else Color.Red.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (networkInfo.isLocationEnabled) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = if (networkInfo.isRoaming) R.drawable.public_ip else R.drawable.home),
                        contentDescription = null,
                        tint = TextTeal,
                        modifier = Modifier.size(14.dp)
                    )
                } else {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(id = R.drawable.location_off),
                        contentDescription = null,
                        tint = Color.Red.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    if (networkInfo.isLocationEnabled) {
                        "${networkInfo.simOperator} | ${if (networkInfo.isRoaming) "Roaming" else "Home Network"}"
                    } else "Location OFF - Band/Cell ID hidden",
                    style = Typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (networkInfo.isLocationEnabled) TextTeal else Color.Red.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            InfoRow(label = "Network", value = networkInfo.networkType.ifEmpty { "--" })
            InfoRow(label = "Band", value = band)
            InfoRow(label = "MCC/MNC", value = networkInfo.operatorCode)
            InfoRow(label = "Cell ID", value = cellId)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = Typography.labelMedium.copy(fontSize = 11.sp), color = TextSecondary)
        Text(value, style = Typography.bodyMedium.copy(fontSize = 11.sp), color = TextPrimary)
    }
}

@Composable
fun SignalStrengthCard(
    modifier: Modifier = Modifier,
    networkInfo: NetworkInfo,
    hasPermission: Boolean = true,
    onRequestPermission: () -> Unit = {}
) {
    val signal = networkInfo.signalStrength
    val signalLevel = getSignalLevelName(signal.level)
    val signalColor = getSignalLevelColor(signal.level)
    val barsCount = getBarsCount(signal.level)

    val isSignalAvailable = signal.rsrp != 0 || signal.rssi != 0

    Box(
        modifier = modifier
            .neumorphic(cornerRadius = 20.dp, elevation = 6.dp)
            .background(NeumorphicBackground, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("SIGNAL", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
            Text("Reference Signal", style = Typography.titleMedium.copy(fontSize = 16.sp), color = TextPrimary, fontWeight = FontWeight.SemiBold)

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                SignalBars(barsCount)
            }

            if (!hasPermission) {
                val errorColor = if (globalIsDarkTheme) Color(0xFFFF5252) else Color(0xFFD32F2F)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(errorColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Permission required",
                            tint = errorColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Permission Required",
                            style = Typography.labelMedium.copy(fontSize = 11.sp),
                            color = errorColor,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Grant permissions to view signal",
                            style = Typography.labelMedium.copy(fontSize = 9.sp),
                            color = TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            } else if (!isSignalAvailable) {
                val infoColor = if (globalIsDarkTheme) Color(0xFFFFD700) else Color(0xFFF59E0B)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(infoColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "No signal",
                            tint = infoColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Signal Unavailable",
                            style = Typography.labelMedium.copy(fontSize = 11.sp),
                            color = infoColor,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Check airplane mode or network",
                            style = Typography.labelMedium.copy(fontSize = 9.sp),
                            color = TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            } else {
                Text(
                    text = "${signal.rsrp} dBm",
                    style = Typography.titleLarge.copy(fontSize = 28.sp),
                    color = signalColor,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = signalLevel,
                    style = Typography.labelMedium.copy(fontSize = 11.sp),
                    color = signalColor,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(GradientStart.copy(alpha = 0.08f), Color.Transparent)), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SignalMetricRow("RSRP", "${signal.rsrp} dBm")
                    SignalMetricRow("RSRQ", "${signal.rsrq} dB")
                    SignalMetricRow("SINR", "${signal.sinr} dB")
                    SignalMetricRow("RSSI", "${signal.rssi} dBm")
                }
            }
        }
    }
}

@Composable
private fun SignalBars(activeBars: Int) {
    if (activeBars == 0) {
        Box(
            modifier = Modifier.height(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val barHeights = listOf(0.3f, 0.5f, 0.7f, 1.0f)
                barHeights.forEachIndexed { index, height ->
                    Box(
                        modifier = Modifier
                            .width(12.dp)
                            .fillMaxHeight(height)
                            .clip(RoundedCornerShape(4.dp))
                            .background(TextSecondary.copy(alpha = 0.3f))
                    )
                }
            }
        }
        return
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(32.dp)
    ) {
        val barHeights = listOf(0.3f, 0.5f, 0.7f, 1.0f)
        barHeights.forEachIndexed { index, height ->
            val isActive = index < activeBars
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isActive && index == 3 -> GradientEnd
                            isActive && index == 2 -> GradientMiddle
                            isActive && index == 1 -> GradientStart.copy(alpha = 0.6f)
                            isActive -> GradientStart.copy(alpha = 0.4f)
                            else -> TextSecondary.copy(alpha = 0.3f)
                        }
                    )
            )
        }
    }
}

private fun getSignalLevelName(level: SignalLevel): String {
    return when (level) {
        SignalLevel.EXCELLENT -> "Excellent Signal"
        SignalLevel.GOOD -> "Good Signal"
        SignalLevel.FAIR -> "Fair Signal"
        SignalLevel.POOR -> "Poor Signal"
        SignalLevel.NO_SIGNAL -> "No Signal"
        SignalLevel.UNKNOWN -> "Unknown"
    }
}

private fun getSignalLevelColor(level: SignalLevel): Color {
    return when (level) {
        SignalLevel.EXCELLENT -> Color(0xFF4CAF50)
        SignalLevel.GOOD -> TextTeal
        SignalLevel.FAIR -> Color(0xFFFF9800)
        SignalLevel.POOR -> Color(0xFFFF5722)
        SignalLevel.NO_SIGNAL -> Color.Red
        SignalLevel.UNKNOWN -> TextSecondary
    }
}

private fun getBarsCount(level: SignalLevel): Int {
    return when (level) {
        SignalLevel.EXCELLENT -> 4
        SignalLevel.GOOD -> 3
        SignalLevel.FAIR -> 2
        SignalLevel.POOR -> 1
        SignalLevel.NO_SIGNAL, SignalLevel.UNKNOWN -> 0
    }
}

private data class BarData(val height: Float, val color: Color)

@Composable
private fun SignalMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
        Text(value, style = Typography.bodyMedium.copy(fontSize = 10.sp), color = TextPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun MainForce4GButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(160.dp)
            .neumorphic(cornerRadius = 80.dp, elevation = 12.dp)
            .background(NeumorphicBackground, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = GradientColors,
                        start = Offset(0f, 0f),
                        end = Offset(120f, 120f)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "4G",
                    style = Typography.titleLarge.copy(fontSize = 28.sp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "TAP TO OPEN",
                    style = Typography.labelMedium.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.9f)
                )
                Text(
                    "NETWORK SETTINGS",
                    style = Typography.labelMedium.copy(fontSize = 10.sp),
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
fun DataSpeedCard(speedInfo: SpeedInfo, context: Context) {
    val speedColor = getSpeedColor(speedInfo.level)
    val speedQuality = getSpeedQuality(speedInfo.level)
    var showInfoSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 20.dp, elevation = 6.dp)
            .background(NeumorphicBackground, RoundedCornerShape(20.dp))
            .clickable { showInfoSheet = true }
            .padding(20.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("NETWORK SPEED", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Current Speed", style = Typography.titleMedium.copy(fontSize = 18.sp), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    modifier = Modifier
                        .background(speedColor.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = speedQuality,
                        style = Typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        color = speedColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SpeedColumn(
                    icon = R.drawable.download,
                    label = "DOWNLOAD",
                    value = formatSpeed(speedInfo.downloadSpeed),
                    color = speedColor,
                    modifier = Modifier.weight(1f)
                )
                SpeedColumn(
                    icon = R.drawable.upload,
                    label = "UPLOAD",
                    value = formatSpeed(speedInfo.uploadSpeed),
                    color = speedColor,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, speedColor.copy(alpha = 0.3f), Color.Transparent)
                        )
                    )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                InfoChip(label = "Downloaded", value = formatBytes(speedInfo.totalDownloaded))
                InfoChip(label = "Uploaded", value = formatBytes(speedInfo.totalUploaded))
            }
        }
    }

    if (showInfoSheet) {
        SpeedInfoBottomSheet(
            speedInfo = speedInfo,
            onDismiss = { showInfoSheet = false }
        )
    }
}

@Composable
private fun SpeedColumn(icon: Int, label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.08f), Color.Transparent)), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = color
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary, letterSpacing = 0.5.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = Typography.titleLarge.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(NeumorphicDarkShadow.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(value, style = Typography.bodyMedium.copy(fontSize = 12.sp), color = TextPrimary, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(2.dp))
        Text(label, style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedInfoBottomSheet(speedInfo: SpeedInfo, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NeumorphicBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Text("Speed Information", style = Typography.titleLarge.copy(fontSize = 22.sp), color = TextPrimary, fontWeight = FontWeight.Bold)
            Text("Detailed network metrics", style = Typography.bodyMedium.copy(fontSize = 14.sp), color = TextSecondary)

            Spacer(modifier = Modifier.height(24.dp))

            InfoRowDetail(label = "Download Speed", value = formatSpeed(speedInfo.downloadSpeed), unit = "Mbps")
            InfoRowDetail(label = "Upload Speed", value = formatSpeed(speedInfo.uploadSpeed), unit = "Mbps")
            InfoRowDetail(label = "Total Downloaded", value = formatBytes(speedInfo.totalDownloaded), unit = "")
            InfoRowDetail(label = "Total Uploaded", value = formatBytes(speedInfo.totalUploaded), unit = "", isLast = true)

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun InfoRowDetail(label: String, value: String, unit: String, isLast: Boolean = false) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = Typography.bodyMedium.copy(fontSize = 14.sp), color = TextSecondary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = Typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = TextPrimary)
                if (unit.isNotEmpty()) {
                    Text(" $unit", style = Typography.labelMedium.copy(fontSize = 12.sp), color = TextSecondary)
                }
            }
        }
        if (!isLast) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.05f)))
        }
    }
}

private fun formatSpeed(mbps: Double): String {
    return when {
        mbps >= 1000 -> String.format("%.1f Gbps", mbps / 1000)
        mbps >= 1 -> String.format("%.1f Mbps", mbps)
        mbps > 0 -> String.format("%.0f Kbps", mbps * 1000)
        else -> "0 Kbps"
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> String.format("%.1f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> String.format("%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> String.format("%.1f KB", bytes / 1_000.0)
        else -> "$bytes B"
    }
}

private fun getSpeedColor(level: SpeedLevel): Color {
    return when (level) {
        SpeedLevel.EXCELLENT -> Color(0xFF4CAF50)
        SpeedLevel.GOOD -> TextTeal
        SpeedLevel.FAIR -> Color(0xFFFF9800)
        SpeedLevel.POOR -> Color(0xFFFF5722)
        SpeedLevel.NO_SIGNAL, SpeedLevel.UNKNOWN -> TextSecondary
    }
}

private fun getSpeedQuality(level: SpeedLevel): String {
    return when (level) {
        SpeedLevel.EXCELLENT -> "Excellent"
        SpeedLevel.GOOD -> "Good"
        SpeedLevel.FAIR -> "Fair"
        SpeedLevel.POOR -> "Poor"
        SpeedLevel.NO_SIGNAL, SpeedLevel.UNKNOWN -> "Unknown"
    }
}

@Composable
private fun SignalHistoryChart(history: List<Int>, modifier: Modifier = Modifier, color: Color) {
    if (history.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Collecting signal data...", style = Typography.bodyMedium, color = TextSecondary)
        }
        return
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val animationProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "SignalChartAnimation"
    )

    BoxWithConstraints(modifier = modifier) {
        val scrollState = rememberScrollState()
        LaunchedEffect(scrollState.maxValue) {
            scrollState.scrollTo(scrollState.maxValue)
        }
        val minWidth = maxWidth
        val itemWidth = 30.dp
        val requiredWidth = maxOf(minWidth, itemWidth * history.size)
        
        val reversedHistory = history.reversed()

        Box(modifier = Modifier.horizontalScroll(scrollState)) {
            Canvas(modifier = Modifier.width(requiredWidth).fillMaxHeight()) {
                val width = size.width
                val height = size.height
                val minRsrp = -140f
                val maxRsrp = -60f
                val range = maxRsrp - minRsrp

                val points = reversedHistory.mapIndexed { index, rsrp ->
                    val x = (index.toFloat() / (reversedHistory.size - 1).coerceAtLeast(1)) * width
                    val normalizedY = ((rsrp - minRsrp) / range).coerceIn(0f, 1f)
                    val y = height - (normalizedY * height)
                    Offset(x, y)
                }

                if (points.size >= 2) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 0 until points.size - 1) {
                            val p1 = points[i]
                            val p2 = points[i + 1]
                            val cp1x = (p1.x + p2.x) / 2f
                            val cp1y = p1.y
                            val cp2x = (p1.x + p2.x) / 2f
                            val cp2y = p2.y
                            cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                        }
                    }

                    clipRect(right = width * animationProgress) {
                        val fillPath = androidx.compose.ui.graphics.Path().apply {
                            addPath(path)
                            lineTo(points.last().x, height)
                            lineTo(points.first().x, height)
                            close()
                        }

                        drawPath(
                            path = fillPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(color.copy(alpha = 0.5f), Color.Transparent)
                            )
                        )

                        drawPath(
                            path = path,
                            color = color,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 3.dp.toPx(),
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )

                        points.forEach { point ->
                            drawCircle(
                                color = color,
                                radius = 4.dp.toPx(),
                                center = point
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.3f),
                                radius = 7.dp.toPx(),
                                center = point,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                } else if (points.size == 1) {
                    val point = points[0]
                    clipRect(right = width * animationProgress) {
                        drawCircle(color = color, radius = 6.dp.toPx(), center = point)
                        drawCircle(
                            color = Color.White.copy(alpha = 0.3f),
                            radius = 10.dp.toPx(),
                            center = point,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeedTestBarChart(
    speedTests: List<SpeedTestEntity>,
    onTestClick: (SpeedTestEntity) -> Unit
) {
    if (speedTests.isEmpty()) return

    val maxSpeed = speedTests.maxOfOrNull { maxOf(it.downloadSpeed, it.uploadSpeed) } ?: 1.0

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val animationProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "SpeedTestBarChartAnimation"
    )

    Column {
        BoxWithConstraints {
            val scrollState = rememberScrollState()
            LaunchedEffect(scrollState.maxValue) {
                scrollState.scrollTo(scrollState.maxValue)
            }
            val minWidth = maxWidth
            val itemWidth = 40.dp
            val requiredWidth = maxOf(minWidth, itemWidth * speedTests.size)
            
            val reversedTests = speedTests.reversed()

            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
                Column(modifier = Modifier.width(requiredWidth)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        reversedTests.forEachIndexed { index, test ->
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxHeight(),
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val targetDownloadHeight = if (maxSpeed > 0) (test.downloadSpeed / maxSpeed).toFloat() else 0f
                                    val targetUploadHeight = if (maxSpeed > 0) (test.uploadSpeed / maxSpeed).toFloat() else 0f

                                    val downloadHeight = (targetDownloadHeight.coerceIn(0.05f, 1f) * animationProgress)
                                    val uploadHeight = (targetUploadHeight.coerceIn(0.05f, 1f) * animationProgress)

                                    Box(
                                        modifier = Modifier
                                            .width(8.dp)
                                            .fillMaxHeight(downloadHeight)
                                            .clip(RoundedCornerShape(4.dp, 4.dp, 0.dp, 0.dp))
                                            .background(Brush.verticalGradient(listOf(GradientStart, GradientStart.copy(alpha = 0.5f))))
                                            .clickable { onTestClick(test) }
                                    )
                                    Box(
                                        modifier = Modifier
                                            .width(8.dp)
                                            .fillMaxHeight(uploadHeight)
                                            .clip(RoundedCornerShape(4.dp, 4.dp, 0.dp, 0.dp))
                                            .background(Brush.verticalGradient(listOf(GradientEnd, GradientEnd.copy(alpha = 0.5f))))
                                            .clickable { onTestClick(test) }
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        reversedTests.forEachIndexed { index, _ ->
                            Text(
                                text = "${index + 1}",
                                style = Typography.labelMedium.copy(fontSize = 8.sp),
                                color = TextSecondary,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(8.dp).background(GradientStart, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Download", style = Typography.labelMedium.copy(fontSize = 9.sp), color = TextSecondary)
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.size(8.dp).background(GradientEnd, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Upload", style = Typography.labelMedium.copy(fontSize = 9.sp), color = TextSecondary)
        }
    }
}

@Composable
private fun SpeedTestDetailSheet(
    test: SpeedTestEntity,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(NeumorphicBackground)
                .padding(24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Test Result",
                        style = Typography.titleMedium.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    formatDateTime(test.timestamp),
                    style = Typography.labelMedium.copy(fontSize = 12.sp),
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.download),
                            contentDescription = null,
                            tint = GradientStart,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            "${String.format("%.1f", test.downloadSpeed)}",
                            style = Typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text("Mbps", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                        Text("Download", style = Typography.labelMedium.copy(fontSize = 9.sp), color = TextSecondary)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(id = R.drawable.upload),
                            contentDescription = null,
                            tint = GradientEnd,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            "${String.format("%.1f", test.uploadSpeed)}",
                            style = Typography.titleLarge.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text("Mbps", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                        Text("Upload", style = Typography.labelMedium.copy(fontSize = 9.sp), color = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(TextTeal.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(painter = androidx.compose.ui.res.painterResource(id = R.drawable.ping), contentDescription = null, tint = TextTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "${test.ping} ms",
                            style = Typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            color = TextTeal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyDataUsageCardLight() {
    Box(
        modifier = Modifier
            .size(180.dp, 160.dp)
            .neumorphic(cornerRadius = 20.dp, elevation = 2.dp) // Lower elevation to appear farther back
            .background(NeumorphicBackground.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Weekly Data Usage", style = Typography.labelMedium, color = TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                listOf(0.4f, 0.7f, 0.5f, 0.9f, 0.6f, 0.8f, 0.3f).forEachIndexed { index, height ->
                    Box(
                        modifier = Modifier
                            .width(8.dp)
                            .fillMaxHeight(height)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(GradientStart, GradientEnd)
                                )
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun ApnEditorCardLite() {
    Box(
        modifier = Modifier
            .size(120.dp, 120.dp)
            .neumorphic(cornerRadius = 20.dp, elevation = 2.dp)
            .background(NeumorphicBackground.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painter = androidx.compose.ui.res.painterResource(id = R.drawable.settings), contentDescription = "Settings", tint = GradientStart, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("APN Editor", style = Typography.labelMedium, color = TextPrimary)
        }
    }
}

@Composable
fun LineGraphCardLite() {
    Box(
        modifier = Modifier
            .size(160.dp, 140.dp)
            .neumorphic(cornerRadius = 20.dp, elevation = 2.dp)
            .background(NeumorphicBackground.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column {
            Text("Signal History", style = Typography.labelMedium, color = TextPrimary)
            Spacer(modifier = Modifier.weight(1f))
            Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, size.height)
                    lineTo(size.width * 0.2f, size.height * 0.5f)
                    lineTo(size.width * 0.4f, size.height * 0.7f)
                    lineTo(size.width * 0.6f, size.height * 0.3f)
                    lineTo(size.width * 0.8f, size.height * 0.6f)
                    lineTo(size.width, 0f)
                }
                drawPath(
                    path = path,
                    color = GradientEnd,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    ForceLTEOnlyTheme {
        val mockThemeManager = ThemeManager.getInstance(android.app.Application())
        DashboardScreen(mockThemeManager)
    }
}

@Composable
fun SpeedometerProgress(
    speed: Double,
    isTesting: Boolean,
    phase: String,
    modifier: Modifier = Modifier
) {
    var currentSpeed by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(isTesting, phase, speed) {
        if (isTesting) {
            // During testing, show the real speed being measured (0 initially)
            currentSpeed = speed.toFloat()
        } else {
            currentSpeed = speed.toFloat()
        }
    }

    val animatedSpeed by animateFloatAsState(
        targetValue = currentSpeed,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "speed_anim"
    )

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 16.dp.toPx()
            val radius = size.minDimension / 2f - strokeWidth

            // Background arc
            drawArc(
                color = NeumorphicDarkShadow.copy(alpha=0.3f),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )

            val maxSpeed = 150f
            val progressPercentage = (animatedSpeed / maxSpeed).coerceIn(0f, 1f)
            val sweep = 270f * progressPercentage

            // Progress arc
            if (sweep > 0) {
                drawArc(
                    brush = Brush.linearGradient(
                        colors = GradientColors,
                        start = Offset(0f, size.height),
                        end = Offset(size.width, 0f)
                    ),
                    startAngle = 135f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = String.format("%.1f", animatedSpeed),
                style = Typography.displayMedium.copy(fontSize = 42.sp),
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            val unit = if (phase == "PING") "ms" else "Mbps"
            Text(unit, style = Typography.labelMedium, color = TextSecondary)

            if (isTesting && phase != "IDLE" && phase != "DONE") {
                Spacer(modifier = Modifier.height(12.dp))
                Text("TESTING $phase...", style = Typography.labelSmall, color = TextTeal, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NeumorphicBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .neumorphic(cornerRadius = 20.dp, elevation = 4.dp)
                    .background(NeumorphicBackground, RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "App Icon",
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Force LTE Only", style = Typography.titleLarge.copy(fontSize = 22.sp), color = TextPrimary, fontWeight = FontWeight.Bold)
            Text("Version ${BuildConfig.VERSION_NAME}", style = Typography.bodyMedium.copy(fontSize = 14.sp), color = TextSecondary)

            Spacer(modifier = Modifier.height(32.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .neumorphic(cornerRadius = 16.dp, elevation = 2.dp)
                    .background(NeumorphicBackground, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text("DEVELOPER", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Ishara Madhusanka", style = Typography.titleMedium.copy(fontSize = 16.sp), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Sophisticated network tools for advanced users. Designed with a focus on simplicity and performance.",
                        style = Typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingsActionRow("Developer Website", onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ishara-madu.github.io/"))
                context.startActivity(intent)
            })

            SettingsActionRow("Privacy Policy", onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ishara-madu.github.io/4GLTEOnlyApp/privacy"))
                context.startActivity(intent)
            })

            SettingsActionRow("Terms of Service", onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ishara-madu.github.io/4GLTEOnlyApp/terms"))
                context.startActivity(intent)
            })

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HowToUseBottomSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NeumorphicBackground,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "How to Use",
                style = Typography.titleLarge.copy(fontSize = 22.sp),
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(24.dp))

            HowToStep(
                number = "1",
                title = "Open Hidden Settings",
                description = "Tap the 'FORCE 4G MODE' button on the home screen to open Android's hidden RadioInfo menu."
            )

            Spacer(modifier = Modifier.height(16.dp))

            HowToStep(
                number = "2",
                title = "Set Network Type",
                description = "Scroll down to 'Set Preferred Network Type' and select your desired mode."
            )

            Spacer(modifier = Modifier.height(16.dp))

            HowToStep(
                number = "3",
                title = "Force 4G or 5G",
                description = "Select 'LTE Only' to force 4G, or 'NR Only' to force 5G. Note: NR requires hardware support."
            )

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Red.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Caution: Forcing LTE Only may disable voice calls if your carrier doesn't support VoLTE.",
                        style = Typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.Red.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}

@Composable
private fun HowToStep(number: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 16.dp, elevation = 2.dp)
            .background(NeumorphicBackground, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(GradientStart, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color.White, style = Typography.titleMedium.copy(fontSize = 14.sp), fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(title, style = Typography.titleSmall.copy(fontSize = 14.sp), color = TextPrimary, fontWeight = FontWeight.Bold)
            Text(description, style = Typography.bodySmall.copy(fontSize = 12.sp), color = TextSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCodesBottomSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NeumorphicBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Manual Secret Codes",
                style = Typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Your device blocks automatic access. You MUST MANUALLY TYPE the secret code below in your dialer. Copy-pasting will NOT work.",
                style = Typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))

            SecretCodeItem(brand = "Samsung", code = "*#0011#", context = context)
            SecretCodeItem(brand = "Samsung Band Selection", code = "*#*#2263#*#*", context = context)
            SecretCodeItem(brand = "Honor / Huawei", code = "*#*#6130#*#*", context = context)
            SecretCodeItem(brand = "Xiaomi / Poco / Vivo", code = "*#*#4636#*#*", context = context)
        }
    }
}

@Composable
fun SecretCodeItem(brand: String, code: String, context: Context) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .neumorphic(cornerRadius = 16.dp, elevation = 4.dp)
            .background(NeumorphicBackground, RoundedCornerShape(16.dp))
            .clickable {
                Toast.makeText(context, "Please manually type: $code", Toast.LENGTH_LONG).show()

                try {
                    val dialIntent = Intent(Intent.ACTION_DIAL)
                    dialIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    context.startActivity(dialIntent)
                } catch (e: Exception) {
                    // Fallback if no dialer
                }
            }
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(brand, style = Typography.labelSmall, color = TextSecondary)
            Text(code, style = Typography.titleMedium, color = TextPrimary, fontWeight = FontWeight.Bold)
        }
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = R.drawable.dialpad),
            contentDescription = "Manual Type",
            tint = GradientStart,
            modifier = Modifier.size(20.dp)
        )
    }
}
@Composable
fun NetworkInfoCard(localIp: String, publicIp: String, wifiSpeed: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 24.dp, elevation = 6.dp)
            .background(NeumorphicBackground, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column {
            Text("NETWORK INFORMATION", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(16.dp))
            
            NetworkInfoRow(icon = R.drawable.public_ip, label = "Public IP", value = publicIp)
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = TextSecondary.copy(alpha = 0.1f))
            NetworkInfoRow(icon = R.drawable.network, label = "Local IP", value = localIp)
            Divider(modifier = Modifier.padding(vertical = 12.dp), color = TextSecondary.copy(alpha = 0.1f))
            NetworkInfoRow(icon = R.drawable.wifi, label = "Wi-Fi Speed", value = wifiSpeed)
        }
    }
}

@Composable
private fun NetworkInfoRow(icon: Int, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painter = androidx.compose.ui.res.painterResource(id = icon), contentDescription = null, tint = GradientStart, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(text = label, style = Typography.bodyMedium, color = TextPrimary)
        }
        Text(text = value, style = Typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = TextPrimary)
    }
}

val GameServerColors = listOf(
    Color(0xFF4CAF50), // Singapore
    Color(0xFF2196F3), // India
    Color(0xFFFF9800), // Middle East
    Color(0xFFE91E63), // Europe
    Color(0xFF9C27B0), // US East
    Color(0xFF00BCD4), // Australia
    Color(0xFFFF5722), // Japan
    Color(0xFF607D8B)  // Google DNS
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameServersPingCard(
    gameServers: List<GameServer>,
    telephonyService: TelephonyService,
    isUserPro: Boolean = false,
    onUpgradeClick: () -> Unit = {}
) {
    var showSheet by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 24.dp, elevation = 6.dp)
            .background(NeumorphicBackground, RoundedCornerShape(24.dp))
            .clickable {
                if (isUserPro) {
                    showSheet = true
                } else {
                    onUpgradeClick()
                }
            }
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painter = androidx.compose.ui.res.painterResource(id = R.drawable.gaming), contentDescription = null, tint = GradientStart, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("GAMING PING ANALYZER", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary, letterSpacing = 1.sp)
                        if (!isUserPro) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF6C63FF), Color(0xFFE040FB))
                                        ),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PRO",
                                    style = Typography.labelMedium.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                    Text("Regional Server Test", style = Typography.titleMedium.copy(fontSize = 18.sp), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
            }
            Icon(androidx.compose.ui.res.painterResource(id = R.drawable.arrow_forward), contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            containerColor = NeumorphicBackground,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary.copy(alpha = 0.3f)) }
        ) {
            GameServersBottomSheetContent(gameServers, telephonyService)
        }
    }
}

@Composable
fun GameServersBottomSheetContent(gameServers: List<GameServer>, telephonyService: TelephonyService) {
    val scope = rememberCoroutineScope()
    var isAnalyzing by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text("Regional Game Servers", style = Typography.titleLarge, color = TextPrimary)
        Text("Check real-time latency for major gaming hubs", style = Typography.bodyMedium, color = TextSecondary)
        
        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(if (isAnalyzing) SolidColor(NeumorphicDarkShadow) else Brush.linearGradient(GradientColors))
                .clickable(enabled = !isAnalyzing) {
                    isAnalyzing = true
                    scope.launch {
                        telephonyService.runGameServerAnalysis(gameServers)
                        isAnalyzing = false
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (isAnalyzing) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("START ANALYSIS", style = Typography.labelLarge, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        gameServers.forEach { server ->
            GameServerItem(server)
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun GameServerItem(server: GameServer) {
    val pingColor = when {
        server.pingMs < 0 -> TextSecondary
        server.pingMs < 80 -> Color(0xFF4CAF50)
        server.pingMs < 150 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 16.dp, elevation = 2.dp)
            .background(NeumorphicBackground, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(server.name, style = Typography.titleMedium, color = TextPrimary)
            Text(server.status, style = Typography.labelSmall, color = TextSecondary)
        }
        
        Text(
            if (server.pingMs < 0) "-- ms" else "${server.pingMs} ms",
            style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = pingColor
        )
    }
}

@Composable
fun GlobalServerLatencyCard(
    gameServers: List<GameServer>
) {
    AnalyticsCard(
        title = "GLOBAL SERVER LATENCY",
        subtitle = "Multi-region historical trends"
    ) {
        val activeServers = gameServers.filter { it.pingHistory.size >= 2 }
        
        if (activeServers.isNotEmpty()) {
            MultiLinePingChart(activeServers)
            Spacer(modifier = Modifier.height(24.dp))
            ChartLegend(activeServers)
        } else {
            Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                Text("Gathering more data... Run analysis in Tools to view history", style = Typography.bodyMedium, color = TextSecondary)
            }
        }
    }
}

@Composable
fun MultiLinePingChart(servers: List<GameServer>) {
    val maxPingValue = (servers.flatMap { it.pingHistory }.maxOrNull() ?: 200).toFloat().coerceAtLeast(100f)
    val maxPing = (Math.ceil(maxPingValue / 100.0) * 100.0).toFloat()
    
    val paddingLeft = 45.dp
    val paddingBottom = 20.dp
    
    val density = androidx.compose.ui.platform.LocalDensity.current
    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textAlign = android.graphics.Paint.Align.RIGHT
            textSize = density.run { 10.sp.toPx() }
            isAntiAlias = true
        }
    }

    var isVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { isVisible = true }

    val animationProgress by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "MultiPingAnimation"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(200.dp)) {
        val maxPoints = servers.maxOfOrNull { it.pingHistory.size } ?: 0
        val minChartWidth = maxWidth - paddingLeft
        val requiredChartWidth = maxOf(minChartWidth, 30.dp * maxPoints)

        Row(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.width(paddingLeft).fillMaxHeight()) {
                val height = size.height
                val bottomOffset = paddingBottom.toPx()
                val chartHeight = height - bottomOffset
                
                val stepCount = (maxPing / 100).toInt()
                for (i in 0..stepCount) {
                    val pingVal = i * 100
                    val y = chartHeight - (pingVal.toFloat() / maxPing * chartHeight)
                    drawContext.canvas.nativeCanvas.drawText(
                        "${pingVal}ms",
                        size.width - 8.dp.toPx(),
                        y + 4.dp.toPx(),
                        textPaint
                    )
                }
            }

            val scrollState = rememberScrollState()
            LaunchedEffect(scrollState.maxValue) {
                scrollState.scrollTo(scrollState.maxValue)
            }
            Box(modifier = Modifier.weight(1f).horizontalScroll(scrollState)) {
                Canvas(modifier = Modifier.width(requiredChartWidth).fillMaxHeight()) {
                    val width = size.width
                    val height = size.height
                    val bottomOffset = paddingBottom.toPx()
                    val chartHeight = height - bottomOffset
                    
                    val stepCount = (maxPing / 100).toInt()
                    for (i in 0..stepCount) {
                        val pingVal = i * 100
                        val y = chartHeight - (pingVal.toFloat() / maxPing * chartHeight)
                        drawLine(
                            color = TextSecondary.copy(alpha = 0.1f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // Horizontal baseline
                    drawLine(
                        color = TextSecondary.copy(alpha = 0.3f),
                        start = Offset(0f, chartHeight),
                        end = Offset(width, chartHeight),
                        strokeWidth = 1.dp.toPx()
                    )

                    clipRect(right = width * animationProgress) {
                        servers.forEachIndexed { index, server ->
                            val points = server.pingHistory
                            if (points.size < 2) return@forEachIndexed
                            
                            val color = GameServerColors[index % GameServerColors.size]
                            val path = androidx.compose.ui.graphics.Path()
                            
                            val coords = points.mapIndexed { pIndex, ping ->
                                val x = if (maxPoints > 1) pIndex * (width / (maxPoints - 1)) else width / 2f
                                val y = chartHeight - (ping.toFloat() / maxPing * chartHeight).coerceIn(0f, chartHeight)
                                Offset(x, y)
                            }

                            path.moveTo(coords[0].x, coords[0].y)
                            for (i in 0 until coords.size - 1) {
                                val p1 = coords[i]
                                val p2 = coords[i + 1]
                                val cp1x = (p1.x + p2.x) / 2f
                                val cp1y = p1.y
                                val cp2x = (p1.x + p2.x) / 2f
                                val cp2y = p2.y
                                path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                            }
                            
                            drawPath(
                                path = path,
                                color = color,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 3.dp.toPx(), 
                                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                                )
                            )
                            
                            coords.forEach { coord ->
                                drawCircle(color = color, center = coord, radius = 3.dp.toPx())
                                drawCircle(color = Color.White.copy(alpha = 0.5f), center = coord, radius = 5.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChartLegend(servers: List<GameServer>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        servers.forEachIndexed { index, server ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(GameServerColors[index % GameServerColors.size], CircleShape))
                Spacer(modifier = Modifier.width(6.dp))
                Text(server.name, style = Typography.labelSmall, color = TextSecondary)
            }
        }
    }
}

@Composable
fun PingStabilizerCard(
    isPingStabilizerEnabled: Boolean,
    onTogglePingStabilizer: (Boolean, String) -> Unit,
    isUserPro: Boolean,
    onUpgradeClick: () -> Unit,
    settingsManager: SettingsManager
) {
    val pingHistory by PingStabilizerService.pingHistory.collectAsState()
    val savedTargetIp by settingsManager.customTargetIpFlow.collectAsState()
    var targetIp by remember(savedTargetIp) { mutableStateOf(savedTargetIp) }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .neumorphic(cornerRadius = 24.dp, elevation = 6.dp)
            .background(NeumorphicBackground, RoundedCornerShape(24.dp))
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Ping Stabilizer", 
                            style = Typography.titleMedium, 
                            color = TextPrimary
                        )
                        if (!isUserPro) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(Color(0xFF6C63FF), Color(0xFFE040FB))
                                        ),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "PRO",
                                    style = Typography.labelMedium.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        }
                    }
                    Text(
                        text = "Prevents connection sleeping to reduce lag spikes", 
                        style = Typography.labelSmall, 
                        color = TextSecondary
                    )
                }
                androidx.compose.material3.Switch(
                    checked = isPingStabilizerEnabled,
                    onCheckedChange = { 
                        if (!isUserPro) {
                            onUpgradeClick()
                        } else {
                            onTogglePingStabilizer(it, targetIp) 
                        }
                    },
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF6C63FF),
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = NeumorphicBackground
                    )
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.material3.OutlinedTextField(
                value = targetIp,
                onValueChange = { 
                    targetIp = it 
                    settingsManager.setCustomTargetIp(it)
                },
                label = { Text("Custom Target IP (Optional)", color = TextSecondary) },
                placeholder = { Text("Default: 1.1.1.1", color = TextSecondary.copy(alpha = 0.5f)) },
                textStyle = androidx.compose.ui.text.TextStyle(color = TextPrimary),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF6C63FF),
                    unfocusedBorderColor = TextSecondary.copy(alpha = 0.5f),
                    focusedLabelColor = Color(0xFF6C63FF),
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isPingStabilizerEnabled
            )

            if (isPingStabilizerEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = TextSecondary.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Live Log",
                    style = Typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color(0xFF1E1E2E), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    val listState = rememberLazyListState()
                    
                    LaunchedEffect(pingHistory.size) {
                        if (pingHistory.isNotEmpty()) {
                            listState.animateScrollToItem(pingHistory.size - 1)
                        }
                    }
                    
                    LazyColumn(state = listState) {
                        items(pingHistory) { ping ->
                            val color = when {
                                ping < 80 -> Color(0xFF4CAF50)
                                ping < 150 -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                            Text(
                                text = "Ping: ${ping}ms",
                                style = Typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = color,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PingStabilizerHistoryChart() {
    val pingHistory by PingStabilizerService.pingHistory.collectAsState()
    
    if (pingHistory.isEmpty()) return

    AnalyticsCard(
        title = "PING STABILIZER HISTORY",
        subtitle = "Live view of background stabilization pings"
    ) {
        val scrollState = rememberScrollState()

        LaunchedEffect(pingHistory.size) {
            scrollState.scrollTo(scrollState.maxValue)
        }

        val itemWidth = 8.dp
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(150.dp)) {
            val minWidth = itemWidth * pingHistory.size
            val parentMaxWidth = maxWidth
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(modifier = Modifier.width(maxOf(minWidth, parentMaxWidth)).fillMaxHeight()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val itemW = itemWidth.toPx()
                        val maxPing = (pingHistory.maxOrNull() ?: 100).coerceAtLeast(100).toFloat()
                        
                        val startX = width - (pingHistory.size * itemW)
                        
                        pingHistory.forEachIndexed { index, ping ->
                            val normalizedHeight = (ping.toFloat() / maxPing) * height * 0.8f
                            val x = startX + (index * itemW)
                            val color = when {
                                ping < 80 -> Color(0xFF4CAF50)
                                ping < 150 -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                            
                            drawLine(
                                color = color,
                                start = androidx.compose.ui.geometry.Offset(x, height),
                                end = androidx.compose.ui.geometry.Offset(x, height - normalizedHeight),
                                strokeWidth = itemW * 0.6f,
                                cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}
