package com.saikumar.expensetracker.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

data class CycleRange(
    val startDate: LocalDateTime,
    val endDate: LocalDateTime
)

object CycleUtils {
    /**
     * Get the last working day (not Saturday or Sunday) of a given month
     */
    fun getLastWorkingDay(yearMonth: YearMonth): LocalDate {
        var lastDay = yearMonth.atEndOfMonth()
        while (lastDay.dayOfWeek == DayOfWeek.SATURDAY || lastDay.dayOfWeek == DayOfWeek.SUNDAY) {
            lastDay = lastDay.minusDays(1)
        }
        return lastDay
    }

    /**
     * Get cycle range from last working day of previous month to day before last working day of current month
     */
    fun getCurrentCycleRange(referenceDate: LocalDate = LocalDate.now()): CycleRange {
        val currentYearMonth = YearMonth.from(referenceDate)
        val previousYearMonth = currentYearMonth.minusMonths(1)
        
        // Cycle starts on last working day of previous month
        val cycleStart = getLastWorkingDay(previousYearMonth)
        
        // Cycle ends one day before last working day of current month
        val lastWorkingDayCurrentMonth = getLastWorkingDay(currentYearMonth)
        val cycleEnd = lastWorkingDayCurrentMonth.minusDays(1)
        
        // If reference date is before cycle start, look at previous cycle
        if (referenceDate.isBefore(cycleStart)) {
            val evenEarlierMonth = previousYearMonth.minusMonths(1)
            return CycleRange(
                getLastWorkingDay(evenEarlierMonth).atStartOfDay(),
                cycleStart.minusDays(1).atTime(23, 59, 59)
            )
        }
        
        // If reference date is after cycle end, look at next cycle
        if (referenceDate.isAfter(cycleEnd)) {
            val nextMonth = currentYearMonth.plusMonths(1)
            return CycleRange(
                lastWorkingDayCurrentMonth.atStartOfDay(),
                getLastWorkingDay(nextMonth).minusDays(1).atTime(23, 59, 59)
            )
        }
        
        return CycleRange(
            cycleStart.atStartOfDay(),
            cycleEnd.atTime(23, 59, 59)
        )
    }

    fun getMonthCycleRange(referenceDate: LocalDate): CycleRange {
        val startDate = referenceDate.withDayOfMonth(1).atStartOfDay()
        val endDate = referenceDate.withDayOfMonth(referenceDate.lengthOfMonth()).atTime(23, 59, 59)
        return CycleRange(startDate, endDate)
    }
}

