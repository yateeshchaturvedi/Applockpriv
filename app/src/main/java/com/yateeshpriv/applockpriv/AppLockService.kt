package com.yateeshpriv.applockpriv

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppLockService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var wakeLock: PowerManager.WakeLock? = null
    private var monitoringJob: Job? = null

    /**
     * Tracks whether notifications are currently suppressed.
     */
    private var notificationsSuppressed = false

    companion object {
        /**
         * Package name of the currently unlocked app.
         */
        @Volatile
        var unlockedApp: String? = null

        /**
         * Epoch timestamp (ms) until which the app remains unlocked when a grace period is configured.
         */
        @Volatile
        var unlockedUntil: Long = 0L

        /**
         * Called by LockScreenActivity upon successful authentication.
         */
        fun markUnlocked(packageName: String, gracePeriodSeconds: Int) {
            unlockedApp = packageName
            unlockedUntil = if (gracePeriodSeconds > 0) {
                System.currentTimeMillis() + (gracePeriodSeconds * 1000L)
            } else {
                Long.MAX_VALUE // Remains valid until user switches away from the app
            }
        }

        /**
         * Clears unlock state, forcing re-authentication on next launch.
         */
        fun clearUnlock() {
            unlockedApp = null
            unlockedUntil = 0L
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    // Always clear unlock state on screen turn-off so device is secure
                    clearUnlock()
                    monitoringJob?.cancel()
                    wakeLock?.let { if (it.isHeld) it.release() }
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    // Screen turned on — resume monitoring
                    if (wakeLock?.isHeld == false) wakeLock?.acquire()
                    startMonitoring()
                    checkForegroundApp()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AppLock::WakeLock")
        wakeLock?.acquire()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, "app_lock_channel_silent")
            .setContentTitle("App Lock Active")
            .setContentText("Protecting your privacy.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        startForeground(1, notification)
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = serviceScope.launch {
            while (true) {
                checkForegroundApp()
                delay(1000)
            }
        }
    }

    private fun checkForegroundApp() {
        val launcherPackage = getLauncherPackageName()
        val foregroundApp = getForegroundPackage(applicationContext) ?: return

        val lockedAppsPrefs = PrefsHelper.getLockedAppsPrefs(applicationContext)
        val lockedApps = lockedAppsPrefs.getStringSet("locked_apps_set", emptySet()) ?: emptySet()

        val settingsPrefs = PrefsHelper.getSettingsPrefs(applicationContext)
        val hideNotifications = settingsPrefs.getBoolean("hide_notifications", false)
        val gracePeriodSeconds = settingsPrefs.getInt("grace_period_seconds", 0)

        val isGraceActive = gracePeriodSeconds > 0 && System.currentTimeMillis() < unlockedUntil

        when {
            // User is in Launcher or AppLock itself
            foregroundApp == launcherPackage || foregroundApp == packageName -> {
                if (foregroundApp == launcherPackage && !isGraceActive) {
                    clearUnlock()
                }
                restoreNotificationsIfNeeded(hideNotifications)
            }

            // Foreground app is in the locked list
            lockedApps.contains(foregroundApp) -> {
                val isScheduleActive = ScheduleHelper.isScheduleActive(applicationContext)
                val isCurrentlyUnlocked = !isScheduleActive || ((foregroundApp == unlockedApp) &&
                    (gracePeriodSeconds == 0 || System.currentTimeMillis() < unlockedUntil))

                if (isCurrentlyUnlocked) {
                    restoreNotificationsIfNeeded(hideNotifications)
                } else {
                    clearUnlock()
                    if (hideNotifications && !notificationsSuppressed) {
                        NotificationHelper.suppressNotifications(applicationContext)
                        notificationsSuppressed = true
                    }
                    val lockIntent = Intent(applicationContext, LockScreenActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        putExtra("locked_app_package", foregroundApp)
                    }
                    startActivity(lockIntent)
                }
            }

            // Foreground app is some other non-locked app
            else -> {
                if (!isGraceActive) {
                    clearUnlock()
                }
                restoreNotificationsIfNeeded(hideNotifications)
            }
        }
    }

    private fun restoreNotificationsIfNeeded(hideNotificationsEnabled: Boolean) {
        if (notificationsSuppressed) {
            if (hideNotificationsEnabled) {
                NotificationHelper.restoreNotifications(applicationContext)
            }
            notificationsSuppressed = false
        }
    }

    private fun getForegroundPackage(context: Context): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val endTime = System.currentTimeMillis()
        val startTime = endTime - 1000 * 10
        val usageEvents = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()
        var lastForegroundApp: String? = null

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                lastForegroundApp = event.packageName
            }
        }
        return lastForegroundApp
    }

    private fun getLauncherPackageName(): String {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName ?: ""
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(android.app.NotificationManager::class.java)
            val channel = android.app.NotificationChannel(
                "app_lock_channel_silent",
                "App Lock (Silent)",
                android.app.NotificationManager.IMPORTANCE_MIN
            )
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(screenReceiver)
        monitoringJob?.cancel()
        if (notificationsSuppressed) {
            NotificationHelper.restoreNotifications(applicationContext)
            notificationsSuppressed = false
        }
        wakeLock?.let { if (it.isHeld) it.release() }
        serviceJob.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
