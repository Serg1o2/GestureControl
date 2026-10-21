package com.gesturecontrol.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Simple user settings stored in DataStore. */
@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext context: Context) {
    private val store = context.settingsDataStore

    /** Drop analysis to ~10 fps while no hand is visible. */
    val powerSaveEnabled: Flow<Boolean> = store.data.map { it[POWER_SAVE] ?: true }

    suspend fun setPowerSaveEnabled(enabled: Boolean) {
        store.edit { it[POWER_SAVE] = enabled }
    }

    private companion object {
        val POWER_SAVE = booleanPreferencesKey("power_save_enabled")
    }
}
