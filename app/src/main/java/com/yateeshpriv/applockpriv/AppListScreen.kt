package com.yateeshpriv.applockpriv

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.biometric.BiometricManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.google.accompanist.drawablepainter.rememberDrawablePainter

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val passPrefs = remember { context.getSharedPreferences("pass_prefs", Context.MODE_PRIVATE) }
    val settingsPrefs = remember { context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE) }
    val lockedPrefs = remember { context.getSharedPreferences("locked_apps", Context.MODE_PRIVATE) }
    val packageManager = context.packageManager

    var showMenu by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAuthMethodDialog by remember { mutableStateOf(false) }
    var showCredentialDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    var authMethod by remember {
        mutableStateOf(AuthMethod.fromValue(passPrefs.getString("auth_method", AuthMethod.PASSCODE.value)))
    }
    var blurInRecents by remember {
        mutableStateOf(settingsPrefs.getBoolean("blur_in_recents", false))
    }
    var hideNotifications by remember {
        mutableStateOf(settingsPrefs.getBoolean("hide_notifications", false))
    }
    var lockedApps by remember {
        mutableStateOf(lockedPrefs.getStringSet("locked_apps_set", emptySet()) ?: emptySet())
    }

    val isBiometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    LaunchedEffect(Unit) {
        val legacyPass = passPrefs.getString("password", null)
        if (!passPrefs.contains("auth_secret") && !legacyPass.isNullOrBlank()) {
            passPrefs.edit {
                putString("auth_secret", legacyPass)
                putString("auth_method", AuthMethod.PASSCODE.value)
            }
            authMethod = AuthMethod.PASSCODE
        }
        if (!passPrefs.contains("auth_method")) {
            passPrefs.edit { putString("auth_method", AuthMethod.PASSCODE.value) }
            authMethod = AuthMethod.PASSCODE
        }
        if (authMethod.requiresSecret() && passPrefs.getString("auth_secret", null).isNullOrBlank()) {
            showCredentialDialog = true
        }
        context.startService(Intent(context, AppLockService::class.java))
    }

    val allApps = remember {
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
        resolveInfos.map {
            AppInfo(
                appName = it.loadLabel(packageManager).toString(),
                packageName = it.activityInfo.packageName,
                icon = it.loadIcon(packageManager)
            )
        }.distinctBy { it.packageName }.sortedBy { it.appName }
    }

    val filteredApps = remember(searchQuery, allApps) {
        if (searchQuery.isBlank()) allApps
        else allApps.filter { it.appName.contains(searchQuery, ignoreCase = true) }
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
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("App Lock Priv", fontWeight = FontWeight.Bold)
                            Text(
                                text = "${lockedApps.size} apps protected",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    },
                    actions = {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Auth: ${authMethod.label}") },
                                onClick = {
                                    showMenu = false
                                    showAuthMethodDialog = true
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when (authMethod) {
                                            AuthMethod.PASSCODE -> Icons.Default.Lock
                                            AuthMethod.PATTERN -> Icons.Default.Lock
                                            AuthMethod.ALPHANUMERIC -> Icons.Default.Lock
                                            AuthMethod.FINGERPRINT -> Icons.Default.Fingerprint
                                        },
                                        contentDescription = null
                                    )
                                }
                            )
                            if (authMethod.requiresSecret()) {
                                DropdownMenuItem(
                                    text = { Text("Change Credential") },
                                    onClick = {
                                        showMenu = false
                                        showCredentialDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) }
                                )
                            }
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Blur in Recents")
                                        Spacer(Modifier.weight(1f))
                                        Switch(
                                            checked = blurInRecents,
                                            onCheckedChange = null,
                                            modifier = Modifier.scale(0.7f)
                                        )
                                    }
                                },
                                onClick = {
                                    blurInRecents = !blurInRecents
                                    settingsPrefs.edit { putBoolean("blur_in_recents", blurInRecents) }
                                    applySecureFlag(blurInRecents)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Hide Notifications")
                                        Spacer(Modifier.weight(1f))
                                        Switch(
                                            checked = hideNotifications,
                                            onCheckedChange = null,
                                            modifier = Modifier.scale(0.7f)
                                        )
                                    }
                                },
                                onClick = {
                                    hideNotifications = !hideNotifications
                                    settingsPrefs.edit { putBoolean("hide_notifications", hideNotifications) }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("About") },
                                onClick = {
                                    showMenu = false
                                    showAboutDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                            )
                        }
                    }
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Search apps...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }
        },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(filteredApps) { app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    ListItem(
                        headlineContent = {
                            Text(
                                text = app.appName,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        },
                        supportingContent = {
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        },
                        leadingContent = {
                            Image(
                                painter = rememberDrawablePainter(drawable = app.icon),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = lockedApps.contains(app.packageName),
                                onCheckedChange = { checked ->
                                    val updated = lockedApps.toMutableSet()
                                    if (checked) updated.add(app.packageName) else updated.remove(app.packageName)
                                    lockedApps = updated
                                    lockedPrefs.edit { putStringSet("locked_apps_set", updated) }
                                }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }

    if (showAuthMethodDialog) {
        AuthMethodDialog(
            currentMethod = authMethod,
            biometricAvailable = isBiometricAvailable,
            onDismiss = { showAuthMethodDialog = false },
            onSelectMethod = { selected ->
                authMethod = selected
                passPrefs.edit { putString("auth_method", selected.value) }
                showAuthMethodDialog = false
                if (selected.requiresSecret()) {
                    showCredentialDialog = true
                }
            }
        )
    }

    if (showCredentialDialog && authMethod.requiresSecret()) {
        CredentialDialog(
            method = authMethod,
            onDismiss = {
                if (passPrefs.contains("auth_secret")) {
                    showCredentialDialog = false
                }
            },
            onSave = { secret ->
                passPrefs.edit {
                    putString("auth_secret", secret)
                    remove("password")
                }
                showCredentialDialog = false
            }
        )
    }

    if (showAboutDialog) {
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val version = packageInfo.versionName
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            icon = { Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(32.dp)) },
            title = { Text("App Lock Priv", fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Secure your apps with privacy.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Version: $version",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("© 2026 Yateesh", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("Close") }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }
}

@Composable
private fun AuthMethodDialog(
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AuthMethod.entries.forEach { method ->
                    val disabled = method == AuthMethod.FINGERPRINT && !biometricAvailable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selectedMethod == method) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else Color.Transparent
                            )
                            .clickable(enabled = !disabled) {
                                selectedMethod = method
                                errorText = null
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(method.label, modifier = Modifier.weight(1f))
                        if (disabled) {
                            Text(
                                "Unavailable",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else if (selectedMethod == method) {
                            Text(
                                "Selected",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
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
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CredentialDialog(
    method: AuthMethod,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var passcode by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var pattern by remember { mutableStateOf(emptyList<Int>()) }

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
                        label = { Text("Passcode") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                }
                AuthMethod.PATTERN -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Tap at least 4 points in order",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PatternBuilderPad(
                            selected = pattern,
                            onTap = { point ->
                                if (!pattern.contains(point)) pattern = pattern + point
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { pattern = emptyList() }) { Text("Clear Pattern") }
                    }
                }
                AuthMethod.ALPHANUMERIC -> {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
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
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PatternBuilderPad(
    selected: List<Int>,
    onTap: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        (0..2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                (1..3).forEach { col ->
                    val point = row * 3 + col
                    val isSelected = selected.contains(point)
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                            .border(
                                width = 2.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .clickable { onTap(point) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(point.toString(), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
