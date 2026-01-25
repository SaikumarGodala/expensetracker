package com.saikumar.expensetracker.data.repository

import com.saikumar.expensetracker.data.db.AppDatabase
import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.entity.CategoryType
import android.util.Log
import kotlinx.coroutines.flow.first
import com.saikumar.expensetracker.data.common.DefaultCategories

object CategorySeeder {
    private const val TAG = "CategorySeeder"

    suspend fun seedDefaultsIfNeeded(dao: com.saikumar.expensetracker.data.db.CategoryDao): List<Category> {
        // use sync method to avoid Flow issues and check ALL categories (enabled or disabled)
        val allCategories = dao.getAllCategoriesSync()
        
        val defaults = DefaultCategories.ALL_CATEGORIES.map { def ->
            Category(name = def.name, type = def.type, isDefault = def.isDefault)
        }

        // Check against ALL categories to prevent re-adding if just disabled
        val existingNames = allCategories.map { it.name }.toSet()
        val missing = defaults.filter { !existingNames.contains(it.name) }

        if (missing.isNotEmpty()) {
            Log.d(TAG, "Seeding ${missing.size} missing default categories: ${missing.map { it.name }}")
            dao.insertCategories(missing)
        }
        
        // UPDATE EXISTING: Ensure specific critical categories have correct type (Migration logic)
        // Specifically: Credit Bill Payments -> LIABILITY
        val updates = mutableListOf<Category>()
        for (default in defaults) {
            val existing = allCategories.find { it.name.equals(default.name, ignoreCase = true) }
            if (existing != null && existing.type != default.type) {
                // Only force update for "Credit Bill Payments" to avoid overriding user customizations for others
                if (default.name == "Credit Bill Payments" || 
                    default.name == "P2P Transfers" || 
                    default.name == "Self Transfer") {
                    Log.d(TAG, "Migrating Category '${default.name}' type from ${existing.type} to ${default.type}")
                    updates.add(existing.copy(type = default.type))
                }
            }
        }
        
        if (updates.isNotEmpty()) {
            dao.updateCategories(updates)
        }
        
        // Return only ENABLED categories for usage
        return dao.getAllCategoriesSync().filter { it.isEnabled }
    }
}
