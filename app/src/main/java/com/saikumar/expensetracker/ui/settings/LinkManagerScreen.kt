package com.saikumar.expensetracker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saikumar.expensetracker.data.entity.LinkWithDetails
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkManagerScreen(
    onNavigateBack: () -> Unit,
    viewModel: LinkManagerViewModel = viewModel()
) {
    val selfTransferLinks by viewModel.selfTransferLinks.collectAsState()
    val refundLinks by viewModel.refundLinks.collectAsState()
    val ccLinks by viewModel.ccLinks.collectAsState()
    val allLinks by viewModel.allLinks.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction Links") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Manage automatically or manually created links between transactions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (allLinks.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 32.dp), contentAlignment = Alignment.Center) {
                        Text("No links found.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                if (selfTransferLinks.isNotEmpty()) {
                    item { SectionHeader("Self Transfers (${selfTransferLinks.size})") }
                    items(selfTransferLinks) { link ->
                        LinkItem(link = link, onDelete = { viewModel.deleteLink(link.link.id) })
                    }
                }

                if (refundLinks.isNotEmpty()) {
                    item { SectionHeader("Refunds (${refundLinks.size})") }
                    items(refundLinks) { link ->
                        LinkItem(link = link, onDelete = { viewModel.deleteLink(link.link.id) })
                    }
                }

                if (ccLinks.isNotEmpty()) {
                    item { SectionHeader("Credit Card Payments (${ccLinks.size})") }
                    items(ccLinks) { link ->
                        LinkItem(link = link, onDelete = { viewModel.deleteLink(link.link.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun LinkItem(link: LinkWithDetails, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val dateFormat = SimpleDateFormat("dd MMM", Locale.getDefault())
                val date1 = dateFormat.format(Date(link.primaryTransaction.timestamp))
                val date2 = dateFormat.format(Date(link.secondaryTransaction.timestamp))
                
                Text(
                    text = "₹${link.primaryTransaction.amountPaisa / 100.0}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                
                // Show Source -> Dest or similar
                Text(
                    text = "Primary: $date1 - ${link.primaryTransaction.note ?: "No Desc"}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
                Text(
                    text = "Linked: $date2 - ${link.secondaryTransaction.note ?: "No Desc"}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if(link.link.confidenceScore < 100) {
                     Text(
                        text = "Confidence: ${link.link.confidenceScore}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Unlink",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
