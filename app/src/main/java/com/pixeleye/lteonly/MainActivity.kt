package com.pixeleye.lteonly

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.work.*
import com.pixeleye.lteonly.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private lateinit var themeManager: ThemeManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeManager = ThemeManager.getInstance(this)
        
        setContent {
            val theme by themeManager.themeFlow.collectAsStateWithLifecycle()
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (theme) {
                AppTheme.LIGHT -> false
                AppTheme.DARK -> true
                AppTheme.SYSTEM -> isSystemDark
            }
            
            com.pixeleye.lteonly.ui.theme.globalIsDarkTheme = darkTheme
            
            ForceLTEOnlyTheme(darkTheme = darkTheme) {
                DashboardScreen(themeManager)
            }
        }
    }
}

@Composable
fun DashboardScreen(themeManager: ThemeManager) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    val context = LocalContext.current

    var hasPermission by remember { mutableStateOf(false) }
    var networkInfo by remember { mutableStateOf(NetworkInfo()) }
    var isSpeedTestRunning by remember { mutableStateOf(false) }
    var isPingTestRunning by remember { mutableStateOf(false) }
    var speedTestResult by remember { mutableStateOf<SpeedTestResult?>(null) }
    var speedTestPhase by remember { mutableStateOf("IDLE") }
    var pingTestResult by remember { mutableStateOf<PingTestResult?>(null) }
    var hasSavedInitialSignal by remember { mutableStateOf(false) }
    
    var storedSignalHistory by remember { mutableStateOf<List<SignalHistoryEntity>>(emptyList()) }
    var storedDataUsage by remember { mutableStateOf<List<DataUsageEntity>>(emptyList()) }
    var storedSpeedTests by remember { mutableStateOf<List<SpeedTestEntity>>(emptyList()) }
    var storedPingTests by remember { mutableStateOf<List<PingTestEntity>>(emptyList()) }
    
    val telephonyService = remember { TelephonyService(context) }
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
            val reminderRequest = PeriodicWorkRequestBuilder<SpeedTestReminderWorker>(1, TimeUnit.DAYS)
                .addTag("speed_test_reminder")
                .build()
            workManager.enqueueUniquePeriodicWork(
                "speed_test_reminder",
                ExistingPeriodicWorkPolicy.KEEP,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NeumorphicBackground)
            .padding(statusBarPadding)
    ) {
        when (selectedTab) {
            0 -> HomeTab(
                networkInfo = networkInfo, 
                telephonyService = telephonyService, 
                context = context, 
                hasPermission = hasPermission,
                onHowToUseClick = { showHowToUseSheet = true }
            )
            1 -> AnalyticsTab(
                networkInfo = networkInfo,
                signalHistory = storedSignalHistory,
                speedTests = storedSpeedTests,
                pingTests = storedPingTests
            )
            2 -> ToolsTab(
                telephonyService = telephonyService,
                isSpeedTestRunning = isSpeedTestRunning,
                speedTestPhase = speedTestPhase,
                isPingTestRunning = isPingTestRunning,
                speedTestResult = speedTestResult,
                pingTestResult = pingTestResult,
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
        
        NeumorphicBottomBar(
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (showHowToUseSheet) {
            HowToUseBottomSheet(onDismiss = { showHowToUseSheet = false })
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
        NavItem(Icons.Default.Home, "Home"),
        NavItem(Icons.Default.List, "Analytics"),
        NavItem(Icons.Default.Build, "Tools"),
        NavItem(Icons.Default.Settings, "Settings")
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
                            imageVector = item.icon,
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

data class NavItem(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String)

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
    onHowToUseClick: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(start = 24.dp, end = 24.dp, bottom = 140.dp),
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
                        imageVector = Icons.Default.HelpOutline, 
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
            
            MainForce4GButton(onClick = { RadioInfoHelper.openRadioInfo(context) })
            
            Spacer(modifier = Modifier.height(32.dp))
            
            DataSpeedCard(speedInfo = networkInfo.speedInfo, context = context)
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
    pingTests: List<PingTestEntity>
) {
    val scrollState = rememberScrollState()
    val currentRsrp = networkInfo.signalStrength.rsrp
    val signalColor = getSignalLevelColor(networkInfo.signalStrength.level)
    val signalRsrpHistory = signalHistory.map { it.rsrp }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 24.dp, end = 24.dp, bottom = 140.dp),
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
    onSpeedTestClick: () -> Unit,
    onPingTestClick: () -> Unit,
    context: Context
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 24.dp, end = 24.dp, bottom = 140.dp),
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
                        icon = Icons.Default.KeyboardArrowDown,
                        label = "DOWNLOAD",
                        value = if (speedTestPhase == "IDLE" && speedTestResult != null) String.format("%.1f", speedTestResult?.downloadSpeed ?: 0.0)
                                else if (speedTestPhase == "DOWNLOAD") animatedDots
                                else if (speedTestResult?.downloadSpeed != null && speedTestResult.downloadSpeed > 0) String.format("%.1f", speedTestResult.downloadSpeed)
                                else "...",
                        unit = "Mbps"
                    )
                    SpeedColumn(
                        icon = Icons.Default.KeyboardArrowUp,
                        label = "UPLOAD",
                        value = if (speedTestPhase == "IDLE" && speedTestResult != null) String.format("%.1f", speedTestResult?.uploadSpeed ?: 0.0)
                                else if (speedTestPhase == "UPLOAD") animatedDots
                                else if (speedTestResult?.uploadSpeed != null && speedTestResult.uploadSpeed > 0) String.format("%.1f", speedTestResult.uploadSpeed)
                                else "...",
                        unit = "Mbps"
                    )
                    SpeedColumn(
                        icon = androidx.compose.material.icons.Icons.Default.Timer,
                        label = "PING",
                        value = if (speedTestPhase == "IDLE" && speedTestResult != null) "${speedTestResult?.ping ?: 0}"
                                else if (speedTestPhase == "PING") animatedDots
                                else if (speedTestResult?.ping != null && speedTestResult.ping > 0) "${speedTestResult.ping}"
                                else "...",
                        unit = "ms"
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .neumorphic(cornerRadius = 24.dp, elevation = 6.dp)
                .background(NeumorphicBackground, RoundedCornerShape(24.dp))
                .padding(20.dp)
                .clickable { 
                    val intent = Intent(Settings.ACTION_APN_SETTINGS)
                    context.startActivity(intent)
                }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("APN SETTINGS", style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary, letterSpacing = 1.sp)
                        Text("Network Configuration", style = Typography.titleMedium.copy(fontSize = 16.sp), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    }
                }
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
            }
        }
    }
}

