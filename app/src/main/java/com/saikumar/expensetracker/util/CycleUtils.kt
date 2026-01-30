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
     * Get cycle range based on salary day.
     * - If salaryDay is set (1-31): Cycle starts on salary day of previous month, ends one day before salary day of current month
     * - If salaryDay is not set or invalid: Falls back to last working day logic
     * - Cycle month is named as per the end date month
     *
     * @param referenceDate The date to calculate cycle for
     * @param salaryDay Day of month when salary is credited (1-31), or 0/-1 for last working day
     */
    fun getCurrentCycleRange(referenceDate: LocalDate = LocalDate.now(), salaryDay: Int = 0): CycleRange {
        val currentYearMonth = YearMonth.from(referenceDate)

        // Determine cycle start and end based on salary day
        val (cycleStart, cycleEnd) = if (salaryDay in 1..31) {
            // Salary-based cycle
            // Cycle starts on salary day of previous month
            val previousMonth = currentYearMonth.minusMonths(1)
            val startDay = minOf(salaryDay, previousMonth.lengthOfMonth()) // Handle months with fewer days
            val start = previousMonth.atDay(startDay)

            // Cycle ends one day before salary day of current month
            val endDay = minOf(salaryDay - 1, currentYearMonth.lengthOfMonth())
            val end = if (endDay >= 1) {
                currentYearMonth.atDay(endDay)
            } else {
                // If salary day is 1, end on last day of previous month
                previousMonth.atEndOfMonth()
            }

            start to end
        } else {
            // Default to last working day logic (backward compatibility)
            val previousYearMonth = currentYearMonth.minusMonths(1)
            val start = getLastWorkingDay(previousYearMonth)
            val end = getLastWorkingDay(currentYearMonth).minusDays(1)
            start to end
        }

        // Adjust if reference date is outside current cycle
        if (referenceDate.isBefore(cycleStart)) {
            // Look at previous cycle
            return if (salaryDay in 1..31) {
                val prevMonth = currentYearMonth.minusMonths(1)
                val evenEarlierMonth = prevMonth.minusMonths(1)
                val start = evenEarlierMonth.atDay(minOf(salaryDay, evenEarlierMonth.lengthOfMonth()))
                val endDay = minOf(salaryDay - 1, prevMonth.lengthOfMonth())
                val end = if (endDay >= 1) prevMonth.atDay(endDay) else evenEarlierMonth.atEndOfMonth()
                CycleRange(start.atStartOfDay(), end.atTime(23, 59, 59))
            } else {
                val evenEarlierMonth = currentYearMonth.minusMonths(2)
                val previousMonth = currentYearMonth.minusMonths(1)
                CycleRange(
                    getLastWorkingDay(evenEarlierMonth).atStartOfDay(),
                    getLastWorkingDay(previousMonth).minusDays(1).atTime(23, 59, 59)
                )
            }
        }

        if (referenceDate.isAfter(cycleEnd)) {
            // Look at next cycle
            return if (salaryDay in 1..31) {
                val nextMonth = currentYearMonth.plusMonths(1)
                val start = currentYearMonth.atDay(minOf(salaryDay, currentYearMonth.lengthOfMonth()))
                val endDay = minOf(salaryDay - 1, nextMonth.lengthOfMonth())
                val end = if (endDay >= 1) nextMonth.atDay(endDay) else currentYearMonth.atEndOfMonth()
                CycleRange(start.atStartOfDay(), end.atTime(23, 59, 59))
            } else {
                val nextMonth = currentYearMonth.plusMonths(1)
                CycleRange(
                    getLastWorkingDay(currentYearMonth).atStartOfDay(),
                    getLastWorkingDay(nextMonth).minusDays(1).atTime(23, 59, 59)
                )
            }
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

