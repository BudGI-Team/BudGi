package com.app.presentation.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.domain.model.AnalyticsSummary
import com.app.presentation.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * AI Insights & Trends Screen
 * Faithfully matches Screen 11 ("AI Insights") and Screen 12 ("Trends") from the design mockup.
 * Features:
 * - Tab pills: [Overview] (AI Insights) and [Trends] (Detailed trends & category spend)
 * - Smart Summary liquid glass card with glowing border and actionable AI recommendations
 * - Key Insights cards (Savings Growth, Top Spending Category, AI Suggestion)
 * - Interactive smooth cubic spline area chart with gradient fill and glowing data points
 * - Expense by Category progress bars with distinct category colors
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    windowSizeClass: WindowSizeClass,
    summary: AnalyticsSummary,
    onBack: (() -> Unit)? = null,
    currencySymbol: String = "₹"
) {
    // 0 = Overview, 1 = Trends
    var selectedMainTab by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = if (selectedMainTab == 0) "Financial Analytics" else "Trends",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Action",
                                tint = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = Dimens.bottomDockHeight + 24.dp)
        ) {
            // ── Tab Pills: [Overview] [Trends] ──
            item {
                val isDark = LocalIsDarkTheme.current
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(23.dp))
                        .background(if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .border(
                            width = 0.8.dp,
                            color = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(23.dp)
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    listOf("Overview", "Trends").forEachIndexed { index, title ->
                        val isSelected = selectedMainTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier.background(MaterialTheme.colorScheme.primary)
                                    } else Modifier
                                )
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selectedMainTab = index
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (selectedMainTab == 0) {
                // ══════════════════════════════════════════════════════
                // BudGi AI Insights Overview
                // ══════════════════════════════════════════════════════

                // ── Smart Summary Liquid Glass Card ──
                item {
                    SmartSummaryCard(summary = summary, currencySymbol = currencySymbol)
                }

                // ── Key Insights Header ──
                item {
                    Text(
                        text = "Key Insights",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                // ── Key Insights Items ──
                item {
                    KeyInsightsSection(summary = summary, currencySymbol = currencySymbol)
                }

                // ── Action Button: "+ View Detailed Report" ──
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    BudgiPrimaryPillButton(
                        text = "View Detailed Report",
                        onClick = {
                            selectedMainTab = 1 // Switch to Trends
                        },
                        icon = {
                            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                tint = if (isDark) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                    Spacer(modifier = Modifier.height(80.dp))
                }
            } else {
                // ══════════════════════════════════════════════════════
                // BudGi Financial Trends
                // ══════════════════════════════════════════════════════
                item {
                    TrendsContent(summary = summary, currencySymbol = currencySymbol)
                }
            }
        }
    }
}

// ── BudGi Smart Summary Card ──────────────────────────────────────────

@Composable
private fun SmartSummaryCard(summary: AnalyticsSummary, currencySymbol: String = "₹") {
    val isSpendingLower = summary.spendingChangePercentage <= 0
    val changePct = abs(summary.spendingChangePercentage).roundToInt()
    val savings = summary.estimatedSavings.coerceAtLeast(0.0)

    val bodyText = remember(summary, currencySymbol) {
        if (summary.monthlySpending == 0.0 && summary.spendingByMonth.isEmpty()) {
            "No expenses recorded for this period yet. Your budget is intact and your finances are on track."
        } else if (isSpendingLower) {
            if (savings > 0) {
                "You're on track with your budget! Your spending is $changePct% lower than last month, and you've saved $currencySymbol${"%,.0f".format(savings)} so far."
            } else {
                "You're on track with your budget! Your spending is $changePct% lower than last month."
            }
        } else {
            "Your spending is $changePct% higher than last month. Keep an eye on top categories to optimize your savings."
        }
    }

    val isDark = LocalIsDarkTheme.current
    val trendsCardBg = if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surface
    val trendsCardBorder = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(trendsCardBg)
            .border(
                width = 0.8.dp,
                color = trendsCardBorder,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = "Smart Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
        }
    }
}

// ── BudGi Key Insights List ───────────────────────────────────────────

@Composable
private fun KeyInsightsSection(summary: AnalyticsSummary, currencySymbol: String = "₹") {
    val topCategory = summary.categoryBreakdown.maxByOrNull { it.value }
    val topCatName = topCategory?.key ?: "Expenses"
    val totalExpense = summary.categoryBreakdown.values.sum().coerceAtLeast(1.0)
    val topCatPct = topCategory?.let { ((it.value / totalExpense) * 100).roundToInt() } ?: 0
    val actualSavingsRate = summary.savingsRate.roundToInt()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Insight 1: Savings Rate
        InsightPillCard(
            icon = Icons.AutoMirrored.Filled.TrendingUp,
            iconTint = CategoryColors.Groceries,
            iconBg = CategoryColors.Groceries.copy(alpha = 0.15f),
            title = "Savings Rate",
            subtitle = if (actualSavingsRate > 0) "$actualSavingsRate% of income saved this period" else if (summary.monthlyIncome > 0) "Expenses exceeded income this period" else "Track income to calculate savings rate"
        )

        // Insight 2: Top Spending Category
        if (topCategory != null && topCategory.value > 0) {
            val catColor = getCategoryColor(topCatName)
            InsightPillCard(
                icon = getCategoryIcon(topCatName),
                iconTint = catColor,
                iconBg = catColor.copy(alpha = 0.15f),
                title = "Top Spending Category",
                subtitle = "$topCatName ($currencySymbol${"%,.0f".format(topCategory.value)} • $topCatPct%)"
            )
        }

        // Insight 3: Smart Recommendation
        if (topCategory != null && topCategory.value > 0) {
            val suggestedSave = (topCategory.value * 0.15).coerceAtLeast(500.0)
            InsightPillCard(
                icon = Icons.Outlined.Lightbulb,
                iconTint = MaterialTheme.colorScheme.primary,
                iconBg = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                title = "Budget Optimization",
                subtitle = "Aim to trim $currencySymbol${"%,.0f".format(suggestedSave)} from $topCatName to grow savings"
            )
        }
    }
}

