package com.evecorp.erp.ui.theme

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _darkMode = MutableStateFlow(prefs.getBoolean("dark_mode", false))
    private val _followSystem = MutableStateFlow(prefs.getBoolean("follow_system", true))

    val isDarkMode: Flow<Boolean?> = _darkMode.asStateFlow()
    val followSystem: Flow<Boolean> = _followSystem.asStateFlow()

    fun setDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean("dark_mode", enabled).putBoolean("follow_system", false).apply()
        _darkMode.value = enabled
        _followSystem.value = false
    }

    fun setFollowSystem(enabled: Boolean) {
        prefs.edit().putBoolean("follow_system", enabled).apply()
        _followSystem.value = enabled
    }
}
