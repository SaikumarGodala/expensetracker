package com.saikumar.expensetracker.ui.insights

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saikumar.expensetracker.ui.theme.Dimens

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
    trendsContent: @Composable () -> Unit,
    onNavigateToSearch: () -> Unit = {}
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // statusBarsPadding: the hub owns the top inset for this destination (the embedded
    // screens' Scaffolds have their insets zeroed so they don't re-apply it below the header)
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        // Uses Dimens.ScreenPadding (16dp) to line up exactly with the Filter/Date row
        // and category cards below - previously this header used 20dp while the
        // embedded content used 16dp, so the "Insights" title and toggle sat 4dp further
        // right than everything under them.
        Column(modifier = Modifier.padding(start = Dimens.ScreenPadding, end = Dimens.ScreenPadding, top = 8.dp)) {
            // Same header anatomy as Home: title on the left, action icons on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Insights",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onNavigateToSearch) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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
