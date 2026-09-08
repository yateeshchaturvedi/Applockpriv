package com.yateeshpriv.applockpriv

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.biometric.BiometricManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.io.File
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: AppListViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val authMethod by viewModel.authMethod.collectAsState()
    val blurInRecents by viewModel.blurInRecents.collectAsState()
    val hideNotifications by viewModel.hideNotifications.collectAsState()
    val gracePeriodSeconds by viewModel.gracePeriodSeconds.collectAsState()
    val selfProtection by viewModel.selfProtection.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val schedule by viewModel.schedule.collectAsState()
    val hasCredential by viewModel.hasCredential.collectAsState()

    var showAuthMethodDialog by remember { mutableStateOf(false) }
    var showCredentialDialog by remember { mutableStateOf(false) }
    var showGracePeriodDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    val intruderCount = remember {
        context.filesDir.listFiles { f -> f.name.startsWith("intruder_") && f.name.endsWith(".jpg") }?.size ?: 0
    }

    val isBiometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun applySecureFlag(enabled: Boolean) {
        val activity = context as? Activity ?: return
        if (enabled) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Section 1: Security & Credentials ───────────────────────────
            SettingsSectionHeader(title = "Security & Credentials")
            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Default.VpnKey,
                        title = "Authentication Method",
                        subtitle = authMethod.label,
                        onClick = { showAuthMethodDialog = true }
                    )
                    if (authMethod.requiresSecret()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        SettingsRow(
                            icon = Icons.Default.Password,
                            title = "Change Credential",
                            subtitle = if (hasCredential) "Credential configured" else "Tap to configure",
                            onClick = { showCredentialDialog = true }
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchRow(
                        icon = Icons.Default.Security,
                        title = "Protect App Lock",
                        subtitle = "Require authentication to open App Lock",
                        checked = selfProtection,
                        onCheckedChange = { viewModel.setSelfProtection(it) }
                    )
                }
            }

            // ── Section 2: Automation & Rules ───────────────────────────────
            SettingsSectionHeader(title = "Automation & Rules")
            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    val accessibilityActive = AppLockAccessibilityService.isAccessibilityEnabled(context)
                    SettingsRow(
                        icon = Icons.Default.Bolt,
                        title = "Instant Lock Engine",
                        subtitle = if (accessibilityActive) "Accessibility Active • 0ms Delay" else "Tap to enable 0ms instant lock",
                        badge = if (accessibilityActive) "0ms" else null,
                        onClick = {
                            context.startActivity(AppLockAccessibilityService.accessibilitySettingsIntent())
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    val relockSummary = when (gracePeriodSeconds) {
                        0 -> "Immediately when leaving"
                        30 -> "30 seconds grace period"
                        60 -> "1 minute grace period"
                        300 -> "5 minutes grace period"
                        else -> "${gracePeriodSeconds}s grace period"
                    }
                    SettingsRow(
                        icon = Icons.Default.Timer,
                        title = "Relock Policy",
                        subtitle = relockSummary,
                        onClick = { showGracePeriodDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    val scheduleSummary = if (schedule.enabled) {
                        "Active (${String.format("%02d:%02d–%02d:%02d", schedule.startHour, schedule.startMinute, schedule.endHour, schedule.endMinute)})"
                    } else {
                        "Disabled (Always locked)"
                    }
                    SettingsRow(
                        icon = Icons.Default.Schedule,
                        title = "Lock Schedule",
                        subtitle = scheduleSummary,
                        onClick = { showScheduleDialog = true }
                    )
                }
            }

            // ── Section 3: Privacy ──────────────────────────────────────────
            SettingsSectionHeader(title = "Privacy")
            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    SettingsSwitchRow(
                        icon = Icons.Default.BlurOn,
                        title = "Blur in Recents",
                        subtitle = "Mask App Lock screen preview in recent tasks",
                        checked = blurInRecents,
                        onCheckedChange = { enabled ->
                            viewModel.setBlurInRecents(enabled)
                            applySecureFlag(enabled)
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    val notifListenerGranted = NotificationHelper.hasNotificationListenerPermission(context)
                    SettingsSwitchRow(
                        icon = Icons.Default.NotificationsOff,
                        title = "Hide Notifications",
                        subtitle = if (!notifListenerGranted) "Tap to grant Notification Access"
                                   else "Surgically conceal locked app alerts",
                        checked = hideNotifications,
                        onCheckedChange = { enabling ->
                            if (enabling && !notifListenerGranted) {
                                context.startActivity(NotificationHelper.notificationListenerSettingsIntent())
                            } else {
                                viewModel.setHideNotifications(enabling)
                            }
                        }
                    )
                }
            }

            // ── Section 4: Appearance & Extras ──────────────────────────────
            SettingsSectionHeader(title = "Appearance & Activity")
            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column {
                    SettingsRow(
                        icon = Icons.Default.Palette,
                        title = "Theme",
                        subtitle = themeMode.label,
                        onClick = { showThemeDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsRow(
                        icon = Icons.Default.PhotoCamera,
                        title = "Intruder Photos",
                        subtitle = if (intruderCount > 0) "$intruderCount photo(s) captured" else "No photos yet",
                        badge = if (intruderCount > 0) intruderCount.toString() else null,
                        onClick = {
                            context.startActivity(Intent(context, IntruderGalleryActivity::class.java))
                        }
                    )
                }
            }

            // ── Section 5: About ────────────────────────────────────────────
            SettingsSectionHeader(title = "About")
            ElevatedCard(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                SettingsRow(
                    icon = Icons.Default.Info,
                    title = "About App Lock Priv",
                    subtitle = "Version 1.0 • AES-256 Encrypted",
                    onClick = { showAboutDialog = true }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ── Nested Dialogs ───────────────────────────────────────────────────────
    if (showScheduleDialog) {
        ScheduleSettingsDialog(
            currentSchedule = schedule,
            onDismiss = { showScheduleDialog = false },
            onSave = { updated ->
                viewModel.updateSchedule(updated)
                showScheduleDialog = false
            }
        )
    }

    if (showThemeDialog) {
        ThemeSettingsDialog(
            currentMode = themeMode,
            onDismiss = { showThemeDialog = false },
            onSelect = { mode ->
                viewModel.setThemeMode(mode)
                showThemeDialog = false
            }
        )
    }

    if (showGracePeriodDialog) {
        GracePeriodSettingsDialog(
            currentSeconds = gracePeriodSeconds,
            onDismiss = { showGracePeriodDialog = false },
            onSelect = { selected ->
                viewModel.setGracePeriod(selected)
                showGracePeriodDialog = false
            }
        )
    }

    if (showAuthMethodDialog) {
        AuthMethodSettingsDialog(
            currentMethod = authMethod,
            biometricAvailable = isBiometricAvailable,
            onDismiss = { showAuthMethodDialog = false },
            onSelectMethod = { selected ->
                viewModel.setAuthMethod(selected)
                showAuthMethodDialog = false
                if (selected.requiresSecret() && !hasCredential) {
                    showCredentialDialog = true
                }
            }
        )
    }

    if (showCredentialDialog && authMethod.requiresSecret()) {
        CredentialSettingsDialog(
            method = authMethod,
            onDismiss = {
                if (hasCredential) showCredentialDialog = false
            },
            onSave = { secret ->
                viewModel.saveCredential(secret)
                showCredentialDialog = false
            }
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("App Lock Priv") },
            text = {
                Text(
                    "Version 1.0\n\nA modern privacy-centric application locker.\n\n" +
                    "• Biometric & custom credentials\n" +
                    "• AES-256-GCM hardware-backed storage\n" +
                    "• Silent notification suppression\n" +
                    "• Automated intruder capture\n\n" +
                    "Designed for security and privacy."
                )
            },
            confirmButton = {
                Button(onClick = { showAboutDialog = false }) { Text("OK") }
            }
        )
    }
}

// ── Reusable Row Components ─────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (badge != null) {
            Badge(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Text(badge)
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(0.85f)
        )
    }
}

// ── Dialog Helpers ───────────────────────────────────────────────────────────

@Composable
private fun ScheduleSettingsDialog(
    currentSchedule: ScheduleSettings,
    onDismiss: () -> Unit,
    onSave: (ScheduleSettings) -> Unit
) {
    var enabled by remember { mutableStateOf(currentSchedule.enabled) }
    var startHour by remember { mutableIntStateOf(currentSchedule.startHour) }
    var startMinute by remember { mutableIntStateOf(currentSchedule.startMinute) }
    var endHour by remember { mutableIntStateOf(currentSchedule.endHour) }
    var endMinute by remember { mutableIntStateOf(currentSchedule.endMinute) }
    var activeDays by remember { mutableStateOf(currentSchedule.activeDays) }

    val daysMap = listOf(
        Calendar.MONDAY to "M",
        Calendar.TUESDAY to "T",
        Calendar.WEDNESDAY to "W",
        Calendar.THURSDAY to "T",
        Calendar.FRIDAY to "F",
        Calendar.SATURDAY to "S",
        Calendar.SUNDAY to "S"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock Schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Schedule", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }

                if (enabled) {
                    Text(
                        "Apps will only lock during this active window. Outside the window, apps remain unlocked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Start Time:")
                        TimeControls(hour = startHour, minute = startMinute, onHourChange = { startHour = it }, onMinuteChange = { startMinute = it })
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("End Time:")
                        TimeControls(hour = endHour, minute = endMinute, onHourChange = { endHour = it }, onMinuteChange = { endMinute = it })
                    }

                    Text("Active Days:", fontWeight = FontWeight.Medium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        daysMap.forEach { (calDay, label) ->
                            val isSelected = activeDays.contains(calDay)
                            Surface(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        activeDays = if (isSelected) {
                                            if (activeDays.size > 1) activeDays - calDay else activeDays
                                        } else {
                                            activeDays + calDay
                                        }
                                    },
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = label,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(
                    ScheduleSettings(
                        enabled = enabled,
                        startHour = startHour,
                        startMinute = startMinute,
                        endHour = endHour,
                        endMinute = endMinute,
                        activeDays = activeDays
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TimeControls(
    hour: Int,
    minute: Int,
    onHourChange: (Int) -> Unit,
    onMinuteChange: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalButton(
            onClick = { onHourChange((hour + 1) % 24) },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(String.format("%02d", hour))
        }
        Text(" : ", fontWeight = FontWeight.Bold)
        FilledTonalButton(
            onClick = { onMinuteChange((minute + 15) % 60) },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(String.format("%02d", minute))
        }
    }
}

@Composable
private fun ThemeSettingsDialog(
    currentMode: ThemeMode,
    onDismiss: () -> Unit,
    onSelect: (ThemeMode) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Theme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (currentMode == mode), onClick = { onSelect(mode) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(mode.label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun GracePeriodSettingsDialog(
    currentSeconds: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val options = listOf(
        0 to "Immediately (Re-lock when leaving)",
        30 to "30 seconds",
        60 to "1 minute",
        300 to "5 minutes"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Relock Policy") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (seconds, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(seconds) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (currentSeconds == seconds), onClick = { onSelect(seconds) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun AuthMethodSettingsDialog(
    currentMethod: AuthMethod,
    biometricAvailable: Boolean,
    onDismiss: () -> Unit,
    onSelectMethod: (AuthMethod) -> Unit
) {
    var selectedMethod by remember { mutableStateOf(currentMethod) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Authentication Method") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AuthMethod.entries.forEach { method ->
                    val isAvailable = method != AuthMethod.FINGERPRINT || biometricAvailable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isAvailable) {
                                selectedMethod = method
                                errorText = null
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedMethod == method),
                            onClick = {
                                selectedMethod = method
                                errorText = null
                            },
                            enabled = isAvailable
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = method.label,
                            color = if (isAvailable) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (!isAvailable) {
                            Text("Not available", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                        } else if (selectedMethod == method) {
                            Text("Selected", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                if (errorText != null) {
                    Text(text = errorText!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (selectedMethod == AuthMethod.FINGERPRINT && !biometricAvailable) {
                    errorText = "Fingerprint is not available on this device."
                } else {
                    onSelectMethod(selectedMethod)
                }
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CredentialSettingsDialog(
    method: AuthMethod,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var passcode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf(emptyList<Int>()) }
    var resetPatternKey by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (method) {
                    AuthMethod.PASSCODE -> "Set 6-digit Passcode"
                    AuthMethod.PATTERN -> "Set Pattern"
                    AuthMethod.ALPHANUMERIC -> "Set Password"
                    AuthMethod.FINGERPRINT -> "Set Credential"
                }
            )
        },
        text = {
            when (method) {
                AuthMethod.PASSCODE -> {
                    OutlinedTextField(
                        value = passcode,
                        onValueChange = { input ->
                            if (input.length <= 6 && input.all { it.isDigit() }) passcode = input
                        },
                        label = { Text("Passcode (6 digits)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AuthMethod.PATTERN -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (pattern.size < 4) "Swipe across at least 4 dots"
                            else "Pattern recorded (${pattern.size} dots)",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (pattern.size >= 4) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                            PatternLockView(
                                resetKey = resetPatternKey,
                                onPatternComplete = { drawn -> pattern = drawn }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                pattern = emptyList()
                                resetPatternKey++
                            },
                            enabled = pattern.isNotEmpty()
                        ) { Text("Reset Pattern") }
                    }
                }
                AuthMethod.ALPHANUMERIC -> {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password (min 4 characters)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                AuthMethod.FINGERPRINT -> Unit
            }
        },
        confirmButton = {
            val enabled = when (method) {
                AuthMethod.PASSCODE -> passcode.length == 6
                AuthMethod.PATTERN -> pattern.size >= 4
                AuthMethod.ALPHANUMERIC -> password.length >= 4
                AuthMethod.FINGERPRINT -> false
            }
            Button(
                enabled = enabled,
                onClick = {
                    val secret = when (method) {
                        AuthMethod.PASSCODE -> passcode
                        AuthMethod.PATTERN -> pattern.joinToString(",")
                        AuthMethod.ALPHANUMERIC -> password
                        AuthMethod.FINGERPRINT -> ""
                    }
                    onSave(secret)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
