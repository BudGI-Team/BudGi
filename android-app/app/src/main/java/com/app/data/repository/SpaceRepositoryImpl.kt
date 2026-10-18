package com.app.data.repository

import com.app.data.local.dao.SpaceDao
import com.app.data.local.dao.UserDao
import com.app.data.local.entity.SharedSpaceEntity
import com.app.data.remote.api.*
import com.app.domain.repository.SpaceRepository
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.UUID

class SpaceRepositoryImpl @Inject constructor(
    private val spaceApi: SpaceApiService,
    private val spaceDao: SpaceDao,
    private val userDao: UserDao,
    private val gson: Gson
) : SpaceRepository {

    override fun observeSpaces(): Flow<List<SharedSpaceEntity>> = spaceDao.observeSpaces()

    override fun observeSpaceById(id: String): Flow<SharedSpaceEntity?> = spaceDao.observeSpaceById(id)

    override suspend fun getSpaceById(id: String): SharedSpaceEntity? = spaceDao.getSpaceById(id)

    private fun isNetworkError(throwable: Throwable): Boolean {
        return throwable is IOException || throwable is ConnectException || throwable is UnknownHostException
    }

    override suspend fun refreshSpaces(): Result<Unit> = runCatching {
        try {
            val response = spaceApi.getSpaces()
            if (response.success && response.data.spaces != null) {
                val entities = response.data.spaces.map { it.toEntity() }
                
                // Preserve local draft changes
                val currentSpaces = spaceDao.getAllSpacesDirect()
                val pendingSpaces = currentSpaces.filter { it.syncStatus != "SYNCED" }
                
                val serverEntities = entities.map { serverSpace ->
                    val localMatch = pendingSpaces.find { it.id == serverSpace.id }
                    if (localMatch != null) {
                        localMatch
                    } else {
                        serverSpace
                    }
                }
                
                spaceDao.insertSpaces(serverEntities)
                
                // Re-insert local pending creations/joins that aren't on server yet
                val pendingCreates = pendingSpaces.filter { it.syncStatus == "PENDING_CREATE" || it.syncStatus == "PENDING_JOIN" }
                spaceDao.insertSpaces(pendingCreates)
            } else {
                throw Exception(response.message ?: "Failed to refresh spaces")
            }
        } catch (e: Exception) {
            if (!isNetworkError(e)) {
                throw e
            }
        }
    }

    override suspend fun createSpace(name: String, type: String): Result<SharedSpaceEntity> = runCatching {
        // 1. Optimistically insert space locally FIRST so it appears on the screen in 0ms!
        val tempId = "space_${UUID.randomUUID().toString().take(12)}"
        val randomInviteCode = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        val currentMember = getCurrentUserDtoMap()
        val localEntity = SharedSpaceEntity(
            id = tempId,
            name = name,
            type = type,
            inviteCode = randomInviteCode,
            membersJson = gson.toJson(listOf(currentMember)),
            goalsJson = "[]",
            expensesJson = "[]",
            syncStatus = "PENDING_CREATE"
        )
        spaceDao.insertSpace(localEntity)

        // 2. Synchronize with server in background
        try {
            val response = spaceApi.createSpace(CreateSpaceRequest(name, type))
            if (response.success && response.data.space != null) {
                val serverEntity = response.data.space.toEntity().copy(
                    expensesJson = localEntity.expensesJson
                )
                spaceDao.deleteSpace(tempId)
                spaceDao.insertSpace(serverEntity)
                serverEntity
            } else {
                localEntity
            }
        } catch (_: Exception) {
            // Keep local entity active and visible on page
            localEntity
        }
    }

    private suspend fun getCurrentUserDtoMap(): Map<String, Any> {
        val userEntity = userDao.getLoggedInUserSync()
        val nameParts = (userEntity?.name ?: "You").split(" ", limit = 2)
        val firstName = nameParts.getOrNull(0) ?: "You"
        val lastName = nameParts.getOrNull(1) ?: ""
        
        val userIdMap = mapOf(
            "_id" to (userEntity?.uid ?: "current_user"),
            "firstName" to firstName,
            "lastName" to lastName,
            "email" to (userEntity?.email ?: ""),
            "profileImage" to (userEntity?.profileImage ?: "")
        )
        return mapOf(
            "userId" to userIdMap,
            "role" to "OWNER",
            "joinedAt" to java.time.Instant.now().toString()
        )
    }

    override suspend fun joinSpace(inviteCode: String): Result<SharedSpaceEntity> = runCatching {
        val cleanCode = inviteCode.trim().uppercase()
        try {
            val response = spaceApi.joinSpace(cleanCode)
            if (response.success && response.data.space != null) {
                val entity = response.data.space.toEntity()
                spaceDao.insertSpace(entity)
                entity
            } else {
                throw Exception(response.message ?: "Failed to join space")
            }
        } catch (_: Exception) {
            val tempId = "temp_join_${UUID.randomUUID().toString().take(8)}"
            val currentMember = getCurrentUserDtoMap()
            val entity = SharedSpaceEntity(
                id = tempId,
                name = "Vault ($cleanCode)",
                type = "CUSTOM",
                inviteCode = cleanCode,
                membersJson = gson.toJson(listOf(currentMember)),
                goalsJson = "[]",
                expensesJson = "[]",
                syncStatus = "PENDING_JOIN"
            )
            spaceDao.insertSpace(entity)
            entity
        }
    }

    override suspend fun addGoal(
        spaceId: String,
        title: String,
        targetAmount: Double,
        category: String
    ): Result<SharedSpaceEntity> = runCatching {
        try {
            val response = spaceApi.addGoal(spaceId, AddGoalRequest(title, targetAmount, category))
            if (response.success && response.data.space != null) {
                val entity = response.data.space.toEntity()
                spaceDao.insertSpace(entity)
                entity
            } else {
                throw Exception(response.message ?: "Failed to add goal")
            }
        } catch (e: Exception) {
            if (isNetworkError(e)) {
                val localSpace = spaceDao.getSpaceById(spaceId) ?: throw e
                val mapType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                val goals: MutableList<Map<String, Any>> = try {
                    gson.fromJson<List<Map<String, Any>>>(localSpace.goalsJson, mapType).toMutableList()
                } catch (ex: Exception) {
                    mutableListOf()
                }
                
                val newGoal = mapOf(
                    "_id" to "temp_goal_${UUID.randomUUID()}",
                    "title" to title,
                    "targetAmount" to targetAmount,
                    "currentAmount" to 0.0,
                    "category" to category,
                    "isPending" to true
                )
                goals.add(newGoal)
                
                val updated = localSpace.copy(
                    goalsJson = gson.toJson(goals),
                    syncStatus = if (localSpace.syncStatus == "SYNCED") "PENDING_UPDATE" else localSpace.syncStatus
                )
                spaceDao.insertSpace(updated)
                updated
            } else {
                throw e
            }
        }
    }

    override suspend fun contributeToGoal(
        spaceId: String,
        goalTitle: String,
        amount: Double
    ): Result<SharedSpaceEntity> = runCatching {
        val localSpace = spaceDao.getSpaceById(spaceId) ?: throw Exception("Space not found")
        val mapType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
        val goals: List<Map<String, Any>> = try {
            gson.fromJson(localSpace.goalsJson, mapType)
        } catch (e: Exception) {
            emptyList()
        }
        val updatedGoals = goals.map { goal ->
            val title = goal["title"] as? String ?: ""
            val id = goal["_id"] as? String ?: ""
            if (title.equals(goalTitle, ignoreCase = true) || id == goalTitle) {
                val cur = (goal["currentAmount"] as? Number)?.toDouble() ?: 0.0
                val target = (goal["targetAmount"] as? Number)?.toDouble() ?: 0.0
                val newAmt = (cur + amount).coerceAtMost(target * 2)
                goal.toMutableMap().apply {
                    put("currentAmount", newAmt)
                    put("isPending", true)
                }
            } else {
                goal
            }
        }
        val updated = localSpace.copy(
            goalsJson = gson.toJson(updatedGoals),
            syncStatus = if (localSpace.syncStatus == "SYNCED") "PENDING_UPDATE" else localSpace.syncStatus
        )
        spaceDao.insertSpace(updated)
        updated
    }

    override suspend fun deleteGoal(
        spaceId: String,
        goalTitle: String
    ): Result<SharedSpaceEntity> = runCatching {
        val localSpace = spaceDao.getSpaceById(spaceId) ?: throw Exception("Space not found")
        val mapType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
        val goals: List<Map<String, Any>> = try {
            gson.fromJson(localSpace.goalsJson, mapType)
        } catch (e: Exception) {
            emptyList()
        }
        val updatedGoals = goals.filterNot {
            val title = it["title"] as? String ?: ""
            val id = it["_id"] as? String ?: ""
            title.equals(goalTitle, ignoreCase = true) || id == goalTitle
        }
        val updated = localSpace.copy(
            goalsJson = gson.toJson(updatedGoals),
            syncStatus = if (localSpace.syncStatus == "SYNCED") "PENDING_UPDATE" else localSpace.syncStatus
        )
        spaceDao.insertSpace(updated)
        updated
    }

    override suspend fun addExpense(
        spaceId: String,
        description: String,
        amount: Double,
        paidById: String,
        paidByName: String,
        splitWithMemberIds: List<String>,
        category: String
    ): Result<SharedSpaceEntity> = runCatching {
        val localSpace = spaceDao.getSpaceById(spaceId) ?: throw Exception("Space not found")
        val mapType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
        val expenses: MutableList<Map<String, Any>> = try {
            gson.fromJson<List<Map<String, Any>>>(localSpace.expensesJson, mapType).toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
        val expenseId = "exp_${UUID.randomUUID().toString().take(10)}"
        val dateStr = java.time.LocalDate.now().toString()
        val newExpense = mapOf(
            "id" to expenseId,
            "description" to description,
            "amount" to amount,
            "paidById" to paidById,
            "paidByName" to paidByName,
            "splitWithMemberIds" to splitWithMemberIds,
            "category" to category,
            "date" to dateStr,
            "timestamp" to System.currentTimeMillis(),
            "isSettlement" to false
        )
        expenses.add(0, newExpense)
        val updated = localSpace.copy(expensesJson = gson.toJson(expenses))
        spaceDao.insertSpace(updated)
        updated
    }

    override suspend fun settleUp(
        spaceId: String,
        fromMemberId: String,
        fromMemberName: String,
        toMemberId: String,
        toMemberName: String,
        amount: Double
    ): Result<SharedSpaceEntity> = runCatching {
        val localSpace = spaceDao.getSpaceById(spaceId) ?: throw Exception("Space not found")
        val mapType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
        val expenses: MutableList<Map<String, Any>> = try {
            gson.fromJson<List<Map<String, Any>>>(localSpace.expensesJson, mapType).toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
        val expenseId = "settle_${UUID.randomUUID().toString().take(10)}"
        val dateStr = java.time.LocalDate.now().toString()
        val newExpense = mapOf(
            "id" to expenseId,
            "description" to "$fromMemberName paid $toMemberName",
            "amount" to amount,
            "paidById" to fromMemberId,
            "paidByName" to fromMemberName,
            "splitWithMemberIds" to listOf(toMemberId),
            "category" to "Settlement",
            "date" to dateStr,
            "timestamp" to System.currentTimeMillis(),
            "isSettlement" to true
        )
        expenses.add(0, newExpense)
        val updated = localSpace.copy(expensesJson = gson.toJson(expenses))
        spaceDao.insertSpace(updated)
        updated
    }

    override suspend fun deleteExpense(
        spaceId: String,
        expenseId: String
    ): Result<SharedSpaceEntity> = runCatching {
        val localSpace = spaceDao.getSpaceById(spaceId) ?: throw Exception("Space not found")
        val mapType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
        val expenses: List<Map<String, Any>> = try {
            gson.fromJson(localSpace.expensesJson, mapType)
        } catch (_: Exception) {
            emptyList()
        }
        val updatedExpenses = expenses.filterNot { it["id"] == expenseId }
        val updated = localSpace.copy(expensesJson = gson.toJson(updatedExpenses))
        spaceDao.insertSpace(updated)
        updated
    }

    override suspend fun deleteSpace(spaceId: String): Result<Unit> = runCatching {
        spaceDao.deleteSpace(spaceId)
    }

    override suspend fun syncPendingSpaces(): Result<Unit> = runCatching {
        val localSpaces = spaceDao.getAllSpacesDirect()
        for (space in localSpaces) {
            if (space.syncStatus == "PENDING_CREATE") {
                try {
                    val response = spaceApi.createSpace(CreateSpaceRequest(space.name, space.type))
                    if (response.success && response.data.space != null) {
                        spaceDao.deleteSpace(space.id)
                        val newEntity = response.data.space.toEntity().copy(expensesJson = space.expensesJson)
                        
                        val pendingGoals = getPendingGoals(space.goalsJson)
                        var finalEntity = newEntity
                        for (goal in pendingGoals) {
                            try {
                                val goalResp = spaceApi.addGoal(
                                    newEntity.id,
                                    AddGoalRequest(
                                        title = goal["title"] as String,
                                        targetAmount = goal["targetAmount"] as Double,
                                        category = goal["category"] as String
                                    )
                                )
                                if (goalResp.success && goalResp.data.space != null) {
                                    finalEntity = goalResp.data.space.toEntity().copy(expensesJson = space.expensesJson)
                                }
                            } catch (_: Exception) { }
                        }
                        spaceDao.insertSpace(finalEntity)
                    }
                } catch (_: Exception) { }
            } else if (space.syncStatus == "PENDING_JOIN") {
                try {
                    val response = spaceApi.joinSpace(space.inviteCode)
                    if (response.success && response.data.space != null) {
                        spaceDao.deleteSpace(space.id)
                        spaceDao.insertSpace(response.data.space.toEntity().copy(expensesJson = space.expensesJson))
                    }
                } catch (_: Exception) { }
            } else if (space.syncStatus == "PENDING_UPDATE") {
                try {
                    val pendingGoals = getPendingGoals(space.goalsJson)
                    var finalEntity = space
                    for (goal in pendingGoals) {
                        try {
                            val goalResp = spaceApi.addGoal(
                                space.id,
                                AddGoalRequest(
                                    title = goal["title"] as String,
                                    targetAmount = goal["targetAmount"] as Double,
                                    category = goal["category"] as String
                                )
                            )
                            if (goalResp.success && goalResp.data.space != null) {
                                finalEntity = goalResp.data.space.toEntity().copy(expensesJson = space.expensesJson)
                            }
                        } catch (_: Exception) { }
                    }
                    spaceDao.insertSpace(finalEntity.copy(syncStatus = "SYNCED"))
                } catch (_: Exception) { }
            }
        }
        refreshSpaces()
    }

    private fun getPendingGoals(goalsJson: String): List<Map<String, Any>> {
        val mapType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
        val goals: List<Map<String, Any>> = try {
            gson.fromJson(goalsJson, mapType)
        } catch (_: Exception) {
            emptyList()
        }
        return goals.filter { it["isPending"] == true }
    }

    private fun SpaceDto.toEntity(): SharedSpaceEntity {
        val resolvedId = id ?: UUID.randomUUID().toString()
        val fallbackInvite = (1..6).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("")
        return SharedSpaceEntity(
            id = resolvedId,
            name = name ?: "Shared Space",
            type = type ?: "CUSTOM",
            inviteCode = if (!inviteCode.isNullOrBlank()) inviteCode else fallbackInvite,
            membersJson = gson.toJson(members ?: emptyList<SpaceMemberDto>()),
            goalsJson = gson.toJson(goals ?: emptyList<SpaceGoalDto>()),
            expensesJson = "[]",
            syncStatus = "SYNCED"
        )
    }
}
