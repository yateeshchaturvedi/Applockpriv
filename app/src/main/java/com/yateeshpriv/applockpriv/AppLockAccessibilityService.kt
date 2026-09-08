package com.yateeshpriv.applockpriv

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent

/**
 * Zero-Latency Accessibility Engine for AppLockPriv.
 *
 * Listens directly to [AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED] to achieve 0ms
 * instant locking without polling delays or battery overhead while idle.
 */
class AppLockAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        fun isAccessibilityEnabled(context: Context): Boolean {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val expectedComponentName = "${context.packageName}/${AppLockAccessibilityService::class.java.name}"
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentName = colonSplitter.next()
                if (componentName.equals(expectedComponentName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        fun accessibilitySettingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val targetPackage = event.packageName?.toString() ?: return

        // Ignore our own app, system UI, and keyboards
        if (targetPackage == packageName ||
            targetPackage.startsWith("com.android.systemui") ||
            targetPackage.contains("inputmethod")
        ) {
            return
        }

        val lockedAppsPrefs = PrefsHelper.getLockedAppsPrefs(applicationContext)
        val lockedApps = lockedAppsPrefs.getStringSet("locked_apps_set", emptySet()) ?: emptySet()

        if (!lockedApps.contains(targetPackage)) return

        // Check if schedule permits locking
        if (!ScheduleHelper.isScheduleActive(applicationContext)) return

        // Check if app is currently unlocked within grace period
        val settingsPrefs = PrefsHelper.getSettingsPrefs(applicationContext)
        val gracePeriodSeconds = settingsPrefs.getInt("grace_period_seconds", 0)
        val isCurrentlyUnlocked = (targetPackage == AppLockService.unlockedApp) &&
            (gracePeriodSeconds == 0 || System.currentTimeMillis() < AppLockService.unlockedUntil)

        if (isCurrentlyUnlocked) return

        // Immediately launch lock screen with zero latency
        AppLockService.clearUnlock()
        val lockIntent = Intent(applicationContext, LockScreenActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("locked_app_package", targetPackage)
        }
        startActivity(lockIntent)
    }

    override fun onInterrupt() {
        // Accessibility service interrupted by system
    }
}
