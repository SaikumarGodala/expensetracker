package com.saikumar.expensetracker.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.saikumar.expensetracker.sms.SmsProcessor
import com.saikumar.expensetracker.util.RawSmsLogger

/**
 * Worker to process incoming SMS messages in the background.
 * Replaces GlobalScope based processing for better reliability.
 */
class SmsProcessingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_SMS_BODY = "sms_body"
        const val KEY_TIMESTAMP = "timestamp"
        const val KEY_SENDER = "sender"
        private const val TAG = "SmsProcessingWorker"
    }

    override suspend fun doWork(): Result {
        val smsBody = inputData.getString(KEY_SMS_BODY)
        val timestamp = inputData.getLong(KEY_TIMESTAMP, System.currentTimeMillis())
        val sender = inputData.getString(KEY_SENDER)

        if (smsBody.isNullOrBlank() || sender.isNullOrBlank()) {
            Log.e(TAG, "Missing SMS body or sender")
            return Result.failure()
        }

        return try {
            Log.d(TAG, "Processing SMS in background: Sender=$sender")
            
            // 🔍 FALLBACK LOG: Redundancy in case SmsReceiver logging failed
            RawSmsLogger.logRawSms(applicationContext, sender, smsBody, timestamp)
            
            SmsProcessor.processAndInsert(applicationContext, sender, smsBody, timestamp)
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
            Result.retry()
        }
    }
}
