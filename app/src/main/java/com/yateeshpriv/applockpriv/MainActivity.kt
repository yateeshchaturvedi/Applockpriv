package com.yateeshpriv.applockpriv

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yateeshpriv.applockpriv.ui.theme.ApplockprivTheme

class MainActivity : FragmentActivity() {

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        checkPermissionsAndShowContent()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // One-time transparent migration from unencrypted v1 prefs to encrypted prefs
        PrefsHelper.migrateFromPlaintext(this)
        applySecureFlag()
    }

    override fun onResume() {
        super.onResume()
        applySecureFlag()
        checkOnboardingAndProceed()
    }

    private fun applySecureFlag() {
        val settingsPrefs = PrefsHelper.getSettingsPrefs(this)
        val blurEnabled = settingsPrefs.getBoolean("blur_in_recents", false)
        if (blurEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private fun checkOnboardingAndProceed() {
        val settingsPrefs = PrefsHelper.getSettingsPrefs(this)
        val onboardingDone = settingsPrefs.getBoolean("onboarding_done", false)

        if (!onboardingDone) {
            enableEdgeToEdge()
            setContent {
                val themeMode = ThemeMode.fromValue(
                    settingsPrefs.getString("theme_mode", ThemeMode.SYSTEM.value)
                )
                val isDark = when (themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
                ApplockprivTheme(darkTheme = isDark) {
                    OnboardingScreen(
                        onComplete = {
                            settingsPrefs.edit().putBoolean("onboarding_done", true).apply()
                            checkPermissionsAndShowContent()
                        }
                    )
                }
            }
        } else {
            checkPermissionsAndShowContent()
        }
    }

    private fun checkPermissionsAndShowContent() {
        if (!isUsageStatsPermissionGranted()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            setContent {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Please grant usage access permission in settings.")
                }
            }
            return
        }

        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            setContent {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Please grant 'Display over other apps' permission.")
                }
            }
            return
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            setContent {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Please disable battery optimization for App Lock to work reliably.")
                }
            }
            return
        }

        val neededPermissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                neededPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            neededPermissions.add(Manifest.permission.CAMERA)
        }

        if (neededPermissions.isNotEmpty()) {
            requestPermissionsLauncher.launch(neededPermissions.toTypedArray())
            setContent {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Requesting necessary permissions...")
                }
            }
        } else {
            showApp()
        }
    }

    private fun showApp() {
        enableEdgeToEdge()
        setContent {
            val viewModel: AppListViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            ApplockprivTheme(darkTheme = isDark) {
                val selfProtection by viewModel.selfProtection.collectAsState()
                val authMethod by viewModel.authMethod.collectAsState()
                val hasCredential by viewModel.hasCredential.collectAsState()

                var isSelfUnlocked by remember {
                    mutableStateOf(!selfProtection || !hasCredential)
                }

                if (!isSelfUnlocked && hasCredential) {
                    LockScreen(
                        packageName = packageName,
                        authMethod = authMethod,
                        onVerifySecret = { input -> viewModel.verifyCredential(input) },
                        biometricAvailable = isBiometricAvailable(),
                        onRequestBiometric = {
                            requestBiometric { isSelfUnlocked = true }
                        },
                        onUnlock = { isSelfUnlocked = true }
                    )
                } else {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        AppListScreen(
                            modifier = Modifier.padding(innerPadding),
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    private fun requestBiometric(onSuccess: () -> Unit) {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock App Lock")
            .setSubtitle("Authenticate to access settings")
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }
}