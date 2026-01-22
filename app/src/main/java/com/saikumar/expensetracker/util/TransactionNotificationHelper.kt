package com.saikumar.expensetracker.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.saikumar.expensetracker.MainActivity
import com.saikumar.expensetracker.R
import java.text.NumberFormat
import java.util.Locale

/**
 * Helper for sending transaction notifications.
 */
object TransactionNotificationHelper {
    
    private const val CHANNEL_ID = "transaction_alerts"
    private const val CHANNEL_NAME = "Transaction Alerts"
    private const val CHANNEL_DESC = "Notifications for new transactions detected from SMS"
    
    /**
     * Create notification channel (required for Android 8+).
     * Should be called once during app initialization.
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * Show notification for a new transaction.
     * @param amountPaise Transaction amount in paise
     * @param merchantName Merchant or counterparty name
     * @param categoryName Category assigned
     * @param isDebit True if money was debited (expense), false if credited (income)
     */
    fun showTransactionNotification(
        context: Context,
        amountPaise: Long,
        merchantName: String?,
        categoryName: String,
        isDebit: Boolean
    ) {
        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                return // No permission, skip notification
            }
        }
        
        val amountRupees = amountPaise / 100.0
        val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        val formattedAmount = formatter.format(amountRupees)
        
        val icon = if (isDebit) "💸" else "💰"
        val action = if (isDebit) "Spent" else "Received"
        val merchant = merchantName?.take(30) ?: "Unknown"
        
        val title = "$icon $action $formattedAmount"
        val body = "$merchant • $categoryName"
        
        // Intent to open app when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        // Use unique ID based on timestamp to allow multiple notifications
        val notificationId = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        } catch (e: SecurityException) {
            // Permission denied - silently ignore
        }
    }
}
