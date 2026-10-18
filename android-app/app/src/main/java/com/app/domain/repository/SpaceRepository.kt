package com.app.domain.repository

import com.app.data.local.entity.SharedSpaceEntity
import kotlinx.coroutines.flow.Flow

interface SpaceRepository {
    fun observeSpaces(): Flow<List<SharedSpaceEntity>>
    fun observeSpaceById(id: String): Flow<SharedSpaceEntity?>
    suspend fun getSpaceById(id: String): SharedSpaceEntity?
    suspend fun refreshSpaces(): Result<Unit>
    suspend fun createSpace(name: String, type: String): Result<SharedSpaceEntity>
    suspend fun joinSpace(inviteCode: String): Result<SharedSpaceEntity>
    suspend fun addGoal(spaceId: String, title: String, targetAmount: Double, category: String): Result<SharedSpaceEntity>
    suspend fun contributeToGoal(spaceId: String, goalTitle: String, amount: Double): Result<SharedSpaceEntity>
    suspend fun deleteGoal(spaceId: String, goalTitle: String): Result<SharedSpaceEntity>
    suspend fun addExpense(
        spaceId: String,
        description: String,
        amount: Double,
        paidById: String,
        paidByName: String,
        splitWithMemberIds: List<String>,
        category: String
    ): Result<SharedSpaceEntity>
    suspend fun settleUp(
        spaceId: String,
        fromMemberId: String,
        fromMemberName: String,
        toMemberId: String,
        toMemberName: String,
        amount: Double
    ): Result<SharedSpaceEntity>
    suspend fun deleteExpense(spaceId: String, expenseId: String): Result<SharedSpaceEntity>
    suspend fun deleteSpace(spaceId: String): Result<Unit>
    suspend fun syncPendingSpaces(): Result<Unit>
}
