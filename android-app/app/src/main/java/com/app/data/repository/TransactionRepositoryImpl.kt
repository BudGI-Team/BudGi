package com.app.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.app.data.local.TokenManager
import com.app.data.local.dao.BudgetDao
import com.app.data.local.dao.GoalDao
import com.app.data.local.dao.MerchantCategoryMappingDao
import com.app.data.local.dao.PendingOperationDao
import com.app.data.local.dao.TransactionDao
import com.app.data.local.db.AppDatabase
import com.app.data.local.entity.BudgetEntity
import com.app.data.local.entity.GoalEntity
import com.app.data.local.entity.MerchantCategoryMappingEntity
import com.app.data.local.entity.PendingOperationEntity
import com.app.data.local.entity.TransactionEntity
import com.app.data.remote.api.BudgetApiService
import com.app.data.remote.api.SyncApiService
import com.app.data.remote.api.TransactionApiService
import com.app.data.remote.dto.*
import com.app.data.remote.api.SmsMessageDto
import com.app.data.sms.LlmSmsFallbackService
import com.app.data.sms.MerchantNormalizer
import com.app.data.sms.ParsedSmsTransaction
import com.app.data.sms.SmsReader
import com.app.data.sms.SmsTransactionParser
import com.app.data.sms.TransactionFusionEngine
import com.app.data.sms.FusionResult
import com.app.data.sms.MerchantLearningService
import com.app.domain.model.AnalyticsSummary
import com.app.domain.model.Budget
import com.app.domain.model.Transaction
import com.app.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.app.data.sync.SyncWorkScheduler
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.app.domain.model.SplitDetail
import com.app.utils.NotificationHelper
import com.app.utils.DateUtils
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

private const val TAG = "TransactionRepo"

/** Regex pattern for MongoDB ObjectID (24 hex chars). Used for safe ID disambiguation. */
private val MONGO_OBJECT_ID_REGEX = Regex("^[a-fA-F0-9]{24}$")

/** Minimum anomaly amount threshold (INR) to avoid noise from small transactions. */
private const val ANOMALY_MIN_AMOUNT = 500.0

/** Z-score threshold for anomaly detection. */
private const val ANOMALY_Z_SCORE_THRESHOLD = 2.5

/** Minimum sample size for meaningful statistical analysis. */
private const val ANOMALY_MIN_SAMPLES = 5

class TransactionRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsReader: SmsReader,
    private val parser: SmsTransactionParser,
    private val transactionDao: TransactionDao,
    private val budgetDao: BudgetDao,
    private val goalDao: GoalDao,
    private val merchantMappingDao: MerchantCategoryMappingDao,
    private val pendingOperationDao: PendingOperationDao,
    private val transactionApi: TransactionApiService,
    private val budgetApi: BudgetApiService,
    private val syncApi: SyncApiService,
    private val authApi: com.app.data.remote.api.AuthApiService,
    private val tokenManager: TokenManager,
    private val llmSmsFallbackService: LlmSmsFallbackService,
    private val notificationHelper: NotificationHelper,
    private val database: AppDatabase,
    private val fusionEngine: TransactionFusionEngine,
    private val merchantLearningService: MerchantLearningService
) : TransactionRepository {
    private val gson = Gson()
    private val splitType = object : TypeToken<List<SplitDetail>>() {}.type
    private val splitDtoType = object : TypeToken<List<SplitDetailDto>>() {}.type

    override fun observeAllTransactions(): Flow<List<Transaction>> =
        transactionDao.observeTransactions().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeCurrentMonthAnalytics(): Flow<AnalyticsSummary> {
        val currentMonth = YearMonth.now()
        val currentMonthStr = currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        val prevMonthStr = currentMonth.minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"))

        return combine(
            transactionDao.observeCategoryTotalsByMonth(currentMonthStr),
            transactionDao.observeTotalSpendingByMonth(currentMonthStr),
            transactionDao.observeTotalSpendingByMonth(prevMonthStr),
            transactionDao.observeTotalIncomeByMonth(currentMonthStr),
            budgetDao.observeAll()
        ) { categoryTotals, currentTotal, prevTotal, income, budgets ->
            val totalSpending = currentTotal ?: 0.0
            val totalIncome = income ?: 0.0
            val previousMonthSpending = prevTotal ?: 0.0
            val totalBudget = budgets.sumOf { it.limitAmount }
            val byCategory = categoryTotals.associate { it.category to it.total }

            AnalyticsSummary(
                monthlySpending = totalSpending,
                monthlyIncome = totalIncome,
                categoryBreakdown = byCategory,
                estimatedSavings = totalIncome - totalSpending,
                previousMonthSpending = previousMonthSpending,
                totalBudget = totalBudget
            )
        }
    }

    override fun observeDetectedBankAccounts(): Flow<List<com.app.data.local.dao.DetectedBankSummary>> =
        transactionDao.observeDetectedBankAccounts()

    override fun observeBudgets(): Flow<List<Budget>> {
        return combine(
            budgetDao.observeAll(),
            transactionDao.observeCategoryTotalsByMonth(
                YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
            )
        ) { budgets, totals ->
            val totalsMap = totals.associate { it.category to it.total }
            budgets.map { entity ->
                Budget(
                    id = entity.serverId ?: entity.id.toString(),
                    category = entity.category,
                    limit = entity.limitAmount,
                    period = entity.period,
                    alertAt = entity.alertAt,
                    color = entity.color,
                    spentAmount = totalsMap[entity.category] ?: 0.0
                )
            }
        }
    }

    override suspend fun createTransaction(transaction: Transaction): Result<Transaction> = runCatching {
        val clientId = UUID.randomUUID().toString()
        var isAnomaly = transaction.isAnomaly
        if (!isAnomaly && (transaction.type.equals("expense", ignoreCase = true) || transaction.type.equals("debit", ignoreCase = true)) && transaction.category.isNotBlank() && transaction.category != "Other" && transaction.category != "Others") {
            try {
                val recentExpenses = transactionDao.getRecentExpensesByCategory(transaction.category, 20)
                if (recentExpenses.size >= ANOMALY_MIN_SAMPLES) {
                    val mean = recentExpenses.average()
                    val variance = recentExpenses.map { Math.pow(it - mean, 2.0) }.average()
                    val stdDev = Math.sqrt(variance)
                    if (stdDev > 0) {
                        val zScore = (transaction.amount - mean) / stdDev
                        if (zScore > ANOMALY_Z_SCORE_THRESHOLD && transaction.amount > ANOMALY_MIN_AMOUNT) {
                            isAnomaly = true
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        val txToSave = transaction.copy(isAnomaly = isAnomaly)
        val entity = txToSave.toEntity(clientId = clientId, syncStatus = "pending_create")
        val localId = transactionDao.insert(entity)
        if (entity.category.isNotBlank()) {
            try {
                database.categoryDao().insertCategory(
                    com.app.data.local.entity.CategoryEntity(name = entity.category.trim(), keywordsCsv = "")
                )
            } catch (_: Exception) {}
        }

        // Try to sync immediately
        try {
            val dto = txToSave.toDto(clientId = clientId)
            val response = transactionApi.create(dto)
            if (response.isSuccessful && response.body()?.success == true) {
                val serverTx = response.body()?.data?.transaction
                if (serverTx != null) {
                    transactionDao.markSynced(localId, serverTx.id ?: "")
                    return@runCatching serverTx.toDomain()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "createTransaction: offline, queued for sync", e)
            SyncWorkScheduler.scheduleImmediateSync(context)
        }

        txToSave.copy(id = localId.toString(), clientId = clientId)
    }

    override suspend fun updateTransaction(id: String, changes: Map<String, Any>): Result<Unit> = runCatching {
        try {
            val response = transactionApi.update(id, changes)
            if (!response.isSuccessful) {
                throw Exception("Update failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "updateTransaction: offline, queuing pending operation", e)
            // Queue for later sync via PendingOperationDao
            pendingOperationDao.insert(
                PendingOperationEntity(
                    entityType = "transaction",
                    operationType = "update",
                    entityId = id,
                    payload = gson.toJson(changes)
                )
            )
            // Also mark the local entity as pending_update if it has a serverId
            val entity = transactionDao.getByServerId(id)
            if (entity != null) {
                transactionDao.updateSyncStatus(entity.id, "pending_update")
            }
            SyncWorkScheduler.scheduleImmediateSync(context)
        }
    }

    override suspend fun deleteTransaction(id: String): Result<Unit> = runCatching {
        try {
            val response = transactionApi.delete(id)
            if (response.isSuccessful) {
                transactionDao.deleteByServerIds(listOf(id))
            } else {
                throw Exception("Delete failed: HTTP ${response.code()}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "deleteTransaction: offline, marking as pending_delete", e)
            // Mark as pending_delete locally so it's excluded from UI and synced on next sync
            val entity = transactionDao.getByServerId(id)
            if (entity != null) {
                transactionDao.updateSyncStatus(entity.id, "pending_delete")
                pendingOperationDao.insert(
                    PendingOperationEntity(
                        entityType = "transaction",
                        operationType = "delete",
                        entityId = id,
                        payload = ""
                    )
                )
                SyncWorkScheduler.scheduleImmediateSync(context)
            }
        }
    }

    override suspend fun clearAllTransactions(): Result<Unit> = runCatching {
        try {
            val response = transactionApi.clearTransactions()
            if (response.isSuccessful) {
                transactionDao.deleteAll()
                tokenManager.lastSmsScanAt = 0 // Reset SMS parser to start fresh
            } else {
                throw Exception("Failed to clear transactions on server")
            }
        } catch (e: Exception) {
            Log.e(TAG, "clearAllTransactions failed", e)
            throw Exception("Failed to clear transactions: ${e.message}")
        }
    }

    override suspend fun saveBudget(budget: Budget): Result<Unit> = runCatching {
        val dto = BudgetDto(
            id = if (isServerObjectId(budget.id)) budget.id else null,
            category = budget.category,
            limit = budget.limit,
            period = budget.period,
            alertAt = budget.alertAt,
            color = budget.color
        )

        try {
            val response = if (dto.id != null) {
                budgetApi.update(dto.id, dto)
            } else {
                budgetApi.create(dto)
            }
            if (response.isSuccessful && response.body()?.data?.budget != null) {
                val serverBudget = response.body()!!.data!!.budget
                budgetDao.insertBudget(serverBudget.toEntity())
                return@runCatching
            }
        } catch (e: Exception) {
            Log.w(TAG, "saveBudget: offline, saving locally", e)
            SyncWorkScheduler.scheduleImmediateSync(context)
        }

        budgetDao.insertBudget(BudgetEntity(
            category = budget.category,
            limitAmount = budget.limit,
            period = budget.period,
            alertAt = budget.alertAt,
            color = budget.color,
            syncStatus = "pending_create"
        ))
    }

    override suspend fun deleteBudget(id: String): Result<Unit> = runCatching {
        try {
            budgetApi.delete(id)
        } catch (e: Exception) {
            Log.w(TAG, "deleteBudget: offline, queuing delete", e)
            pendingOperationDao.insert(
                PendingOperationEntity(
                    entityType = "budget",
                    operationType = "delete",
                    entityId = id,
                    payload = ""
                )
            )
            SyncWorkScheduler.scheduleImmediateSync(context)
        }
        budgetDao.getByServerId(id)?.let { budgetDao.deleteBudget(it) }
    }

    /**
     * Read SMS, parse transactions, and store in Room with 3-tier deduplication.
     *
     * Dedup strategy:
     * 1. Reference number match (strongest — unique per transaction)
     * 2. Amount + timestamp range (±2 min) + sender (catches same tx without ref)
     * 3. In-memory hash in parser (catches duplicates within same scan batch)
     *
     * All SMS parsing runs on [Dispatchers.Default] to avoid blocking the main thread.
     */
    override suspend fun readParseAndStoreSms() {
        // Clear parser's in-memory duplicate cache for fresh scan
        parser.clearProcessedHashes()

        // 0. Auto-heal any existing Room DB anomalies (fix false mandate entries, dedup Swiggy duplicates)
        cleanupDuplicatesAndInvalidSmsTransactions()

        // Calculate scan window: max of (last scan, 6 months ago)
        val sixMonthsAgo = Instant.now().minus(180, java.time.temporal.ChronoUnit.DAYS).toEpochMilli()
        val lastScanTimestamp = tokenManager.lastSmsScanAt
        val sinceTimestamp = maxOf(lastScanTimestamp, sixMonthsAgo)

        // Read only SMS newer than the calculated sinceTimestamp
        val messages = smsReader.readInbox(sinceTimestamp = sinceTimestamp)

        val unparsedMessages = mutableListOf<SmsMessageDto>()

        // Run CPU-intensive regex parsing on Default dispatcher (§5.1)
        val processed = withContext(Dispatchers.Default) {
            messages.mapNotNull { sms ->
                val parsed = parser.parse(body = sms.body, sender = sms.sender, timestamp = sms.timestamp)
                if (parsed == null) {
                    if (parser.isPotentialScam(sms.body)) {
                        // It's a potential scam. Call backend for LLM verification.
                        val scamResponse = llmSmsFallbackService.verifySmishing(sms.body)
                        if (scamResponse != null && scamResponse.isScam) {
                            notificationHelper.showSmishingAlert(sms.sender, scamResponse.reason)
                        }
                    } else if (parser.classify(sms.body, sms.sender) == com.app.data.sms.SmsClassification.TRANSACTION) {
                        unparsedMessages.add(SmsMessageDto(text = sms.body, sender = sms.sender, timestamp = sms.timestamp))
                    }
                }
                parsed
            }
        }
        
        val fallbackTransactions = llmSmsFallbackService.parseWithFallback(unparsedMessages)
        val fallbackParsed = fallbackTransactions.map { tx ->
            ParsedSmsTransaction(
                amount = tx.amount,
                type = if (tx.type == "expense") "debit" else "credit",
                category = tx.category,
                method = "llm",
                merchant = tx.merchant,
                upiId = tx.upiId,
                referenceNumber = tx.referenceNumber,
                accountLast4 = tx.accountLast4,
                availableBalance = tx.availableBalance,
                transactionDate = if (tx.timestamp > 0) tx.timestamp else System.currentTimeMillis(),
                smsDate = if (tx.timestamp > 0) tx.timestamp else System.currentTimeMillis(),
                sender = tx.sender,
                rawSms = tx.rawSms ?: "",
                isRecurring = tx.isRecurring
            )
        }
        val allTransactions: List<ParsedSmsTransaction> = processed + fallbackParsed

        if (allTransactions.isEmpty()) {
            // Still update scan timestamp even if no transactions found
            if (messages.isNotEmpty()) {
                tokenManager.lastSmsScanAt = messages.maxOf { it.timestamp }
            }
            return
        }

        // Track seen keys in this batch to prevent intra-batch duplicates
        val seenInBatch = mutableSetOf<String>()

        // Multi-tier dedup against existing Room DB entries and current batch
        val entities = allTransactions.mapNotNull { tx ->
            // Batch dedup (prevents identical SMS in the same batch)
            val batchKey = if (!tx.referenceNumber.isNullOrBlank()) {
                "ref_${tx.referenceNumber.trim()}"
            } else if (!tx.rawSms.isNullOrBlank()) {
                "raw_${tx.sender}_${tx.amount}_${tx.rawSms.trim().hashCode()}_${tx.smsDate}"
            } else {
                "${tx.amount}_${tx.sender}_${tx.merchant}_${tx.smsDate}"
            }
            if (!seenInBatch.add(batchKey)) return@mapNotNull null

            // ── Gap 2: Pre-Debit Mandates (e-NACH, UPI Autopay, SI Warnings) ──
            if (tx.isPreDebitMandate || com.app.data.sms.BankSmsTemplateEngine.isPreDebitMandate(tx.rawSms)) {
                Log.i(TAG, "Captured pre-debit mandate warning of ₹${tx.amount} for ${tx.merchant}")
                val merchantName = tx.merchant ?: "Merchant"
                notificationHelper.showUpcomingMandateAlert(
                    merchant = merchantName,
                    amount = tx.amount,
                    accountLast4 = tx.accountLast4
                )
                // Skip inserting as completed expense today so it doesn't duplicate tomorrow's real debit!
                return@mapNotNull null
            }

            // ── Gap 1: UPI Auto-Reversal & Failed Payment Refund Reconciliation ──
            if (tx.isReversal || com.app.data.sms.BankSmsTemplateEngine.isReversalOrRefund(tx.rawSms)) {
                val minTime = tx.transactionDate - 48 * 60 * 60 * 1000L
                val maxTime = tx.transactionDate + 10 * 60 * 1000L
                val matchingDebit = transactionDao.findMatchingDebitForRefund(
                    amount = tx.amount,
                    accountLast4 = tx.accountLast4,
                    minTimestamp = minTime,
                    maxTimestamp = maxTime
                )
                if (matchingDebit != null) {
                    Log.i(TAG, "Auto-reconciled reversal of ₹${tx.amount} with debit ID ${matchingDebit.id}")
                    transactionDao.markTransactionReversed(matchingDebit.id)
                    val merchantName = matchingDebit.alias ?: matchingDebit.merchant ?: tx.merchant ?: "Vendor"
                    notificationHelper.showRefundReconciledAlert(merchantName, tx.amount)
                    // Skip inserting duplicate credit/income! Debit was neutralized.
                    return@mapNotNull null
                }
            }

            // Tier 1: Reference number dedup (strongest — unique per financial transaction)
            if (!tx.referenceNumber.isNullOrBlank()) {
                val refCount = transactionDao.countByReferenceNumber(tx.referenceNumber.trim())
                if (refCount > 0) return@mapNotNull null
            }

            // Tier 1b: Exact rawSms match dedup
            if (!tx.rawSms.isNullOrBlank()) {
                val rawCount = transactionDao.countByRawSms(tx.rawSms.trim())
                if (rawCount > 0) return@mapNotNull null
            }

            // Tier 2: Logical deduplication on calendar day
            val txDay = if (tx.transactionDate > 0) {
                Instant.ofEpochMilli(tx.transactionDate).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE)
            } else ""

            val duplicateWindowMs = 30 * 60 * 1000L
            val potentialDuplicates = transactionDao.getPotentialDuplicatesBroad(
                amount = tx.amount,
                dateDay = txDay,
                minTimestamp = tx.smsDate - duplicateWindowMs,
                maxTimestamp = tx.smsDate + duplicateWindowMs
            )
            
            var isDuplicate = false
            for (existingTx in potentialDuplicates) {
                // If both transactions have reference numbers and they differ, they are definitely different transactions!
                if (!tx.referenceNumber.isNullOrBlank() && !existingTx.referenceNumber.isNullOrBlank() &&
                    !tx.referenceNumber.trim().equals(existingTx.referenceNumber.trim(), ignoreCase = true)) {
                    continue
                }

                // Check 1: Identical raw SMS delivered multiple times
                if (!tx.rawSms.isNullOrBlank() && !existingTx.rawSms.isNullOrBlank() &&
                    tx.rawSms.trim() == existingTx.rawSms.trim()) {
                    isDuplicate = true
                    break
                }

                // Check 2: Same sender or matching merchant within same day
                val sameSender = existingTx.sender.equals(tx.sender, ignoreCase = true)
                val existingMerchantNormal = existingTx.normalizedMerchant ?: MerchantNormalizer.normalize(existingTx.merchant)
                val newMerchantNormal = MerchantNormalizer.normalize(tx.merchant)
                val sameMerchant = existingMerchantNormal.isNotBlank() && existingMerchantNormal == newMerchantNormal
                val typeMatch = existingTx.type.equals(if (tx.type == "debit") "expense" else "income", ignoreCase = true)

                if ((sameSender || sameMerchant) && typeMatch) {
                    isDuplicate = true
                    break
                }
            }
            if (isDuplicate) return@mapNotNull null

            // Convert transaction date to ISO full Instant string (e.g. 2026-10-17T15:45:30.123Z)
            val dateStr = Instant.ofEpochMilli(tx.transactionDate).toString()

            // SMS body date as separate field (if different from SMS timestamp)
            val smsBodyDateStr = if (tx.transactionDate != tx.smsDate) {
                Instant.ofEpochMilli(tx.transactionDate)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            } else null

            // ── Phase 5: Transaction Fusion with Payment App Notifications ──
            val fusionResult = fusionEngine.onSmsParsed(
                amount = tx.amount,
                type = tx.type,
                timestamp = tx.transactionDate,
                merchant = tx.merchant,
                referenceNumber = tx.referenceNumber
            )
            val effectiveMerchant = when (fusionResult) {
                is FusionResult.Enrich -> fusionResult.cleanMerchant ?: tx.merchant
                else -> tx.merchant
            }
            val effectiveMethod = when (fusionResult) {
                is FusionResult.Enrich -> fusionResult.sourceApp
                else -> tx.method
            }

            // ── Phase 4: Merchant Learning & Multi-Signal Categorization ──
            val merchantKey = MerchantNormalizer.getBestKey(effectiveMerchant, tx.upiId)
            val normalizedMerchant = MerchantNormalizer.normalize(merchantKey)

            val txHour = try {
                Instant.ofEpochMilli(tx.transactionDate).atZone(ZoneId.systemDefault()).hour
            } catch (_: Exception) { -1 }

            val decision = merchantLearningService.decideCategory(
                merchant = effectiveMerchant,
                upiId = tx.upiId,
                normalizedMerchant = normalizedMerchant,
                amount = tx.amount,
                type = tx.type,
                message = tx.rawSms,
                hourOfDay = txHour
            )

            val finalCategory = decision.category
            val categorySource = decision.source
            val isReviewed = !decision.needsReview
            val alias = decision.alias

            val txType = if (tx.type == "debit") "expense" else "income"

            // ── Real-Time Anomaly Detection ──
            var isAnomaly = false
            if (txType == "expense" && finalCategory.isNotBlank() && finalCategory != "Other" && finalCategory != "Others") {
                try {
                    val recentExpenses = transactionDao.getRecentExpensesByCategory(finalCategory, 20)
                    if (recentExpenses.size >= ANOMALY_MIN_SAMPLES) {
                        val mean = recentExpenses.average()
                        val variance = recentExpenses.map { Math.pow(it - mean, 2.0) }.average()
                        val stdDev = Math.sqrt(variance)
                        
                        if (stdDev > 0) {
                            val zScore = (tx.amount - mean) / stdDev
                            if (zScore > ANOMALY_Z_SCORE_THRESHOLD && tx.amount > ANOMALY_MIN_AMOUNT) {
                                isAnomaly = true
                                val merchantName = tx.merchant ?: tx.upiId ?: tx.sender
                                notificationHelper.showAnomalyAlert(merchantName, tx.amount, finalCategory)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Anomaly detection failed for $finalCategory", e)
                }
            }

            val entity = TransactionEntity(
                clientId = UUID.randomUUID().toString(),
                amount = tx.amount,
                type = txType,
                category = finalCategory,
                categorySource = categorySource,
                description = "SMS: ${tx.merchant ?: tx.upiId ?: tx.sender}",
                merchant = effectiveMerchant,
                normalizedMerchant = normalizedMerchant.ifBlank { null },
                date = dateStr,
                source = if (fusionResult is FusionResult.Enrich) "fused_notification_sms" else "sms",
                syncStatus = "pending_create",
                isRecurring = tx.isRecurring,
                isAnomaly = isAnomaly,
                method = effectiveMethod,
                timestamp = tx.transactionDate,
                sender = tx.sender,
                upiId = tx.upiId,
                referenceNumber = tx.referenceNumber,
                accountLast4 = tx.accountLast4,
                availableBalance = tx.availableBalance,
                rawSms = tx.rawSms,
                smsBodyDate = smsBodyDateStr,
                alias = alias,
                isReviewed = isReviewed
            )

            entity
        }

        if (entities.isNotEmpty()) {
            val insertedIds = transactionDao.insertAll(entities)
            // Re-run dedup cleanup post-insertion to ensure zero duplicates
            cleanupDuplicatesAndInvalidSmsTransactions()
            triggerWidgetUpdate()
            for ((index, entity) in entities.withIndex()) {
                if (System.currentTimeMillis() - entity.timestamp < 10 * 60 * 1000) {
                    val isExpense = entity.type == "expense"
                    val typeStr = if (isExpense) "expense" else "income"
                    val assignedId = if (index < insertedIds.size && insertedIds[index] > 0) insertedIds[index] else entity.id
                    notificationHelper.showTransactionAdded(
                        merchant = entity.alias ?: entity.merchant ?: entity.description.removePrefix("SMS: "),
                        amount = entity.amount,
                        type = typeStr,
                        transactionId = assignedId,
                        upiId = entity.upiId,
                        category = entity.category,
                        categorySource = entity.categorySource,
                        normalizedMerchant = entity.normalizedMerchant
                    )
                }
            }
        }

        // Update last scan timestamp
        tokenManager.lastSmsScanAt = messages.maxOf { it.timestamp }
    }

    override suspend fun syncWithServer(): Result<Unit> = runCatching {
        // Process any queued pending operations first
        val pendingOps = pendingOperationDao.getAll()
        for (op in pendingOps) {
            try {
                when {
                    op.entityType == "transaction" && op.operationType == "update" -> {
                        val changes: Map<String, Any> = gson.fromJson(op.payload, object : TypeToken<Map<String, Any>>() {}.type)
                        val response = transactionApi.update(op.entityId, changes)
                        if (response.isSuccessful) {
                            pendingOperationDao.deleteById(op.id)
                        } else {
                            pendingOperationDao.incrementRetry(op.id)
                        }
                    }
                    op.entityType == "transaction" && op.operationType == "delete" -> {
                        val response = transactionApi.delete(op.entityId)
                        if (response.isSuccessful) {
                            transactionDao.deleteByServerIds(listOf(op.entityId))
                            pendingOperationDao.deleteById(op.id)
                        } else {
                            pendingOperationDao.incrementRetry(op.id)
                        }
                    }
                    op.entityType == "budget" && op.operationType == "delete" -> {
                        try {
                            budgetApi.delete(op.entityId)
                            pendingOperationDao.deleteById(op.id)
                        } catch (e: Exception) {
                            pendingOperationDao.incrementRetry(op.id)
                        }
                    }
                    op.entityType == "profile" && op.operationType == "update" -> {
                        val request = gson.fromJson(op.payload, UpdateProfileRequest::class.java)
                        val response = authApi.updateProfile(request)
                        if (response.isSuccessful) {
                            pendingOperationDao.deleteById(op.id)
                        } else {
                            pendingOperationDao.incrementRetry(op.id)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to process pending op: ${op.entityType}/${op.operationType}", e)
                pendingOperationDao.incrementRetry(op.id)
            }
        }
        // Clean up stale operations that exceeded retry limit
        pendingOperationDao.deleteStale(5)

        val pendingTx = transactionDao.getPendingSync()
        val pendingBudgets = budgetDao.getPendingSync()
        val pendingGoals = goalDao.getPendingSync()

        val request = SyncRequest(
            lastSyncAt = tokenManager.lastSyncAt,
            pendingTransactions = pendingTx
                .filter { it.syncStatus == "pending_create" }
                .map { it.toDto() },
            pendingUpdates = pendingTx
                .filter { it.syncStatus == "pending_update" && it.serverId != null }
                .map { entity ->
                    com.app.data.remote.dto.PendingUpdate(
                        id = entity.serverId!!,
                        changes = buildMap {
                            put("category", entity.category)
                            put("categorySource", entity.categorySource)
                            entity.alias?.let { put("alias", it) }
                            put("isReviewed", entity.isReviewed)
                        },
                        updatedAt = entity.updatedAt ?: java.time.Instant.now().toString()
                    )
                },
            pendingDeletes = pendingTx
                .filter { it.syncStatus == "pending_delete" && it.serverId != null }
                .map { it.serverId!! },
            pendingBudgets = pendingBudgets
                .filter { it.syncStatus == "pending_create" }
                .map { BudgetDto(category = it.category, limit = it.limitAmount, period = it.period, color = it.color) },
            pendingGoals = pendingGoals
                .filter { it.syncStatus == "pending_create" }
                .map { GoalDto(title = it.title, targetAmount = it.targetAmount, currentAmount = it.currentAmount, deadline = it.deadline, category = it.category, icon = it.icon, color = it.color) }
        )

        val response = syncApi.fullSync(request)
        if (!response.isSuccessful || response.body()?.data == null) {
            throw Exception("Sync failed: HTTP ${response.code()}")
        }

        val data = response.body()!!.data!!
        applySyncData(data)
        tokenManager.lastSyncAt = data.syncTimestamp
    }

    override suspend fun refreshFromServer(): Result<Unit> = runCatching {
        val response = syncApi.deltaPull(tokenManager.lastSyncAt)
        if (!response.isSuccessful || response.body()?.data == null) {
            throw Exception("Refresh failed: HTTP ${response.code()}")
        }
        val data = response.body()!!.data!!

        // Use Room @Transaction to ensure atomicity (§3.3)
        database.withTransaction {
            // Upsert transactions safely without duplicating
            data.transactions.forEach { dto ->
                upsertTransactionDto(dto)
            }

            // Remove deleted
            if (data.deletedTransactionIds.isNotEmpty()) {
                transactionDao.deleteByServerIds(data.deletedTransactionIds)
            }

            // Non-destructively reconcile budgets without wiping offline pending items
            reconcileBudgetsNonDestructively(data.budgets)

            // Non-destructively reconcile goals without wiping offline pending items
            reconcileGoalsNonDestructively(data.goals)
        }

        cleanupDuplicatesAndInvalidSmsTransactions()
        tokenManager.lastSyncAt = data.syncTimestamp
    }

    // ── Sync data application ────────────────────────────────────────────

    private suspend fun applySyncData(data: SyncResponseData) {
        // Use Room @Transaction for atomicity (§3.3)
        database.withTransaction {
            // Upsert new/updated transactions from server safely
            (data.newTransactions + data.updatedTransactions).forEach { dto ->
                upsertTransactionDto(dto)
            }

            // Remove server-deleted transactions
            if (data.deletedTransactionIds.isNotEmpty()) {
                transactionDao.deleteByServerIds(data.deletedTransactionIds)
            }

            // Non-destructively reconcile budgets
            reconcileBudgetsNonDestructively(data.budgets)

            // Non-destructively reconcile goals
            reconcileGoalsNonDestructively(data.goals)

            // Mark pending items as synced
            transactionDao.getPendingSync().forEach { entity ->
                if (entity.syncStatus == "pending_create" || entity.syncStatus == "pending_update") {
                    transactionDao.updateSyncStatus(entity.id, "synced")
                }
            }
            budgetDao.getPendingSync().forEach { entity ->
                if (entity.syncStatus == "pending_create" || entity.syncStatus == "pending_update") {
                    budgetDao.insertBudget(entity.copy(syncStatus = "synced"))
                }
            }
            goalDao.getPendingSync().forEach { entity ->
                if (entity.syncStatus == "pending_create" || entity.syncStatus == "pending_update") {
                    goalDao.update(entity.copy(syncStatus = "synced"))
                }
            }
        }
        cleanupDuplicatesAndInvalidSmsTransactions()
        triggerWidgetUpdate()
    }

    /**
     * Non-destructively merges incoming server budgets with local Room state.
     * Preserves locally created/updated budgets that have not synced yet.
     */
    private suspend fun reconcileBudgetsNonDestructively(serverBudgets: List<com.app.data.remote.dto.BudgetDto>) {
        val serverIds = serverBudgets.mapNotNull { it.id }.filter { it.isNotBlank() }

        // Remove only already-synced local budgets that no longer exist on server (deleted remotely)
        if (serverIds.isNotEmpty()) {
            budgetDao.deleteSyncedBudgetsNotIn(serverIds)
        } else if (serverBudgets.isEmpty()) {
            budgetDao.deleteSyncedBudgets()
        }

        // Upsert incoming server budgets without overwriting local pending budgets
        val localPendingBudgets = budgetDao.getPendingSync().associateBy { it.category.lowercase().trim() }
        serverBudgets.forEach { serverDto ->
            val catKey = serverDto.category.lowercase().trim()
            val localPending = localPendingBudgets[catKey]
            if (localPending == null) {
                val existing = serverDto.id?.let { budgetDao.getByServerId(it) }
                    ?: budgetDao.getByCategory(serverDto.category)
                val entityToInsert = serverDto.toEntity().copy(
                    id = existing?.id ?: 0,
                    syncStatus = "synced"
                )
                budgetDao.insertBudget(entityToInsert)
            }
        }
    }

    /**
     * Non-destructively merges incoming server goals with local Room state.
     * Preserves locally created/updated goals that have not synced yet.
     */
    private suspend fun reconcileGoalsNonDestructively(serverGoals: List<com.app.data.remote.dto.GoalDto>) {
        val serverIds = serverGoals.mapNotNull { it.id }.filter { it.isNotBlank() }

        // Remove only already-synced local goals that no longer exist on server (deleted remotely)
        if (serverIds.isNotEmpty()) {
            goalDao.deleteSyncedGoalsNotIn(serverIds)
        } else if (serverGoals.isEmpty()) {
            goalDao.deleteSyncedGoals()
        }

        // Upsert incoming server goals without overwriting local pending goals
        val localPendingGoals = goalDao.getPendingSync().associateBy { (it.clientId ?: it.title).lowercase().trim() }
        serverGoals.forEach { serverDto ->
            val clientKey = (serverDto.clientId ?: serverDto.title).lowercase().trim()
            val localPending = localPendingGoals[clientKey]
            if (localPending == null) {
                val existing = serverDto.id?.let { goalDao.getByServerId(it) }
                val entityToInsert = serverDto.toGoalEntity().copy(
                    id = existing?.id ?: 0,
                    syncStatus = "synced"
                )
                goalDao.insert(entityToInsert)
            }
        }
    }

    /**
     * Upsert a server transaction DTO into Room by resolving existing local records
     * via serverId, clientId, referenceNumber, or precision logical matching.
     * Prevents generating duplicate SQLite auto-increment IDs.
     */
    private suspend fun upsertTransactionDto(dto: TransactionDto) {
        fun mergeWithExisting(existing: TransactionEntity): TransactionEntity {
            val bestTimestamp = if (existing.timestamp > 0) existing.timestamp else dto.timestamp ?: DateUtils.parseIsoDateToMillis(dto.date)
            val preservedAlias = existing.alias ?: dto.alias
            val preservedCategory = if (existing.categorySource == "UserLearned") existing.category else dto.category
            val preservedSource = if (existing.categorySource == "UserLearned") existing.categorySource else (dto.categorySource ?: "Default")
            val preservedReviewed = existing.isReviewed || (dto.isReviewed ?: true)

            return dto.toEntity().copy(
                id = existing.id,
                serverId = dto.id ?: existing.serverId,
                timestamp = bestTimestamp,
                alias = preservedAlias,
                category = preservedCategory,
                categorySource = preservedSource,
                isReviewed = preservedReviewed
            )
        }

        // 1. Check by serverId
        if (!dto.id.isNullOrBlank()) {
            val existing = transactionDao.getByServerId(dto.id)
            if (existing != null) {
                transactionDao.insert(mergeWithExisting(existing))
                return
            }
        }

        // 2. Check by clientId
        if (!dto.clientId.isNullOrBlank()) {
            val existing = transactionDao.getByClientId(dto.clientId)
            if (existing != null) {
                transactionDao.insert(mergeWithExisting(existing))
                return
            }
        }

        // 3. Check by referenceNumber
        if (!dto.referenceNumber.isNullOrBlank()) {
            val existing = transactionDao.getByReferenceNumber(dto.referenceNumber.trim())
            if (existing != null) {
                transactionDao.insert(mergeWithExisting(existing))
                return
            }
        }

        // 3b. Check by exact rawSms
        if (!dto.rawSms.isNullOrBlank()) {
            val existing = transactionDao.getByRawSms(dto.rawSms.trim())
            if (existing != null) {
                transactionDao.insert(mergeWithExisting(existing))
                return
            }
        }

        // 4. Broad calendar-day logical match to link unlinked local transactions
        val dtoDay = if (dto.date.length >= 10) dto.date.substring(0, 10) else ""
        if (dtoDay.isNotBlank()) {
            val txTime = DateUtils.parseIsoDateToMillis(dto.date)
            val candidates = transactionDao.getPotentialDuplicatesBroad(
                amount = dto.amount,
                dateDay = dtoDay,
                minTimestamp = txTime - 86400000L,
                maxTimestamp = txTime + 86400000L
            )
            val matchingLocal = candidates.firstOrNull { local ->
                // If both have reference numbers and they differ, distinct
                if (!dto.referenceNumber.isNullOrBlank() && !local.referenceNumber.isNullOrBlank() &&
                    !dto.referenceNumber.trim().equals(local.referenceNumber.trim(), ignoreCase = true)) {
                    return@firstOrNull false
                }
                val merchantMatch = dto.merchant.isNotBlank() &&
                    (local.merchant?.equals(dto.merchant, ignoreCase = true) == true ||
                     MerchantNormalizer.normalize(local.merchant).equals(MerchantNormalizer.normalize(dto.merchant), ignoreCase = true))
                val rawSmsMatch = !dto.rawSms.isNullOrBlank() && local.rawSms == dto.rawSms
                val accountMatch = !dto.accountLast4.isNullOrBlank() && local.accountLast4 == dto.accountLast4
                (merchantMatch || rawSmsMatch || accountMatch) && local.type.equals(dto.type, ignoreCase = true)
            }
            if (matchingLocal != null) {
                val bestTimestamp = if (matchingLocal.timestamp > 0) matchingLocal.timestamp else dto.timestamp ?: txTime
                transactionDao.insert(dto.toEntity().copy(id = matchingLocal.id, serverId = dto.id, timestamp = bestTimestamp))
                return
            }
        }

        // 5. Fresh transaction from server
        transactionDao.insert(dto.toEntity())
    }

    // ── Merchant Learning ────────────────────────────────────────────────

    override suspend fun applyCategoryToMerchant(
        transactionId: String,
        newCategory: String,
        applyToPast: Boolean,
        applyToFuture: Boolean,
        alias: String?
    ): Result<Unit> = runCatching {
        // Find the transaction locally
        val localId = transactionId.toLongOrNull()
        val entity = if (localId != null) {
            transactionDao.getById(localId)
        } else {
            transactionDao.getByServerId(transactionId)
        } ?: throw Exception("Transaction not found")

        val merchantKey = MerchantNormalizer.getBestKey(entity.merchant, entity.upiId)
        val normalized = MerchantNormalizer.normalize(merchantKey)
        val nowStr = Instant.now().toString()

        // 1. Always update this single transaction (and mark isReviewed = 1)
        transactionDao.updateCategoryAndReview(entity.id, newCategory)
        if (!alias.isNullOrBlank()) {
            transactionDao.updateTransactionAlias(entity.id, alias.trim())
        }
        try {
            database.categoryDao().insertCategory(
                com.app.data.local.entity.CategoryEntity(name = newCategory.trim(), keywordsCsv = "")
            )
        } catch (_: Exception) {}

        if (normalized.isNotBlank()) {
            // 2. If applyToFuture: save to merchant mapping table
            if (applyToFuture) {
                merchantMappingDao.upsert(
                    MerchantCategoryMappingEntity(
                        merchantKey = merchantKey,
                        normalizedMerchant = normalized,
                        category = newCategory,
                        alias = alias?.trim(),
                        confidence = 1.0,
                        frequency = 1,
                        lastUsedTimestamp = System.currentTimeMillis(),
                        learnedFromUser = true,
                        source = "UserLearned",
                        syncStatus = "pending_create",
                        createdAt = nowStr,
                        updatedAt = nowStr
                    )
                )
            } else if (!alias.isNullOrBlank()) {
                merchantMappingDao.updateAlias(normalized, alias.trim(), nowStr)
            }

            // 3. If applyToPast: bulk-update all local transactions from same merchant
            if (applyToPast) {
                transactionDao.updateCategoryByNormalizedMerchant(normalized, newCategory, nowStr)
                if (!alias.isNullOrBlank()) {
                    transactionDao.updateAliasForMerchant(normalized, alias.trim())
                }
            }
        }

        // 4. Try to sync the update to the server (best-effort)
        val serverId = entity.serverId
        if (serverId != null) {
            try {
                val updateBody = mutableMapOf<String, Any>(
                    "category" to newCategory,
                    "applyToPast" to applyToPast,
                    "applyToFuture" to applyToFuture
                )
                if (!alias.isNullOrBlank()) {
                    updateBody["alias"] = alias.trim()
                }
                val response = transactionApi.update(serverId, updateBody)
                if (response.isSuccessful) {
                    transactionDao.updateSyncStatus(entity.id, "synced")
                    if (applyToPast && normalized.isNotBlank()) {
                        transactionDao.markMerchantTransactionsSynced(normalized)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "applyCategoryToMerchant: offline, will sync later", e)
            }
        }
    }

    override fun observeUnreviewedTransactions(): kotlinx.coroutines.flow.Flow<List<Transaction>> =
        transactionDao.observeUnreviewedTransactions().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun dismissReview(transactionId: String): Result<Unit> = runCatching {
        val localId = transactionId.toLongOrNull()
        if (localId != null) {
            transactionDao.markReviewed(localId)
        } else {
            val entity = transactionDao.getByServerId(transactionId)
            if (entity != null) transactionDao.markReviewed(entity.id)
        }
    }

    override suspend fun markTransactionsRecurring(transactionIds: List<String>): Result<Unit> = runCatching {
        if (transactionIds.isEmpty()) return@runCatching
        val (serverIds, localIds) = transactionIds.partition { isServerObjectId(it) }
        if (serverIds.isNotEmpty()) {
            transactionDao.markRecurringByServerIds(serverIds)
        }
        val numericLocalIds = localIds.mapNotNull { it.toLongOrNull() }
        if (numericLocalIds.isNotEmpty()) {
            transactionDao.markRecurringByLocalIds(numericLocalIds)
        }
        SyncWorkScheduler.scheduleImmediateSync(context)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Safely determine if an ID string is a MongoDB ObjectID (24 hex chars)
     * rather than a local Room auto-increment ID. Replaces the brittle `id.length > 10` heuristic.
     */
    private fun isServerObjectId(id: String): Boolean = MONGO_OBJECT_ID_REGEX.matches(id)

    // ── Mappers ──────────────────────────────────────────────────────────

    private fun TransactionEntity.toDomain() = Transaction(
        id = serverId ?: id.toString(),
        clientId = clientId,
        amount = amount,
        type = type,
        category = category,
        description = description,
        merchant = merchant,
        normalizedMerchant = normalizedMerchant,
        date = date,
        source = source,
        categorySource = categorySource,
        isRecurring = isRecurring,
        isAnomaly = isAnomaly,
        splitDetails = splitDetailsJson?.let { gson.fromJson(it, splitType) } ?: emptyList(),
        sender = sender,
        method = method,
        timestamp = timestamp,
        upiId = upiId,
        referenceNumber = referenceNumber,
        accountLast4 = accountLast4,
        availableBalance = availableBalance,
        rawSms = rawSms,
        smsBodyDate = smsBodyDate,
        alias = alias,
        isReviewed = isReviewed
    )

    private fun Transaction.toEntity(
        clientId: String? = this.clientId,
        syncStatus: String = "synced"
    ) = TransactionEntity(
        serverId = if (isServerObjectId(id)) id else null,
        clientId = clientId,
        amount = amount,
        type = type,
        category = category,
        categorySource = categorySource,
        description = description,
        merchant = merchant,
        normalizedMerchant = normalizedMerchant,
        date = date,
        source = source,
        isRecurring = isRecurring,
        isAnomaly = isAnomaly,
        splitDetailsJson = if (splitDetails.isNotEmpty()) gson.toJson(splitDetails) else null,
        syncStatus = syncStatus,
        method = method,
        timestamp = timestamp,
        sender = sender,
        upiId = upiId,
        referenceNumber = referenceNumber,
        accountLast4 = accountLast4,
        availableBalance = availableBalance,
        rawSms = rawSms,
        smsBodyDate = smsBodyDate,
        alias = alias,
        isReviewed = isReviewed
    )

    private fun Transaction.toDto(clientId: String? = this.clientId) = TransactionDto(
        id = if (isServerObjectId(id)) id else null,
        amount = amount,
        category = category,
        type = type,
        description = description,
        merchant = merchant ?: "",
        normalizedMerchant = normalizedMerchant,
        categorySource = categorySource,
        date = date,
        source = source,
        clientId = clientId,
        isRecurring = isRecurring,
        isAnomaly = isAnomaly,
        splitDetails = splitDetails.map { it.toDto() },
        upiId = upiId,
        referenceNumber = referenceNumber,
        accountLast4 = accountLast4,
        availableBalance = availableBalance,
        rawSms = rawSms,
        smsBodyDate = smsBodyDate,
        sender = sender.ifBlank { null },
        alias = alias,
        isReviewed = isReviewed
    )

    private fun TransactionEntity.toDto() = TransactionDto(
        id = serverId,
        amount = amount,
        category = category,
        type = type,
        description = description,
        merchant = merchant ?: "",
        normalizedMerchant = normalizedMerchant,
        categorySource = categorySource,
        date = date,
        source = source,
        clientId = clientId,
        isRecurring = isRecurring,
        isAnomaly = isAnomaly,
        splitDetails = splitDetailsJson?.let {
            val splits: List<SplitDetail> = gson.fromJson(it, splitType)
            splits.map { s -> SplitDetailDto(s.personName, s.amount, s.settled) }
        },
        timestamp = timestamp,
        upiId = upiId,
        referenceNumber = referenceNumber,
        accountLast4 = accountLast4,
        availableBalance = availableBalance,
        rawSms = rawSms,
        smsBodyDate = smsBodyDate,
        sender = sender.ifBlank { null },
        alias = alias,
        isReviewed = isReviewed
    )

    private fun TransactionDto.toEntity() = TransactionEntity(
        serverId = id,
        amount = amount,
        type = type,
        category = category,
        categorySource = categorySource ?: "Default",
        description = description,
        merchant = merchant,
        normalizedMerchant = normalizedMerchant,
        date = date,
        source = source,
        isRecurring = isRecurring,
        isAnomaly = isAnomaly,
        syncStatus = "synced",
        splitDetailsJson = splitDetails?.let { gson.toJson(it.map { s -> SplitDetail(s.personName, s.amount, s.settled) }) },
        updatedAt = updatedAt,
        createdAt = createdAt,
        upiId = upiId,
        referenceNumber = referenceNumber,
        accountLast4 = accountLast4,
        availableBalance = availableBalance,
        rawSms = rawSms,
        smsBodyDate = smsBodyDate,
        sender = sender ?: "",
        alias = alias,
        isReviewed = isReviewed,
        timestamp = when {
            timestamp != null && timestamp > 0 -> timestamp
            date.contains('T') -> DateUtils.parseIsoDateToMillis(date)
            !createdAt.isNullOrBlank() -> DateUtils.parseIsoDateToMillis(createdAt)
            else -> DateUtils.parseIsoDateToMillis(date)
        }
    )

    private fun TransactionDto.toDomain() = Transaction(
        id = id ?: "",
        amount = amount,
        type = type,
        category = category,
        description = description,
        merchant = merchant,
        normalizedMerchant = normalizedMerchant,
        date = date,
        source = source,
        categorySource = categorySource ?: "Default",
        isRecurring = isRecurring,
        isAnomaly = isAnomaly,
        aiCategory = aiCategory,
        splitDetails = splitDetails?.map { it.toDomain() } ?: emptyList(),
        sender = sender ?: "",
        timestamp = when {
            timestamp != null && timestamp > 0 -> timestamp
            date.contains('T') -> DateUtils.parseIsoDateToMillis(date)
            !createdAt.isNullOrBlank() -> DateUtils.parseIsoDateToMillis(createdAt)
            else -> DateUtils.parseIsoDateToMillis(date)
        },
        upiId = upiId,
        referenceNumber = referenceNumber,
        accountLast4 = accountLast4,
        availableBalance = availableBalance,
        rawSms = rawSms,
        smsBodyDate = smsBodyDate,
        alias = alias,
        isReviewed = isReviewed
    )

    /**
     * Auto-heals existing Room DB anomalies:
     * 1. Fixes/removes false mandate entries (e.g. JioHotstar mandate limit ₹149 recorded instead of the ₹1 authentication offer debit, or reminder without payment).
     * 2. Deduplicates duplicate records (e.g. Swiggy 30 Sep duplicate entries).
     */
    private suspend fun cleanupDuplicatesAndInvalidSmsTransactions() {
        try {
            val all = transactionDao.getAllActiveTransactions()
            if (all.isEmpty()) return

            val toDelete = mutableListOf<Long>()

            // 1. Fix JioHotstar / Mandate false entries
            for (tx in all) {
                val raw = (tx.rawSms ?: "").lowercase(Locale.US)
                val merchant = (tx.merchant ?: tx.description).lowercase(Locale.US)
                val isHotstarOrJio = merchant.contains("hotstar") || merchant.contains("jio") || raw.contains("hotstar")

                if (isHotstarOrJio && tx.amount == 149.0) {
                    val isMandateSetup = raw.contains("mandate") || raw.contains("autopay") || raw.contains("standing instruction") || raw.contains("scheduled") || raw.contains("limit")
                    if (isMandateSetup) {
                        // Check if ₹1 was actually debited as auth/offer
                        val hasOneRupeeAuth = raw.contains("1.00 debited") || raw.contains("1 debited") || raw.contains("re 1") || raw.contains("rs 1") || raw.contains("rs. 1")
                        val isCancelledOrScheduled = raw.contains("cancelled") || raw.contains("canceled") || raw.contains("scheduled to") || raw.contains("maintain balance") || raw.contains("due on")
                        if (hasOneRupeeAuth && !isCancelledOrScheduled) {
                            // Update the amount to the actual ₹1 deduction
                            transactionDao.insert(tx.copy(amount = 1.0, description = "Disney+ Hotstar (Mandate Auth)"))
                        } else {
                            // No debit occurred or mandate was cancelled -> delete false expense
                            toDelete.add(tx.id)
                        }
                    }
                }
            }

            // 2. Remove duplicate transactions (same amount, same calendar day, same merchant)
            val groups = all.filter { it.id !in toDelete }.groupBy { tx ->
                val day = if (tx.date.length >= 10) tx.date.substring(0, 10) else ""
                val norm = tx.normalizedMerchant ?: MerchantNormalizer.normalize(tx.merchant ?: tx.description)
                Triple(tx.amount, day, norm)
            }

            for ((_, txList) in groups) {
                if (txList.size > 1) {
                    // Pick the best record to keep: prefer record with serverId != null, or valid non-midnight timestamp
                    val sorted = txList.sortedWith(
                        compareByDescending<TransactionEntity> { it.serverId != null }
                            .thenByDescending { !it.referenceNumber.isNullOrBlank() }
                            .thenByDescending { it.timestamp > 0 && (it.timestamp % 86400000L) != 0L }
                            .thenByDescending { it.id }
                    )
                    val winner = sorted.first()
                    for (candidate in sorted.drop(1)) {
                        val bothHaveRefs = !winner.referenceNumber.isNullOrBlank() && !candidate.referenceNumber.isNullOrBlank()
                        val distinctRefs = bothHaveRefs && !winner.referenceNumber.equals(candidate.referenceNumber, ignoreCase = true)
                        if (!distinctRefs) {
                            toDelete.add(candidate.id)
                        }
                    }
                }
            }

            if (toDelete.isNotEmpty()) {
                val uniqueDeleteIds = toDelete.distinct()
                transactionDao.deleteByIds(uniqueDeleteIds)
                Log.i(TAG, "Cleaned up ${uniqueDeleteIds.size} invalid/duplicate transactions from Room DB.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "cleanupDuplicatesAndInvalidSmsTransactions encountered non-fatal error", e)
        }
    }

    private fun SplitDetailDto.toDomain() = SplitDetail(personName, amount, settled)
    private fun SplitDetail.toDto() = SplitDetailDto(personName, amount, settled)

    private fun BudgetDto.toEntity() = BudgetEntity(
        serverId = id,
        category = category,
        limitAmount = limit,
        period = period,
        alertAt = alertAt,
        color = color,
        syncStatus = "synced",
        updatedAt = updatedAt,
        createdAt = createdAt
    )

    private fun GoalDto.toGoalEntity() = GoalEntity(
        serverId = id,
        title = title,
        targetAmount = targetAmount,
        currentAmount = currentAmount,
        deadline = deadline,
        category = category,
        icon = icon,
        color = color,
        isCompleted = isCompleted,
        completedAt = completedAt,
        syncStatus = "synced",
        updatedAt = updatedAt,
        createdAt = createdAt
    )

    override suspend fun scanReceipt(file: java.io.File): Result<ScannedReceiptDto> = withContext(Dispatchers.IO) {
        try {
            val reqBody = file.asRequestBody("image/*".toMediaTypeOrNull())
            val part = okhttp3.MultipartBody.Part.createFormData("receipt", file.name, reqBody)
            val response = transactionApi.scanReceipt(part)
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data ?: return@withContext Result.failure(Exception("Empty scan result"))
                Result.success(data)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Scan failed with HTTP ${response.code()}"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Receipt scan failed", e)
            Result.failure(e)
        }
    }

    override suspend fun importCsv(file: java.io.File): Result<CsvImportResultDto> = withContext(Dispatchers.IO) {
        try {
            val reqBody = file.asRequestBody("text/*".toMediaTypeOrNull())
            val part = okhttp3.MultipartBody.Part.createFormData("file", file.name, reqBody)
            val response = transactionApi.importCsv(part)
            if (response.isSuccessful && response.body()?.success == true) {
                val data = response.body()?.data ?: return@withContext Result.failure(Exception("Empty CSV import result"))
                refreshFromServer()
                triggerWidgetUpdate()
                Result.success(data)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "CSV import failed with HTTP ${response.code()}"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "CSV import failed", e)
            Result.failure(e)
        }
    }

    private suspend fun triggerWidgetUpdate() {
        try {
            com.app.presentation.widget.BudGiWidget.updateWidget(context)
        } catch (_: Exception) {}
    }
}
