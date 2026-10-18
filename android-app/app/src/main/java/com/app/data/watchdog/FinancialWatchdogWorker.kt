package com.app.data.watchdog

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.data.local.dao.BudgetDao
import com.app.data.local.dao.GoalDao
import com.app.data.local.dao.NotificationDao
import com.app.data.local.dao.SpaceDao
import com.app.data.local.dao.TransactionDao
import com.app.utils.NotificationHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Autonomous Financial Watchdog Worker
 * Operates in the background on user's behalf to:
 * 1. Detect Salary credits and formulate the Payday Blueprint.
 * 2. Monitor Subscription Price Creep & Silent Hikes.
 * 3. Calculate Weekend Safe-to-Spend Radar on Friday afternoons.
 * 4. Cross-reference incoming UPI credits against pending Shared Space splits for 1-tap auto-reconciliation.
 */
class FinancialWatchdogWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WatchdogEntryPoint {
        fun transactionDao(): TransactionDao
        fun budgetDao(): BudgetDao
        fun goalDao(): GoalDao
        fun spaceDao(): SpaceDao
        fun notificationDao(): NotificationDao
        fun notificationHelper(): NotificationHelper
    }

    companion object {
        const val TAG = "FinancialWatchdogWorker"
        const val PREFS_WATCHDOG = "budgi_watchdog_prefs"
        const val KEY_LAST_SALARY_TX_ID = "last_salary_tx_id"
        const val KEY_LAST_RADAR_FRIDAY = "last_radar_friday"
        const val KEY_LAST_HIKE_CHECK = "last_hike_check"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting autonomous financial watchdog inspection...")
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                WatchdogEntryPoint::class.java
            )
            val txDao = entryPoint.transactionDao()
            val budgetDao = entryPoint.budgetDao()
            val goalDao = entryPoint.goalDao()
            val spaceDao = entryPoint.spaceDao()
            val notifHelper = entryPoint.notificationHelper()
            val prefs = applicationContext.getSharedPreferences(PREFS_WATCHDOG, Context.MODE_PRIVATE)

            // ── 1. Check for Payday Salary Credit ──────────────────────────────
            checkPaydayBlueprint(txDao, budgetDao, goalDao, notifHelper, prefs)

            // ── 2. Check for Subscription Price Creep ─────────────────────────
            checkSubscriptionPriceHikes(txDao, notifHelper, prefs)

            // ── 3. Check Weekend Safe-to-Spend Radar ──────────────────────────
            checkWeekendRadar(txDao, notifHelper, prefs)

            // ── 4. Check Shared Space Split Debt Auto-Reconciliation ──────────
            checkSharedSpaceDebtReconciliation(txDao, spaceDao, notifHelper, prefs)

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Watchdog execution failed", e)
            Result.failure()
        }
    }

    /**
     * Autonomous Sentry: Detects recent salary credit and prepares 1-tap distribution blueprint.
     */
    private suspend fun checkPaydayBlueprint(
        txDao: TransactionDao,
        budgetDao: BudgetDao,
        goalDao: GoalDao,
        notifHelper: NotificationHelper,
        prefs: android.content.SharedPreferences
    ) {
        val recentIncomes = txDao.getRecentExpensesByCategory("Salary", 5)
        // Also look for general credit transactions over ₹15,000 in last 48 hours
        val fortyEightHoursAgo = System.currentTimeMillis() - (48 * 60 * 60 * 1000L)
        val allRecentTxs = txDao.observeTransactions().first()
        val salaryTx = allRecentTxs.firstOrNull { tx ->
            (tx.type == "income" || tx.type == "credit") &&
            tx.timestamp >= fortyEightHoursAgo &&
            (tx.category.equals("Salary", ignoreCase = true) || tx.amount >= 20000.0 || tx.sender.contains("SALARY", ignoreCase = true))
        }

        if (salaryTx != null) {
            val lastProcessedSalaryId = prefs.getLong(KEY_LAST_SALARY_TX_ID, -1L)
            if (salaryTx.id != lastProcessedSalaryId) {
                // Calculate allocations:
                val salaryAmount = salaryTx.amount
                
                // Identify monthly fixed recurring bills
                val recurringTxs = allRecentTxs.filter { it.isRecurring && (it.type == "expense" || it.type == "debit") }
                val fixedBillsEstimate = recurringTxs.sumOf { it.amount }.coerceAtLeast(salaryAmount * 0.25)
                
                // Active goals allocation (20% of net or prorated target)
                val activeGoals = goalDao.observeAll().first().filter { !it.isCompleted }
                val goalsAllocation = (salaryAmount * 0.20).coerceAtMost(activeGoals.sumOf { (it.targetAmount - it.currentAmount).coerceAtLeast(0.0) })
                
                // Living budgets allocation
                val livingAllocation = (salaryAmount - fixedBillsEstimate - goalsAllocation).coerceAtLeast(salaryAmount * 0.40)

                notifHelper.showPaydayBlueprintNotification(
                    salaryAmount = salaryAmount,
                    billsAmount = fixedBillsEstimate,
                    livingAmount = livingAllocation,
                    goalsAmount = goalsAllocation,
                    notificationId = 8801
                )

                prefs.edit().putLong(KEY_LAST_SALARY_TX_ID, salaryTx.id).apply()
                Log.i(TAG, "Payday blueprint prepared and notified for salary ₹$salaryAmount")
            }
        }
    }

    /**
     * Autonomous Sentry: Flags when recurring merchant debits increase unexpectedly.
     */
    private suspend fun checkSubscriptionPriceHikes(
        txDao: TransactionDao,
        notifHelper: NotificationHelper,
        prefs: android.content.SharedPreferences
    ) {
        val now = System.currentTimeMillis()
        val lastCheck = prefs.getLong(KEY_LAST_HIKE_CHECK, 0L)
        // Run once every 24 hours
        if (now - lastCheck < 24 * 60 * 60 * 1000L) return

        val allTxs = txDao.observeTransactions().first()
            .filter { (it.type == "expense" || it.type == "debit") && it.isRecurring }
            .groupBy { it.normalizedMerchant.orEmpty().ifBlank { it.description.trim().lowercase() } }

        for ((merchant, txList) in allTxs) {
            if (merchant.isBlank() || txList.size < 2) continue
            val sorted = txList.sortedByDescending { it.timestamp }
            val latest = sorted[0]
            val previous = sorted[1]

            // Check if latest is within last 7 days and is higher by at least ₹50 and 5%
            val sevenDaysAgo = now - (7 * 24 * 60 * 60 * 1000L)
            if (latest.timestamp >= sevenDaysAgo && latest.amount > (previous.amount * 1.05) && (latest.amount - previous.amount) >= 49.0) {
                val hikeKey = "hike_${merchant}_${latest.timestamp}"
                if (!prefs.getBoolean(hikeKey, false)) {
                    val cleanMerchant = latest.merchant.orEmpty().ifBlank { latest.description }
                    notifHelper.showSubscriptionHikeNotification(
                        merchant = cleanMerchant,
                        oldAmount = previous.amount,
                        newAmount = latest.amount,
                        notificationId = 8802 + Math.abs(cleanMerchant.hashCode()) % 100
                    )
                    prefs.edit().putBoolean(hikeKey, true).apply()
                    Log.i(TAG, "Price hike flagged for $cleanMerchant: ₹${previous.amount} -> ₹${latest.amount}")
                }
            }
        }

        prefs.edit().putLong(KEY_LAST_HIKE_CHECK, now).apply()
    }

    /**
     * Autonomous Sentry: Friday Safe-to-Spend Radar.
     */
    private suspend fun checkWeekendRadar(
        txDao: TransactionDao,
        notifHelper: NotificationHelper,
        prefs: android.content.SharedPreferences
    ) {
        val today = LocalDate.now()
        if (today.dayOfWeek != DayOfWeek.FRIDAY) return

        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val lastRadarFriday = prefs.getString(KEY_LAST_RADAR_FRIDAY, null)
        if (todayStr == lastRadarFriday) return

        val allTxs = txDao.observeTransactions().first()
        val totalIncome = allTxs.filter { it.type == "income" || it.type == "credit" }.sumOf { it.amount }
        val totalExpense = allTxs.filter { it.type == "expense" || it.type == "debit" }.sumOf { it.amount }
        val balance = (totalIncome - totalExpense).coerceAtLeast(0.0)

        // Estimated weekend allowance (safe 3-day buffer)
        val daysLeft = (today.lengthOfMonth() - today.dayOfMonth).coerceAtLeast(3)
        val safeWeekendSpend = (balance / daysLeft * 3.0).coerceAtLeast(500.0)
        val dailyPace = safeWeekendSpend / 3.0

        notifHelper.showWeekendSafeToSpendNotification(
            safeWeekendSpend = safeWeekendSpend,
            dailyPace = dailyPace,
            notificationId = 8803
        )

        prefs.edit().putString(KEY_LAST_RADAR_FRIDAY, todayStr).apply()
        Log.i(TAG, "Weekend safe-to-spend radar emitted: ₹$safeWeekendSpend")
    }

    /**
     * Autonomous Sentry: Cross-reference recent incoming credits against pending Shared Space splits.
     */
    private suspend fun checkSharedSpaceDebtReconciliation(
        txDao: TransactionDao,
        spaceDao: SpaceDao,
        notifHelper: NotificationHelper,
        prefs: android.content.SharedPreferences
    ) {
        val spaces = spaceDao.getAllSpacesDirect()
        if (spaces.isEmpty()) return

        val twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L)
        val allTxs = txDao.observeTransactions().first()
        val recentCredits = allTxs.filter {
            (it.type == "credit" || it.type == "income") && it.timestamp >= twentyFourHoursAgo && it.amount > 0.0
        }
        if (recentCredits.isEmpty()) return

        val gson = Gson()
        val listMapType = object : TypeToken<List<Map<String, Any>>>() {}.type

        for (tx in recentCredits) {
            val processedKey = "reconciled_credit_${tx.id}"
            if (prefs.getBoolean(processedKey, false)) continue

            val candidateText = (tx.sender + " " + tx.merchant.orEmpty() + " " + tx.description).lowercase()

            for (space in spaces) {
                val members: List<Map<String, Any>> = try {
                    gson.fromJson(space.membersJson, listMapType) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }

                val expenses: List<Map<String, Any>> = try {
                    gson.fromJson(space.expensesJson, listMapType) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }

                for (member in members) {
                    val mId = member["id"] as? String ?: continue
                    val mName = (member["name"] as? String)?.trim() ?: continue
                    if (mName.isBlank() || mName.equals("You", ignoreCase = true) || mName.equals("me", ignoreCase = true)) continue

                    val cleanMemberFirstName = mName.split(" ").first().lowercase()
                    if (cleanMemberFirstName.length >= 3 && candidateText.contains(cleanMemberFirstName)) {
                        var memberOwedAmount = 0.0
                        for (exp in expenses) {
                            val isSettle = exp["isSettlement"] as? Boolean ?: false
                            val amt = (exp["amount"] as? Number)?.toDouble() ?: 0.0
                            val paidById = exp["paidById"] as? String ?: ""
                            val splitMembers = (exp["splitWithMemberIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

                            if (isSettle) {
                                if (paidById == mId) {
                                    memberOwedAmount -= amt
                                }
                            } else {
                                if (splitMembers.contains(mId) && paidById != mId) {
                                    val splitCount = if (splitMembers.isNotEmpty()) splitMembers.size else 1
                                    memberOwedAmount += (amt / splitCount)
                                }
                            }
                        }

                        if (memberOwedAmount > 0.0 && (Math.abs(tx.amount - memberOwedAmount) <= 50.0 || tx.amount <= memberOwedAmount * 1.1)) {
                            notifHelper.showDebtReconciliationNotification(
                                spaceId = space.id,
                                spaceName = space.name,
                                memberId = mId,
                                memberName = mName,
                                amount = tx.amount,
                                txId = tx.id
                            )
                            prefs.edit().putBoolean(processedKey, true).apply()
                            Log.i(TAG, "Reconciled credit detected: ₹${tx.amount} from $mName in space '${space.name}'")
                            break
                        }
                    }
                }
            }
        }
    }
}

