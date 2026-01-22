package com.saikumar.expensetracker.util

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
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
        val FIRST_LAUNCH_COMPLETE = booleanPreferencesKey("first_launch_complete")
        val THEME_MODE = intPreferencesKey("theme_mode") // 0=System, 1=Light, 2=Dark
        val COLOR_PALETTE = stringPreferencesKey("color_palette") // DYNAMIC, BLUE, GREEN, ORANGE, PURPLE, SNOW
        val SMALL_P2P_THRESHOLD_PAISE = longPreferencesKey("small_p2p_threshold_paise") // Default: 50000 (₹500)
        val SELECTED_ACCOUNTS = stringPreferencesKey("selected_accounts") // JSON array of account last4 digits
        val SMS_FILTER_ENABLED = booleanPreferencesKey("sms_filter_enabled")
        val HAS_SEEN_ONBOARDING = booleanPreferencesKey("has_seen_onboarding")
    }

    val themeMode: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[THEME_MODE] ?: 0
    }

    val colorPalette: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[COLOR_PALETTE] ?: "DYNAMIC"
    }

    val salaryDay: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[SALARY_DAY] ?: 1
    }

    val smsAutoRead: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[SMS_AUTO_READ] ?: false
    }
    
    val debugMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DEBUG_MODE] ?: true
    }
    
    /**
     * Threshold in paise below which P2P transfers are treated as merchant expenses.
     * Default: 50000 paise (₹500)
     */
    val smallP2pThresholdPaise: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[SMALL_P2P_THRESHOLD_PAISE] ?: 50000L
    }
    
    /**
     * User-defined company names for salary detection.
     * Stored as JSON array, returned as Set<String>.
     * These are matched case-insensitively against SMS body.
     * Includes default company names if user hasn't configured any.
     */
    val salaryCompanyNames: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val json = preferences[SALARY_COMPANY_NAMES]
        // Default company names for out-of-box salary detection
        val defaultNames = setOf("ZF IND", "OPEN TEXT", "OPENTEXT", "INFOSYS", "TCS", "WIPRO", "COGNIZANT", "ACCENTURE", "CAPGEMINI", "HCL", "TECH MAHINDRA")
        
        if (json == null || json == "[]") {
            // No user-configured names, return defaults
            defaultNames
        } else {
            try {
                val jsonArray = org.json.JSONArray(json)
                val set = mutableSetOf<String>()
                for (i in 0 until jsonArray.length()) {
                    set.add(jsonArray.getString(i).uppercase())
                }
                // Merge user names with defaults
                set + defaultNames
            } catch (e: Exception) {
                defaultNames
            }
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

    val smsFilterEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[SMS_FILTER_ENABLED] ?: false
        }
    
    /**
     * Whether user has seen the onboarding color legend.
     */
    val hasSeenOnboarding: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[HAS_SEEN_ONBOARDING] ?: false
    }
    
    suspend fun setOnboardingComplete() {
        context.dataStore.edit { preferences ->
            preferences[HAS_SEEN_ONBOARDING] = true
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
    
    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { preferences ->
            preferences[THEME_MODE] = mode
        }
    }
    
    suspend fun setColorPalette(palette: String) {
        context.dataStore.edit { preferences ->
            preferences[COLOR_PALETTE] = palette
        }
    }
    
    suspend fun setSmallP2pThresholdPaise(thresholdPaise: Long) {
        context.dataStore.edit { preferences ->
            preferences[SMALL_P2P_THRESHOLD_PAISE] = thresholdPaise
        }
    }
    
    suspend fun getSmallP2pThresholdSync(): Long {
        return smallP2pThresholdPaise.first()
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
     * Returns true if this is the first launch (inbox scan not yet done).
     */
    val isFirstLaunch: Flow<Boolean> = context.dataStore.data.map { preferences ->
        !(preferences[FIRST_LAUNCH_COMPLETE] ?: false)
    }
    
    /**
     * Mark first launch as complete (after initial inbox scan).
     */
    suspend fun setFirstLaunchComplete() {
        context.dataStore.edit { preferences ->
            preferences[FIRST_LAUNCH_COMPLETE] = true
        }
    }
    
    /**
     * Get salary company names synchronously (for SmsProcessor).
     */
    suspend fun getSalaryCompanyNamesSync(): Set<String> {
        return salaryCompanyNames.first()
    }
    
    /**
     * Selected accounts for filtering (synced across Dashboard and Overview).
     * Stored as JSON array, returned as Set<String>.
     */
    val selectedAccounts: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val json = preferences[SELECTED_ACCOUNTS] ?: "[]"
        try {
            val jsonArray = org.json.JSONArray(json)
            val set = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                set.add(jsonArray.getString(i))
            }
            set
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    suspend fun updateSelectedAccounts(accounts: Set<String>) {
        context.dataStore.edit { preferences ->
            val jsonArray = org.json.JSONArray()
            accounts.forEach { jsonArray.put(it) }
            preferences[SELECTED_ACCOUNTS] = jsonArray.toString()
        }
    }

    // Alias for backward compatibility
    suspend fun setSelectedAccounts(accounts: Set<String>) {
        updateSelectedAccounts(accounts)
    }
    
    suspend fun getSelectedAccountsSync(): Set<String> {
        return selectedAccounts.first()
    }
}


