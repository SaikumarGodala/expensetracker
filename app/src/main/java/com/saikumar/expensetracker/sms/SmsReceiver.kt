package com.saikumar.expensetracker.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.saikumar.expensetracker.util.RawSmsLogger

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("SmsReceiver", "SMS Broadcast Received")
        
        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            val fullBody = messages.joinToString("") { it.displayMessageBody }
            val timestamp = messages.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
            val sender = messages.firstOrNull()?.originatingAddress

            Log.d("SmsReceiver", "📱 SMS Details: sender=$sender, bodyLength=${fullBody.length}")
            
            // Queue for background processing (Logging + Transaction Extraction)
            if (fullBody.isNotEmpty()) {
                val inputData = androidx.work.workDataOf(
                    com.saikumar.expensetracker.worker.SmsProcessingWorker.KEY_SMS_BODY to fullBody,
                    com.saikumar.expensetracker.worker.SmsProcessingWorker.KEY_TIMESTAMP to timestamp,
                    com.saikumar.expensetracker.worker.SmsProcessingWorker.KEY_SENDER to sender
                )

                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.saikumar.expensetracker.worker.SmsProcessingWorker>()
                    .setInputData(inputData)
                    .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

                androidx.work.WorkManager.getInstance(context).enqueue(workRequest)
                Log.d("SmsReceiver", "✅ Enqueued processing work")
            }
        }
    }
}
