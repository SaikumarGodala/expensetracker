package com.saikumar.expensetracker.ui.common

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommonDateRangePicker(
    initialStartDate: LocalDate?,
    initialEndDate: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    // BUGFIX: androidx.compose.material3's DateRangePickerState millis are documented to
    // represent UTC midnight of the selected calendar day - NOT local-timezone midnight.
    // This code previously encoded/decoded using ZoneId.systemDefault(), which is wrong on
    // both ends:
    //  - Encoding the initial pre-filled dates via local time meant that re-opening this
    //    picker to edit an already-saved custom range could highlight the WRONG day (shifted
    //    by the local UTC offset) instead of the date that was actually saved.
    //  - Decoding the user's final selection via local time is only "accidentally correct" for
    //    timezones ahead of UTC and silently shifts a day earlier for timezones behind UTC.
    // Using ZoneOffset.UTC consistently for both conversions matches what the picker itself
    // uses internally, so the calendar day round-trips correctly for every timezone.
    val initialStartMillis = initialStartDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    val initialEndMillis = initialEndDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

    val dateRangePickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMillis,
        initialSelectedEndDateMillis = initialEndMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (dateRangePickerState.selectedStartDateMillis != null && dateRangePickerState.selectedEndDateMillis != null) {
                        val start = Instant.ofEpochMilli(dateRangePickerState.selectedStartDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        val end = Instant.ofEpochMilli(dateRangePickerState.selectedEndDateMillis!!).atZone(ZoneOffset.UTC).toLocalDate()
                        onConfirm(start, end)
                    }
                }
            ) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DateRangePicker(
            state = dateRangePickerState,
            title = {
                Text(
                    text = "Select Date Range",
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp)
                )
            },
            headline = {
                DateRangePickerDefaults.DateRangePickerHeadline(
                    selectedStartDateMillis = dateRangePickerState.selectedStartDateMillis,
                    selectedEndDateMillis = dateRangePickerState.selectedEndDateMillis,
                    displayMode = dateRangePickerState.displayMode,
                    dateFormatter = DatePickerDefaults.dateFormatter(),
                    modifier = Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp)
                )
            },
            showModeToggle = false
        )
    }
}
