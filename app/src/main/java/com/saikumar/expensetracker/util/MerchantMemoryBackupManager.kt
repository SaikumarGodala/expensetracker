package com.saikumar.expensetracker.util

import android.content.Context
import android.util.Log
import com.saikumar.expensetracker.data.db.MerchantMemoryDao
import com.saikumar.expensetracker.data.entity.MerchantMemory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Manages backup and restore of merchant memory (learned category preferences).
 * This ensures user preferences survive app updates even if destructive migration occurs.
 *
 * Backup file location: app_data/files/merchant_memory_backup.json
 * This location survives app updates but not app uninstalls.
 */
class MerchantMemoryBackupManager(
    private val context: Context,
    private val merchantMemoryDao: MerchantMemoryDao
) {
    companion object {
        private const val TAG = "MerchantMemoryBackup"
        private const val BACKUP_FILENAME = "merchant_memory_backup.json"
        private const val BACKUP_VERSION = 1 // Increment if backup format changes
    }

    private val backupFile: File
        get() = File(context.filesDir, BACKUP_FILENAME)

    /**
     * Export all merchant memory entries to a JSON backup file.
     * Should be called periodically or before critical operations.
     *
     * @return true if backup succeeded, false otherwise
     */
    suspend fun backupMerchantMemory(): Boolean = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting merchant memory backup...")

            // Get all confirmed/locked merchant memories (these are the valuable learned preferences)
            val memories = merchantMemoryDao.getAllConfirmedMemories()

            if (memories.isEmpty()) {
                Log.d(TAG, "No merchant memories to back up")
                return@withContext true
            }

            // Build JSON structure
            val backupJson = JSONObject().apply {
                put("version", BACKUP_VERSION)
                put("timestamp", System.currentTimeMillis())
                put("count", memories.size)
                put("memories", JSONArray().apply {
                    memories.forEach { memory ->
                        put(JSONObject().apply {
                            put("normalizedMerchant", memory.normalizedMerchant)
                            put("originalMerchant", memory.originalMerchant)
                            put("categoryId", memory.categoryId)
                            put("occurrenceCount", memory.occurrenceCount)
                            put("firstSeenTimestamp", memory.firstSeenTimestamp)
                            put("lastSeenTimestamp", memory.lastSeenTimestamp)
                            put("isLocked", memory.isLocked)
                            put("userConfirmed", memory.userConfirmed)
                            memory.transactionType?.let { put("transactionType", it) }
                        })
                    }
                })
            }

            // Write to backup file
            backupFile.writeText(backupJson.toString(2)) // Pretty print with indent 2

            Log.i(TAG, "Successfully backed up ${memories.size} merchant memories to ${backupFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to backup merchant memory", e)
            false
        }
    }

    /**
     * Restore merchant memory from backup file if database is empty.
     * Should be called on app start after database is initialized.
     *
     * @return Number of entries restored, or -1 if restore failed
     */
    suspend fun restoreMerchantMemoryIfNeeded(): Int = withContext(Dispatchers.IO) {
        try {
            // Check if backup file exists
            if (!backupFile.exists()) {
                Log.d(TAG, "No backup file found, skipping restore")
                return@withContext 0
            }

            // Check if database already has data (don't restore if data exists)
            val existingCount = merchantMemoryDao.getLockedCount()
            if (existingCount > 0) {
                Log.d(TAG, "Merchant memory database already has $existingCount entries, skipping restore")
                return@withContext 0
            }

            Log.i(TAG, "Database is empty but backup exists, attempting restore...")

            // Parse backup file
            val backupJson = JSONObject(backupFile.readText())
            val version = backupJson.getInt("version")

            if (version != BACKUP_VERSION) {
                Log.w(TAG, "Backup version mismatch: expected $BACKUP_VERSION, got $version")
                // Could implement version migration logic here if needed
            }

            val memoriesArray = backupJson.getJSONArray("memories")
            var restoredCount = 0

            // Restore each memory entry
            for (i in 0 until memoriesArray.length()) {
                try {
                    val memoryJson = memoriesArray.getJSONObject(i)

                    val memory = MerchantMemory(
                        normalizedMerchant = memoryJson.getString("normalizedMerchant"),
                        originalMerchant = memoryJson.getString("originalMerchant"),
                        categoryId = memoryJson.getLong("categoryId"),
                        occurrenceCount = memoryJson.getInt("occurrenceCount"),
                        firstSeenTimestamp = memoryJson.getLong("firstSeenTimestamp"),
                        lastSeenTimestamp = memoryJson.getLong("lastSeenTimestamp"),
                        isLocked = memoryJson.getBoolean("isLocked"),
                        userConfirmed = memoryJson.getBoolean("userConfirmed"),
                        transactionType = memoryJson.optString("transactionType").ifBlank { null }
                    )

                    merchantMemoryDao.insert(memory)
                    restoredCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore individual memory entry: ${e.message}")
                    // Continue with other entries
                }
            }

            Log.i(TAG, "Successfully restored $restoredCount merchant memories from backup")
            restoredCount
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore merchant memory from backup", e)
            -1
        }
    }

    /**
     * Delete the backup file. Use with caution.
     */
    suspend fun deleteBackup(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (backupFile.exists()) {
                val deleted = backupFile.delete()
                Log.i(TAG, "Backup file deleted: $deleted")
                deleted
            } else {
                Log.d(TAG, "No backup file to delete")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete backup file", e)
            false
        }
    }

    /**
     * Check if a backup file exists.
     */
    fun hasBackup(): Boolean = backupFile.exists()

    /**
     * Get backup file metadata (timestamp and count).
     */
    suspend fun getBackupInfo(): BackupInfo? = withContext(Dispatchers.IO) {
        try {
            if (!backupFile.exists()) return@withContext null

            val backupJson = JSONObject(backupFile.readText())
            BackupInfo(
                timestamp = backupJson.getLong("timestamp"),
                count = backupJson.getInt("count"),
                fileSizeBytes = backupFile.length()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read backup info", e)
            null
        }
    }

    data class BackupInfo(
        val timestamp: Long,
        val count: Int,
        val fileSizeBytes: Long
    )
}
