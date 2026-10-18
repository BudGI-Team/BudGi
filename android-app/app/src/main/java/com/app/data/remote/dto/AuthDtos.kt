package com.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── Requests ─────────────────────────────────────────────────────────

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class RegisterRequest(
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String,
    @SerializedName("username") val username: String? = null,
    @SerializedName("dateOfBirth") val dateOfBirth: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("currency") val currency: String = "INR",
    @SerializedName("monthlyIncome") val monthlyIncome: Double = 0.0
)

data class RefreshRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

data class DeviceTokenRequest(
    @SerializedName("token") val token: String,
    @SerializedName("platform") val platform: String = "android"
)

data class UpdateProfileRequest(
    @SerializedName("name") val name: String? = null,
    @SerializedName("username") val username: String? = null,
    @SerializedName("dateOfBirth") val dateOfBirth: String? = null,
    @SerializedName("currency") val currency: String? = null,
    @SerializedName("monthlyIncome") val monthlyIncome: Double? = null,
    @SerializedName("financialGoal") val financialGoal: String? = null,
    @SerializedName("profileImage") val profileImage: String? = null,
    @SerializedName("onboarded") val onboarded: Boolean? = null,
    @SerializedName("mood") val mood: String? = null,
    @SerializedName("avatar") val avatar: String? = null
)

// ── Responses ────────────────────────────────────────────────────────

data class AuthData(
    @SerializedName("token") val token: String,
    @SerializedName("refreshToken") val refreshToken: String,
    @SerializedName("user") val user: UserDto
)

data class UserStreakDto(
    @SerializedName("count") val count: Int = 1
)

data class UserDto(
    @SerializedName("_id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("username") val username: String? = null,
    @SerializedName("dateOfBirth") val dateOfBirth: String? = null,
    @SerializedName("currency") val currency: String = "INR",
    @SerializedName("monthlyIncome") val monthlyIncome: Double = 0.0,
    @SerializedName("financialGoal") val financialGoal: String? = null,
    @SerializedName("plan") val plan: String = "free",
    @SerializedName("profileImage") val profileImage: String? = null,
    @SerializedName("healthScore") val healthScore: Int? = null,
    @SerializedName("isEmailVerified") val isEmailVerified: Boolean = false,
    @SerializedName("onboarded") val onboarded: Boolean = false,
    @SerializedName("streak") val streak: UserStreakDto? = null,
    @SerializedName("mood") val mood: String? = null,
    @SerializedName("avatar") val avatar: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class UserWrapper(
    @SerializedName("user") val user: UserDto? = null
)

data class UsernameCheckDto(
    @SerializedName("available") val available: Boolean,
    @SerializedName("username") val username: String,
    @SerializedName("reason") val reason: String? = null
)
