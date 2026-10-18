package com.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shared_spaces")
data class SharedSpaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val inviteCode: String,
    // Store JSON representation for simpler persistence without complex relations
    val membersJson: String,
    val goalsJson: String,
    val expensesJson: String = "[]",
    val syncStatus: String = "SYNCED"
)
