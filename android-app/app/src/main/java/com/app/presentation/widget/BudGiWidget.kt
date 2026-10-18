package com.app.presentation.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.app.MainActivity
import com.app.R
import com.app.data.local.dao.BudgetDao
import com.app.data.local.dao.TransactionDao
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Modern, High-End Home Screen Glance App Widget for BudGi.
 * Features:
 * - Dynamic real-time financial metrics from Room Database (Safe to spend, Today's spend)
 * - Responsive sizing (Compact 2x2 square and Wide 4x2 interactive banner)
 * - 1-tap quick action shortcuts (+ Add Expense, Sync SMS, AI Advisory)
 * - Consistent obsidian slate aesthetic with ambient accents
 */
class BudGiWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun transactionDao(): TransactionDao
        fun budgetDao(): BudgetDao
    }

    companion object {
        private val SMALL_SQUARE = DpSize(100.dp, 100.dp)
        private val WIDE_BANNER = DpSize(220.dp, 100.dp)

        // Palette matching BudGi Obsidian Slate design
        private val BG_CARD = Color(0xFF0D121D)
        private val BG_SUB_CARD = Color(0xFF161F2E)
        private val PRIMARY_BRAND = Color(0xFF38BDF8)
        private val ACCENT_CYAN = Color(0xFF38BDF8)
        private val ACCENT_EMERALD = Color(0xFF10B981)
        private val ACCENT_AMBER = Color(0xFFF59E0B)
        private val ACCENT_ROSE = Color(0xFFF43F5E)
        private val TEXT_SECONDARY = Color(0xFF9CA3AF)
        private val TEXT_MUTED = Color(0xFF6B7280)

        suspend fun updateWidget(context: Context) {
            try {
                BudGiWidget().updateAll(context)
            } catch (_: Exception) {
                // Widget might not be placed on home screen or Glance not initialized
            }
        }
    }

    override val sizeMode = SizeMode.Responsive(setOf(SMALL_SQUARE, WIDE_BANNER))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java
            )
        } catch (_: Exception) {
            null
        }

        val todayIso = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val monthYear = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))

        val spentToday = try {
            entryPoint?.transactionDao()?.getDailySpending(todayIso) ?: 0.0
        } catch (_: Exception) {
            0.0
        }

        val monthlySpent = try {
            entryPoint?.transactionDao()?.getMonthlySpending(monthYear) ?: 0.0
        } catch (_: Exception) {
            0.0
        }

        val monthlyIncome = try {
            entryPoint?.transactionDao()?.getMonthlyIncome(monthYear) ?: 0.0
        } catch (_: Exception) {
            0.0
        }

        val totalBudget = try {
            entryPoint?.budgetDao()?.getTotalBudgetLimit() ?: 0.0
        } catch (_: Exception) {
            0.0
        }

        val safeToSpend = if (totalBudget > 0) {
            (totalBudget - monthlySpent).coerceAtLeast(0.0)
        } else if (monthlyIncome > 0) {
            (monthlyIncome - monthlySpent).coerceAtLeast(0.0)
        } else {
            0.0
        }

        provideContent {
            val size = LocalSize.current
            val isWide = size.width >= 200.dp

            if (isWide) {
                WideBannerWidget(
                    context = context,
                    safeToSpend = safeToSpend,
                    spentToday = spentToday,
                    totalBudget = totalBudget
                )
            } else {
                CompactSquareWidget(
                    context = context,
                    safeToSpend = safeToSpend,
                    spentToday = spentToday
                )
            }
        }
    }

    @Composable
    private fun WideBannerWidget(
        context: Context,
        safeToSpend: Double,
        spentToday: Double,
        totalBudget: Double
    ) {
        val openAppAction = actionStartActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("budgi://dashboard")).apply {
                setClass(context, MainActivity::class.java)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )

        val addExpenseAction = actionStartActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("budgi://add_transaction")).apply {
                setClass(context, MainActivity::class.java)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )

        val syncSmsAction = actionStartActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("budgi://sync")).apply {
                setClass(context, MainActivity::class.java)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )

        val aiChatAction = actionStartActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("budgi://ai_chat")).apply {
                setClass(context, MainActivity::class.java)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )

        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(22.dp)
                .background(BG_CARD)
                .padding(14.dp)
                .clickable(openAppAction),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            // Left Column: Financial Status Metrics
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // Brand Header Row
                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Image(
                        provider = ImageProvider(R.drawable.budgi_logo),
                        contentDescription = "BudGi Logo",
                        modifier = GlanceModifier.size(20.dp)
                    )
                    Spacer(modifier = GlanceModifier.width(8.dp))
                    Text(
                        text = "BUDGI",
                        style = TextStyle(
                            color = ColorProvider(PRIMARY_BRAND),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = GlanceModifier.width(6.dp))
                    Box(
                        modifier = GlanceModifier
                            .size(6.dp)
                            .cornerRadius(3.dp)
                            .background(ACCENT_EMERALD)
                    ) {}
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                Text(
                    text = if (totalBudget > 0) "SAFE TO SPEND" else "NET CASH FLOW",
                    style = TextStyle(
                        color = ColorProvider(TEXT_SECONDARY),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Text(
                    text = "₹${"%,.0f".format(safeToSpend)}",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = GlanceModifier.height(4.dp))

                Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text(
                        text = "Today: ",
                        style = TextStyle(color = ColorProvider(TEXT_MUTED), fontSize = 11.sp)
                    )
                    Text(
                        text = if (spentToday > 0) "₹${"%,.0f".format(spentToday)}" else "₹0",
                        style = TextStyle(
                            color = ColorProvider(if (spentToday > 0) ACCENT_AMBER else ACCENT_EMERALD),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.width(12.dp))

            // Right Column: Interactive Quick Action Shortcuts
            Column(
                modifier = GlanceModifier.width(105.dp),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                // "+ Add Expense" Pill Button
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .cornerRadius(12.dp)
                        .background(Color.White)
                        .padding(horizontal = 8.dp)
                        .clickable(addExpenseAction),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                        Text(
                            text = "+ Add",
                            style = TextStyle(
                                color = ColorProvider(Color.Black),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // "⚡ Sync SMS" Pill Button
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .cornerRadius(10.dp)
                        .background(BG_SUB_CARD)
                        .padding(horizontal = 8.dp)
                        .clickable(syncSmsAction),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚡ Sync SMS",
                        style = TextStyle(
                            color = ColorProvider(ACCENT_CYAN),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }

                Spacer(modifier = GlanceModifier.height(6.dp))

                // "💡 BudGi AI" Pill Button
                Box(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .cornerRadius(10.dp)
                        .background(BG_SUB_CARD)
                        .padding(horizontal = 8.dp)
                        .clickable(aiChatAction),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "💡 Ask AI",
                        style = TextStyle(
                            color = ColorProvider(Color.White),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        }
    }

    @Composable
    private fun CompactSquareWidget(
        context: Context,
        safeToSpend: Double,
        spentToday: Double
    ) {
        val openAppAction = actionStartActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("budgi://dashboard")).apply {
                setClass(context, MainActivity::class.java)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )

        val addExpenseAction = actionStartActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("budgi://add_transaction")).apply {
                setClass(context, MainActivity::class.java)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(22.dp)
                .background(BG_CARD)
                .padding(14.dp)
                .clickable(openAppAction)
        ) {
            // Header Row: Logo & Brand
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Image(
                    provider = ImageProvider(R.drawable.budgi_logo),
                    contentDescription = "BudGi Logo",
                    modifier = GlanceModifier.size(20.dp)
                )
                Spacer(modifier = GlanceModifier.width(6.dp))
                Text(
                    text = "BUDGI",
                    style = TextStyle(
                        color = ColorProvider(PRIMARY_BRAND),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                // Quick + Button (monochrome pill matching Auth/Onboarding)
                Box(
                    modifier = GlanceModifier
                        .size(24.dp)
                        .cornerRadius(12.dp)
                        .background(Color.White)
                        .clickable(addExpenseAction),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "+",
                        style = TextStyle(
                            color = ColorProvider(Color.Black),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Metric
            Column {
                Text(
                    text = "Safe to Spend",
                    style = TextStyle(
                        color = ColorProvider(TEXT_SECONDARY),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    text = "₹${"%,.0f".format(safeToSpend)}",
                    style = TextStyle(
                        color = ColorProvider(Color.White),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.defaultWeight())

            // Footer Row: Spent Today
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                Text(
                    text = "Today:",
                    style = TextStyle(color = ColorProvider(TEXT_MUTED), fontSize = 11.sp)
                )
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = if (spentToday > 0) "₹${"%,.0f".format(spentToday)}" else "₹0",
                    style = TextStyle(
                        color = ColorProvider(if (spentToday > 0) ACCENT_AMBER else ACCENT_EMERALD),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
    }
}
