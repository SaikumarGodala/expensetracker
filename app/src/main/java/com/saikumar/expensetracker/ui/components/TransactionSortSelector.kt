package com.saikumar.expensetracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.saikumar.expensetracker.R

/**
 * Sort options for transaction lists
 */
enum class SortOption {
    DATE_DESC,    // Newest first (default)
    DATE_ASC,     // Oldest first
    AMOUNT_DESC,  // Highest amount first
    AMOUNT_ASC    // Lowest amount first
}

/**
 * A compact sort selector with filter chips for transaction lists.
 * Shows Time/Amount toggle with ascending/descending indicator.
 */
@Composable
fun TransactionSortSelector(
    currentSort: SortOption,
    onSortChange: (SortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Time sort chip
        val isTimeSort = currentSort == SortOption.DATE_DESC || currentSort == SortOption.DATE_ASC
        val isTimeDesc = currentSort == SortOption.DATE_DESC
        
        FilterChip(
            selected = isTimeSort,
            onClick = {
                onSortChange(
                    if (isTimeSort) {
                        if (isTimeDesc) SortOption.DATE_ASC else SortOption.DATE_DESC
                    } else {
                        SortOption.DATE_DESC
                    }
                )
            },
            label = { 
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.sort_time), style = MaterialTheme.typography.labelSmall)
                    if (isTimeSort) {
                        Icon(
                            imageVector = if (isTimeDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = stringResource(if (isTimeDesc) R.string.sort_newest_first else R.string.sort_oldest_first),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            },
            leadingIcon = {
                Icon(
                    Icons.Default.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            modifier = Modifier.height(32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
        
        // Amount sort chip
        val isAmountSort = currentSort == SortOption.AMOUNT_DESC || currentSort == SortOption.AMOUNT_ASC
        val isAmountDesc = currentSort == SortOption.AMOUNT_DESC
        
        FilterChip(
            selected = isAmountSort,
            onClick = {
                onSortChange(
                    if (isAmountSort) {
                        if (isAmountDesc) SortOption.AMOUNT_ASC else SortOption.AMOUNT_DESC
                    } else {
                        SortOption.AMOUNT_DESC
                    }
                )
            },
            label = { 
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(stringResource(R.string.sort_amount), style = MaterialTheme.typography.labelSmall)
                    if (isAmountSort) {
                        Icon(
                            imageVector = if (isAmountDesc) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            contentDescription = stringResource(if (isAmountDesc) R.string.sort_highest_first else R.string.sort_lowest_first),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            },
            leadingIcon = {
                Icon(
                    Icons.Default.AttachMoney,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            modifier = Modifier.height(32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
}
