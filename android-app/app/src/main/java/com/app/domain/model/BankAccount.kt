package com.app.domain.model

data class BankAccount(
    val id: Int = 0,
    val bankName: String,
    val accountType: String,
    val lastFourDigits: String
)
