package com.yateeshpriv.applockpriv

import android.content.Context
import java.util.Calendar

data class ScheduleSettings(
    val enabled: Boolean = false,
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 17,
    val endMinute: Int = 0,
    val activeDays: Set<Int> = setOf(
        Calendar.MONDAY,
        Calendar.TUESDAY,
        Calendar.WEDNESDAY,
        Calendar.THURSDAY,
        Calendar.FRIDAY,
        Calendar.SATURDAY,
        Calendar.SUNDAY
    )
)

object ScheduleHelper {
    private const val KEY_ENABLED = "schedule_enabled"
    private const val KEY_START_HOUR = "schedule_start_hour"
    private const val KEY_START_MINUTE = "schedule_start_minute"
    private const val KEY_END_HOUR = "schedule_end_hour"
    private const val KEY_END_MINUTE = "schedule_end_minute"
    private const val KEY_DAYS = "schedule_days"

    fun loadSchedule(context: Context): ScheduleSettings {
        val prefs = PrefsHelper.getSettingsPrefs(context)
        val defaultDays = setOf("1", "2", "3", "4", "5", "6", "7")
        val savedDays = prefs.getStringSet(KEY_DAYS, defaultDays) ?: defaultDays
        val activeDays = savedDays.mapNotNull { it.toIntOrNull() }.toSet()

        return ScheduleSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            startHour = prefs.getInt(KEY_START_HOUR, 9),
            startMinute = prefs.getInt(KEY_START_MINUTE, 0),
            endHour = prefs.getInt(KEY_END_HOUR, 17),
            endMinute = prefs.getInt(KEY_END_MINUTE, 0),
            activeDays = if (activeDays.isEmpty()) ScheduleSettings().activeDays else activeDays
        )
    }

    fun saveSchedule(context: Context, schedule: ScheduleSettings) {
        val prefs = PrefsHelper.getSettingsPrefs(context)
        prefs.edit()
            .putBoolean(KEY_ENABLED, schedule.enabled)
            .putInt(KEY_START_HOUR, schedule.startHour)
            .putInt(KEY_START_MINUTE, schedule.startMinute)
            .putInt(KEY_END_HOUR, schedule.endHour)
            .putInt(KEY_END_MINUTE, schedule.endMinute)
            .putStringSet(KEY_DAYS, schedule.activeDays.map { it.toString() }.toSet())
            .apply()
    }

    /**
     * Checks whether apps should be locked according to the schedule.
     * Returns true if:
     * - Schedule is disabled (app locking is always active)
     * - Current time & day falls within the configured active schedule window.
     */
    fun isScheduleActive(context: Context): Boolean {
        val schedule = loadSchedule(context)
        val calendar = Calendar.getInstance()
        return isTimeWithinSchedule(
            schedule = schedule,
            dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
            hourOfDay = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE)
        )
    }

    /**
     * Pure function for schedule evaluation, suitable for unit testing.
     */
    fun isTimeWithinSchedule(
        schedule: ScheduleSettings,
        dayOfWeek: Int,
        hourOfDay: Int,
        minute: Int
    ): Boolean {
        if (!schedule.enabled) return true
        if (!schedule.activeDays.contains(dayOfWeek)) return false

        val currentMinutes = hourOfDay * 60 + minute
        val startMinutes = schedule.startHour * 60 + schedule.startMinute
        val endMinutes = schedule.endHour * 60 + schedule.endMinute

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            // Overnight window (e.g. 22:00 to 06:00)
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }
}
