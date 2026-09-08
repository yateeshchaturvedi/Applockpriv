package com.yateeshpriv.applockpriv

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable?,
    val isSystemApp: Boolean = false
)

enum class AppFilter(val label: String) {
    ALL("All"),
    LOCKED("Locked"),
    USER("User"),
    SYSTEM("System")
}

enum class AppSort(val label: String) {
    NAME_ASC("A–Z"),
    NAME_DESC("Z–A"),
    LOCKED_FIRST("Locked First")
}

/**
 * ViewModel for [AppListScreen].
 *
 * Owns all persistent state (prefs, locked apps, auth settings, filters, schedules, themes)
 * and exposes it as [StateFlow]s so the composable is stateless w.r.t. business data.
 */
class AppListViewModel(application: Application) : AndroidViewModel(application) {

    // ── Prefs ────────────────────────────────────────────────────────────────
    private val passPrefs     = PrefsHelper.getPassPrefs(application)
    private val settingsPrefs = PrefsHelper.getSettingsPrefs(application)
    private val lockedPrefs   = PrefsHelper.getLockedAppsPrefs(application)

    // ── Auth ────────────────────────────────────────────────────────────────
    private val _authMethod = MutableStateFlow(
        AuthMethod.fromValue(passPrefs.getString("auth_method", AuthMethod.PASSCODE.value))
    )
    val authMethod: StateFlow<AuthMethod> = _authMethod.asStateFlow()

    private val _hasCredential = MutableStateFlow(checkHasCredential())
    val hasCredential: StateFlow<Boolean> = _hasCredential.asStateFlow()

    // ── Locked apps ─────────────────────────────────────────────────────────
    private val _lockedApps = MutableStateFlow<Set<String>>(
        lockedPrefs.getStringSet("locked_apps_set", emptySet()) ?: emptySet()
    )
    val lockedApps: StateFlow<Set<String>> = _lockedApps.asStateFlow()

    // ── Settings ────────────────────────────────────────────────────────────
    private val _blurInRecents = MutableStateFlow(
        settingsPrefs.getBoolean("blur_in_recents", false)
    )
    val blurInRecents: StateFlow<Boolean> = _blurInRecents.asStateFlow()

    private val _hideNotifications = MutableStateFlow(
        settingsPrefs.getBoolean("hide_notifications", false)
    )
    val hideNotifications: StateFlow<Boolean> = _hideNotifications.asStateFlow()

    private val _gracePeriodSeconds = MutableStateFlow(
        settingsPrefs.getInt("grace_period_seconds", 0)
    )
    val gracePeriodSeconds: StateFlow<Int> = _gracePeriodSeconds.asStateFlow()

    private val _selfProtection = MutableStateFlow(
        settingsPrefs.getBoolean("self_protection", false)
    )
    val selfProtection: StateFlow<Boolean> = _selfProtection.asStateFlow()

    private val _themeMode = MutableStateFlow(
        ThemeMode.fromValue(settingsPrefs.getString("theme_mode", ThemeMode.SYSTEM.value))
    )
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    private val _schedule = MutableStateFlow(
        ScheduleHelper.loadSchedule(application)
    )
    val schedule: StateFlow<ScheduleSettings> = _schedule.asStateFlow()

    // ── Search, Filters & Sorting ───────────────────────────────────────────
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(AppFilter.ALL)
    val selectedFilter: StateFlow<AppFilter> = _selectedFilter.asStateFlow()

    private val _selectedSort = MutableStateFlow(AppSort.NAME_ASC)
    val selectedSort: StateFlow<AppSort> = _selectedSort.asStateFlow()

    /**
     * Full app list loaded once via PackageManager (lazy).
     */
    val allApps: List<AppInfo> by lazy {
        val pm = application.packageManager
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).map { ri ->
            val isSystem = (ri.activityInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            AppInfo(
                appName     = ri.loadLabel(pm).toString(),
                packageName = ri.activityInfo.packageName,
                icon        = ri.loadIcon(pm),
                isSystemApp = isSystem
            )
        }.distinctBy { it.packageName }.sortedBy { it.appName }
    }

