package com.app.data.repository

import android.util.Log
import com.app.data.local.TokenManager
import com.app.data.local.dao.UserDao
import com.app.data.local.entity.UserEntity
import com.app.data.remote.api.AuthApiService
import com.app.data.remote.dto.*
import com.app.domain.model.User
import com.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApiService,
    private val tokenManager: TokenManager,
    private val userDao: UserDao,
    private val webSocketManager: com.app.data.remote.websocket.WebSocketManager,
    private val pendingOperationDao: com.app.data.local.dao.PendingOperationDao,
    private val appDatabase: com.app.data.local.db.AppDatabase
) : AuthRepository {
    private val gson = com.google.gson.Gson()

    override val currentUser: Flow<User?> = userDao.getLoggedInUser().map { entity ->
        entity?.toDomain() ?: tokenManager.cachedUser?.toDomain()
    }

    override val isLoggedIn: Boolean
        get() = tokenManager.isLoggedIn

    override suspend fun login(email: String, password: String): Result<Unit> = try {
        val response = authApi.login(LoginRequest(email.trim().lowercase(), password))
        if (!response.isSuccessful) {
            val errorMsg = response.errorBody()?.string() ?: "Login failed"
            Result.failure(Exception(parseErrorMessage(errorMsg)))
        } else {
            val body = response.body() ?: throw Exception("Empty response from server")
            if (!body.success || body.data == null) {
                Result.failure(Exception(body.message))
            } else {
                val authData = body.data
                tokenManager.saveAuthData(authData.token, authData.refreshToken, authData.user)
                saveUserLocally(authData.user)
                // Connect WebSocket after successful login
                try { webSocketManager.connect() } catch (e: Exception) { Log.w(TAG, "WebSocket connect failed", e) }
                Result.success(Unit)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Login failed", e)
        Result.failure(Exception(mapNetworkError(e)))
    }

    override suspend fun googleLogin(idToken: String): Result<Unit> = try {
        val response = authApi.googleLogin(mapOf("idToken" to idToken))
        if (!response.isSuccessful) {
            val errorMsg = response.errorBody()?.string() ?: "Google login failed"
            Result.failure(Exception(parseErrorMessage(errorMsg)))
        } else {
            val body = response.body() ?: throw Exception("Empty response from server")
            if (!body.success || body.data == null) {
                Result.failure(Exception(body.message))
            } else {
                val authData = body.data
                tokenManager.saveAuthData(authData.token, authData.refreshToken, authData.user)
                saveUserLocally(authData.user)
                try { webSocketManager.connect() } catch (e: Exception) { Log.w(TAG, "WebSocket connect failed", e) }
                Result.success(Unit)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Google Login failed", e)
        Result.failure(Exception(mapNetworkError(e)))
    }

    override suspend fun signUp(
        name: String,
        email: String,
        password: String,
        username: String?,
        dateOfBirth: String?,
        avatar: String?,
        currency: String
    ): Result<Unit> = try {
        val response = authApi.register(RegisterRequest(
            name = name.trim(),
            email = email.trim().lowercase(),
            password = password,
            username = username?.trim()?.removePrefix("@")?.lowercase(),
            dateOfBirth = dateOfBirth,
            avatar = avatar,
            currency = currency
        ))

        if (!response.isSuccessful) {
            val errorMsg = response.errorBody()?.string() ?: "Registration failed"
            Result.failure(Exception(parseErrorMessage(errorMsg)))
        } else {
            val body = response.body() ?: throw Exception("Empty response from server")
            if (!body.success || body.data == null) {
                Result.failure(Exception(body.message))
            } else {
                val authData = body.data
                tokenManager.saveAuthData(authData.token, authData.refreshToken, authData.user)
                saveUserLocally(authData.user)
                // Connect WebSocket after successful signup
                try { webSocketManager.connect() } catch (e: Exception) { Log.w(TAG, "WebSocket connect failed", e) }
                Result.success(Unit)
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "SignUp failed", e)
        Result.failure(Exception(mapNetworkError(e)))
    }

    override suspend fun checkUsername(username: String): Result<Boolean> = try {
        val clean = username.trim().removePrefix("@").lowercase()
        val response = authApi.checkUsername(clean)
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null && body.success && body.data != null) {
                Result.success(body.data.available)
            } else {
                Result.failure(Exception(body?.message ?: "Username check failed"))
            }
        } else {
            Result.failure(Exception("Username check failed"))
        }
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun logout() {
        // Disconnect WebSocket before clearing tokens
        webSocketManager.disconnect()
        try { authApi.logout() } catch (_: Exception) { /* Best-effort server logout */ }
        tokenManager.clearAll()
        userDao.logoutAll()
    }

    override suspend fun refreshToken(): Result<Unit> = runCatching {
        val refreshToken = tokenManager.refreshToken ?: throw Exception("No refresh token")
        val response = authApi.refresh(RefreshRequest(refreshToken))

        if (!response.isSuccessful) {
            tokenManager.clearAll()
            throw Exception("Session expired — please log in again")
        }

        val body = response.body() ?: throw Exception("Empty response")
        if (!body.success || body.data == null) {
            tokenManager.clearAll()
            throw Exception(body.message)
        }

        val authData = body.data
        tokenManager.saveAuthData(authData.token, authData.refreshToken, authData.user)
        saveUserLocally(authData.user)
        // Reconnect WebSocket with new token
        webSocketManager.disconnect()
        webSocketManager.connect()
    }

    override suspend fun getProfile(): Result<User> = runCatching {
        val response = authApi.getProfile()
        if (!response.isSuccessful) throw Exception("Failed to fetch profile")

        val body = response.body() ?: throw Exception("Empty response")
        val userDto = body.data?.user ?: throw Exception("User data missing")

        tokenManager.cachedUser = userDto
        saveUserLocally(userDto)
        userDto.toDomain()
    }

    override suspend fun updateProfile(
        name: String?,
        username: String?,
        dateOfBirth: String?,
        currency: String?,
        monthlyIncome: Double?,
        mood: String?,
        avatar: String?,
        profileImage: String?,
        onboarded: Boolean?
    ): Result<User> = runCatching {
        // 1. Local Optimistic Update
        val localUser = userDao.getLoggedInUserSync()
        if (localUser != null) {
            val updatedLocal = localUser.copy(
                name = name ?: localUser.name,
                fullName = name ?: localUser.fullName,
                username = username ?: localUser.username,
                dateOfBirth = dateOfBirth ?: localUser.dateOfBirth,
                currency = currency ?: localUser.currency,
                monthlyIncome = monthlyIncome ?: localUser.monthlyIncome,
                mood = mood ?: localUser.mood,
                avatar = avatar ?: localUser.avatar,
                onboarded = onboarded ?: localUser.onboarded,
                profileImage = if (profileImage == "") null else (profileImage ?: localUser.profileImage)
            )
            userDao.insertUser(updatedLocal)
        }

        // 2. Network Sync Try
        try {
            val response = authApi.updateProfile(UpdateProfileRequest(
                name = name,
                username = username?.trim()?.removePrefix("@")?.lowercase(),
                dateOfBirth = dateOfBirth,
                currency = currency,
                monthlyIncome = monthlyIncome,
                mood = mood,
                avatar = avatar,
                profileImage = profileImage,
                onboarded = onboarded
            ))

            if (response.isSuccessful) {
                val body = response.body() ?: throw Exception("Empty response")
                val userDto = body.data?.user ?: throw Exception("User data missing")

                tokenManager.cachedUser = userDto
                saveUserLocally(userDto)
                return@runCatching userDto.toDomain()
            }
        } catch (e: Exception) {
            // Offline fallback: Queue this action to be synchronized when back online!
            Log.w(TAG, "Offline update profile queued", e)
            val updatePayload = gson.toJson(UpdateProfileRequest(
                name = name,
                username = username,
                dateOfBirth = dateOfBirth,
                currency = currency,
                monthlyIncome = monthlyIncome,
                mood = mood,
                avatar = avatar,
                profileImage = profileImage,
                onboarded = onboarded
            ))
            pendingOperationDao.insert(com.app.data.local.entity.PendingOperationEntity(
                entityId = localUser?.uid ?: "user",
                entityType = "profile",
                operationType = "update",
                payload = updatePayload
            ))
        }

        // Return local state representing optimism
        userDao.getLoggedInUserSync()?.toDomain() ?: throw Exception("No local user logged in")
    }

    override suspend fun uploadProfilePicture(fileBytes: ByteArray, fileName: String, mimeType: String): Result<User> = runCatching {
        val requestBody = fileBytes.toRequestBody(mimeType.toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("profileImage", fileName, requestBody)

        val response = authApi.uploadProfilePicture(part)
        if (!response.isSuccessful) throw Exception("Failed to upload profile picture")

        val body = response.body() ?: throw Exception("Empty response")
        val userDto = body.data?.user ?: throw Exception("User data missing")

        tokenManager.cachedUser = userDto
        saveUserLocally(userDto)
        userDto.toDomain()
    }

    override suspend fun forgotPassword(email: String): Result<Unit> = runCatching {
        val requestBody = mapOf("email" to email)
        val response = authApi.forgotPassword(requestBody)
        if (!response.isSuccessful) {
            val errorMsg = response.errorBody()?.string() ?: "Failed to request password reset"
            throw Exception(parseErrorMessage(errorMsg))
        }
    }

    override suspend fun deleteAccount(): Result<Unit> = runCatching {
        val response = authApi.deleteAccount()
        if (!response.isSuccessful) {
            val errorMsg = response.errorBody()?.string() ?: "Failed to delete account"
            throw Exception(parseErrorMessage(errorMsg))
        }
        // Cascade delete all local Room database tables for full Play Store user data deletion compliance
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            appDatabase.clearAllTables()
        }
        logout()
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private suspend fun saveUserLocally(dto: UserDto) {
        userDao.logoutAll()
        userDao.insertUser(UserEntity(
            uid = dto.id,
            name = dto.name,
            fullName = dto.name,
            email = dto.email,
            username = dto.username,
            dateOfBirth = dto.dateOfBirth,
            currency = dto.currency,
            monthlyIncome = dto.monthlyIncome,
            financialGoal = dto.financialGoal,
            plan = dto.plan,
            profileImage = dto.profileImage,
            healthScore = dto.healthScore,
            isEmailVerified = dto.isEmailVerified,
            onboarded = dto.onboarded,
            streak = dto.streak?.count ?: 1,
            isLoggedIn = true,
            mood = dto.mood ?: "neutral",
            avatar = dto.avatar ?: "zen_saver"
        ))
    }

    private fun parseErrorMessage(errorBody: String): String {
        return try {
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<ApiResponse<Any>>() {}.type
            val error = gson.fromJson<ApiResponse<Any>>(errorBody, type)
            error.errors?.firstOrNull()?.message ?: error.message
        } catch (_: Exception) {
            "Something went wrong"
        }
    }

    private fun mapNetworkError(e: Exception): String = when (e) {
        is SocketTimeoutException -> "Connection timed out. Please check if the server is running and your device is on the same network."
        is ConnectException -> "Cannot connect to server. Please check your network connection and server address."
        is UnknownHostException -> "Cannot reach server. Please check your network connection."
        else -> e.message ?: "An unexpected error occurred"
    }

    companion object {
        private const val TAG = "AuthRepository"
    }

    private fun getAbsoluteProfileImageUrl(url: String?): String? {
        if (url.isNullOrBlank() || url.equals("null", ignoreCase = true) || url.equals("undefined", ignoreCase = true)) return null
        if (url.startsWith("http")) return url
        val baseUrl = com.app.BuildConfig.API_BASE_URL.trimEnd('/')
        val path = if (url.startsWith("/")) url else "/$url"
        return "$baseUrl$path"
    }

    private fun UserEntity.toDomain() = User(
        id = uid,
        name = name.ifBlank { fullName },
        email = email,
        username = username,
        dateOfBirth = dateOfBirth,
        currency = currency,
        monthlyIncome = monthlyIncome,
        plan = plan,
        profileImage = getAbsoluteProfileImageUrl(profileImage),
        healthScore = healthScore,
        isEmailVerified = isEmailVerified,
        onboarded = onboarded,
        streak = streak,
        mood = mood,
        avatar = avatar
    )

    private fun UserDto.toDomain() = User(
        id = id,
        name = name,
        email = email,
        username = username,
        dateOfBirth = dateOfBirth,
        currency = currency,
        monthlyIncome = monthlyIncome,
        plan = plan,
        profileImage = getAbsoluteProfileImageUrl(profileImage),
        healthScore = healthScore,
        isEmailVerified = isEmailVerified,
        onboarded = onboarded,
        streak = streak?.count ?: 1,
        mood = mood ?: "neutral",
        avatar = avatar ?: "zen_saver"
    )
}
