package com.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.app.data.remote.dto.UserDto
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure token storage using EncryptedSharedPreferences.
 * Stores access token, refresh token, and cached user profile.
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val gson = Gson()

    private val prefs: SharedPreferences by lazy {
        try {
            createPrefs()
        } catch (e: Exception) {
            android.util.Log.e("TokenManager", "EncryptedSharedPreferences corrupted or keys invalidated. Resetting.", e)
            try {
                context.deleteSharedPreferences("budgi_secure_prefs")
                val freshPrefs = createPrefs()
                freshPrefs.edit().putBoolean("encrypted_prefs_reset", true).apply()
                freshPrefs
            } catch (fallbackEx: Exception) {
                android.util.Log.e("TokenManager", "Critical Keystore/Tink failure. Falling back to standard SharedPreferences.", fallbackEx)
                context.getSharedPreferences("budgi_prefs_fallback", Context.MODE_PRIVATE)
            }
        }
    }

    private fun createPrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        return EncryptedSharedPreferences.create(
            context,
            "budgi_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_ACCESS_TOKEN, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)
        set(value) = prefs.edit().putString(KEY_REFRESH_TOKEN, value).apply()

    var lastSyncAt: String?
        get() = prefs.getString(KEY_LAST_SYNC, null)
        set(value) = prefs.edit().putString(KEY_LAST_SYNC, value).apply()

    var lastSmsScanAt: Long
        get() = prefs.getLong(KEY_LAST_SMS_SCAN, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_SMS_SCAN, value).apply()

    var cachedUser: UserDto?
        get() {
            val json = prefs.getString(KEY_USER, null) ?: return null
            return try { gson.fromJson(json, UserDto::class.java) } catch (e: Exception) { null }
        }
        set(value) {
            val json = if (value != null) gson.toJson(value) else null
            prefs.edit().putString(KEY_USER, json).apply()
        }

    var isBiometricEnabled: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, value).apply()

    var isPushEnabled: Boolean
        get() = prefs.getBoolean(KEY_PUSH_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_PUSH_ENABLED, value).apply()

    val isDarkModeCustomized: Boolean
        get() = prefs.contains(KEY_DARK_MODE_ENABLED)

    var isDarkModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE_ENABLED, value).apply()

    var themeModePreference: Boolean?
        get() = if (prefs.contains(KEY_DARK_MODE_ENABLED)) prefs.getBoolean(KEY_DARK_MODE_ENABLED, true) else null
        set(value) {
            if (value == null) {
                prefs.edit().remove(KEY_DARK_MODE_ENABLED).apply()
            } else {
                prefs.edit().putBoolean(KEY_DARK_MODE_ENABLED, value).apply()
            }
        }

    fun setSystemDefaultTheme() {
        prefs.edit().remove(KEY_DARK_MODE_ENABLED).apply()
    }

    var profilePictureUri: String?
        get() = prefs.getString(KEY_PROFILE_PICTURE_URI, null)
        set(value) = prefs.edit().putString(KEY_PROFILE_PICTURE_URI, value).apply()

    var isEncryptedPrefsReset: Boolean
        get() = prefs.getBoolean("encrypted_prefs_reset", false)
        set(value) = prefs.edit().putBoolean("encrypted_prefs_reset", value).apply()

    val isLoggedIn: Boolean
        get() = !accessToken.isNullOrBlank()

    fun saveAuthData(accessToken: String, refreshToken: String, user: UserDto) {
        this.accessToken = accessToken
        this.refreshToken = refreshToken
        this.cachedUser = user
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_USER = "cached_user"
        private const val KEY_LAST_SYNC = "last_sync_at"
        private const val KEY_LAST_SMS_SCAN = "last_sms_scan_at"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_PUSH_ENABLED = "push_enabled"
        private const val KEY_DARK_MODE_ENABLED = "dark_mode_enabled"
        private const val KEY_PROFILE_PICTURE_URI = "profile_picture_uri"
    }
}
