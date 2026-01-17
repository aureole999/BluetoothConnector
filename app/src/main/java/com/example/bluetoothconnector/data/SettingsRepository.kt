package com.example.bluetoothconnector.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking

// Extension property to create DataStore
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Repository for managing app settings using DataStore
 */
class SettingsRepository(private val context: Context) {
    
    companion object {
        private val AUTO_DISCONNECT_KEY = booleanPreferencesKey("auto_disconnect_after_connect")
    }
    
    /**
     * Flow that emits the current auto-disconnect setting
     */
    val autoDisconnectEnabled: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[AUTO_DISCONNECT_KEY] ?: false
        }
    
    /**
     * Update the auto-disconnect setting
     */
    suspend fun setAutoDisconnect(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_DISCONNECT_KEY] = enabled
        }
    }
    
    /**
     * Get auto disconnect setting synchronously (for use in services)
     */
    fun getAutoDisconnectSync(): Boolean = runBlocking {
        autoDisconnectEnabled.first()
    }
}
