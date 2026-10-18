package com.app.utils

object SyncStatus {
    const val SYNCED = "synced"
    const val PENDING_CREATE = "pending_create"
    const val PENDING_UPDATE = "pending_update"
    const val PENDING_DELETE = "pending_delete"
}

object EntityType {
    const val TRANSACTION = "transaction"
    const val BUDGET = "budget"
    const val GOAL = "goal"
    const val DEBT = "debt"
}
