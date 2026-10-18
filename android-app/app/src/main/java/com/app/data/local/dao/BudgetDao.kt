package com.app.data.local.dao

import androidx.room.*
import com.app.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBudget(budget: BudgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Query("SELECT * FROM budgets WHERE syncStatus != 'pending_delete' ORDER BY category ASC")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE category = :category LIMIT 1")
    suspend fun getByCategory(category: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE syncStatus != 'synced'")
    suspend fun getPendingSync(): List<BudgetEntity>

    @Delete
    suspend fun deleteBudget(budget: BudgetEntity): Int

    @Query("DELETE FROM budgets")
    suspend fun deleteAll()

    @Query("DELETE FROM budgets WHERE syncStatus = 'synced' AND (serverId IS NULL OR serverId NOT IN (:serverIds))")
    suspend fun deleteSyncedBudgetsNotIn(serverIds: List<String>)

    @Query("DELETE FROM budgets WHERE syncStatus = 'synced'")
    suspend fun deleteSyncedBudgets()

    @Query("SELECT SUM(limitAmount) FROM budgets WHERE syncStatus != 'pending_delete'")
    suspend fun getTotalBudgetLimit(): Double?

    // Legacy query
    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear")
    fun getBudgetsForMonth(monthYear: String): Flow<List<BudgetEntity>>
}
