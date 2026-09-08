package com.yateeshpriv.applockpriv

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Manages notification suppression via Android's Do-Not-Disturb API.
 *
 * When "Hide Notifications" is enabled and a locked app's lock screen is showing,
 * we temporarily set INTERRUPTION_FILTER_NONE to silence all notifications.
 * Notifications are fully restored as soon as the locked app is unlocked or
 * the user navigates away from it.
 *
 * Requires [android.Manifest.permission.ACCESS_NOTIFICATION_POLICY] and the
 * user must have granted DND access via Settings → Special App Access → Do Not Disturb.
 */
object NotificationHelper {

    /**
     * Silences all notifications (sets DND to Total Silence).
     * No-ops if DND permission has not been granted.
     */
    fun suppressNotifications(context: Context) {
        val manager = notificationManager(context)
        if (!manager.isNotificationPolicyAccessGranted) return
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
    }

    /**
     * Restores full notifications (clears DND Total Silence).
     * No-ops if DND permission has not been granted.
     */
    fun restoreNotifications(context: Context) {
        val manager = notificationManager(context)
        if (!manager.isNotificationPolicyAccessGranted) return
        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
    }

    /**
     * Returns true if this app has been granted Do-Not-Disturb policy access.
     */
    fun hasDndPermission(context: Context): Boolean =
        notificationManager(context).isNotificationPolicyAccessGranted

    /**
     * Returns an [Intent] that opens the system's DND policy access settings screen,
     * where the user can grant permission to this app.
     */
    fun dndSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /**
     * Returns true if this app has been granted Notification Listener Access
     * for granular per-app notification concealment.
     */
    fun hasNotificationListenerPermission(context: Context): Boolean =
        AppLockNotificationListenerService.isNotificationListenerEnabled(context)

    /**
     * Returns an [Intent] that opens the system's Notification Listener settings screen.
     */
    fun notificationListenerSettingsIntent(): Intent =
        AppLockNotificationListenerService.notificationListenerSettingsIntent()

    private fun notificationManager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
