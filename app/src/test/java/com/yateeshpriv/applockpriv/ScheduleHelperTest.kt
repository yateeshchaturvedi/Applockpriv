package com.yateeshpriv.applockpriv

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class ScheduleHelperTest {

    @Test
    fun `isTimeWithinSchedule returns true when schedule is disabled`() {
        val schedule = ScheduleSettings(enabled = false)
        val result = ScheduleHelper.isTimeWithinSchedule(
            schedule = schedule,
            dayOfWeek = Calendar.MONDAY,
            hourOfDay = 3,
            minute = 0
        )
        assertTrue("Disabled schedule should always return true (lock active)", result)
    }

    @Test
    fun `isTimeWithinSchedule returns false when day is not active`() {
        val schedule = ScheduleSettings(
            enabled = true,
            startHour = 9,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            activeDays = setOf(Calendar.MONDAY, Calendar.TUESDAY)
        )
        val result = ScheduleHelper.isTimeWithinSchedule(
            schedule = schedule,
            dayOfWeek = Calendar.SUNDAY,
            hourOfDay = 12,
            minute = 0
        )
        assertFalse("Inactive day should return false", result)
    }

    @Test
    fun `isTimeWithinSchedule normal daytime window within range`() {
        val schedule = ScheduleSettings(
            enabled = true,
            startHour = 9,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            activeDays = setOf(Calendar.MONDAY)
        )
        val result = ScheduleHelper.isTimeWithinSchedule(
            schedule = schedule,
            dayOfWeek = Calendar.MONDAY,
            hourOfDay = 12,
            minute = 30
        )
        assertTrue("Time inside 0900 to 1700 should return true", result)
    }

    @Test
    fun `isTimeWithinSchedule normal daytime window outside range`() {
        val schedule = ScheduleSettings(
            enabled = true,
            startHour = 9,
            startMinute = 0,
            endHour = 17,
            endMinute = 0,
            activeDays = setOf(Calendar.MONDAY)
        )
        val result = ScheduleHelper.isTimeWithinSchedule(
            schedule = schedule,
            dayOfWeek = Calendar.MONDAY,
            hourOfDay = 19,
            minute = 0
        )
        assertFalse("Time outside 0900 to 1700 should return false", result)
    }

    @Test
    fun `isTimeWithinSchedule overnight window within range late night`() {
        val schedule = ScheduleSettings(
            enabled = true,
            startHour = 22,
            startMinute = 0,
            endHour = 6,
            endMinute = 0,
            activeDays = setOf(Calendar.FRIDAY)
        )
        val result = ScheduleHelper.isTimeWithinSchedule(
            schedule = schedule,
            dayOfWeek = Calendar.FRIDAY,
            hourOfDay = 23,
            minute = 15
        )
        assertTrue("Time 2315 in overnight 2200 to 0600 should return true", result)
    }

    @Test
    fun `isTimeWithinSchedule overnight window within range early morning`() {
        val schedule = ScheduleSettings(
            enabled = true,
            startHour = 22,
            startMinute = 0,
            endHour = 6,
            endMinute = 0,
            activeDays = setOf(Calendar.FRIDAY)
        )
        val result = ScheduleHelper.isTimeWithinSchedule(
            schedule = schedule,
            dayOfWeek = Calendar.FRIDAY,
            hourOfDay = 4,
            minute = 30
        )
        assertTrue("Time 0430 in overnight 2200 to 0600 should return true", result)
    }

    @Test
    fun `isTimeWithinSchedule overnight window outside range`() {
        val schedule = ScheduleSettings(
            enabled = true,
            startHour = 22,
            startMinute = 0,
            endHour = 6,
            endMinute = 0,
            activeDays = setOf(Calendar.FRIDAY)
        )
        val result = ScheduleHelper.isTimeWithinSchedule(
            schedule = schedule,
            dayOfWeek = Calendar.FRIDAY,
            hourOfDay = 14,
            minute = 0
        )
        assertFalse("Time 1400 outside overnight 2200 to 0600 should return false", result)
    }
}