    /**
     * Reactive list computed from query, filter, sort, and locked state.
     */
    val filteredApps: StateFlow<List<AppInfo>> = combine(
        _searchQuery,
        _selectedFilter,
        _selectedSort,
        _lockedApps
    ) { query, filter, sort, locked ->
        var list = allApps

        // 1. Filter by category
        list = when (filter) {
            AppFilter.ALL -> list
            AppFilter.LOCKED -> list.filter { locked.contains(it.packageName) }
            AppFilter.USER -> list.filter { !it.isSystemApp }
            AppFilter.SYSTEM -> list.filter { it.isSystemApp }
        }

        // 2. Filter by search query
        if (query.isNotBlank()) {
            list = list.filter { it.appName.contains(query, ignoreCase = true) }
        }

        // 3. Sort
        when (sort) {
            AppSort.NAME_ASC -> list.sortedBy { it.appName.lowercase() }
            AppSort.NAME_DESC -> list.sortedByDescending { it.appName.lowercase() }
            AppSort.LOCKED_FIRST -> list.sortedWith(
                compareByDescending<AppInfo> { locked.contains(it.packageName) }
                    .thenBy { it.appName.lowercase() }
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), allApps)

    // ── Actions ─────────────────────────────────────────────────────────────

    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun setFilter(filter: AppFilter) { _selectedFilter.value = filter }

    fun setSort(sort: AppSort) { _selectedSort.value = sort }

    fun toggleAppLock(packageName: String) {
        val updated = _lockedApps.value.toMutableSet().also {
            if (it.contains(packageName)) it.remove(packageName) else it.add(packageName)
        }
        _lockedApps.value = updated
        lockedPrefs.edit().putStringSet("locked_apps_set", updated).apply()
    }

    fun setAuthMethod(method: AuthMethod) {
        _authMethod.value = method
        passPrefs.edit().putString("auth_method", method.value).apply()
        _hasCredential.value = checkHasCredential()
    }

    fun saveCredential(secret: String) {
        val salt   = CryptoUtils.generateSalt()
        val hashed = CryptoUtils.hashSecret(secret, salt)
        passPrefs.edit()
            .putString("auth_secret", hashed)
            .putString("auth_salt",   salt)
            .remove("password")
            .apply()
        _hasCredential.value = true
    }

    fun verifyCredential(input: String): Boolean {
        val hash = passPrefs.getString("auth_secret", null) ?: return false
        val salt = passPrefs.getString("auth_salt",   null) ?: return false
        return CryptoUtils.verify(input, hash, salt)
    }

    fun setBlurInRecents(enabled: Boolean) {
        _blurInRecents.value = enabled
        settingsPrefs.edit().putBoolean("blur_in_recents", enabled).apply()
    }

    fun setHideNotifications(enabled: Boolean) {
        _hideNotifications.value = enabled
        settingsPrefs.edit().putBoolean("hide_notifications", enabled).apply()
    }

    fun setGracePeriod(seconds: Int) {
        _gracePeriodSeconds.value = seconds
        settingsPrefs.edit().putInt("grace_period_seconds", seconds).apply()
    }

    fun setSelfProtection(enabled: Boolean) {
        _selfProtection.value = enabled
        settingsPrefs.edit().putBoolean("self_protection", enabled).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        settingsPrefs.edit().putString("theme_mode", mode.value).apply()
    }

    fun updateSchedule(schedule: ScheduleSettings) {
        _schedule.value = schedule
        ScheduleHelper.saveSchedule(getApplication(), schedule)
    }

    fun ensureServiceStarted(context: Context) {
        context.startService(Intent(context, AppLockService::class.java))
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun checkHasCredential(): Boolean {
        if (!_authMethod.value.requiresSecret()) return true
        val secret = passPrefs.getString("auth_secret", null)
        val salt   = passPrefs.getString("auth_salt",   null)
        return !secret.isNullOrBlank() && !salt.isNullOrBlank()
    }
}
