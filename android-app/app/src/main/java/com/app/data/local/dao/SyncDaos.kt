package com.app.data.local.dao

import androidx.room.*
import com.app.data.local.entity.GoalEntity
import com.app.data.local.entity.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: GoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<GoalEntity>)

    @Query("SELECT * FROM goals WHERE syncStatus != 'pending_delete' ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): GoalEntity?

    @Query("SELECT * FROM goals WHERE syncStatus != 'synced'")
    suspend fun getPendingSync(): List<GoalEntity>

    @Query("DELETE FROM goals WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: String)

    @Delete
    suspend fun delete(goal: GoalEntity)

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM goals WHERE clientId = :clientId")
    suspend fun deleteByClientId(clientId: String)

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("DELETE FROM goals")
    suspend fun deleteAll()

    @Query("DELETE FROM goals WHERE syncStatus = 'synced' AND (serverId IS NULL OR serverId NOT IN (:serverIds))")
    suspend fun deleteSyncedGoalsNotIn(serverIds: List<String>)

    @Query("DELETE FROM goals WHERE syncStatus = 'synced'")
    suspend fun deleteSyncedGoals()
}

@Dao
interface PendingOperationDao {
    @Insert
    suspend fun insert(op: PendingOperationEntity): Long

    @Query("SELECT * FROM pending_operations ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingOperationEntity>

    @Query("DELETE FROM pending_operations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE pending_operations SET retryCount = retryCount + 1 WHERE id = :id")
    suspend fun incrementRetry(id: Long)

    @Query("DELETE FROM pending_operations WHERE retryCount >= :maxRetries")
    suspend fun deleteStale(maxRetries: Int = 5)

    @Query("DELETE FROM pending_operations")
    suspend fun deleteAll()
}