@Composable
private fun SpeedColumn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = TextSecondary)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = Typography.titleLarge.copy(fontSize = 22.sp), color = TextPrimary, fontWeight = FontWeight.Bold)
        Text(unit, style = Typography.labelMedium.copy(fontSize = 10.sp), color = TextSecondary)
        Text(label, style = Typography.labelMedium.copy(fontSize = 9.sp), color = TextSecondary)
    }
}

@Composable
private fun PingHistoryChart(history: List<Int>, modifier: Modifier = Modifier) {
    if (history.isEmpty()) return
    
    val maxLatency = history.maxOrNull() ?: 1
    val normalizedHistory = history.map { (it.toFloat() / maxLatency).coerceIn(0.1f, 1f) }
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barWidth = width / history.size
        
        normalizedHistory.forEachIndexed { index, value ->
            val barHeight = height * value
            val x = index * barWidth
            val color = when {
                history[index] < 50 -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                history[index] < 100 -> androidx.compose.ui.graphics.Color(0xFFFF9800)
                else -> androidx.compose.ui.graphics.Color(0xFFFF5722)
            }
            drawRoundRect(
                color = color,
                topLeft = androidx.compose.ui.geometry.Offset(x + 2, height - barHeight),
                size = androidx.compose.ui.geometry.Size(barWidth - 4, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )
        }
    }
}

