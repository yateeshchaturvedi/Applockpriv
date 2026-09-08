package com.yateeshpriv.applockpriv

import android.content.Context
import android.content.SharedPreferences
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

        val settingsPrefs = PrefsHelper.getSettingsPrefs(this)
        val blurEnabled = settingsPrefs.getBoolean("blur_in_recents", false)
        if (blurEnabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        val passPrefs = PrefsHelper.getPassPrefs(this)
        // Migrate any legacy plaintext secrets to hashed form before reading
        migrateLegacyPasscode(passPrefs)

        lockedPackageName = intent.getStringExtra("locked_app_package") ?: ""
        val authMethodString = passPrefs.getString("auth_method", AuthMethod.PASSCODE.value)
            ?: AuthMethod.PASSCODE.value
        val authMethod = AuthMethod.fromValue(authMethodString)
        val biometricAvailable = isBiometricAvailable()

        // Load the stored hash and salt — never fall back to a default credential
        val storedHash = passPrefs.getString("auth_secret", null)
        val salt = passPrefs.getString("auth_salt", null)

        // Credential is required for all methods except FINGERPRINT
        val credentialMissing = authMethod.requiresSecret() && (storedHash == null || salt == null)

        setContent {
            if (credentialMissing) {
                // No credential has been set up — guide user to configure the app
                NoCredentialScreen()
            } else {
                LockScreen(
                    packageName = lockedPackageName,
                    authMethod = authMethod,
                    onVerifySecret = { input ->
                        // Verify using hashed comparison; biometric path never reaches here
                        if (storedHash != null && salt != null) {
                            CryptoUtils.verify(input, storedHash, salt)
                        } else {
                            false
                        }
                    },
                    biometricAvailable = biometricAvailable,
                    onRequestBiometric = { requestBiometric() },
                    onUnlock = { unlockAndFinish() }
                )
            }
        }
    }

    /**
     * Handles two migration steps in order:
     * 1. Move legacy "password" key → "auth_secret" (plaintext, will be hashed next).
     * 2. Detect any existing plaintext "auth_secret" and hash it with a new salt.
     */
    private fun migrateLegacyPasscode(passPrefs: SharedPreferences) {
        // Step 1: old "password" key → "auth_secret"
        val legacyPass = passPrefs.getString("password", null)
        val hasSecret = passPrefs.contains("auth_secret")
        if (!hasSecret && !legacyPass.isNullOrBlank()) {
            passPrefs.edit()
                .putString("auth_secret", legacyPass) // temporarily plaintext; hashed in step 2
                .putString("auth_method", AuthMethod.PASSCODE.value)
                .apply()
        }

        // Step 2: hash any remaining plaintext "auth_secret"
        val existingSecret = passPrefs.getString("auth_secret", null)
        if (existingSecret != null && CryptoUtils.isLegacyPlaintext(existingSecret)) {
            val salt = CryptoUtils.generateSalt()
            val hashed = CryptoUtils.hashSecret(existingSecret, salt)
            passPrefs.edit()
                .putString("auth_secret", hashed)
                .putString("auth_salt", salt)
                .remove("password")
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

                // Called when a finger is presented but not recognized —
                // the system already shows "Not recognized", so no extra action needed.
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }

                // Called on sensor errors or user cancellation.
                // We only act on genuine errors (not user-initiated cancel).
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // ERROR_NEGATIVE_BUTTON = user pressed "Cancel"; ERROR_USER_CANCELED = swiped away.
                    // In both cases, just let the lock screen stay visible.
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
        val gracePeriodSeconds = PrefsHelper.getSettingsPrefs(this).getInt("grace_period_seconds", 0)
        AppLockService.markUnlocked(lockedPackageName, gracePeriodSeconds)
        finish()
    }
}
