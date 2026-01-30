package com.saikumar.expensetracker.ui.components

import androidx.compose.foundation.background
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val MAX_FAILED_ATTEMPTS = 5
private const val LOCKOUT_DURATION_MS = 30_000L // 30 seconds

@Composable
fun LockScreen(
    onUnlockWithBiometric: () -> Unit,
    onUnlockWithPin: (String) -> Boolean,
    isBiometricEnabled: Boolean = true,
    isPinEnabled: Boolean = false
) {
    var enteredPin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var lockoutEndTime by remember { mutableLongStateOf(0L) }
    var currentTime by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    // Update current time every second during lockout
    LaunchedEffect(lockoutEndTime) {
        while (lockoutEndTime > 0 && currentTime < lockoutEndTime) {
            kotlinx.coroutines.delay(1000L)
            currentTime = SystemClock.elapsedRealtime()
        }
    }

    val isLockedOut = lockoutEndTime > currentTime
    val remainingLockoutSeconds = if (isLockedOut) ((lockoutEndTime - currentTime) / 1000).toInt() else 0

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Expense Tracker Locked",
                style = MaterialTheme.typography.headlineSmall
            )
            
            Spacer(modifier = Modifier.height(32.dp))

            // PIN Dots
            if (isPinEnabled) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(
                                    if (index < enteredPin.length) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.surfaceVariant
                                )
                        )
                    }
                }
                
                if (isLockedOut) {
                    Text(
                        text = "Too many attempts. Try again in ${remainingLockoutSeconds}s",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                } else if (showError) {
                    Text(
                        text = if (failedAttempts >= MAX_FAILED_ATTEMPTS - 2)
                            "Incorrect PIN (${MAX_FAILED_ATTEMPTS - failedAttempts} attempts left)"
                        else "Incorrect PIN",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // NumPad
                NumPad(
                    onNumberClick = { num ->
                        if (isLockedOut) return@NumPad // Disable input during lockout

                        if (enteredPin.length < 4) {
                            enteredPin += num
                            showError = false
                            if (enteredPin.length == 4) {
                                if (onUnlockWithPin(enteredPin)) {
                                    // Success handled by parent
                                    failedAttempts = 0
                                } else {
                                    enteredPin = ""
                                    showError = true
                                    failedAttempts++
                                    if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                                        lockoutEndTime = SystemClock.elapsedRealtime() + LOCKOUT_DURATION_MS
                                        currentTime = SystemClock.elapsedRealtime()
                                        failedAttempts = 0
                                    }
                                }
                            }
                        }
                    },
                    onBackspaceClick = {
                        if (enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                            showError = false
                        }
                    },
                    onBiometricClick = if (isBiometricEnabled) onUnlockWithBiometric else null,
                    showBiometric = isBiometricEnabled
                )
            } else {
                 // Fallback if only Biometric is enabled (legacy behavior, though we plan to allow PIN)
                 Button(onClick = onUnlockWithBiometric) {
                     Icon(Icons.Default.Fingerprint, contentDescription = null)
                     Spacer(modifier = Modifier.width(8.dp))
                     Text("Unlock with Biometrics")
                 }
            }
        }
    }
}

@Composable
private fun NumPad(
    onNumberClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricClick: (() -> Unit)?,
    showBiometric: Boolean
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("Bio", "0", "Back")
        )

        for (row in rows) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (item in row) {
                    when (item) {
                        "Bio" -> {
                            if (showBiometric && onBiometricClick != null) {
                                IconButton(
                                    onClick = onBiometricClick,
                                    modifier = Modifier.size(64.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Fingerprint, 
                                        contentDescription = "Biometric",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(64.dp))
                            }
                        }
                        "Back" -> {
                            IconButton(
                                onClick = onBackspaceClick,
                                modifier = Modifier.size(64.dp)
                            ) {
                                Icon(
                                    Icons.Default.Backspace, 
                                    contentDescription = "Backspace",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        else -> {
                           Box(
                               modifier = Modifier
                                   .size(64.dp)
                                   .clip(CircleShape)
                                   .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                   .clickable { onNumberClick(item) },
                               contentAlignment = Alignment.Center
                           ) {
                               Text(
                                   text = item,
                                   style = MaterialTheme.typography.titleLarge,
                                   fontWeight = FontWeight.SemiBold
                               )
                           }
                        }
                    }
                }
            }
        }
    }
}
