package com.saikumar.expensetracker.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 * Database migrations for ExpenseTracker.
 *
 * IMPORTANT: Always add a new migration when bumping database version.
 * Never rely on fallbackToDestructiveMigration() in production.
 *
 * Migration Naming Convention: MIGRATION_X_Y where X is old version and Y is new version.
 */
object Migrations {
    private const val TAG = "DatabaseMigrations"

    /**
     * Migration 40 → 41: Added CategoryBudget table for per-category budget limits.
     */
    val MIGRATION_40_41 = object : Migration(40, 41) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating from version 40 to 41: Adding CategoryBudget table")

            database.execSQL("""
                CREATE TABLE IF NOT EXISTS `category_budgets` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `categoryId` INTEGER NOT NULL,
                    `amountPaisa` INTEGER NOT NULL,
                    `month` INTEGER NOT NULL,
                    `year` INTEGER NOT NULL,
                    `isSoftCap` INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
            """.trimIndent())

            database.execSQL("""
                CREATE UNIQUE INDEX IF NOT EXISTS `index_category_budgets_categoryId_month_year`
                ON `category_budgets` (`categoryId`, `month`, `year`)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS `index_category_budgets_categoryId`
                ON `category_budgets` (`categoryId`)
            """.trimIndent())

            Log.i(TAG, "Migration 40→41 complete")
        }
    }

    /**
     * Migration 41 → 42: Added upiVpa column to user_accounts for better self-transfer detection.
     */
    val MIGRATION_41_42 = object : Migration(41, 42) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating from version 41 to 42: Adding upiVpa column to user_accounts")

            database.execSQL("""
                ALTER TABLE user_accounts ADD COLUMN upiVpa TEXT DEFAULT NULL
            """.trimIndent())

            Log.i(TAG, "Migration 41→42 complete")
        }
    }

    /**
     * Migration 42 → 43: Added upiId column to transactions for storing counterparty UPI VPA.
     */
    val MIGRATION_42_43 = object : Migration(42, 43) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating from version 42 to 43: Adding upiId column to transactions")

            database.execSQL("""
                ALTER TABLE transactions ADD COLUMN upiId TEXT DEFAULT NULL
            """.trimIndent())

            Log.i(TAG, "Migration 42→43 complete")
        }
    }

    /**
     * Migration 43 → 44: Added performance indices on upiId, status, and transactionType columns.
     */
    val MIGRATION_43_44 = object : Migration(43, 44) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Log.i(TAG, "Migrating from version 43 to 44: Adding performance indices")

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS index_transactions_upiId
                ON transactions (upiId)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS index_transactions_status
                ON transactions (status)
            """.trimIndent())

            database.execSQL("""
                CREATE INDEX IF NOT EXISTS index_transactions_transactionType
                ON transactions (transactionType)
            """.trimIndent())

            Log.i(TAG, "Migration 43→44 complete")
        }
    }

    /**
     * All migrations should be added to this list for automatic registration.
     * NOTE: Declare individual migrations BEFORE this array to avoid forward reference errors.
     */
    val ALL_MIGRATIONS: Array<Migration> = arrayOf(
        MIGRATION_40_41,
        MIGRATION_41_42,
        MIGRATION_42_43,
        MIGRATION_43_44
    )

    /**
     * Template for future migrations:
     *
     * val MIGRATION_42_43 = object : Migration(42, 43) {
     *     override fun migrate(database: SupportSQLiteDatabase) {
     *         Log.i(TAG, "Migrating from version 42 to 43: [Description]")
     *         // Add SQL statements here
     *         Log.i(TAG, "Migration 42→43 complete")
     *     }
     * }
     */
}
