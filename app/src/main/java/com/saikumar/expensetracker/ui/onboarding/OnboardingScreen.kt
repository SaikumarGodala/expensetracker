package com.saikumar.expensetracker.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Onboarding screen with transaction color legend.
 * Shown on first app launch to help users understand color coding.
 */
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // Header
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically { -40 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Palette,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Understanding Your Transactions",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Each color tells you what type of transaction it is",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            // How it works
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically { 20 }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                         Text(
                             text = "🤖 Automatic Tracking", 
                             style = MaterialTheme.typography.titleMedium,
                             fontWeight = FontWeight.Bold,
                             color = MaterialTheme.colorScheme.primary
                         )
                         Spacer(modifier = Modifier.height(8.dp))
                         Text(
                             text = "The app scans your SMS inbox to find bank and UPI transactions. No manual entry required!",
                             style = MaterialTheme.typography.bodyMedium,
                             color = MaterialTheme.colorScheme.onSurface
                         )
                    }
                }
            }

            // Transfer Circle
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically { 30 }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)
                    )
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                         Text(
                             text = "🔄 Transfer Circle", 
                             style = MaterialTheme.typography.titleMedium,
                             fontWeight = FontWeight.Bold,
                             color = MaterialTheme.colorScheme.secondary
                         )
                         Spacer(modifier = Modifier.height(8.dp))
                         Text(
                             text = "Transfers between your own accounts and credit card bill payments are detected to prevent double counting.",
                             style = MaterialTheme.typography.bodyMedium,
                             color = MaterialTheme.colorScheme.onSurface
                         )
                    }
                }
            }
            
            // Color Legend
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically { 40 }
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ColorLegendItem(
                            color = Color(0xFFE53935), // Red
                            icon = Icons.Default.TrendingDown,
                            title = "Expense",
                            subtitle = "Money going out (purchases, bills)"
                        )
                        ColorLegendItem(
                            color = Color(0xFF43A047), // Green
                            icon = Icons.Default.TrendingUp,
                            title = "Income",
                            subtitle = "Money coming in (salary, refunds)"
                        )
                        ColorLegendItem(
                            color = Color(0xFF1E88E5), // Blue
                            icon = Icons.Default.SwapHoriz,
                            title = "Transfer",
                            subtitle = "Between your own accounts"
                        )
                        ColorLegendItem(
                            color = Color(0xFF8E24AA), // Purple
                            icon = Icons.Default.TrendingUp,
                            title = "Investment",
                            subtitle = "Mutual funds, SIPs, pension"
                        )
                        ColorLegendItem(
                            color = Color(0xFFFF9800), // Orange
                            icon = Icons.Default.HelpOutline,
                            title = "Needs Review",
                            subtitle = "Uncertain - please verify"
                        )
                    }
                }
            }
            
            // Privacy & Security Info
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically { 50 }
            ) {
                 Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), // Add padding bottom for separation
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                         Row(verticalAlignment = Alignment.CenterVertically) {
                             Icon(
                                 imageVector = Icons.Default.Lock, 
                                 contentDescription = null,
                                 tint = MaterialTheme.colorScheme.primary,
                                 modifier = Modifier.size(16.dp)
                             )
                             Spacer(modifier = Modifier.width(8.dp))
                             Text(
                                 text = "Privacy & Security",
                                 style = MaterialTheme.typography.titleSmall,
                                 fontWeight = FontWeight.Bold,
                                 color = MaterialTheme.colorScheme.primary
                             )
                         }
                         Spacer(modifier = Modifier.height(8.dp))
                         Text(
                             text = "• All transaction processing happens locally on your device.\n• App Lock is disabled by default. Enable it in Settings > Security for extra protection with PIN or Biometrics.",
                             style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurface,
                             lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2
                         )
                    }
                }
            }
            
            // Button
            AnimatedVisibility(
                visible = visible,
                enter = fadeIn() + slideInVertically { 60 }
            ) {
                Button(
                    onClick = onComplete,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text(
                        text = "Got it!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ColorLegendItem(
    color: Color,
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color indicator
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
