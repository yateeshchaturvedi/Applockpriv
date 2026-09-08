package com.yateeshpriv.applockpriv

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }

    val steps = listOf(
        OnboardingStepData(
            icon = Icons.Default.Shield,
            title = "Welcome to App Lock",
            description = "Protect your private apps and data with biometric, passcode, or pattern locks.\n\nLet's set up the necessary permissions for real-time protection.",
            actionLabel = null
        ),
        OnboardingStepData(
            icon = Icons.Default.BarChart,
            title = "Usage Access",
            description = "App Lock detects when a protected app is opened so it can immediately present the security screen.\n\nPlease enable Usage Access.",
            actionLabel = "Grant Usage Access"
        ),
        OnboardingStepData(
            icon = Icons.Default.Layers,
            title = "Display Over Apps",
            description = "The security screen needs permission to display on top of other running applications.\n\nPlease enable 'Display over other apps'.",
            actionLabel = "Grant Overlay Permission"
        ),
        OnboardingStepData(
            icon = Icons.Default.BatteryFull,
            title = "Battery Optimization",
            description = "To ensure continuous background monitoring without Android pausing the protection service, exempt App Lock from battery optimization.",
            actionLabel = "Disable Optimization"
        ),
        OnboardingStepData(
            icon = Icons.Default.CheckCircle,
            title = "You're All Set!",
            description = "App Lock is ready to protect your device.\n\nToggle the switch next to any app to lock it immediately.",
            actionLabel = null
        )
    )

    fun isPermissionGranted(stepIndex: Int): Boolean {
        return when (stepIndex) {
            1 -> {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
                mode == AppOpsManager.MODE_ALLOWED
            }
            2 -> Settings.canDrawOverlays(context)
            3 -> {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            }
            else -> false
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Step Progress Indicator ──────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                steps.forEachIndexed { i, _ ->
                    val isCurrent = i == step
                    val isPast = i < step
                    Surface(
                        modifier = Modifier
                            .height(8.dp)
                            .width(if (isCurrent) 28.dp else 12.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = when {
                            isCurrent -> MaterialTheme.colorScheme.primary
                            isPast -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ) {}
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // ── Animated Step Card ───────────────────────────────────────────
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                },
                label = "onboarding_step"
            ) { targetStep ->
                val data = steps[targetStep]
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(88.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = data.icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(44.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = data.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = data.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        // If this step is a permission step, show status
                        if (data.actionLabel != null) {
                            val granted = isPermissionGranted(targetStep)
                            Spacer(modifier = Modifier.height(20.dp))
                            if (granted) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Permission Granted",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        when (targetStep) {
                                            1 -> context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                            2 -> context.startActivity(
                                                Intent(
                                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    Uri.parse("package:${context.packageName}")
                                                )
                                            )
                                            3 -> {
                                                val pm = context.getSystemService(PowerManager::class.java)
                                                if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                                                    context.startActivity(
                                                        Intent(
                                                            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                                            Uri.parse("package:${context.packageName}")
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(data.actionLabel)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // ── Next / Complete Button ───────────────────────────────────────
            Button(
                onClick = {
                    if (step < steps.lastIndex) step++ else onComplete()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = if (step < steps.lastIndex) "Next" else "Get Started",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (step == 0) {
                Spacer(modifier = Modifier.height(10.dp))
                TextButton(onClick = { step = steps.lastIndex }) {
                    Text("Skip intro", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private data class OnboardingStepData(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val actionLabel: String?
)
