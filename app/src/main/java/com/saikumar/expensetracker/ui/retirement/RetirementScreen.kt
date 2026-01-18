package com.saikumar.expensetracker.ui.retirement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saikumar.expensetracker.data.entity.RetirementBalance
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetirementScreen(
    onBack: () -> Unit,
    viewModel: RetirementViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val formatter = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Retirement Balances") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // EPF Card
            item {
                BalanceCard(
                    title = "EPF Balance",
                    balance = state.epfBalance,
                    formatter = formatter,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // NPS Card
            item {
                BalanceCard(
                    title = "NPS Balance",
                    balance = state.npsBalance,
                    formatter = formatter,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // EPF History
            if (state.epfHistory.isNotEmpty()) {
                item {
                    Text("EPF Contribution History", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                items(state.epfHistory.take(12)) { balance ->
                    ContributionRow(balance, formatter)
                }
            }

            // NPS History
            if (state.npsHistory.isNotEmpty()) {
                item {
                    Text("NPS Contribution History", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                items(state.npsHistory.take(12)) { balance ->
                    ContributionRow(balance, formatter)
                }
            }
        }
    }
}

@Composable
fun BalanceCard(title: String, balance: Long, formatter: NumberFormat, color: androidx.compose.ui.graphics.Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, fontSize = 14.sp, color = color)
            Text(
                formatter.format(balance / 100.0),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun ContributionRow(balance: RetirementBalance, formatter: NumberFormat) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(balance.month)
        Text(formatter.format(balance.contributionPaisa / 100.0))
    }
}
