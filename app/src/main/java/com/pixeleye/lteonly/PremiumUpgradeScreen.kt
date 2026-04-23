package com.pixeleye.lteonly

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.purchaseWith
import com.revenuecat.purchases.restorePurchasesWith

@Composable
fun PremiumUpgradeScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    
    var availablePackages by remember { mutableStateOf<List<Package>>(emptyList()) }
    var selectedPackage by remember { mutableStateOf<Package?>(null) }
    var isPurchasing by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Purchases.sharedInstance.getOfferingsWith(
            onError = { error ->
                Toast.makeText(context, "Error fetching offers: ${error.message}", Toast.LENGTH_SHORT).show()
            },
            onSuccess = { offerings ->
                val packages = offerings.current?.availablePackages ?: emptyList()
                availablePackages = packages
                selectedPackage = packages.find { it.packageType == com.revenuecat.purchases.PackageType.ANNUAL } 
                    ?: packages.firstOrNull()
            }
        )
    }

    // Dynamic Best Value and Discount Logic
    val (bestValuePackage, savingsPercent) = remember(availablePackages) {
        if (availablePackages.isEmpty()) return@remember null to 0
        
        val monthlyEquivalents = availablePackages.map { pkg ->
            val months = when (pkg.packageType) {
                com.revenuecat.purchases.PackageType.ANNUAL -> 12.0
                com.revenuecat.purchases.PackageType.SIX_MONTH -> 6.0
                com.revenuecat.purchases.PackageType.THREE_MONTH -> 3.0
                com.revenuecat.purchases.PackageType.TWO_MONTH -> 2.0
                com.revenuecat.purchases.PackageType.MONTHLY -> 1.0
                com.revenuecat.purchases.PackageType.WEEKLY -> 0.25
                else -> 1.0
            }
            pkg to (pkg.product.price.amountMicros.toDouble() / months)
        }
        
        val bestValue = monthlyEquivalents.minByOrNull { it.second }?.first
        val maxMonthly = monthlyEquivalents.maxByOrNull { it.second }?.second ?: 1.0
        val bestValueMonthly = monthlyEquivalents.find { it.first == bestValue }?.second ?: 1.0
        
        val savings = if (maxMonthly > bestValueMonthly && maxMonthly > 0) {
            ((1.0 - (bestValueMonthly / maxMonthly)) * 100).toInt()
        } else 0
        
        bestValue to savings
    }

    if (showSuccess) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(240.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(72.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = "Pro Mode Activated!",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
        
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            onDismiss()
        }
        return
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Close button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onBackground)
                    }
                }

                // Header Graphic
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), 
                        modifier = Modifier.size(64.dp)
                    )
                    Icon(
                        imageVector = Icons.Default.Bolt, 
                        contentDescription = null, 
                        tint = MaterialTheme.colorScheme.primary, 
                        modifier = Modifier.size(36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                Text(
                    text = "LTE ONLY PRO",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "ELEVATE YOUR CONNECTIVITY",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(40.dp))

                // Feature List
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FeatureRow(
                        icon = Icons.Default.Gamepad,
                        title = "Game Servers Ping Analyzer",
                        description = "Real-time regional server tests."
                    )
                    FeatureRow(
                        icon = Icons.Default.Analytics,
                        title = "Pro Analytics",
                        description = "Remove blur effect and view clear historical data charts (data limited to recent results)."
                    )
                    FeatureRow(
                        icon = Icons.Default.Block,
                        title = "Ad-Free Interface",
                        description = "Completely remove all banner and interstitial ads."
                    )
                }

                Spacer(modifier = Modifier.height(40.dp))

                // Pricing Section
                if (availablePackages.isEmpty()) {
                    Box(modifier = Modifier.height(160.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        availablePackages.forEach { pkg ->
                            PackageCard(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                pkg = pkg,
                                isSelected = selectedPackage == pkg,
                                isBestValue = pkg == bestValuePackage,
                                savingsPercent = if (pkg == bestValuePackage) savingsPercent else 0,
                                onClick = { selectedPackage = pkg }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // CTA Button
                Button(
                    onClick = {
                        val pkgToPurchase = selectedPackage
                        if (activity != null && pkgToPurchase != null && !isPurchasing) {
                            isPurchasing = true
                            Purchases.sharedInstance.purchaseWith(
                                PurchaseParams.Builder(activity, pkgToPurchase).build(),
                                onError = { error, userCancelled ->
                                    isPurchasing = false
                                    if (!userCancelled) {
                                        Toast.makeText(context, "Purchase failed: ${error.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onSuccess = { _, _ ->
                                    isPurchasing = false
                                    ProStateManager.checkEntitlement()
                                    showSuccess = true
                                }
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(28.dp),
                    enabled = !isPurchasing && selectedPackage != null
                ) {
                    if (isPurchasing) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                    } else {
                        Text(
                            text = "Upgrade Now",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Legal Footer
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FooterLink("Terms of Use") { /* Link */ }
                        Text(" • ", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                        FooterLink("Privacy Policy") { /* Link */ }
                        Text(" • ", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                        FooterLink("Restore Purchases") {
                            isPurchasing = true
                            Purchases.sharedInstance.restorePurchasesWith(
                                onError = { error ->
                                    isPurchasing = false
                                    Toast.makeText(context, "Restore failed: ${error.message}", Toast.LENGTH_SHORT).show()
                                },
                                onSuccess = { _ ->
                                    isPurchasing = false
                                    ProStateManager.checkEntitlement()
                                    Toast.makeText(context, "Purchases restored", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                }
                            )
                        }
                    }

                    Text(
                        text = "Subscription automatically renews. Cancel anytime via the Play Store settings.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun FeatureRow(icon: ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary, 
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = title, 
                color = MaterialTheme.colorScheme.onBackground, 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.Bold
            )
            Text(
                text = description, 
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), 
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun PackageCard(
    modifier: Modifier = Modifier,
    pkg: Package,
    isSelected: Boolean,
    isBestValue: Boolean,
    savingsPercent: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) 
                             else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = if (isSelected || isBestValue) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary 
                    else if (isBestValue) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Best Value Badge
            if (isBestValue && savingsPercent > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(
                            MaterialTheme.colorScheme.tertiary, 
                            RoundedCornerShape(bottomStart = 8.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "SAVE $savingsPercent%",
                        color = MaterialTheme.colorScheme.onTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .padding(top = if (isBestValue) 12.dp else 0.dp)
            ) {
                Text(
                    text = when (pkg.packageType) {
                        com.revenuecat.purchases.PackageType.ANNUAL -> "Yearly"
                        com.revenuecat.purchases.PackageType.MONTHLY -> "Monthly"
                        com.revenuecat.purchases.PackageType.WEEKLY -> "Weekly"
                        else -> pkg.packageType.name.lowercase().capitalize()
                    },
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = pkg.product.price.formatted,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = when (pkg.packageType) {
                        com.revenuecat.purchases.PackageType.ANNUAL -> "per year"
                        com.revenuecat.purchases.PackageType.MONTHLY -> "per month"
                        com.revenuecat.purchases.PackageType.WEEKLY -> "per week"
                        else -> ""
                    },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun FooterLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        style = MaterialTheme.typography.labelSmall,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable { onClick() }
    )
}
