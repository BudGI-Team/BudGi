package com.app.domain.model

data class User(
    val id: String = "",
    val name: String = "",
    val email: String = "",
    val username: String? = null,
    val dateOfBirth: String? = null,
    val currency: String = "INR",
    val monthlyIncome: Double = 0.0,
    val plan: String = "free",
    val profileImage: String? = null,
    val healthScore: Int? = null,
    val isEmailVerified: Boolean = false,
    val onboarded: Boolean = false,
    val streak: Int = 1,
    val mood: String = "neutral",
    val avatar: String = "zen_saver",
    // Legacy compatibility
    val fullName: String = "",
    val phoneNumber: String = ""
) {
    val displayName: String get() = name.ifBlank { fullName }
    val handle: String get() = if (!username.isNullOrBlank()) "@$username" else "@${name.filter { it.isLetterOrDigit() }.lowercase().ifBlank { "member" }}"
    val uid: String get() = id
}
