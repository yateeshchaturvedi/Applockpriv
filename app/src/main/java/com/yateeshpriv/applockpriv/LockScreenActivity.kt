package com.yateeshpriv.applockpriv

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent

class LockScreenActivity : FragmentActivity() {
    private lateinit var lockedPackageName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val settingsPrefs = getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
        val blurEnabled = settingsPrefs.getBoolean("blur_in_recents", false)

        if (blurEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        val passPrefs = getSharedPreferences("pass_prefs", Context.MODE_PRIVATE)
        migrateLegacyPasscode(passPrefs)

        lockedPackageName = intent.getStringExtra("locked_app_package") ?: ""
        val authMethodString = passPrefs.getString("auth_method", AuthMethod.PASSCODE.value) ?: AuthMethod.PASSCODE.value
        val authMethod = AuthMethod.fromValue(authMethodString)
        val savedSecret = passPrefs.getString("auth_secret", "123456") ?: "123456"
        val biometricAvailable = isBiometricAvailable()

        setContent {
            LockScreen(
                packageName = lockedPackageName,
                authMethod = authMethod,
                savedSecret = savedSecret,
                biometricAvailable = biometricAvailable,
                onRequestBiometric = { requestBiometric() },
                onUnlock = { unlockAndFinish() }
            )
        }
    }

    private fun migrateLegacyPasscode(passPrefs: android.content.SharedPreferences) {
        val legacyPass = passPrefs.getString("password", null)
        val hasSecret = passPrefs.contains("auth_secret")
        if (!hasSecret && !legacyPass.isNullOrBlank()) {
            passPrefs.edit()
                .putString("auth_secret", legacyPass)
                .putString("auth_method", AuthMethod.PASSCODE.value)
                .apply()
        }
    }

    private fun isBiometricAvailable(): Boolean {
        val biometricManager = BiometricManager.from(this)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    private fun requestBiometric() {
        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    unlockAndFinish()
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock App")
            .setSubtitle("Authenticate to continue")
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun unlockAndFinish() {
        AppLockService.unlockedApp = lockedPackageName
        finish()
    }
}