@Composable
private fun SpeedHistoryChart(history: List<SpeedTestEntity>, modifier: Modifier = Modifier) {
    if (history.isEmpty()) return
    
    val maxDownload = history.maxOfOrNull { it.downloadSpeed }?.toFloat() ?: 1f
    val maxUpload = history.maxOfOrNull { it.uploadSpeed }?.toFloat() ?: 1f
    val maxSpeed = maxOf(maxDownload, maxUpload, 1f)
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barWidth = width / history.size
        
        history.forEachIndexed { index, test ->
            val downHeight = height * ((test.downloadSpeed.toFloat() / maxSpeed).coerceIn(0.05f, 1f))
            val upHeight = height * ((test.uploadSpeed.toFloat() / maxSpeed).coerceIn(0.05f, 1f))
            val x = index * barWidth
            
            drawRoundRect(
                color = androidx.compose.ui.graphics.Color(0xFF8E99F3),
                topLeft = androidx.compose.ui.geometry.Offset(x + 2f, height - downHeight),
                size = androidx.compose.ui.geometry.Size((barWidth / 2f) - 2f, downHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
            
            drawRoundRect(
                color = androidx.compose.ui.graphics.Color(0xFF5C6BC0),
                topLeft = androidx.compose.ui.geometry.Offset(x + (barWidth / 2f), height - upHeight),
                size = androidx.compose.ui.geometry.Size((barWidth / 2f) - 2f, upHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
            )
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
            .padding(start = 24.dp, end = 24.dp, bottom = 140.dp),
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
        SettingsCard(title = "App Theme", icon = Icons.Default.Settings) {
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
        SettingsCard(title = "Notifications", icon = Icons.Default.Notifications) {
            ToggleRow("Speed Test Reminder", speedTestReminder, onToggleChange = { settingsManager.setSpeedTestReminder(it) })
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Help & Support
        SettingsCard(
            title = "Help & Support", 
            icon = Icons.Default.Star,
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
        
        SettingsCard(
            title = "About", 
            icon = Icons.Default.Info,
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
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
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
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
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
                    Icon(icon, contentDescription = null, tint = GradientStart, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(title, style = Typography.titleMedium.copy(fontSize = 16.sp), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                }
                if (onClick != null) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
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
                Icon(
                    if (networkInfo.isLocationEnabled) {
                        if (networkInfo.isRoaming) Icons.Default.Public else Icons.Default.Home
                    } else Icons.Default.LocationOff,
                    contentDescription = null,
                    tint = if (networkInfo.isLocationEnabled) TextTeal else Color.Red.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp)
                )
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
                    icon = Icons.Default.KeyboardArrowDown,
                    label = "DOWNLOAD",
                    value = formatSpeed(speedInfo.downloadSpeed),
                    color = speedColor,
                    modifier = Modifier.weight(1f)
                )
                SpeedColumn(
                    icon = Icons.Default.KeyboardArrowUp,
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
private fun SpeedColumn(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Brush.verticalGradient(listOf(color.copy(alpha = 0.08f), Color.Transparent)), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
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
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val minRsrp = -140f
        val maxRsrp = -60f
        val range = maxRsrp - minRsrp
        
        val points = history.mapIndexed { index, rsrp ->
            val x = (index.toFloat() / (history.size - 1).coerceAtLeast(1)) * width
            val normalizedY = ((rsrp - minRsrp) / range).coerceIn(0f, 1f)
            val y = height - (normalizedY * height)
            Offset(x, y)
        }
        
        if (points.size >= 2) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(points[0].x, points[0].y)
                for (i in 1 until points.size) {
                    lineTo(points[i].x, points[i].y)
                }
            }
            
            val fillPath = androidx.compose.ui.graphics.Path().apply {
                addPath(path)
                lineTo(points.last().x, height)
                lineTo(points.first().x, height)
                close()
            }
            
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(color.copy(alpha = 0.4f), Color.Transparent)
                )
            )
            
            drawPath(
                path = path,
                color = color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
            
            for (point in points) {
                drawCircle(
                    color = color,
                    radius = 4.dp.toPx(),
                    center = point
                )
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
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().height(80.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            speedTests.forEachIndexed { index, test ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        val downloadHeight = if (maxSpeed > 0) (test.downloadSpeed / maxSpeed).toFloat() else 0f
                        val uploadHeight = if (maxSpeed > 0) (test.uploadSpeed / maxSpeed).toFloat() else 0f
                        
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .fillMaxHeight(downloadHeight.coerceIn(0.05f, 1f))
                                .clip(RoundedCornerShape(2.dp))
                                .background(GradientStart)
                                .clickable { onTestClick(test) }
                        )
                        Box(
                            modifier = Modifier
                                .width(8.dp)
                                .fillMaxHeight(uploadHeight.coerceIn(0.05f, 1f))
                                .clip(RoundedCornerShape(2.dp))
                                .background(GradientEnd)
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
            speedTests.forEachIndexed { index, _ ->
                Text(
                    text = "${index + 1}",
                    style = Typography.labelMedium.copy(fontSize = 8.sp),
                    color = TextSecondary,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
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
                            Icons.Default.KeyboardArrowDown,
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
                            Icons.Default.KeyboardArrowUp,
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
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(TextTeal.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Home, contentDescription = null, tint = TextTeal, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "${test.ping} ms",
                            style = Typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            color = TextTeal
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.background(TextPrimary.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            test.networkType,
                            style = Typography.bodyMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            color = TextPrimary
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
            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = GradientStart, modifier = Modifier.size(32.dp))
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
                Icon(Icons.Default.Build, contentDescription = null, tint = GradientStart, modifier = Modifier.size(40.dp))
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("Force LTE Only", style = Typography.titleLarge.copy(fontSize = 22.sp), color = TextPrimary, fontWeight = FontWeight.Bold)
            Text("Version 1.0.0", style = Typography.bodyMedium.copy(fontSize = 14.sp), color = TextSecondary)
            
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
                    Text("Pixeleye Studio", style = Typography.titleMedium.copy(fontSize = 16.sp), color = TextPrimary, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Sophisticated network tools for advanced users. Designed with a focus on simplicity and performance.",
                        style = Typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 20.sp),
                        color = TextPrimary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            SettingsActionRow("Privacy Policy", onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pixeleye.studio/privacy"))
                context.startActivity(intent)
            })
            
            SettingsActionRow("Terms of Service", onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://pixeleye.studio/terms"))
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
