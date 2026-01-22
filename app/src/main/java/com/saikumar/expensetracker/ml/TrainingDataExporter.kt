package com.saikumar.expensetracker.ml

import android.content.Context
import android.util.Log
import com.saikumar.expensetracker.ExpenseTrackerApplication
import com.saikumar.expensetracker.sms.CategoryMapper
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Utility to export training data for ML classifier.
 * Collects data from:
 * 1. Static MERCHANT_CATEGORIES map
 * 2. MerchantMemory table (user confirmations)
 * 3. High-confidence transactions
 */
object TrainingDataExporter {
    private const val TAG = "TrainingDataExporter"
    
    data class TrainingSample(
        val merchantName: String,
        val category: String,
        val source: String, // "STATIC_MAP", "USER_CONFIRMED", "TRANSACTION"
        val confidence: Int, // 0-100
        val sender: String? = null
    )
    
    data class ExportStats(
        val totalSamples: Int,
        val uniqueMerchants: Int,
        val categories: Int,
        val samplesPerCategory: Map<String, Int>,
        val sourceCounts: Map<String, Int>
    )
    
    /**
     * Export training data to JSON file.
     * Returns file path and statistics.
     */
    suspend fun export(context: Context): Pair<File, ExportStats> {
        val app = context.applicationContext as ExpenseTrackerApplication
        val db = app.database
        
        val samples = mutableListOf<TrainingSample>()
        
        // 1. Static MERCHANT_CATEGORIES map (via reflection or access if public)
        val staticMap = getStaticMerchantCategories()
        staticMap.forEach { (merchant, category) ->
            samples.add(TrainingSample(merchant, category, "STATIC_MAP", 90))
        }
        Log.d(TAG, "Static map: ${staticMap.size} entries")
        
        // 2. MerchantMemory table - user confirmed mappings
        val categories = db.categoryDao().getAllEnabledCategories().first()
        val categoryIdToName = categories.associate { it.id to it.name }
        
        val memories = db.merchantMemoryDao().getAll().first()
        memories.forEach { memory ->
            val categoryName = categoryIdToName[memory.categoryId] ?: return@forEach
            val confidence = when {
                memory.userConfirmed -> 100
                memory.isLocked -> 85
                memory.occurrenceCount >= 2 -> 70
                else -> 50
            }
            samples.add(TrainingSample(
                memory.normalizedMerchant,
                categoryName,
                if (memory.userConfirmed) "USER_CONFIRMED" else "MERCHANT_MEMORY",
                confidence
            ))
        }
        Log.d(TAG, "Merchant memory: ${memories.size} entries")
        
        // 3. High-confidence transactions
        val transactions = db.transactionDao().getAllForMlExportWithSender()
        transactions.filter { it.confidenceScore >= 80 }
            .forEach { txn ->
                val categoryName = categoryIdToName[txn.categoryId] ?: return@forEach
                val merchantKey = txn.merchantName.uppercase().trim()
                // Skip if already in samples (dedup by merchant name)
                if (samples.none { it.merchantName.equals(merchantKey, ignoreCase = true) }) {
                    samples.add(TrainingSample(
                        merchantKey,
                        categoryName,
                        "TRANSACTION",
                        txn.confidenceScore,
                        txn.smsSender
                    ))
                }
            }
        Log.d(TAG, "Transactions: added high-confidence entries")
        
        // Deduplicate by normalized merchant name
        val deduped = samples
            .groupBy { it.merchantName.uppercase().trim() }
            .map { (_, group) -> 
                // Keep highest confidence sample for each merchant
                group.maxByOrNull { it.confidence }!!
            }
        
        // Compute stats
        val samplesPerCategory = deduped.groupBy { it.category }
            .mapValues { it.value.size }
            .toSortedMap()
        
        val sourceCounts = deduped.groupBy { it.source }
            .mapValues { it.value.size }
        
        val stats = ExportStats(
            totalSamples = deduped.size,
            uniqueMerchants = deduped.map { it.merchantName }.distinct().size,
            categories = samplesPerCategory.size,
            samplesPerCategory = samplesPerCategory,
            sourceCounts = sourceCounts
        )
        
        // Export to JSON
        val json = JSONObject().apply {
            put("exportedAt", System.currentTimeMillis())
            put("totalSamples", stats.totalSamples)
            put("categories", JSONArray(samplesPerCategory.keys.toList()))
            put("samples", JSONArray().apply {
                deduped.forEach { sample ->
                    put(JSONObject().apply {
                        put("merchant", sample.merchantName)
                        put("category", sample.category)
                        put("source", sample.source)
                        put("confidence", sample.confidence)
                        put("sender", sample.sender)
                    })
                }
            })
            put("stats", JSONObject().apply {
                put("samplesPerCategory", JSONObject(samplesPerCategory))
                put("sourceCounts", JSONObject(sourceCounts))
            })
        }
        
        // Write to Downloads folder for easy access
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS
        )
        val mlDir = File(downloadsDir, "ExpenseTrackerML")
        mlDir.mkdirs()
        val file = File(mlDir, "ml_training_data.json")
        file.writeText(json.toString(2))
        
        Log.d(TAG, "Exported ${stats.totalSamples} samples to: ${file.absolutePath}")
        
        return file to stats
    }
    
    /**
     * Generate analysis report as markdown.
     */
    fun generateReport(stats: ExportStats): String {
        return buildString {
            appendLine("# ML Training Data Report")
            appendLine()
            appendLine("## Summary")
            appendLine("- **Total samples**: ${stats.totalSamples}")
            appendLine("- **Unique merchants**: ${stats.uniqueMerchants}")
            appendLine("- **Categories**: ${stats.categories}")
            appendLine()
            appendLine("## Samples by Source")
            stats.sourceCounts.forEach { (source, count) ->
                appendLine("- $source: $count")
            }
            appendLine()
            appendLine("## Samples by Category")
            appendLine("| Category | Count | Status |")
            appendLine("|----------|-------|--------|")
            stats.samplesPerCategory.forEach { (category, count) ->
                val status = when {
                    count >= 10 -> "✅ Good"
                    count >= 5 -> "⚠️ OK"
                    count >= 3 -> "🔶 Low"
                    else -> "❌ Needs more"
                }
                appendLine("| $category | $count | $status |")
            }
            appendLine()
            appendLine("## ML Readiness")
            val minSamples = stats.samplesPerCategory.values.minOrNull() ?: 0
            val avgSamples = stats.samplesPerCategory.values.average()
            appendLine("- Min samples per category: $minSamples")
            appendLine("- Avg samples per category: %.1f".format(avgSamples))
            appendLine()
            if (stats.totalSamples >= 500 && minSamples >= 3) {
                appendLine("✅ **Ready for training!**")
            } else if (stats.totalSamples >= 200) {
                appendLine("⚠️ **Can train with augmentation**")
            } else {
                appendLine("❌ **Need more data** - collect more user confirmations")
            }
        }
    }
    
    /**
     * Get static merchant categories map.
     * Using reflection since MERCHANT_CATEGORIES is private.
     */
    private fun getStaticMerchantCategories(): Map<String, String> {
        return try {
            val field = CategoryMapper::class.java.getDeclaredField("MERCHANT_CATEGORIES")
            field.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            field.get(CategoryMapper) as Map<String, String>
        } catch (e: Exception) {
            Log.w(TAG, "Could not access MERCHANT_CATEGORIES via reflection: ${e.message}")
            // Fallback: return known categories manually
            emptyMap()
        }
    }
}
