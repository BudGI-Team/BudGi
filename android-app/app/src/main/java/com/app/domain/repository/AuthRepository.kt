package com.app.domain.repository

import com.app.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<User?>
    val isLoggedIn: Boolean
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun googleLogin(idToken: String): Result<Unit>
    suspend fun signUp(
        name: String,
        email: String,
        password: String,
        username: String? = null,
        dateOfBirth: String? = null,
        avatar: String? = null,
        currency: String = "INR"
    ): Result<Unit>
    suspend fun checkUsername(username: String): Result<Boolean>
    suspend fun logout()
    suspend fun refreshToken(): Result<Unit>
    suspend fun getProfile(): Result<User>
    suspend fun updateProfile(
        name: String? = null,
        username: String? = null,
        dateOfBirth: String? = null,
        currency: String? = null,
        monthlyIncome: Double? = null,
        mood: String? = null,
        avatar: String? = null,
        profileImage: String? = null,
        onboarded: Boolean? = null
    ): Result<User>
    suspend fun uploadProfilePicture(fileBytes: ByteArray, fileName: String, mimeType: String): Result<User>
    suspend fun forgotPassword(email: String): Result<Unit>
    suspend fun deleteAccount(): Result<Unit>
}
