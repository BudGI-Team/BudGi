package com.app.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.R
import com.app.data.local.dao.NotificationDao
import com.app.data.local.entity.NotificationEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import com.app.di.ApplicationScope

/**
 * Helper for creating and showing local notifications.
 * Handles notification channels, permission checks, and event-based notifications.
 */
@Singleton
class NotificationHelper @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notificationDao: NotificationDao,
    @param:ApplicationScope private val scope: CoroutineScope
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createChannels()
    }

    /**
     * Create all notification channels (required for Android 8.0+).
     */
    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channels = listOf(
                NotificationChannel(
                    CHANNEL_BUDGET_ALERTS,
                    "Budget Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications when you exceed or approach budget limits"
                },
                NotificationChannel(
                    CHANNEL_GOALS,
                    "Savings Goals",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Goal progress updates and completion notifications"
                },
                NotificationChannel(
                    CHANNEL_AI_INSIGHTS,
                    "AI Insights",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Smart financial insights and weekly summaries from BudGi AI"
                },
                NotificationChannel(
                    CHANNEL_GENERAL,
                    "General",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Sync status, app updates, and general notifications"
                },
                NotificationChannel(
                    CHANNEL_TRANSACTIONS,
                    "Transactions",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Real-time updates on detected spends and income transactions"
                }
            )
            val manager = context.getSystemService(NotificationManager::class.java)
            channels.forEach { manager?.createNotificationChannel(it) }
        }
    }

    /**
     * Helper to create a PendingIntent opening MainActivity and optionally routing to a specific destination.
     */
    private fun getLaunchPendingIntent(destinationRoute: String? = null): android.app.PendingIntent {
        val intent = android.content.Intent(context, com.app.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (destinationRoute != null) {
                data = android.net.Uri.parse("budgi://$destinationRoute")
            }
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        return android.app.PendingIntent.getActivity(
            context,
            destinationRoute?.hashCode() ?: 0,
            intent,
            flags
        )
    }

    /**
     * Check if notification permission is granted (for Android 13+).
     */
    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }

    /**
     * Show a budget exceeded notification.
     */
    fun showBudgetExceeded(category: String, spent: Double, limit: Double) {
        if (!hasNotificationPermission()) return

        val percentUsed = if (limit > 0) ((spent / limit) * 100).toInt() else 100
        val title = "⚠️ Budget Exceeded: $category"
        val shortContent = "₹${"%,.0f".format(spent)} spent of ₹${"%,.0f".format(limit)} limit ($percentUsed% used)"
        val detailBody = "You've exceeded your monthly $category budget by ₹${"%,.0f".format(spent - limit)}.\nTotal spent: ₹${"%,.0f".format(spent)} / ₹${"%,.0f".format(limit)} ($percentUsed% used).\nTap to review category spending."

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_ALERTS)
            .setSmallIcon(R.drawable.budgi_logo)
            .setColor(BRAND_COLOR_ALERT)
            .setContentTitle(title)
            .setSubText("Budget Alert")
            .setContentText(shortContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailBody))
            .setContentIntent(getLaunchPendingIntent("budget"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        try {
            notificationManager.notify(NOTIFY_ID_BUDGET + category.hashCode(), notification)
            scope.launch {
                notificationDao.insert(
                    NotificationEntity(
                        icon = "⚠️",
                        title = title,
                        body = detailBody,
                        type = "budget_exceeded",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: SecurityException) { /* Permission revoked */ }
    }

    /**
     * Show a general alert notification.
     */
    fun showAlert(type: String, message: String) {
        if (!hasNotificationPermission()) return

        val (title, subText, channel, route, iconStr, colorInt) = when (type) {
            "budget_warning" -> Tuple6("⚠️ Budget Threshold Warning", "Budget Monitor", CHANNEL_BUDGET_ALERTS, "budget", "⚠️", BRAND_COLOR_WARNING)
            "budget_exceeded" -> Tuple6("⚠️ Monthly Budget Exceeded", "Budget Alert", CHANNEL_BUDGET_ALERTS, "budget", "⚠️", BRAND_COLOR_ALERT)
            "goal_completed" -> Tuple6("🎉 Goal Completed!", "Savings Milestone", CHANNEL_GOALS, "goals", "🎉", BRAND_COLOR_SUCCESS)
            "goal_reminder" -> Tuple6("🎯 Goal Progress Reminder", "Savings Goal", CHANNEL_GOALS, "goals", "🎯", BRAND_COLOR_PRIMARY)
            "ai_insight" -> Tuple6("💡 Smart Financial Insight", "BudGi Intelligence", CHANNEL_AI_INSIGHTS, "insights", "💡", BRAND_COLOR_PRIMARY)
            "weekly_summary" -> Tuple6("📊 Weekly Financial Summary", "BudGi Intelligence", CHANNEL_AI_INSIGHTS, "insights", "📊", BRAND_COLOR_PRIMARY)
            "smishing_alert" -> Tuple6("🚨 Potential Scam SMS Detected", "Fraud Guard", CHANNEL_BUDGET_ALERTS, "profile_security", "🚨", BRAND_COLOR_ALERT)
            else -> Tuple6("🔔 BudGi Notification", "BudGi", CHANNEL_GENERAL, "dashboard", "🔔", BRAND_COLOR_PRIMARY)
        }

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.budgi_logo)
            .setColor(colorInt)
            .setContentTitle(title)
            .setSubText(subText)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(getLaunchPendingIntent(route))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        try {
            notificationManager.notify(NOTIFY_ID_ALERT + type.hashCode() + message.hashCode(), notification)
            scope.launch {
                notificationDao.insert(
                    NotificationEntity(
                        icon = iconStr,
                        title = title,
                        body = message,
                        type = type,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: SecurityException) { /* Permission revoked */ }
    }

    /**
     * Show a transaction added notification with 1-tap interactive categorization actions.
     * For unknown/roadside vendors, provides lockscreen quick action buttons.
     * For learned merchants, displays breathing auto-categorized status with edit shortcut.
     */
    fun showTransactionAdded(
        merchant: String,
        amount: Double,
        type: String,
        transactionId: Long = 0L,
        upiId: String? = null,
        category: String = "Others",
        categorySource: String = "Rule",
        normalizedMerchant: String? = null
    ) {
        if (!hasNotificationPermission()) return

        val isExpense = type == "expense" || type == "debit"
        val formattedAmount = "₹${"%,.2f".format(amount)}"
        val cleanMerchant = com.app.data.sms.MerchantNormalizer.extractFriendlyName(merchant, upiId)
        val notifId = (NOTIFY_ID_TRANSACTION + (cleanMerchant.hashCode() and 0xFFFF) + (amount.toInt() and 0xFFFF))

        val builder = NotificationCompat.Builder(context, CHANNEL_TRANSACTIONS)
            .setSmallIcon(R.drawable.budgi_logo)
            .setColor(if (isExpense) BRAND_COLOR_PRIMARY else BRAND_COLOR_SUCCESS)
            .setAutoCancel(true)
            .setShowWhen(true)
            .setContentIntent(getLaunchPendingIntent("transactions"))

        if (isExpense) {
            val isLearned = categorySource.equals("UserLearned", ignoreCase = true)

            if (isLearned) {
                // Breathing, peaceful auto-categorized notification
                val title = "Spent $formattedAmount at $cleanMerchant"
                val subText = "Auto • $category"
                val body = "Recognized from past visits and categorized as $category."

                builder.setContentTitle(title)
                    .setSubText(subText)
                    .setContentText("Categorized as $category")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_LOW)

                // Quick Change action
                val editIntent = android.content.Intent(context, com.app.MainActivity::class.java).apply {
                    action = android.content.Intent.ACTION_VIEW
                    data = android.net.Uri.parse("budgi://transactions")
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val editPending = android.app.PendingIntent.getActivity(
                    context,
                    notifId + 10,
                    editIntent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.drawable.ic_notif_edit, "Change", editPending)

            } else {
                // Interactive 1-tap categorization actions for roadside / P2P payment
                val title = "Spent $formattedAmount at $cleanMerchant"
                val subText = "Tap to Categorize"
                val body = "Quick-tag this spend to keep your budget 100% accurate:"

                builder.setContentTitle(title)
                    .setSubText(subText)
                    .setContentText("Tap a category below")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setPriority(NotificationCompat.PRIORITY_HIGH)

                // Dynamic 3 quick actions based on Time of Day & Amount
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                data class QuickOption(val categoryName: String, val label: String, val iconRes: Int)

                val options = when {
                    hour in 6..11 -> listOf(
                        QuickOption("Food & Dining", "☕ Chai/Food", R.drawable.ic_notif_food),
                        QuickOption("Groceries", "🥦 Grocery", R.drawable.ic_notif_grocery),
                        QuickOption("Transport", "🛺 Travel", R.drawable.ic_notif_transit)
                    )
                    hour in 12..16 -> listOf(
                        QuickOption("Food & Dining", "🍽️ Food", R.drawable.ic_notif_food),
                        QuickOption("Groceries", "🥦 Grocery", R.drawable.ic_notif_grocery),
                        QuickOption("Shopping", "🛍️ Shopping", R.drawable.ic_notif_shopping)
                    )
                    hour in 17..20 -> listOf(
                        QuickOption("Food & Dining", "☕ Snacks", R.drawable.ic_notif_food),
                        QuickOption("Groceries", "🥦 Sabzi/Grocery", R.drawable.ic_notif_grocery),
                        QuickOption("Transport", "🛺 Travel", R.drawable.ic_notif_transit)
                    )
                    else -> listOf(
                        QuickOption("Food & Dining", "🍽️ Food", R.drawable.ic_notif_food),
                        QuickOption("Groceries", "🥦 Grocery", R.drawable.ic_notif_grocery),
                        QuickOption("Entertainment", "🍿 Fun", R.drawable.ic_notif_shopping)
                    )
                }

                for ((idx, opt) in options.withIndex()) {
                    val actionIntent = android.content.Intent(context, com.app.data.sms.NotificationActionReceiver::class.java).apply {
                        action = com.app.data.sms.NotificationActionReceiver.ACTION_QUICK_CATEGORIZE
                        putExtra(com.app.data.sms.NotificationActionReceiver.EXTRA_TX_ID, transactionId)
                        putExtra(com.app.data.sms.NotificationActionReceiver.EXTRA_CATEGORY, opt.categoryName)
                        putExtra(com.app.data.sms.NotificationActionReceiver.EXTRA_MERCHANT, cleanMerchant)
                        putExtra(com.app.data.sms.NotificationActionReceiver.EXTRA_UPI_ID, upiId)
                        putExtra(com.app.data.sms.NotificationActionReceiver.EXTRA_AMOUNT, amount)
                        putExtra(com.app.data.sms.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notifId)
                    }
                    val pending = android.app.PendingIntent.getBroadcast(
                        context,
                        notifId + (idx + 1) * 7,
                        actionIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(opt.iconRes, opt.label, pending)
                }
            }
        } else {
            // Income / Credit
            val title = "💰 Received $formattedAmount"
            val shortContent = "Credited from $cleanMerchant"
            val detailBody = "Received $formattedAmount from $cleanMerchant.\nCredited to your account and logged by BudGi."

            builder.setContentTitle(title)
                .setSubText("Income Logged")
                .setContentText(shortContent)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detailBody))
                .setPriority(NotificationCompat.PRIORITY_LOW)
        }

        val notification = builder.build()

        try {
            notificationManager.notify(notifId, notification)
            scope.launch {
                notificationDao.insert(
                    NotificationEntity(
                        icon = if (isExpense) "💸" else "💰",
                        title = if (isExpense) "Spent $formattedAmount" else "Received $formattedAmount",
                        body = "Spent $formattedAmount at $cleanMerchant",
                        type = "transaction",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: SecurityException) { /* Permission revoked */ }
    }

    /**
     * Show sync status notification.
     */
    fun showSyncComplete(transactionCount: Int) {
        if (!hasNotificationPermission() || transactionCount == 0) return

        val title = "✅ Sync Complete"
        val message = "$transactionCount new transactions synced and categorized"
        val detailBody = "Successfully synced $transactionCount new transactions. All your expenses, budgets, and accounts are up to date."

        val notification = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setSmallIcon(R.drawable.budgi_logo)
            .setColor(BRAND_COLOR_PRIMARY)
            .setContentTitle(title)
            .setSubText("BudGi Cloud Sync")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailBody))
            .setContentIntent(getLaunchPendingIntent("transactions"))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        try {
            notificationManager.notify(NOTIFY_ID_SYNC, notification)
            scope.launch {
                notificationDao.insert(
                    NotificationEntity(
                        icon = "✅",
                        title = title,
                        body = detailBody,
                        type = "sync",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: SecurityException) { /* Permission revoked */ }
    }

    /**
     * Show a Smishing/Scam alert notification.
     */
    fun showSmishingAlert(sender: String, reason: String) {
        if (!hasNotificationPermission()) return

        val title = "🚨 Potential Scam SMS Detected"
        val shortContent = "Suspicious message from $sender"
        val detailBody = "Sender: $sender\nRisk: $reason\nBudGi detected potential fraud and protected your account logs from unauthorized activity."

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_ALERTS)
            .setSmallIcon(R.drawable.budgi_logo)
            .setColor(BRAND_COLOR_ALERT)
            .setContentTitle(title)
            .setSubText("Security Shield")
            .setContentText(shortContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailBody))
            .setContentIntent(getLaunchPendingIntent("profile_security"))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        try {
            notificationManager.notify(NOTIFY_ID_ALERT + sender.hashCode(), notification)
            scope.launch {
                notificationDao.insert(
                    NotificationEntity(
                        icon = "🚨",
                        title = title,
                        body = detailBody,
                        type = "smishing_alert",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: SecurityException) { /* Permission revoked */ }
    }

    /**
     * Show an Anomaly/Unusual Spending alert notification.
     */
    fun showAnomalyAlert(merchant: String, amount: Double, category: String) {
        if (!hasNotificationPermission()) return

        val formattedAmount = "₹${"%,.0f".format(amount)}"
        val title = "📈 Unusual Spending Detected • $category"
        val shortContent = "$formattedAmount spent at $merchant is higher than usual"
        val detailBody = "You just spent $formattedAmount at $merchant in $category.\nThis is significantly higher than your typical average for this category.\nTap to review spending insights."

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_ALERTS)
            .setSmallIcon(R.drawable.budgi_logo)
            .setColor(BRAND_COLOR_WARNING)
            .setContentTitle(title)
            .setSubText("AI Spending Guard")
            .setContentText(shortContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailBody))
            .setContentIntent(getLaunchPendingIntent("insights"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        try {
            notificationManager.notify(NOTIFY_ID_ALERT + merchant.hashCode() + amount.toInt(), notification)
            scope.launch {
                notificationDao.insert(
                    NotificationEntity(
                        icon = "📈",
                        title = title,
                        body = detailBody,
                        type = "budget_warning",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: SecurityException) { /* Permission revoked */ }
    }

    /**
     * Show autonomous Payday Blueprint notification with lockscreen 1-tap apply action.
     */
    fun showPaydayBlueprintNotification(
        salaryAmount: Double,
        billsAmount: Double,
        livingAmount: Double,
        goalsAmount: Double,
        notificationId: Int = 8801
    ) {
        if (!hasNotificationPermission()) return

        val fmtSalary = "₹${"%,.0f".format(salaryAmount)}"
        val fmtBills = "₹${"%,.0f".format(billsAmount)}"
        val fmtLiving = "₹${"%,.0f".format(livingAmount)}"
        val fmtGoals = "₹${"%,.0f".format(goalsAmount)}"

        val title = "⚡ BudGi Sentry • Salary Detected ($fmtSalary)"
        val shortContent = "AI Blueprint ready: $fmtBills Bills • $fmtLiving Living • $fmtGoals Goals"
        val detailBody = "Salary of $fmtSalary detected.\nBudGi prepared an automated allocation blueprint:\n• Fixed Bills Runway: $fmtBills\n• Living Budgets: $fmtLiving\n• Savings Goals: $fmtGoals\n\nTap below to apply directly without manual budgeting."

        val applyIntent = android.content.Intent(context, com.app.data.sms.NotificationActionReceiver::class.java).apply {
            action = com.app.data.sms.NotificationActionReceiver.ACTION_APPLY_PAYDAY_BLUEPRINT
            putExtra("EXTRA_SALARY_AMOUNT", salaryAmount)
            putExtra("EXTRA_BILLS_AMOUNT", billsAmount)
            putExtra("EXTRA_LIVING_AMOUNT", livingAmount)
            putExtra("EXTRA_GOALS_AMOUNT", goalsAmount)
            putExtra("EXTRA_NOTIFICATION_ID", notificationId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val applyPendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            notificationId,
            applyIntent,
            flags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_AI_INSIGHTS)
            .setSmallIcon(R.drawable.budgi_logo)
            .setColor(BRAND_COLOR_PRIMARY)
            .setContentTitle(title)
            .setSubText("Payday Auto-Distributor")
            .setContentText(shortContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailBody))
            .setContentIntent(getLaunchPendingIntent("chat"))
            .addAction(R.drawable.ic_agent_lightning, "Apply Blueprint", applyPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            scope.launch {
                notificationDao.insert(
                    NotificationEntity(
                        icon = "⚡",
                        title = title,
                        body = detailBody,
                        type = "payday_blueprint",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: SecurityException) {}
    }

    /**
     * Show Subscription Price Hike / Creep warning notification.
     */
    fun showSubscriptionHikeNotification(
        merchant: String,
        oldAmount: Double,
        newAmount: Double,
        notificationId: Int
    ) {
        if (!hasNotificationPermission()) return

        val diff = newAmount - oldAmount
        val fmtOld = "₹${"%,.0f".format(oldAmount)}"
        val fmtNew = "₹${"%,.0f".format(newAmount)}"
        val fmtDiff = "₹${"%,.0f".format(diff)}"

        val title = "⚠️ Subscription Price Hike • $merchant"
        val shortContent = "Billed $fmtNew (+$fmtDiff increase vs $fmtOld last cycle)"
        val detailBody = "$merchant debited $fmtNew, which is $fmtDiff higher than your previous cycle ($fmtOld).\nReview your recurring plan or cancel zombie subscriptions."

        val notification = NotificationCompat.Builder(context, CHANNEL_BUDGET_ALERTS)
            .setSmallIcon(R.drawable.budgi_logo)
            .setColor(BRAND_COLOR_WARNING)
            .setContentTitle(title)
            .setSubText("Subscription Watchdog")
            .setContentText(shortContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailBody))
            .setContentIntent(getLaunchPendingIntent("budget"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            scope.launch {
                notificationDao.insert(
                    NotificationEntity(
                        icon = "⚠️",
                        title = title,
                        body = detailBody,
                        type = "price_hike",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: SecurityException) {}
    }

    /**
     * Show Friday Weekend Safe-to-Spend Radar notification.
     */
    fun showWeekendSafeToSpendNotification(
        safeWeekendSpend: Double,
        dailyPace: Double,
        notificationId: Int = 8803
    ) {
        if (!hasNotificationPermission()) return

        val fmtTotal = "₹${"%,.0f".format(safeWeekendSpend)}"
        val fmtDaily = "₹${"%,.0f".format(dailyPace)}"

        val title = "🎯 Weekend Runway • $fmtTotal Safe-to-Spend"
        val shortContent = "Pacing: $fmtDaily/day to keep all savings goals on track"
        val detailBody = "Happy Friday! After reserving funds for upcoming fixed commitments, you have $fmtTotal safe to spend this weekend ($fmtDaily/day).\nStay under this ceiling to protect your active financial goals."

        val notification = NotificationCompat.Builder(context, CHANNEL_AI_INSIGHTS)
            .setSmallIcon(R.drawable.budgi_logo)
            .setColor(BRAND_COLOR_SUCCESS)
            .setContentTitle(title)
            .setSubText("Safe-to-Spend Radar")
            .setContentText(shortContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailBody))
            .setContentIntent(getLaunchPendingIntent("chat"))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            scope.launch {
                notificationDao.insert(
                    NotificationEntity(
                        icon = "🎯",
                        title = title,
                        body = detailBody,
                        type = "weekend_radar",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: SecurityException) {}
    }

    /**
     * Show Shared Space Split Debt Auto-Reconciliation notification with 1-tap lockscreen action.
     */
    fun showDebtReconciliationNotification(
        spaceId: String,
        spaceName: String,
        memberId: String,
        memberName: String,
        amount: Double,
        txId: Long,
        notificationId: Int = 8804 + (spaceId.hashCode() % 100).coerceAtLeast(0)
    ) {
        if (!hasNotificationPermission()) return

        val fmtAmount = "₹${"%,.0f".format(amount)}"
        val title = "⚡ Split Reconciliation • $spaceName"
        val shortContent = "Received $fmtAmount from $memberName. Auto-settle shared debt?"
        val detailBody = "Incoming payment of $fmtAmount from $memberName detected.\nThis matches their pending balance in '$spaceName'.\nTap below to settle their split automatically."

        val settleIntent = android.content.Intent(context, com.app.data.sms.NotificationActionReceiver::class.java).apply {
            action = com.app.data.sms.NotificationActionReceiver.ACTION_SETTLE_SPLIT_DEBT
            putExtra(com.app.data.sms.NotificationActionReceiver.EXTRA_SPACE_ID, spaceId)
            putExtra(com.app.data.sms.NotificationActionReceiver.EXTRA_SPACE_NAME, spaceName)
            putExtra(com.app.data.sms.NotificationActionReceiver.EXTRA_MEMBER_ID, memberId)
            putExtra(com.app.data.sms.NotificationActionReceiver.EXTRA_MEMBER_NAME, memberName)
            putExtra(com.app.data.sms.NotificationActionReceiver.EXTRA_AMOUNT, amount)
            putExtra(com.app.data.sms.NotificationActionReceiver.EXTRA_TX_ID, txId)
            putExtra(com.app.data.sms.NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        } else {
            android.app.PendingIntent.FLAG_UPDATE_CURRENT
        }
        val settlePendingIntent = android.app.PendingIntent.getBroadcast(
            context,
            notificationId,
            settleIntent,
            flags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_AI_INSIGHTS)
            .setSmallIcon(R.drawable.budgi_logo)
            .setColor(BRAND_COLOR_PRIMARY)
            .setContentTitle(title)
            .setSubText("Space Auto-Reconciliation")
            .setContentText(shortContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailBody))
            .setContentIntent(getLaunchPendingIntent("spaces"))
            .addAction(R.drawable.ic_agent_lightning, "Settle $fmtAmount", settlePendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()

        try {
            notificationManager.notify(notificationId, notification)
            scope.launch {
                notificationDao.insert(
                    NotificationEntity(
                        icon = "⚡",
                        title = title,
                        body = detailBody,
                        type = "split_reconciliation",
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        } catch (_: SecurityException) {}
    }

    private data class Tuple6<A, B, C, D, E, F>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E,
        val sixth: F
    )

    companion object {
        const val CHANNEL_BUDGET_ALERTS = "budgi_budget_alerts"
        const val CHANNEL_GOALS = "budgi_goals"
        const val CHANNEL_AI_INSIGHTS = "budgi_insights"
        const val CHANNEL_GENERAL = "budgi_general"
        const val CHANNEL_TRANSACTIONS = "budgi_transactions"

        private const val NOTIFY_ID_BUDGET = 1000
        private const val NOTIFY_ID_ALERT = 2000
        private const val NOTIFY_ID_TRANSACTION = 3000
        private const val NOTIFY_ID_SYNC = 4000

        // Branded notification tint colors
        private const val BRAND_COLOR_PRIMARY = 0xFF0284C7.toInt()
        private const val BRAND_COLOR_SUCCESS = 0xFF10B981.toInt()
        private const val BRAND_COLOR_WARNING = 0xFFF59E0B.toInt()
        private const val BRAND_COLOR_ALERT = 0xFFEF4444.toInt()
    }
}
