package com.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.domain.model.User
import com.app.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    val currentUser = authRepository.currentUser
    val isLoggedIn: Boolean get() = authRepository.isLoggedIn

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Please fill in all fields") }
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(error = "Please enter a valid email address") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.login(email, password)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun handleGoogleSignIn(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.googleLogin(idToken)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun signUp(name: String, email: String, password: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Please fill in all fields") }
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(error = "Please enter a valid email address") }
            return
        }
        if (password.length < 8) {
            _uiState.update { it.copy(error = "Password must be at least 8 characters") }
            return
        }
        if (!password.matches(Regex(".*[A-Z].*")) || !password.matches(Regex(".*[0-9].*"))) {
            _uiState.update { it.copy(error = "Password must contain at least one uppercase letter and one number") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.signUp(name, email, password)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun forgotPassword(email: String) {
        if (email.isBlank() || !email.contains("@")) {
            _uiState.update { it.copy(error = "Please enter a valid email") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.forgotPassword(email)
                .onSuccess {
                    _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to send reset email") }
                }
        }
    }

    fun checkUsername(username: String, onResult: (Boolean, String?) -> Unit) {
        if (username.isBlank() || username.length < 3) {
            onResult(false, "Must be at least 3 characters")
            return
        }
        viewModelScope.launch {
            authRepository.checkUsername(username)
                .onSuccess { available ->
                    onResult(available, if (available) null else "Username already taken")
                }
                .onFailure { e ->
                    onResult(false, e.message ?: "Check failed")
                }
        }
    }

    fun signUpWithProfile(
        name: String,
        email: String,
        password: String,
        username: String? = null,
        dateOfBirth: String? = null,
        avatar: String? = null,
        currency: String = "INR",
        onComplete: (() -> Unit)? = null
    ) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = "Please fill in all required fields") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.signUp(
                name = name.trim(),
                email = email.trim().lowercase(),
                password = password,
                username = username?.trim()?.removePrefix("@")?.lowercase(),
                dateOfBirth = dateOfBirth,
                avatar = avatar ?: "zen_saver",
                currency = currency
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                onComplete?.invoke()
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateProfile(
        name: String? = null,
        username: String? = null,
        dateOfBirth: String? = null,
        currency: String? = null,
        monthlyIncome: Double? = null,
        avatar: String? = null,
        onboarded: Boolean? = true,
        onComplete: (() -> Unit)? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.updateProfile(
                name = name?.trim()?.ifBlank { null },
                username = username?.trim()?.removePrefix("@")?.lowercase()?.ifBlank { null },
                dateOfBirth = dateOfBirth?.ifBlank { null },
                currency = currency?.ifBlank { null },
                monthlyIncome = monthlyIncome,
                avatar = avatar?.ifBlank { null },
                onboarded = onboarded
            ).onSuccess {
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
                onComplete?.invoke()
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _uiState.update { AuthUiState() }
        }
    }

    fun resetState() {
        _uiState.update { AuthUiState() }
    }
}
