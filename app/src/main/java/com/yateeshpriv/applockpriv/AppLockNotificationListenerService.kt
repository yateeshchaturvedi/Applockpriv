package com.yateeshpriv.applockpriv

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Granular Notification Interception Service.
 *
 * Conceals notification alerts, previews, and sender messages ONLY for apps
 * that are currently in the locked apps set and not yet authenticated,
 * while leaving system alarms, phone calls, and other non-locked apps unaffected.
 */
class AppLockNotificationListenerService : NotificationListenerService() {

    companion object {
        @Volatile
        var isRunning: Boolean = false
            private set

        fun isNotificationListenerEnabled(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false

            val expectedComponentName = ComponentName(context, AppLockNotificationListenerService::class.java).flattenToString()
            return enabledListeners.contains(expectedComponentName)
        }

        fun notificationListenerSettingsIntent(): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isRunning = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        isRunning = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val targetPackage = sbn.packageName ?: return

        // Do not suppress our own persistent foreground service notification
        if (targetPackage == packageName) return

        val settingsPrefs = PrefsHelper.getSettingsPrefs(applicationContext)
        val hideNotifications = settingsPrefs.getBoolean("hide_notifications", false)
        if (!hideNotifications) return

        val lockedAppsPrefs = PrefsHelper.getLockedAppsPrefs(applicationContext)
        val lockedApps = lockedAppsPrefs.getStringSet("locked_apps_set", emptySet()) ?: emptySet()
        if (!lockedApps.contains(targetPackage)) return

        // Check if target app has been unlocked and is within grace period
        val gracePeriodSeconds = settingsPrefs.getInt("grace_period_seconds", 0)
        val isCurrentlyUnlocked = (targetPackage == AppLockService.unlockedApp) &&
            (gracePeriodSeconds == 0 || System.currentTimeMillis() < AppLockService.unlockedUntil)

        if (isCurrentlyUnlocked) return

        // Surgically suppress/cancel the notification from status bar and shade
        try {
            cancelNotification(sbn.key)
        } catch (_: Exception) {
            // Ignore if notification cannot be cancelled by system
        }
    }
}
