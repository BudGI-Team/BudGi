package com.app.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reactive theme state holder.
 *
 * Reads the persisted dark-mode preference from [TokenManager] on creation
 * and exposes it as a [StateFlow] so that `MainActivity` can observe it
 * and pass the value into `AppTheme(darkTheme = …)`.
 *
 * When the user toggles the setting via [ProfileViewModel], it calls
 * [setDarkMode] which updates both the flow (triggering recomposition)
 * and the persisted preference (surviving process death).
 */
@Singleton
class ThemeManager @Inject constructor(
    private val tokenManager: TokenManager
) {
    private val _isDarkMode = MutableStateFlow<Boolean?>(
        if (tokenManager.isDarkModeCustomized) tokenManager.isDarkModeEnabled else null
    )

    /**
     * Observable dark-mode state.
     * `null` means no custom preference is set (defaults to device system theme).
     * `true` / `false` represents an explicit user preference.
     */
    val isDarkMode: StateFlow<Boolean?> = _isDarkMode.asStateFlow()

    val isCustomized: Boolean
        get() = tokenManager.isDarkModeCustomized

    /**
     * Update the theme. This:
     * 1. Persists the preference to EncryptedSharedPreferences (survives restart)
     * 2. Pushes the new value to the StateFlow (triggers live recomposition)
     */
    fun setDarkMode(enabled: Boolean) {
        tokenManager.themeModePreference = enabled
        _isDarkMode.value = enabled
    }

    /**
     * Reset to system default theme (follows device appearance automatically).
     */
    fun setSystemDefault() {
        tokenManager.setSystemDefaultTheme()
        _isDarkMode.value = null
    }

    /**
     * Set theme preference: `null` for system default, `true` for dark mode, `false` for light mode.
     */
    fun setThemePreference(preference: Boolean?) {
        tokenManager.themeModePreference = preference
        _isDarkMode.value = preference
    }
}
