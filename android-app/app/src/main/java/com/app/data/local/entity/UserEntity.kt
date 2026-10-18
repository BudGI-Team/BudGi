package com.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val uid: String,
    val name: String = "",
    val email: String = "",
    val username: String? = null,
    val dateOfBirth: String? = null,
    val currency: String = "INR",
    val monthlyIncome: Double = 0.0,
    val financialGoal: String? = null,
    val plan: String = "free",
    val profileImage: String? = null,
    val healthScore: Int? = null,
    val isEmailVerified: Boolean = false,
    val onboarded: Boolean = false,
    val streak: Int = 1,
    val isLoggedIn: Boolean = false,
    val mood: String = "neutral",
    val avatar: String = "zen_saver",
    // Legacy fields
    val fullName: String = "",
    val phoneNumber: String = ""
)
