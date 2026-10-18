package com.app.data.sms

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.app.data.local.dao.BudgetDao
import com.app.data.local.dao.CategoryDao
import com.app.data.local.dao.GoalDao
import com.app.data.local.dao.MerchantCategoryMappingDao
import com.app.data.local.dao.NotificationDao
import com.app.data.local.dao.SpaceDao
import com.app.data.local.dao.TransactionDao
import com.app.data.local.entity.BudgetEntity
import com.app.data.local.entity.CategoryEntity
import com.app.data.local.entity.GoalEntity
import com.app.data.local.entity.MerchantCategoryMappingEntity
import com.app.data.local.entity.NotificationEntity
import com.app.presentation.widget.BudGiWidget
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * High-performance background receiver for 1-tap interactive notification actions.
 * Allows users to categorize small roadside & P2P payments from the lockscreen/notification shade
 * in zero time without opening the app, and permanently learns the merchant for future visits.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface NotificationActionReceiverEntryPoint {
        fun transactionDao(): TransactionDao
        fun merchantCategoryMappingDao(): MerchantCategoryMappingDao
        fun notificationDao(): NotificationDao
        fun categoryDao(): CategoryDao
        fun budgetDao(): BudgetDao
        fun goalDao(): GoalDao
        fun spaceDao(): SpaceDao
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        if (action != ACTION_QUICK_CATEGORIZE && 
            action != ACTION_APPLY_PAYDAY_BLUEPRINT && 
            action != ACTION_SETTLE_SPLIT_DEBT) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    NotificationActionReceiverEntryPoint::class.java
                )

                if (action == ACTION_APPLY_PAYDAY_BLUEPRINT) {
                    val salaryAmount = intent.getDoubleExtra("EXTRA_SALARY_AMOUNT", 0.0)
                    val billsAmount = intent.getDoubleExtra("EXTRA_BILLS_AMOUNT", 0.0)
                    val livingAmount = intent.getDoubleExtra("EXTRA_LIVING_AMOUNT", 0.0)
                    val goalsAmount = intent.getDoubleExtra("EXTRA_GOALS_AMOUNT", 0.0)
                    val notifId = intent.getIntExtra("EXTRA_NOTIFICATION_ID", 8801)

                    val budgetDao = entryPoint.budgetDao()
                    val goalDao = entryPoint.goalDao()
                    val notificationDao = entryPoint.notificationDao()

                    // 1. Propose & Upsert Living Category Budgets
                    val allocations = mapOf(
                        "Food & Dining" to (livingAmount * 0.40),
                        "Shopping" to (livingAmount * 0.25),
                        "Transportation" to (livingAmount * 0.20),
                        "Personal Care" to (livingAmount * 0.15)
                    )

                    for ((cat, limit) in allocations) {
                        val roundedLimit = (Math.ceil(limit / 500.0) * 500.0).coerceAtLeast(1000.0)
                        val existing = budgetDao.getByCategory(cat)
                        if (existing != null) {
                            budgetDao.insertBudget(existing.copy(limitAmount = roundedLimit, syncStatus = "pending_update"))
                        } else {
                            budgetDao.insertBudget(BudgetEntity(category = cat, limitAmount = roundedLimit, period = "monthly", syncStatus = "pending_create"))
                        }
                    }

                    // 2. Fund Active Savings Goal if available
                    val goals = goalDao.observeAll().first().filter { !it.isCompleted }
                    if (goals.isNotEmpty() && goalsAmount > 0) {
                        val targetGoal = goals.first()
                        val updatedGoal = targetGoal.copy(
                            currentAmount = targetGoal.currentAmount + goalsAmount,
                            syncStatus = "pending_update",
                            updatedAt = Instant.now().toString()
                        )
                        goalDao.update(updatedGoal)
                    }

                    // 3. Post Confirmation Notification
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    if (notificationManager != null) {
                        val successNotification = NotificationCompat.Builder(context, "budgi_insights")
                            .setSmallIcon(com.app.R.drawable.budgi_logo)
                            .setColor(0xFF10B981.toInt())
                            .setContentTitle("✅ Payday Blueprint Applied")
                            .setContentText("₹${"%,.0f".format(salaryAmount)} allocated across Budgets, Bills & Goals")
                            .setSubText("BudGi Agent")
                            .setStyle(NotificationCompat.BigTextStyle().bigText(
                                "Your salary distribution blueprint was executed successfully:\n• Living Budgets: ₹${"%,.0f".format(livingAmount)} updated\n• Savings Goals: +₹${"%,.0f".format(goalsAmount)} funded\n• Fixed Bills Runway: ₹${"%,.0f".format(billsAmount)} protected."
                            ))
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                            .setAutoCancel(true)
                            .build()

                        notificationManager.notify(notifId, successNotification)
                    }

                    // 4. Update widget
                    BudGiWidget.updateWidget(context)
                    return@launch
                }

                if (action == ACTION_SETTLE_SPLIT_DEBT) {
                    val spaceId = intent.getStringExtra(EXTRA_SPACE_ID) ?: return@launch
                    val spaceName = intent.getStringExtra(EXTRA_SPACE_NAME) ?: "Shared Space"
                    val memberId = intent.getStringExtra(EXTRA_MEMBER_ID) ?: ""
                    val memberName = intent.getStringExtra(EXTRA_MEMBER_NAME) ?: "Member"
                    val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)
                    val txId = intent.getLongExtra(EXTRA_TX_ID, -1L)
                    val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 8804)

                    val spaceDao = entryPoint.spaceDao()
                    val localSpace = spaceDao.getSpaceById(spaceId)
                    if (localSpace != null) {
                        val gson = Gson()
                        val mapType = object : TypeToken<List<Map<String, Any>>>() {}.type
                        val expenses: MutableList<Map<String, Any>> = try {
                            gson.fromJson<List<Map<String, Any>>>(localSpace.expensesJson, mapType).toMutableList()
                        } catch (_: Exception) {
                            mutableListOf()
                        }
                        val dateStr = java.time.LocalDate.now().toString()
                        val settleExpense = mapOf(
                            "id" to "settle_${java.util.UUID.randomUUID().toString().take(10)}",
                            "description" to "$memberName paid (Auto-Reconciled)",
                            "amount" to amount,
                            "paidById" to memberId,
                            "paidByName" to memberName,
                            "splitWithMemberIds" to listOf("me"),
                            "category" to "Settlement",
                            "date" to dateStr,
                            "timestamp" to System.currentTimeMillis(),
                            "isSettlement" to true
                        )
                        expenses.add(0, settleExpense)
                        spaceDao.insertSpace(localSpace.copy(expensesJson = gson.toJson(expenses), syncStatus = "pending_update"))
                    }

                    if (txId > 0) {
                        val tx = entryPoint.transactionDao().getById(txId)
                        if (tx != null) {
                            entryPoint.transactionDao().insert(
                                tx.copy(
                                    description = if (tx.description.isBlank()) "Reconciled: $spaceName" else "${tx.description} (Reconciled: $spaceName)",
                                    isReviewed = true,
                                    syncStatus = "pending_update"
                                )
                            )
                        }
                    }

                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    if (notificationManager != null) {
                        val successNotification = NotificationCompat.Builder(context, "budgi_insights")
                            .setSmallIcon(com.app.R.drawable.budgi_logo)
                            .setColor(0xFF10B981.toInt())
                            .setContentTitle("✅ Split Settled • $spaceName")
                            .setContentText("₹${"%,.0f".format(amount)} from $memberName marked as settled.")
                            .setSubText("BudGi Agent")
                            .setPriority(NotificationCompat.PRIORITY_LOW)
                            .setAutoCancel(true)
                            .build()
                        notificationManager.notify(notifId, successNotification)

                        delay(2500)
                        notificationManager.cancel(notifId)
                    }

                    BudGiWidget.updateWidget(context)
                    return@launch
                }

                // ── ACTION_QUICK_CATEGORIZE ──
                val txId = intent.getLongExtra(EXTRA_TX_ID, -1L)
                val category = intent.getStringExtra(EXTRA_CATEGORY) ?: return@launch
                val merchant = intent.getStringExtra(EXTRA_MERCHANT) ?: "Merchant"
                val upiId = intent.getStringExtra(EXTRA_UPI_ID)
                val notifId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
                val amount = intent.getDoubleExtra(EXTRA_AMOUNT, 0.0)

                val transactionDao = entryPoint.transactionDao()
                val mappingDao = entryPoint.merchantCategoryMappingDao()
                val notificationDao = entryPoint.notificationDao()
                val categoryDao = entryPoint.categoryDao()

                // 1. Update the transaction in Room DB (mark isReviewed = 1)
                if (txId > 0) {
                    transactionDao.updateCategoryAndReview(txId, category)
                }

                // 2. Insert category into CategoryEntity if not already present
                try {
                    categoryDao.insertCategory(CategoryEntity(name = category.trim(), keywordsCsv = ""))
                } catch (_: Exception) {}

                // 3. Permanent Auto-Learning: Upsert into merchant_category_mappings
                val merchantKey = MerchantNormalizer.getBestKey(merchant, upiId)
                val normalized = MerchantNormalizer.normalize(merchantKey)
                val nowStr = Instant.now().toString()

                if (normalized.isNotBlank()) {
                    mappingDao.upsert(
                        MerchantCategoryMappingEntity(
                            merchantKey = merchantKey,
                            normalizedMerchant = normalized,
                            category = category,
                            alias = null,
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
                }

                // 4. Update the notification to a smooth, breathing confirmation
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                if (notificationManager != null && notifId > 0) {
                    val cleanMerchant = MerchantNormalizer.extractFriendlyName(merchant, upiId)
                    val formattedAmount = if (amount > 0) "₹${"%,.2f".format(amount)}" else ""
                    val successTitle = "✅ $cleanMerchant • Saved as $category"
                    val successBody = "Learned for future visits! All incoming payments to this vendor will auto-categorize as $category."

                    val successNotification = NotificationCompat.Builder(context, "budget_transactions")
                        .setSmallIcon(com.app.R.drawable.budgi_logo)
                        .setColor(0xFF10B981.toInt()) // Brand Emerald Green
                        .setContentTitle(successTitle)
                        .setContentText("Auto-learned for future payments")
                        .setSubText("BudGi • Memory Updated")
                        .setStyle(NotificationCompat.BigTextStyle().bigText(successBody))
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setAutoCancel(true)
                        .build()

                    notificationManager.notify(notifId, successNotification)

                    // Auto-dismiss after 2.5 seconds so notification shade stays completely clutter-free
                    delay(2500)
                    notificationManager.cancel(notifId)
                }

                // 5. Refresh home screen widget
                try {
                    BudGiWidget.updateWidget(context)
                } catch (_: Exception) {}

                // 6. Record notification history
                try {
                    notificationDao.insert(
                        NotificationEntity(
                            icon = "✅",
                            title = "Categorized: $category",
                            body = "$merchant ($category) learned and saved.",
                            type = "transaction",
                            timestamp = System.currentTimeMillis()
                        )
                    )
                } catch (_: Exception) {}

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_QUICK_CATEGORIZE = "com.app.ACTION_QUICK_CATEGORIZE"
        const val ACTION_APPLY_PAYDAY_BLUEPRINT = "com.app.action.APPLY_PAYDAY_BLUEPRINT"
        const val ACTION_SETTLE_SPLIT_DEBT = "com.app.action.SETTLE_SPLIT_DEBT"
        const val EXTRA_TX_ID = "extra_tx_id"
        const val EXTRA_CATEGORY = "extra_category"
        const val EXTRA_MERCHANT = "extra_merchant"
        const val EXTRA_UPI_ID = "extra_upi_id"
        const val EXTRA_AMOUNT = "extra_amount"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_SPACE_ID = "extra_space_id"
        const val EXTRA_SPACE_NAME = "extra_space_name"
        const val EXTRA_MEMBER_ID = "extra_member_id"
        const val EXTRA_MEMBER_NAME = "extra_member_name"
    }
}
