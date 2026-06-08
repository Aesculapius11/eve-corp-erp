package com.evecorp.erp.ui.theme

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.themeDataStore by preferencesDataStore(name = "theme_prefs")

@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val darkModeKey = booleanPreferencesKey("dark_mode")
    private val followSystemKey = booleanPreferencesKey("follow_system")

    val isDarkMode: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
        val followSystem = prefs[followSystemKey] ?: true
        if (followSystem) null else prefs[darkModeKey] ?: false
    }

    val followSystem: Flow<Boolean> = context.themeDataStore.data.map { prefs ->
        prefs[followSystemKey] ?: true
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[darkModeKey] = enabled
            prefs[followSystemKey] = false
        }
    }

    suspend fun setFollowSystem(enabled: Boolean) {
        context.themeDataStore.edit { prefs ->
            prefs[followSystemKey] = enabled
        }
    }
}
