package com.realdebrid.downloader.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
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
        val MAX_CONCURRENT_DOWNLOADS = intPreferencesKey("max_concurrent_downloads")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val AUTO_START_DOWNLOADS = booleanPreferencesKey("auto_start_downloads")
        val SHOW_COMPLETED_NOTIFICATION = booleanPreferencesKey("show_completed_notification")
        val SHOW_FAILED_NOTIFICATION = booleanPreferencesKey("show_failed_notification")
        val VIBRATE_ON_COMPLETE = booleanPreferencesKey("vibrate_on_complete")
        val DARK_MODE = stringPreferencesKey("dark_mode") // "system", "light", "dark"
    }

    // API Token
    val apiToken: Flow<String> = dataStore.data.map { it[Keys.API_TOKEN] ?: "" }

    suspend fun setApiToken(token: String) {
        dataStore.edit { it[Keys.API_TOKEN] = token }
    }

    // Download Path
    val downloadPath: Flow<String> = dataStore.data.map { it[Keys.DOWNLOAD_PATH] ?: "" }

    suspend fun setDownloadPath(path: String) {
        dataStore.edit { it[Keys.DOWNLOAD_PATH] = path }
    }

    // Max Concurrent Downloads
    val maxConcurrentDownloads: Flow<Int> = dataStore.data.map { it[Keys.MAX_CONCURRENT_DOWNLOADS] ?: 2 }

    suspend fun setMaxConcurrentDownloads(max: Int) {
        dataStore.edit { it[Keys.MAX_CONCURRENT_DOWNLOADS] = max.coerceIn(1, 5) }
    }

    // WiFi Only
    val wifiOnly: Flow<Boolean> = dataStore.data.map { it[Keys.WIFI_ONLY] ?: false }

    suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    // Auto Start Downloads
    val autoStartDownloads: Flow<Boolean> = dataStore.data.map { it[Keys.AUTO_START_DOWNLOADS] ?: true }

    suspend fun setAutoStartDownloads(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_START_DOWNLOADS] = enabled }
    }

    // Notification Settings
    val showCompletedNotification: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_COMPLETED_NOTIFICATION] ?: true }

    suspend fun setShowCompletedNotification(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_COMPLETED_NOTIFICATION] = enabled }
    }

    val showFailedNotification: Flow<Boolean> = dataStore.data.map { it[Keys.SHOW_FAILED_NOTIFICATION] ?: true }

    suspend fun setShowFailedNotification(enabled: Boolean) {
        dataStore.edit { it[Keys.SHOW_FAILED_NOTIFICATION] = enabled }
    }

    val vibrateOnComplete: Flow<Boolean> = dataStore.data.map { it[Keys.VIBRATE_ON_COMPLETE] ?: false }

    suspend fun setVibrateOnComplete(enabled: Boolean) {
        dataStore.edit { it[Keys.VIBRATE_ON_COMPLETE] = enabled }
    }

    // Theme
    val darkMode: Flow<String> = dataStore.data.map { it[Keys.DARK_MODE] ?: "system" }

    suspend fun setDarkMode(mode: String) {
        dataStore.edit { it[Keys.DARK_MODE] = mode }
    }

    // Clear all
    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }

    // Get all settings as a data class for easy access
    val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            apiToken = prefs[Keys.API_TOKEN] ?: "",
            downloadPath = prefs[Keys.DOWNLOAD_PATH] ?: "",
            maxConcurrentDownloads = prefs[Keys.MAX_CONCURRENT_DOWNLOADS] ?: 2,
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
            autoStartDownloads = prefs[Keys.AUTO_START_DOWNLOADS] ?: true,
            showCompletedNotification = prefs[Keys.SHOW_COMPLETED_NOTIFICATION] ?: true,
            showFailedNotification = prefs[Keys.SHOW_FAILED_NOTIFICATION] ?: true,
            vibrateOnComplete = prefs[Keys.VIBRATE_ON_COMPLETE] ?: false,
            darkMode = prefs[Keys.DARK_MODE] ?: "system"
        )
    }
}

data class AppSettings(
    val apiToken: String = "",
    val downloadPath: String = "",
    val maxConcurrentDownloads: Int = 2,
    val wifiOnly: Boolean = false,
    val autoStartDownloads: Boolean = true,
    val showCompletedNotification: Boolean = true,
    val showFailedNotification: Boolean = true,
    val vibrateOnComplete: Boolean = false,
    val darkMode: String = "system"
)
