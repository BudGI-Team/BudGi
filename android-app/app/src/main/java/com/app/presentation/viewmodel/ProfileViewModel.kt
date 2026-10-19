package com.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.domain.model.User
import com.app.domain.repository.AuthRepository
import com.app.domain.repository.TransactionRepository
import com.app.domain.repository.SpaceRepository
import com.app.data.local.ThemeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val isSyncing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val isUpdating: Boolean = false,
    val updateSuccess: Boolean = false,
    val user: User? = null,
    val isLoaded: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isPushEnabled: Boolean = true,
    val isDarkModeEnabled: Boolean = true,
    val isLoggedIn: Boolean = true
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val repository: TransactionRepository,
    private val authRepository: AuthRepository,
    private val tokenManager: com.app.data.local.TokenManager,
    private val themeManager: ThemeManager,
    private val spaceRepository: SpaceRepository,
    private val biometricSecurityManager: com.app.security.BiometricSecurityManager,
    val appUpdateManager: com.app.data.updater.AppUpdateManager
) : ViewModel() {

    val updateState = appUpdateManager.updateState

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    fun checkForUpdates(isUserInitiated: Boolean = true) {
        viewModelScope.launch {
            appUpdateManager.checkForUpdates(isUserInitiated)
        }
    }

    fun downloadAndInstallUpdate(apkUrl: String) {
        viewModelScope.launch {
            appUpdateManager.downloadAndInstall(apkUrl)
        }
    }

    fun dismissUpdate() {
        appUpdateManager.dismiss()
    }

    init {
        _uiState.update { 
            it.copy(
                isBiometricEnabled = tokenManager.isBiometricEnabled,
                isPushEnabled = tokenManager.isPushEnabled,
                isDarkModeEnabled = tokenManager.isDarkModeEnabled
            ) 
        }
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { 
                    it.copy(
                        user = user, 
                        isLoaded = true,
                        isLoggedIn = authRepository.isLoggedIn
                    ) 
                }
            }
        }
        // Fetch fresh profile from server to ensure data is current
        viewModelScope.launch {
            try { authRepository.getProfile() } catch (e: Exception) { android.util.Log.w("ProfileViewModel", "offline ok", e) }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { it.copy(isLoggedIn = false) }
        }
    }

    fun updateProfile(name: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, error = null, updateSuccess = false) }
            val result = authRepository.updateProfile(name = name)
            result.onSuccess { user ->
                _uiState.update { state -> state.copy(isUpdating = false, updateSuccess = true, message = "Profile updated successfully", user = user) }
            }.onFailure { e ->
                _uiState.update { state -> state.copy(isUpdating = false, error = e.message ?: "Failed to update profile") }
            }
        }
    }

    fun updateMoodAndAvatar(mood: String, avatar: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, error = null, updateSuccess = false) }
            val result = authRepository.updateProfile(mood = mood, avatar = avatar)
            result.onSuccess { user ->
                _uiState.update { state -> state.copy(isUpdating = false, updateSuccess = true, message = "Mood and Avatar updated successfully", user = user) }
            }.onFailure { e ->
                _uiState.update { state -> state.copy(isUpdating = false, error = e.message ?: "Failed to update mood") }
            }
        }
    }

    fun updateFullProfile(
        name: String? = null,
        username: String? = null,
        dateOfBirth: String? = null,
        avatar: String? = null,
        currency: String? = null,
        monthlyIncome: Double? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true, error = null, updateSuccess = false) }
            val result = authRepository.updateProfile(
                name = name,
                username = username,
                dateOfBirth = dateOfBirth,
                avatar = avatar,
                currency = currency,
                monthlyIncome = monthlyIncome
            )
            result.onSuccess { user ->
                _uiState.update { state -> state.copy(isUpdating = false, updateSuccess = true, message = "Profile updated successfully", user = user) }
            }.onFailure { e ->
                _uiState.update { state -> state.copy(isUpdating = false, error = e.message ?: "Failed to update profile") }
            }
        }
    }
    
    fun checkUsername(username: String, onResult: (Boolean, String?) -> Unit) {
        val clean = username.trim().removePrefix("@").lowercase()
        if (clean.length < 3) {
            onResult(false, "Username must be at least 3 characters")
            return
        }
        viewModelScope.launch {
            authRepository.checkUsername(clean)
                .onSuccess { available ->
                    if (available) onResult(true, null)
                    else onResult(false, "Username is already taken")
                }
                .onFailure {
                    onResult(false, it.message ?: "Failed to verify username")
                }
        }
    }

    fun resetUpdateState() {
        _uiState.update { it.copy(updateSuccess = false, error = null, message = null) }
    }

    fun backupData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = null, error = null) }
            repository.syncWithServer()
                .onSuccess {
                    _uiState.update { it.copy(isSyncing = false, message = "Sync successful!") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSyncing = false, error = "Sync failed: ${e.message}") }
                }
        }
    }

    fun restoreData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = null, error = null) }
            repository.refreshFromServer()
                .onSuccess {
                    _uiState.update { it.copy(isSyncing = false, message = "Restore successful!") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSyncing = false, error = "Restore failed: ${e.message}") }
                }
        }
    }

    fun checkSpaces() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = null, error = null) }
            spaceRepository.syncPendingSpaces()
                .onSuccess {
                    _uiState.update { it.copy(isSyncing = false, message = "Spaces synchronized successfully!") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSyncing = false, error = "Spaces sync failed: ${e.message}") }
                }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, error = null) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        if (enabled) {
            val availability = biometricSecurityManager.checkBiometricAvailability()
            if (availability != com.app.security.BiometricAvailability.AVAILABLE) {
                val errorMsg = when (availability) {
                    com.app.security.BiometricAvailability.NOT_ENROLLED -> "Please enroll a biometric credential or screen lock in your Android settings first."
                    com.app.security.BiometricAvailability.HARDWARE_UNAVAILABLE -> "Biometric hardware is currently unavailable on this device."
                    else -> "Biometric authentication is not supported on this device."
                }
                _uiState.update { it.copy(error = errorMsg) }
                return
            }
        }
        tokenManager.isBiometricEnabled = enabled
        _uiState.update { it.copy(isBiometricEnabled = enabled) }
    }

    fun setPushEnabled(enabled: Boolean) {
        tokenManager.isPushEnabled = enabled
        _uiState.update { it.copy(isPushEnabled = enabled) }
    }

    val themePreference: StateFlow<Boolean?> = themeManager.isDarkMode

    fun setDarkModeEnabled(enabled: Boolean) {
        themeManager.setDarkMode(enabled)
        _uiState.update { it.copy(isDarkModeEnabled = enabled) }
    }

    fun setThemePreference(preference: Boolean?) {
        themeManager.setThemePreference(preference)
        _uiState.update { it.copy(isDarkModeEnabled = preference ?: true) }
    }

    fun uploadProfilePicture(bytes: ByteArray, fileName: String, mimeType: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = null, error = null) }
            authRepository.uploadProfilePicture(bytes, fileName, mimeType)
                .onSuccess { user ->
                    _uiState.update { 
                        it.copy(
                            isSyncing = false, 
                            message = "Profile picture updated!",
                            user = user
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSyncing = false, error = "Failed to upload picture: ${e.message}") }
                }
        }
    }

    fun forgotPassword() {
        val email = _uiState.value.user?.email ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = null, error = null) }
            authRepository.forgotPassword(email)
                .onSuccess {
                    _uiState.update { it.copy(isSyncing = false, message = "Password reset link sent to your email!") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSyncing = false, error = "Failed to send reset link: ${e.message}") }
                }
        }
    }

    fun removeProfilePicture() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = null, error = null) }
            authRepository.updateProfile(profileImage = "")
                .onSuccess { user ->
                    _uiState.update { 
                        it.copy(
                            isSyncing = false, 
                            message = "Photo removed. Switched to mood avatar!",
                            user = user
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSyncing = false, error = "Failed to remove photo: ${e.message}") }
                }
        }
    }

    fun deleteAccount(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, message = null, error = null) }
            authRepository.deleteAccount()
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isSyncing = false,
                            isLoggedIn = false,
                            user = null,
                            message = "Account and data permanently deleted"
                        )
                    }
                    onSuccess()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSyncing = false, error = "Failed to delete account: ${e.message}") }
                }
        }
    }
}
