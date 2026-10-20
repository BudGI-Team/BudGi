package com.app.domain.repository

import com.app.domain.model.User
import kotlinx.coroutines.flow.Flow

data class GoogleAuthResult(
    val isComplete: Boolean = false,
    val requiresPhoneVerification: Boolean = false,
    val preAuthToken: String? = null
)

interface AuthRepository {
    val currentUser: Flow<User?>
    val isLoggedIn: Boolean
    suspend fun login(email: String, password: String): Result<GoogleAuthResult>
    suspend fun googleLogin(idToken: String): Result<GoogleAuthResult>
    suspend fun sendPhoneOtp(phone: String, preAuthToken: String): Result<Unit>
    suspend fun verifyPhoneOtp(phone: String, otp: String, preAuthToken: String): Result<Unit>
    suspend fun phoneLoginRequest(phone: String): Result<Unit>
    suspend fun phoneLoginVerify(phone: String, otp: String): Result<Unit>
    suspend fun phoneRegisterRequest(phone: String, name: String, username: String? = null): Result<Unit>
    suspend fun phoneRegisterVerify(phone: String, otp: String, name: String, username: String? = null): Result<Unit>
    suspend fun signUp(
        name: String,
        email: String,
        password: String,
        username: String? = null,
        dateOfBirth: String? = null,
        avatar: String? = null,
        currency: String = "INR"
    ): Result<GoogleAuthResult>
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
