package com.saikumar.expensetracker.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.saikumar.expensetracker.ExpenseTrackerApplication
import com.saikumar.expensetracker.data.entity.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Exports the whole ledger to a CSV in Downloads/ExpenseTracker/.
 *
 * Everything stays on the device: the file is written to the user's own Downloads
 * folder, and sharing it is a separate, explicit action from the Settings screen.
 */
object TransactionExporter {
    private const val TAG = "TransactionExporter"
    private const val FOLDER = "ExpenseTracker"

    data class ExportResult(val uri: Uri?, val displayPath: String, val rowCount: Int)

    private val fileStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US)
    private val rowStamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    private val HEADER = listOf(
        "id", "date", "time_millis", "amount_inr", "direction", "type", "category",
        "merchant", "upi_id", "account_last4", "account_type", "entity_type",
        "counts_as_expense", "status", "confidence", "source", "manual_override",
        "is_subscription", "is_reversal", "reference_no", "note", "sms_body"
    )

    suspend fun exportAll(context: Context): ExportResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext as ExpenseTrackerApplication
        val db = app.database

        val transactions = db.transactionDao().getAllForExport()
        val categoryNames = db.categoryDao().getAllCategoriesSync().associate { it.id to it.name }

        val sb = StringBuilder()
        sb.append(HEADER.joinToString(",")).append('\n')
        for (t in transactions) {
            sb.append(toCsvRow(t, categoryNames[t.categoryId] ?: "Unknown")).append('\n')
        }

        val fileName = "transactions_${fileStamp.format(Date())}.csv"
        val uri = writeCsv(context, fileName, sb.toString())
        Log.i(TAG, "Exported ${transactions.size} transactions to $fileName")

        ExportResult(uri, "Downloads/$FOLDER/$fileName", transactions.size)
    }

    private fun toCsvRow(t: Transaction, categoryName: String): String {
        // Money is stored in paisa; emit plain rupees so a spreadsheet reads it as a number.
        val rupees = String.format(Locale.US, "%.2f", t.amountPaisa / 100.0)
        val direction = when (t.transactionType) {
            com.saikumar.expensetracker.data.entity.TransactionType.INCOME,
            com.saikumar.expensetracker.data.entity.TransactionType.CASHBACK,
            com.saikumar.expensetracker.data.entity.TransactionType.REFUND,
            com.saikumar.expensetracker.data.entity.TransactionType.PENSION -> "IN"
            com.saikumar.expensetracker.data.entity.TransactionType.STATEMENT,
            com.saikumar.expensetracker.data.entity.TransactionType.IGNORE,
            com.saikumar.expensetracker.data.entity.TransactionType.UNKNOWN -> "NONE"
            else -> "OUT"
        }

        val fields = listOf(
            t.id.toString(),
            rowStamp.format(Date(t.timestamp)),
            t.timestamp.toString(),
            rupees,
            direction,
            t.transactionType.name,
            categoryName,
            t.merchantName ?: "",
            t.upiId ?: "",
            t.accountNumberLast4 ?: "",
            t.accountType.name,
            t.entityType.name,
            if (t.isExpenseEligible) "yes" else "no",
            t.status.name,
            t.confidenceScore.toString(),
            t.source.name,
            t.manualClassification ?: "",
            if (t.isSubscription) "yes" else "no",
            if (t.isReversal) "yes" else "no",
            t.referenceNo ?: "",
            t.note ?: "",
            t.fullSmsBody ?: ""
        )
        return fields.joinToString(",") { escape(it) }
    }

    private fun escape(value: String): String {
        // Newlines inside an SMS body would split the row, so flatten them first.
        val flat = value.replace("\r\n", " | ").replace('\n', '|').replace('\r', ' ')
        return "\"" + flat.replace("\"", "\"\"") + "\""
    }

    private fun writeCsv(context: Context, fileName: String, content: String): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.also {
                context.contentResolver.openOutputStream(it)?.use { out ->
                    out.write(content.toByteArray())
                }
            }
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                FOLDER
            )
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            file.writeText(content)
            Uri.fromFile(file)
        }
    }
}
