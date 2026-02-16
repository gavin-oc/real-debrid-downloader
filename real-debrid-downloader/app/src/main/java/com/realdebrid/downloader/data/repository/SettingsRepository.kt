package com.realdebrid.downloader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val API_TOKEN = stringPreferencesKey("api_token")
        val DOWNLOAD_PATH = stringPreferencesKey("download_path")
    }

    val apiToken: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.API_TOKEN] ?: ""
    }

    val downloadPath: Flow<String> = dataStore.data.map { preferences ->
        preferences[Keys.DOWNLOAD_PATH] ?: ""
    }

    suspend fun setApiToken(token: String) {
        dataStore.edit { preferences ->
            preferences[Keys.API_TOKEN] = token
        }
    }

    suspend fun setDownloadPath(path: String) {
        dataStore.edit { preferences ->
            preferences[Keys.DOWNLOAD_PATH] = path
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
