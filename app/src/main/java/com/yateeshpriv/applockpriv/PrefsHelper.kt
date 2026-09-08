package com.yateeshpriv.applockpriv

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Central factory for all SharedPreferences access, backed by AES-256 encryption via
 * Android Keystore. All sensitive data (credentials, locked app list, settings) is stored
 * in encrypted files so root-level reads cannot expose them in plaintext.
 *
 * Also provides [migrateFromPlaintext] to transparently upgrade existing v1.0 users
 * from the old unencrypted files to the new encrypted ones.
 */
object PrefsHelper {

    // Encrypted file names (different from old plaintext names to allow side-by-side migration)
    private const val PASS_PREFS_FILE     = "pass_prefs_enc"
    private const val SETTINGS_PREFS_FILE = "settings_prefs_enc"
    private const val LOCKED_APPS_FILE    = "locked_apps_enc"

    // Migration guard key stored inside the encrypted settings file
    private const val MIGRATION_DONE_KEY  = "migration_v1_enc_done"

    fun getPassPrefs(context: Context): SharedPreferences =
        getEncrypted(context, PASS_PREFS_FILE)

    fun getSettingsPrefs(context: Context): SharedPreferences =
        getEncrypted(context, SETTINGS_PREFS_FILE)

    fun getLockedAppsPrefs(context: Context): SharedPreferences =
        getEncrypted(context, LOCKED_APPS_FILE)

    /**
     * One-time migration: copies data from the old plaintext SharedPreferences files to the
     * new encrypted ones, then deletes the plaintext files.
     *
     * Safe to call repeatedly — idempotent via [MIGRATION_DONE_KEY].
     * Also marks onboarding as done for existing users who already have a configuration,
     * so they don't see the first-launch wizard.
     */
    fun migrateFromPlaintext(context: Context) {
        val settingsPrefs = getSettingsPrefs(context)
        if (settingsPrefs.getBoolean(MIGRATION_DONE_KEY, false)) return  // already migrated

        // ── Pass prefs ──────────────────────────────────────────────────────
        migrateSinglePrefs(
            context,
            oldName  = "pass_prefs",
            newPrefs = getPassPrefs(context),
            copy     = { old, new ->
                old.getString("auth_secret", null)?.let { new.putString("auth_secret", it) }
                old.getString("auth_salt", null)?.let   { new.putString("auth_salt",   it) }
                old.getString("auth_method", null)?.let { new.putString("auth_method", it) }
            }
        )

        // ── Locked apps prefs ───────────────────────────────────────────────
        migrateSinglePrefs(
            context,
            oldName  = "locked_apps",
            newPrefs = getLockedAppsPrefs(context),
            copy     = { old, new ->
                old.getStringSet("locked_apps_set", null)
                    ?.let { new.putStringSet("locked_apps_set", it) }
            }
        )

        // ── Settings prefs ──────────────────────────────────────────────────
        migrateSinglePrefs(
            context,
            oldName  = "settings_prefs",
            newPrefs = settingsPrefs,
            copy     = { old, new ->
                new.putBoolean("blur_in_recents",    old.getBoolean("blur_in_recents",    false))
                new.putBoolean("hide_notifications", old.getBoolean("hide_notifications", false))
                new.putInt("grace_period_seconds",   old.getInt("grace_period_seconds",   0))
            }
        )

        // Mark existing users as having completed onboarding (skip the first-run wizard)
        val hasConfig = getPassPrefs(context).contains("auth_secret") ||
            (getLockedAppsPrefs(context).getStringSet("locked_apps_set", emptySet())?.isNotEmpty() == true)
        if (hasConfig) {
            settingsPrefs.edit().putBoolean("onboarding_done", true).apply()
        }

        settingsPrefs.edit().putBoolean(MIGRATION_DONE_KEY, true).apply()
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private fun migrateSinglePrefs(
        context: Context,
        oldName: String,
        newPrefs: SharedPreferences,
        copy: (SharedPreferences, SharedPreferences.Editor) -> Unit
    ) {
        try {
            val old = context.getSharedPreferences(oldName, Context.MODE_PRIVATE)
            if (old.all.isNotEmpty()) {
                val editor = newPrefs.edit()
                copy(old, editor)
                editor.apply()
                context.deleteSharedPreferences(oldName)
            }
        } catch (_: Exception) {
            // Migration failure is non-fatal — user will just need to re-configure
        }
    }

    /**
     * Creates or opens an AES-256-GCM encrypted SharedPreferences file.
     * Falls back to re-creating the file (data loss) if the Keystore is corrupted,
     * and as a last resort falls back to plaintext if the Keystore is completely broken.
     */
    private fun getEncrypted(context: Context, fileName: String): SharedPreferences {
        val appContext = context.applicationContext
        return try {
            buildEncrypted(appContext, fileName)
        } catch (_: Exception) {
            // KeyStore entry might be corrupted (e.g., device lock screen changed) — clear and retry
            try {
                appContext.deleteSharedPreferences(fileName)
                buildEncrypted(appContext, fileName)
            } catch (_: Exception) {
                // Ultimate fallback — plaintext (shouldn't happen in practice)
                appContext.getSharedPreferences("${fileName}_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    private fun buildEncrypted(context: Context, fileName: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
}
