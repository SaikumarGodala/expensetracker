package com.saikumar.expensetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.saikumar.expensetracker.ExpenseTrackerApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val app = context.applicationContext as ExpenseTrackerApplication
            val pendingResult = goAsync()
            val scope = CoroutineScope(Dispatchers.IO)
            
            scope.launch {
                try {
                    val isEnabled = app.preferencesManager.smsAutoRead.first()
                    if (!isEnabled) return@launch

                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    val fullBody = messages.joinToString("") { it.displayMessageBody }
                    val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
                    val sender = messages.firstOrNull()?.originatingAddress
                    SmsProcessor.processSms(context, fullBody, timestamp, sender)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
