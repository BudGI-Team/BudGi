package com.app.data.remote.api

import com.app.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

interface AuthApiService {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): Response<ApiResponse<AuthData>>

    @POST("api/v1/auth/google")
    suspend fun googleLogin(@Body request: Map<String, String>): Response<ApiResponse<AuthData>>

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<ApiResponse<AuthData>>

    @GET("api/v1/auth/check-username")
    suspend fun checkUsername(@Query("username") username: String): Response<ApiResponse<UsernameCheckDto>>

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): Response<ApiResponse<AuthData>>

    @POST("api/v1/auth/logout")
    suspend fun logout(): Response<ApiResponse<Any>>

    @GET("api/v1/auth/me")
    suspend fun getProfile(): Response<ApiResponse<UserWrapper>>

    @Multipart
    @POST("api/v1/auth/upload-profile-picture")
    suspend fun uploadProfilePicture(
        @Part file: okhttp3.MultipartBody.Part
    ): Response<ApiResponse<UserWrapper>>

    @DELETE("api/v1/auth/me")
    suspend fun deleteAccount(): Response<ApiResponse<Any>>

    @PUT("api/v1/auth/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<ApiResponse<UserWrapper>>

    @POST("api/v1/auth/device-token")
    suspend fun registerDeviceToken(@Body request: DeviceTokenRequest): Response<ApiResponse<Any>>

    @HTTP(method = "DELETE", path = "api/v1/auth/device-token", hasBody = true)
    suspend fun removeDeviceToken(@Body body: Map<String, String>): Response<ApiResponse<Any>>

    @POST("api/v1/auth/forgot-password")
    suspend fun forgotPassword(@Body body: Map<String, String>): Response<ApiResponse<Any>>
}
