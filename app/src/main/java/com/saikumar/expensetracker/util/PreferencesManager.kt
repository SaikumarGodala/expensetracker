package com.saikumar.expensetracker.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "settings")

class PreferencesManager(val context: Context) {
    companion object {
        val SALARY_DAY = intPreferencesKey("salary_day")
        val SMS_AUTO_READ = booleanPreferencesKey("sms_auto_read")
        val MERCHANT_PATTERNS = stringPreferencesKey("merchant_patterns")
        val DEBUG_MODE = booleanPreferencesKey("classification_debug_mode")
        val SALARY_COMPANY_NAMES = stringPreferencesKey("salary_company_names")
    }

    val salaryDay: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SALARY_DAY] ?: 1
    }

    val smsAutoRead: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SMS_AUTO_READ] ?: false
    }
    
    val debugMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DEBUG_MODE] ?: false  // Default: disabled
    }
    
    /**
     * User-defined company names for salary detection.
     * Stored as JSON array, returned as Set<String>.
     * These are matched case-insensitively against SMS body.
     */
    val salaryCompanyNames: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val json = preferences[SALARY_COMPANY_NAMES] ?: "[]"
        try {
            val jsonArray = org.json.JSONArray(json)
            val set = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                set.add(jsonArray.getString(i).uppercase())
            }
            set
        } catch (e: Exception) {
            emptySet()
        }
    }


    val merchantPatterns: Flow<Map<String, String>> = context.dataStore.data.map { preferences ->
        val json = preferences[MERCHANT_PATTERNS] ?: "{}"
        try {
            val jsonObject = JSONObject(json)
            val map = mutableMapOf<String, String>()
            jsonObject.keys().forEach { key ->
                map[key] = jsonObject.getString(key)
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun setSalaryDay(day: Int) {
        context.dataStore.edit { preferences ->
            preferences[SALARY_DAY] = day
        }
    }

    suspend fun setSmsAutoRead(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SMS_AUTO_READ] = enabled
        }
    }

    suspend fun saveMerchantPattern(keyword: String, categoryName: String) {
        context.dataStore.edit { preferences ->
            val currentJson = preferences[MERCHANT_PATTERNS] ?: "{}"
            val jsonObject = try {
                JSONObject(currentJson)
            } catch (e: Exception) {
                JSONObject()
            }
            jsonObject.put(keyword.uppercase(), categoryName)
            preferences[MERCHANT_PATTERNS] = jsonObject.toString()
        }
    }

    suspend fun loadMerchantPatternsSync(): Map<String, String> {
        return merchantPatterns.first()
    }

    suspend fun clearMerchantPatterns() {
        context.dataStore.edit { preferences ->
            preferences[MERCHANT_PATTERNS] = "{}"
        }
    }
    
    suspend fun setDebugMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DEBUG_MODE] = enabled
        }
    }
    
    /**
     * Add a company name for salary detection.
     * Validates: min 3 chars, stored uppercase.
     */
    suspend fun addSalaryCompanyName(name: String): Boolean {
        val trimmed = name.trim().uppercase()
        if (trimmed.length < 3) return false  // Prevent junk like "PAY"
        
        context.dataStore.edit { preferences ->
            val currentJson = preferences[SALARY_COMPANY_NAMES] ?: "[]"
            val jsonArray = try { org.json.JSONArray(currentJson) } catch (e: Exception) { org.json.JSONArray() }
            
            // Check if already exists
            for (i in 0 until jsonArray.length()) {
                if (jsonArray.getString(i).equals(trimmed, ignoreCase = true)) return@edit
            }
            
            jsonArray.put(trimmed)
            preferences[SALARY_COMPANY_NAMES] = jsonArray.toString()
        }
        return true
    }
    
    /**
     * Remove a company name from salary detection.
     */
    suspend fun removeSalaryCompanyName(name: String) {
        val trimmed = name.trim().uppercase()
        context.dataStore.edit { preferences ->
            val currentJson = preferences[SALARY_COMPANY_NAMES] ?: "[]"
            val jsonArray = try { org.json.JSONArray(currentJson) } catch (e: Exception) { org.json.JSONArray() }
            
            val newArray = org.json.JSONArray()
            for (i in 0 until jsonArray.length()) {
                if (!jsonArray.getString(i).equals(trimmed, ignoreCase = true)) {
                    newArray.put(jsonArray.getString(i))
                }
            }
            preferences[SALARY_COMPANY_NAMES] = newArray.toString()
        }
    }
    
    /**
     * Get salary company names synchronously (for SmsProcessor).
     */
    suspend fun getSalaryCompanyNamesSync(): Set<String> {
        return salaryCompanyNames.first()
    }
}