@Composable
private fun InsightPillCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String
) {
    val isDark = LocalIsDarkTheme.current
    val itemCardBg = if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surface
    val itemCardBorder = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(itemCardBg)
            .border(
                width = 0.8.dp,
                color = itemCardBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── BudGi Trends Screen Content ───────────────────────────────────────

@Composable
private fun TrendsContent(summary: AnalyticsSummary, currencySymbol: String = "₹") {
    var selectedTrendType by remember { mutableIntStateOf(0) } // 0=Spending, 1=Income, 2=Savings
    var selectedTimeframe by remember { mutableIntStateOf(0) } // 0=3M, 1=6M, 2=1Y, 3=All
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current

    val rawMonthlyData = remember(summary, selectedTrendType) {
        when (selectedTrendType) {
            0 -> summary.spendingByMonth
            1 -> summary.incomeByMonth
            else -> summary.savingsByMonth
        }
    }

    val filteredData = remember(rawMonthlyData, selectedTimeframe) {
        when (selectedTimeframe) {
            0 -> rawMonthlyData.takeLast(3)
            1 -> rawMonthlyData.takeLast(6)
            2 -> rawMonthlyData.takeLast(12)
            else -> rawMonthlyData
        }
    }

    val currentAmount = remember(filteredData, selectedTrendType, summary) {
        if (filteredData.isNotEmpty()) {
            filteredData.last().amount
        } else {
            when (selectedTrendType) {
                0 -> summary.monthlySpending
                1 -> summary.monthlyIncome
                else -> summary.estimatedSavings
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        // ── High-Quality Radial Ray Spoke Financial Clock Chart (Reference Image 1) ──
        BudgiRadialRayClockChart(
            spendingData = summary.spendingByMonth,
            incomeData = summary.incomeByMonth,
            savingsData = summary.savingsByMonth,
            currencySymbol = currencySymbol,
            initialTab = selectedTrendType
        )

        // ── Timeline & Detailed Metrics Section ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Historical Timeline",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Sub-tabs: [Spending] [Income] [Savings]
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf("Spend", "Income", "Net").forEachIndexed { index, name ->
                    val isSelected = selectedTrendType == index
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedTrendType = index
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Timeframe pills: [3M] [6M] [1Y] [All]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("3M", "6M", "1Y", "All").forEachIndexed { index, tf ->
                val isSelected = selectedTimeframe == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        )
                        .border(
                            width = 0.5.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.outlineVariant else Color.Transparent,
                            shape = RoundedCornerShape(17.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedTimeframe = index
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = tf,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val currentPeriodLabel = remember {
            java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMM yyyy"))
        }

        // Trend Area Chart Card
        TrendAreaChartCard(
            amount = currentAmount,
            periodLabel = currentPeriodLabel,
            monthlyData = filteredData,
            trendType = selectedTrendType,
            currencySymbol = currencySymbol
        )

        // Expense by Category Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Expense by Category",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.clickable { }
            ) {
                Text(
                    text = "See All",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Category Rows
        ExpenseByCategoryList(summary = summary, currencySymbol = currencySymbol)

        Spacer(modifier = Modifier.height(80.dp))
    }
}

// ── Smooth Spline Area Chart ──────────────────────────────────────────

@Composable
private fun TrendAreaChartCard(
    amount: Double,
    periodLabel: String,
    monthlyData: List<com.app.domain.model.MonthlySpend>,
    trendType: Int,
    currencySymbol: String = "₹"
) {
    val primaryColor = when (trendType) {
        0 -> MaterialTheme.colorScheme.primary
        1 -> CategoryColors.Groceries
        else -> Color(0xFF38BDF8)
    }
    val secondaryColor = primaryColor.copy(alpha = 0.65f)
    val haptic = LocalHapticFeedback.current

    val chartPoints: List<Float> = remember(monthlyData) {
        monthlyData.map { it.amount.toFloat() }
    }

    val months: List<String> = remember(monthlyData) {
        monthlyData.map { it.monthLabel }
    }

    val trendTypeName = when (trendType) {
        0 -> "Spending"
        1 -> "Income"
        else -> "Savings"
    }

    var activePointIndex by remember(chartPoints) { mutableStateOf<Int?>(null) }
    val displayIdx = activePointIndex ?: (chartPoints.size - 1).coerceAtLeast(0)
    val displayAmount = if (chartPoints.isNotEmpty()) chartPoints.getOrElse(displayIdx) { amount.toFloat() }.toDouble() else amount
    val displayLabel = if (months.isNotEmpty()) months.getOrElse(displayIdx) { periodLabel } else periodLabel

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .border(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(24.dp)
            )
            .padding(18.dp)
    ) {
        Column {
            // Interactive Amount badge tooltip
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (activePointIndex != null) primaryColor.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                    )
                    .border(
                        0.8.dp,
                        if (activePointIndex != null) primaryColor.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        if (activePointIndex != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activePointIndex = null
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$currencySymbol${"%,.0f".format(displayAmount)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "$displayLabel • $trendTypeName",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            fontWeight = FontWeight.Bold,
                            color = if (activePointIndex != null) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (activePointIndex != null) {
                            Text(
                                text = "• Tap to reset",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (chartPoints.size >= 2) {
                val pointCount = chartPoints.size
                val gridLineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)

                // Interactive Spline Canvas with Touch Scrubbing
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .pointerInput(pointCount) {
                            detectTapGestures { offset ->
                                val stepX = size.width / (pointCount - 1).coerceAtLeast(1)
                                val tappedIdx = (offset.x / stepX).roundToInt().coerceIn(0, pointCount - 1)
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activePointIndex = if (activePointIndex == tappedIdx) null else tappedIdx
                            }
                        }
                        .pointerInput(pointCount) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    val stepX = size.width / (pointCount - 1).coerceAtLeast(1)
                                    val idx = (offset.x / stepX).roundToInt().coerceIn(0, pointCount - 1)
                                    if (activePointIndex != idx) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        activePointIndex = idx
                                    }
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val stepX = size.width / (pointCount - 1).coerceAtLeast(1)
                                    val idx = (change.position.x / stepX).roundToInt().coerceIn(0, pointCount - 1)
                                    if (activePointIndex != idx) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        activePointIndex = idx
                                    }
                                }
                            )
                        }
                ) {
                    val w = size.width
                    val h = size.height - 24.dp.toPx()
                    val maxVal = chartPoints.maxOrNull()?.coerceAtLeast(1f) ?: 1f
                    val minVal = (chartPoints.minOrNull() ?: 0f) * 0.8f

                    // Draw subtle horizontal grid lines
                    drawLine(
                        color = gridLineColor,
                        start = Offset(0f, h * 0.5f),
                        end = Offset(w, h * 0.5f),
                        strokeWidth = 1.dp.toPx()
                    )

                    val stepX = w / (pointCount - 1)
                    val points = chartPoints.mapIndexed { idx, v ->
                        val normY = ((v - minVal) / (maxVal - minVal)).coerceIn(0.1f, 0.95f)
                        Offset(idx * stepX, h - (normY * h))
                    }

                    // Build smooth cubic path
                    val strokePath = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 0 until points.size - 1) {
                            val p0 = points[i]
                            val p1 = points[i + 1]
                            val cx1 = p0.x + (p1.x - p0.x) / 2f
                            val cy1 = p0.y
                            val cx2 = p0.x + (p1.x - p0.x) / 2f
                            val cy2 = p1.y
                            cubicTo(cx1, cy1, cx2, cy2, p1.x, p1.y)
                        }
                    }

                    // Fill path under curve
                    val fillPath = Path().apply {
                        addPath(strokePath)
                        lineTo(points.last().x, h)
                        lineTo(points.first().x, h)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.35f),
                                secondaryColor.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            startY = 0f,
                            endY = h
                        )
                    )

                    // Draw curve line
                    drawPath(
                        path = strokePath,
                        brush = Brush.horizontalGradient(
                            listOf(primaryColor, secondaryColor)
                        ),
                        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Active scrubbed point
                    val currentIdx = displayIdx.coerceIn(0, points.size - 1)
                    val hlPoint = points[currentIdx]

                    // Draw vertical guideline when actively scrubbing
                    if (activePointIndex != null) {
                        drawLine(
                            color = primaryColor.copy(alpha = 0.6f),
                            start = Offset(hlPoint.x, 0f),
                            end = Offset(hlPoint.x, h),
                            strokeWidth = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                        )
                    }

                    // Draw glowing highlight dot with animated outer aura
                    drawCircle(
                        color = (if (activePointIndex != null) primaryColor else secondaryColor).copy(alpha = 0.30f),
                        radius = (if (activePointIndex != null) 16.dp else 12.dp).toPx(),
                        center = hlPoint
                    )
                    drawCircle(
                        color = Color.White,
                        radius = (if (activePointIndex != null) 6.dp else 5.dp).toPx(),
                        center = hlPoint
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = 3.5.dp.toPx(),
                        center = hlPoint
                    )
                }

                // Month labels below with interactive selection pills
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    months.forEachIndexed { idx, m ->
                        val isSelected = displayIdx == idx
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected && activePointIndex != null) primaryColor.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    activePointIndex = if (activePointIndex == idx) null else idx
                                }
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = m,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                ),
                                color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else if (chartPoints.size == 1) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(primaryColor.copy(alpha = 0.14f))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Current Month: $currencySymbol${"%,.0f".format(displayAmount)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        }
                        Text(
                            text = "1 Month of $trendTypeName Data",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "As transactions span across multiple months, your curve will chart automatically.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            text = "No $trendTypeName trend data yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Monthly trends will be mapped here as transactions are recorded.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

// ── Expense by Category List ──────────────────────────────────────────

@Composable
private fun ExpenseByCategoryList(summary: AnalyticsSummary, currencySymbol: String = "₹") {
    val items: List<CategoryTrendItem> = remember(summary.categoryBreakdown) {
        if (summary.categoryBreakdown.isNotEmpty()) {
            val total = summary.categoryBreakdown.values.sum().coerceAtLeast(1.0)
            summary.categoryBreakdown.entries.map {
                CategoryTrendItem(
                    name = it.key,
                    amount = it.value,
                    percentage = ((it.value / total) * 100).roundToInt(),
                    color = getCategoryColor(it.key)
                )
            }.sortedByDescending { it.amount }
        } else {
            emptyList()
        }
    }

    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                .border(
                    0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                    RoundedCornerShape(18.dp)
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Category,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "No category expenses yet",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Category breakdown will appear here as transactions are logged.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items.forEach { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .border(
                            0.5.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                            RoundedCornerShape(18.dp)
                        )
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(item.color.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = getCategoryIcon(item.name),
                                        contentDescription = null,
                                        tint = item.color,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "${item.percentage}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Text(
                                text = "$currencySymbol${"%,.0f".format(item.amount)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        // Horizontal gradient progress bar
                        LiquidProgressIndicator(
                            progress = (item.percentage / 100f).coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp),
                            color = item.color,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

private data class CategoryTrendItem(
    val name: String,
    val amount: Double,
    val percentage: Int,
    val color: Color
)
