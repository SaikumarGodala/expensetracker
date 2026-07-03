package com.saikumar.expensetracker.ui.insights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Unified Insights destination: one place to answer "where does my money go?"
 *
 * - "This Cycle": the category/budget breakdown for the current salary cycle
 *   (previously the separate Monthly Overview tab)
 * - "Over Time": multi-month/yearly trends and charts
 *   (previously the separate Trends & Analytics tab)
 *
 * Merging the two removes a whole top-level destination whose distinction from
 * this one was never obvious - both are analytical views, differing only in
 * time horizon, which is exactly what a segmented toggle expresses.
 */
@Composable
fun InsightsHubScreen(
    cycleContent: @Composable () -> Unit,
    trendsContent: @Composable () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
            Text(
                "Insights",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text("This Cycle") }
                SegmentedButton(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text("Over Time") }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (selectedTab == 0) cycleContent() else trendsContent()
        }
    }
}
