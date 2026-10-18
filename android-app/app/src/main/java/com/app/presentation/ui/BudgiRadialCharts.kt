package com.app.presentation.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.data.local.entity.GoalEntity
import com.app.domain.model.AnalyticsSummary
import com.app.domain.model.MonthlySpend
import com.app.presentation.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.*

// ═════════════════════════════════════════════════════════════════════════════
// 1. INSIGHTS RADIAL RAY / CLOCK SPOKE FINANCIAL CALENDAR CHART (Reference Image 1)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * High-quality, interactive Radial Ray Spoke Chart matching reference Image 1:
 * - Top segmented pills: [Spending] [Income] [Net]
 * - 12 radial rays representing the 12 months (Jan–Dec) radiating from a dark central hub
 * - Concentric dashed guide circles
 * - Active selected month highlighted with high-contrast white rounded ray
 * - Top spending/income months highlighted with authentic warm amber, coral red, orange, and teal rays
 * - Smooth interactive touch drag scrubbing with haptic feedback
 * - Center circular hub displaying active metric ("SPENDING", "INCOME", "NET"), bold value, and period label
 */
@Composable
fun BudgiRadialRayClockChart(
    spendingData: List<MonthlySpend>,
    incomeData: List<MonthlySpend>,
    savingsData: List<MonthlySpend>,
    currencySymbol: String = "₹",
    modifier: Modifier = Modifier,
    initialTab: Int = 0 // 0=Spending, 1=Income, 2=Net
) {
    var selectedTab by remember { mutableIntStateOf(initialTab) } // 0=Spending, 1=Income, 2=Net
    val isDark = LocalIsDarkTheme.current
    val haptic = LocalHapticFeedback.current

    val currentMonthIndex = remember { (LocalDate.now().monthValue - 1).coerceIn(0, 11) }
    var selectedSpokeIndex by remember { mutableIntStateOf(currentMonthIndex) }

    // Active dataset based on selectedTab
    val activeMonthlyData = remember(selectedTab, spendingData, incomeData, savingsData) {
        val raw = when (selectedTab) {
            0 -> spendingData
            1 -> incomeData
            else -> savingsData
        }
        padTo12Months(raw)
    }

    val currentYear = remember { LocalDate.now().year }
    val tabTitle = when (selectedTab) {
        0 -> "SPENDING"
        1 -> "INCOME"
        else -> "NET"
    }

    val selectedItem = activeMonthlyData.getOrNull(selectedSpokeIndex)
    val displayAmount = selectedItem?.amount ?: 0.0
    val displayMonth = selectedItem?.monthLabel ?: getMonthName(selectedSpokeIndex)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (isDark) Color(0xFF111317) else MaterialTheme.colorScheme.surface)
            .border(
                width = 0.8.dp,
                color = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(vertical = 20.dp, horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── Top Segmented Pill Toggle: [Spending] [Income] [Net] ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (isDark) Color(0xFF1B1E24) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
                    .border(
                        0.8.dp,
                        if (isDark) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                        RoundedCornerShape(22.dp)
                    )
                    .padding(3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf("Spending", "Income", "Net").forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    val pillBg = if (isSelected) {
                        if (isDark) Color(0xFFF5F3EF) else MaterialTheme.colorScheme.primary
                    } else Color.Transparent

                    val textColor = if (isSelected) {
                        if (isDark) Color(0xFF121417) else Color.White
                    } else {
                        if (isDark) Color(0xFF9CA3AF) else MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(pillBg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedTab = index
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = textColor
                        )
                    }
                }
            }

            // ── Interactive Radial Ray Spoke Canvas with Centered Circular Hub ──
            Box(
                modifier = Modifier
                    .size(290.dp)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                RadialRayCanvas(
                    monthlyData = activeMonthlyData,
                    selectedIndex = selectedSpokeIndex,
                    onSpokeSelected = { idx ->
                        if (selectedSpokeIndex != idx) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedSpokeIndex = idx
                        }
                    },
                    isDark = isDark
                )

                // ── Central Dark Hub Overlay (SPENDING / AMOUNT / DATE) ──
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .size(118.dp)
                        .clip(CircleShape)
                        .clickable {
                            // Tap center hub resets to current active month
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedSpokeIndex = currentMonthIndex
                        }
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = tabTitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (isDark) Color(0xFF9CA3AF) else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "$currencySymbol${"%,.0f".format(displayAmount)}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "$displayMonth $currentYear",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (isDark) Color(0xFF9CA3AF) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Interactive Month Detail Badge & Navigation Hint ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color(0xFF181B22) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(
                        0.8.dp,
                        if (isDark) Color.White.copy(alpha = 0.06f) else Color.Transparent,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color.White else MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = "$displayMonth $currentYear: $currencySymbol${"%,.0f".format(displayAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Touch dial to scrub",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun RadialRayCanvas(
    monthlyData: List<MonthlySpend>,
    selectedIndex: Int,
    onSpokeSelected: (Int) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val maxVal = remember(monthlyData) {
        monthlyData.maxOfOrNull { it.amount }?.coerceAtLeast(100.0) ?: 100.0
    }

    // Curated accent colors for notable spending spokes matching Image 1
    val spokeColors = remember {
        listOf(
            Color(0xFFF59E0B), // Jan: Amber gold
            Color(0xFF78716C), // Feb: Taupe
            Color(0xFF6B7280), // Mar: Slate
            Color(0xFFD4D0C8), // Apr: Off-white
            Color(0xFF14B8A6), // May: Teal (Image 1 ~5 o'clock)
            Color(0xFF78716C), // Jun: Taupe
            Color(0xFFF97316), // Jul: Coral Orange (Image 1 ~7 o'clock)
            Color(0xFF78716C), // Aug: Taupe
            Color(0xFFEF4444), // Sep: Red-orange (Image 1 ~9 o'clock)
            Color(0xFF6B7280), // Oct: Slate
            Color(0xFFF59E0B), // Nov: Amber (Image 1 ~11 o'clock)
            Color(0xFF78716C)  // Dec: Taupe
        )
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(monthlyData) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val dist = sqrt(dx * dx + dy * dy)
                    val hubRadius = 55.dp.toPx()

                    if (dist >= hubRadius * 0.7f) {
                        // Calculate angle: 12 o'clock is -90° (index 0)
                        val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        val normalizedDeg = (deg + 90f + 360f) % 360f
                        val idx = ((normalizedDeg + 15f) / 30f).toInt() % 12
                        onSpokeSelected(idx)
                    }
                }
            }
            .pointerInput(monthlyData) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = change.position.x - center.x
                    val dy = change.position.y - center.y
                    val dist = sqrt(dx * dx + dy * dy)
                    val hubRadius = 55.dp.toPx()

                    if (dist >= hubRadius * 0.7f) {
                        val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                        val normalizedDeg = (deg + 90f + 360f) % 360f
                        val idx = ((normalizedDeg + 15f) / 30f).toInt() % 12
                        onSpokeSelected(idx)
                    }
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)
        val outerRadius = size.minDimension / 2f - 12.dp.toPx()
        val hubRadius = 58.dp.toPx()

        // 1. Concentric Subtle Dashed Guide Rings
        val guideColor = if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f)
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 7.dp.toPx()), 0f)

        val ring1R = hubRadius + (outerRadius - hubRadius) * 0.45f
        val ring2R = hubRadius + (outerRadius - hubRadius) * 0.82f

        drawCircle(
            color = guideColor,
            radius = ring1R,
            center = center,
            style = Stroke(width = 1.dp.toPx(), pathEffect = dashEffect)
        )
        drawCircle(
            color = guideColor,
            radius = ring2R,
            center = center,
            style = Stroke(width = 1.dp.toPx(), pathEffect = dashEffect)
        )

        // 2. 12 Radial Rays (Jan to Dec, 30° spacing)
        val minRayLen = 16.dp.toPx()
        val maxRayLen = outerRadius - hubRadius - 6.dp.toPx()
        val standardStroke = 9.5.dp.toPx()
        val selectedStroke = 11.5.dp.toPx()

        for (i in 0 until 12) {
            val angleDeg = i * 30f - 90f // 0 is Top (-90°)
            val rad = Math.toRadians(angleDeg.toDouble())
            val cosA = cos(rad).toFloat()
            val sinA = sin(rad).toFloat()

            val amt = monthlyData.getOrNull(i)?.amount ?: 0.0
            val isSelected = selectedIndex == i

            // Ray length dynamically scaled to amount
            val rayLen = if (amt > 0.0) {
                minRayLen + ((amt / maxVal) * (maxRayLen - minRayLen)).toFloat()
            } else {
                minRayLen * 0.75f
            }

            val startDist = hubRadius + 3.dp.toPx()
            val endDist = startDist + rayLen

            val startOffset = Offset(cx + startDist * cosA, cy + startDist * sinA)
            val endOffset = Offset(cx + endDist * cosA, cy + endDist * sinA)

            // Exact ray coloring matching Image 1
            val rayColor = when {
                isSelected -> if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
                amt <= 0.0 -> if (isDark) Color(0xFF3F3F46).copy(alpha = 0.45f) else Color(0xFFCBD5E1).copy(alpha = 0.5f)
                else -> spokeColors[i % spokeColors.size]
            }

            // Draw ray line with rounded ends
            drawLine(
                color = rayColor,
                start = startOffset,
                end = endOffset,
                strokeWidth = if (isSelected) selectedStroke else standardStroke,
                cap = StrokeCap.Round
            )
        }

        // 3. Central Hub Background & Edge Shadow
        val hubBg = if (isDark) Color(0xFF14161C) else Color(0xFFF8FAFC)
        val hubBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

        // Ambient dark shadow behind hub
        drawCircle(
            color = Color.Black.copy(alpha = if (isDark) 0.50f else 0.08f),
            radius = hubRadius + 2.dp.toPx(),
            center = center
        )
        // Hub body
        drawCircle(
            color = hubBg,
            radius = hubRadius,
            center = center
        )
        // Hub border
        drawCircle(
            color = hubBorder,
            radius = hubRadius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 2. CONCENTRIC MULTI-RING SAVINGS GOALS CHART (Reference Image 2)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * High-quality, interactive Concentric Multi-Ring Goals Chart matching reference Image 2:
 * - 4 concentric activity progress rings with rounded caps
 * - Palette: Warm Gold/Amber (Outer), Cyan Aqua, Emerald Green, Slate/Cream (Inner)
 * - Dark matte circular tracks under each ring
 * - Interactive: Tapping any ring or legend pill focuses on that goal, displaying its name,
 *   saved amount, and target in the central dark hub
 * - Center hub displays "SAVED", "$24,640" (or focused goal metrics) with tap-to-reset
 */
@Composable
fun BudgiConcentricGoalsChart(
    goals: List<GoalEntity>,
    currencySymbol: String = "₹",
    modifier: Modifier = Modifier,
    onAddNewGoal: () -> Unit = {}
) {
    val isDark = LocalIsDarkTheme.current
    val haptic = LocalHapticFeedback.current

    var focusedGoalIndex by remember { mutableStateOf<Int?>(null) }

    // Palette matching Image 2
    val ringColors = remember {
        listOf(
            Color(0xFFF59E0B), // Ring 0 (Outermost): Rich Amber Gold
            Color(0xFF06B6D4), // Ring 1: Vivid Cyan Aqua
            Color(0xFF10B981), // Ring 2: Emerald Green
            Color(0xFFD4D0C8)  // Ring 3 (Innermost): Warm Slate / Cream
        )
    }

    val activeGoals = remember(goals) {
        if (goals.isNotEmpty()) goals.take(4)
        else emptyList()
    }

    val totalSaved = remember(goals) { goals.sumOf { it.currentAmount } }
    val totalTarget = remember(goals) { goals.sumOf { it.targetAmount }.coerceAtLeast(1.0) }
    val overallPercentage = ((totalSaved / totalTarget) * 100).roundToInt().coerceIn(0, 100)

    // Current displayed metric in center hub
    val focusedGoal = focusedGoalIndex?.let { activeGoals.getOrNull(it) }
    val hubTitle = focusedGoal?.title?.uppercase() ?: "SAVED"
    val hubAmount = focusedGoal?.currentAmount ?: totalSaved
    val hubSubtitle = if (focusedGoal != null) {
        val pct = ((focusedGoal.currentAmount / focusedGoal.targetAmount.coerceAtLeast(1.0)) * 100).roundToInt()
        "Target $currencySymbol${"%,.0f".format(focusedGoal.targetAmount)} • $pct%"
    } else {
        "Total Saved • $overallPercentage%"
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (isDark) Color(0xFF111317) else MaterialTheme.colorScheme.surface)
            .border(
                width = 0.8.dp,
                color = if (isDark) Color.White.copy(alpha = 0.08f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(28.dp)
            )
            .padding(vertical = 20.dp, horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── Top Header: "< Goals" + "New" in Gold ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Goal Targets",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (goals.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "${goals.size} Active",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                        )
                    }
                }
            }

            // ── Concentric Rings Canvas with Centered Circular Hub ──
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                ConcentricRingsCanvas(
                    goals = activeGoals,
                    ringColors = ringColors,
                    focusedIndex = focusedGoalIndex,
                    onRingFocused = { idx ->
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        focusedGoalIndex = idx
                    },
                    isDark = isDark
                )

                // ── Central Dark Circular Hub Overlay ──
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .size(108.dp)
                        .clip(CircleShape)
                        .clickable {
                            // Tap center hub toggles between focused goal and total
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            focusedGoalIndex = null
                        }
                        .padding(horizontal = 6.dp)
                ) {
                    Text(
                        text = hubTitle,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            letterSpacing = 1.1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isDark) Color(0xFF9CA3AF) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "$currencySymbol${"%,.0f".format(hubAmount)}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = if (focusedGoalIndex != null) "Tap to reset" else "$overallPercentage% achieved",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (focusedGoalIndex != null) Color(0xFFF59E0B) else (if (isDark) Color(0xFF9CA3AF) else MaterialTheme.colorScheme.onSurfaceVariant)
                    )
                }
            }

            // ── Interactive Goal Chips Legend ──
            if (activeGoals.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeGoals.forEachIndexed { index, goal ->
                        val isFocused = focusedGoalIndex == index
                        val color = ringColors[index % ringColors.size]
                        val pct = ((goal.currentAmount / goal.targetAmount.coerceAtLeast(1.0)) * 100).roundToInt()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isFocused) color.copy(alpha = 0.16f)
                                    else if (isDark) Color(0xFF16181F)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                                .border(
                                    0.8.dp,
                                    if (isFocused) color.copy(alpha = 0.5f) else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    focusedGoalIndex = if (focusedGoalIndex == index) null else index
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                                Text(
                                    text = goal.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(goal.currentAmount)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = color
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "No active savings goals. Tap 'New Goal +' to start your first ring.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ConcentricRingsCanvas(
    goals: List<GoalEntity>,
    ringColors: List<Color>,
    focusedIndex: Int?,
    onRingFocused: (Int?) -> Unit,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val ringCount = 4
    val ringWidth = 13.5.dp
    val ringSpacing = 5.dp

    // Smooth animation on data/progress change
    val animatedProgress = List(ringCount) { i ->
        val goal = goals.getOrNull(i)
        val target = if (goal != null) {
            (goal.currentAmount / goal.targetAmount.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
        } else if (goals.isEmpty()) {
            // Friendly baseline starter arcs if user hasn't added goals yet
            when (i) {
                0 -> 0.70f
                1 -> 0.60f
                2 -> 0.35f
                else -> 0.50f
            }
        } else 0f

        animateFloatAsState(
            targetValue = target,
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
            label = "ring_progress_$i"
        ).value
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(goals) {
                detectTapGestures { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val dist = sqrt(dx * dx + dy * dy)
                    val hubRadius = 54.dp.toPx()

                    if (dist < hubRadius) {
                        onRingFocused(null)
                    } else {
                        val outerR = min(size.width, size.height).toFloat() / 2f - 14.dp.toPx()
                        val rwPx = ringWidth.toPx()
                        val spPx = ringSpacing.toPx()

                        var matched: Int? = null
                        for (i in 0 until ringCount) {
                            val r = outerR - i * (rwPx + spPx)
                            if (dist in (r - rwPx / 2f - 3.dp.toPx())..(r + rwPx / 2f + 3.dp.toPx())) {
                                matched = i
                                break
                            }
                        }
                        if (matched != null && matched < goals.size) {
                            onRingFocused(if (focusedIndex == matched) null else matched)
                        } else {
                            onRingFocused(null)
                        }
                    }
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val center = Offset(cx, cy)
        val rwPx = ringWidth.toPx()
        val spPx = ringSpacing.toPx()
        val outerR = size.minDimension / 2f - 14.dp.toPx()
        val hubRadius = 54.dp.toPx()

        val trackColor = if (isDark) Color(0xFF22242B) else Color(0xFFE2E8F0)

        for (i in 0 until ringCount) {
            val r = outerR - i * (rwPx + spPx)
            val arcSize = Size(r * 2, r * 2)
            val topLeft = Offset(cx - r, cy - r)

            val isRingActive = focusedIndex == null || focusedIndex == i
            val ringColor = ringColors[i % ringColors.size]
            val alpha = if (isRingActive) 1f else 0.28f

            // 1. Dark Matte Circular Background Track
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = rwPx, cap = StrokeCap.Round)
            )

            // 2. Foreground Progress Arc Starting at Top (-90°)
            val pct = animatedProgress[i]
            if (pct > 0f) {
                val sweep = (pct * 360f).coerceIn(2f, 360f)
                drawArc(
                    color = ringColor.copy(alpha = alpha),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = rwPx, cap = StrokeCap.Round)
                )
            }
        }

        // 3. Central Hub Background & Edge Shadow
        val hubBg = if (isDark) Color(0xFF14161C) else Color(0xFFF8FAFC)
        val hubBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

        drawCircle(
            color = Color.Black.copy(alpha = if (isDark) 0.50f else 0.08f),
            radius = hubRadius + 2.dp.toPx(),
            center = center
        )
        drawCircle(
            color = hubBg,
            radius = hubRadius,
            center = center
        )
        drawCircle(
            color = hubBorder,
            radius = hubRadius,
            center = center,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

// ── Helper Utilities ──────────────────────────────────────────────────────────

private fun padTo12Months(input: List<MonthlySpend>): List<MonthlySpend> {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val inputMap = input.associateBy { it.monthLabel.take(3) }

    return months.map { m ->
        inputMap[m] ?: MonthlySpend(monthLabel = m, amount = 0.0)
    }
}

private fun getMonthName(index: Int): String {
    val months = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    return months.getOrElse(index.coerceIn(0, 11)) { "July" }
}

// ═════════════════════════════════════════════════════════════════════════════
// 3. MONTHLY BUDGET INTERACTIVE RADIAL ARC GAUGE (Home Screen Hero Budget)
// ═════════════════════════════════════════════════════════════════════════════

enum class BudgetGaugeMode {
    USAGE,    // % used, spent / total
    RUNWAY,   // remaining amount, days left in month
    PACE      // daily safe limit, spent today
}

@Composable
fun BudgiMonthlyBudgetArcCard(
    spent: Double,
    total: Double,
    spentToday: Double = 0.0,
    safeToSpendDaily: Double = 0.0,
    dailyBurnRate: Double = 0.0,
    currencySymbol: String = "₹",
    onManageBudget: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current

    var activeMode by remember { mutableStateOf(BudgetGaugeMode.USAGE) }

    val progress = if (total > 0) (spent / total).toFloat().coerceIn(0f, 1.2f) else 0f
    val remaining = (total - spent).coerceAtLeast(0.0)

    val today = remember { LocalDate.now() }
    val daysInMonth = remember { YearMonth.now().lengthOfMonth() }
    val daysLeft = remember { (daysInMonth - today.dayOfMonth).coerceAtLeast(1) }

    val animatedSweep by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 380f),
        label = "budget_sweep"
    )

    val gaugeColor = when {
        progress >= 0.90f -> MaterialTheme.colorScheme.error
        progress >= 0.75f -> Color(0xFFF59E0B) // Amber warning
        else -> Color(0xFF38BDF8) // BudGi Signature Cyan
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 4.dp else 2.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Transparent
            ),
        shape = RoundedCornerShape(22.dp),
        color = if (isDark) Color(0xFF13161C) else Color(0xFFFFFFFF),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isDark) Color(0xFF222630) else Color.Black.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Monthly Budget",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Real-time burn & runway meter",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onManageBudget()
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF181C26) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isDark) Color(0xFF282D3C) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (total > 0) "Adjust" else "Set Budget",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            if (total <= 0.0) {
                // Empty State when no budget is defined
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No Monthly Budget Set",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Set a target budget to unlock safe daily allowance and runway alerts.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                // ── 220° Precision Radial Gauge Arch ──
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val trackBg = if (isDark) Color(0xFF1B1E2B) else Color(0xFFE2E8F0)
                    val dashedGuideColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                    val hubBg = if (isDark) Color(0xFF14161F) else Color(0xFFFFFFFF)
                    val hubBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    activeMode = when (activeMode) {
                                        BudgetGaugeMode.USAGE -> BudgetGaugeMode.RUNWAY
                                        BudgetGaugeMode.RUNWAY -> BudgetGaugeMode.PACE
                                        BudgetGaugeMode.PACE -> BudgetGaugeMode.USAGE
                                    }
                                }
                            }
                    ) {
                        val center = Offset(size.width / 2f, size.height * 0.58f)
                        val arcRadius = min(size.width, size.height) * 0.44f
                        val strokeWidth = 14.dp.toPx()

                        val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                        val arcSize = Size(arcRadius * 2f, arcRadius * 2f)

                        // 1. Concentric Outer Dashed Guide Arc
                        drawArc(
                            color = dashedGuideColor,
                            startAngle = 160f,
                            sweepAngle = 220f,
                            useCenter = false,
                            topLeft = Offset(center.x - arcRadius - 8.dp.toPx(), center.y - arcRadius - 8.dp.toPx()),
                            size = Size((arcRadius + 8.dp.toPx()) * 2f, (arcRadius + 8.dp.toPx()) * 2f),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 7f), 0f)
                            )
                        )

                        // 2. Recessed Gauge Track
                        drawArc(
                            color = trackBg,
                            startAngle = 160f,
                            sweepAngle = 220f,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // 3. Active Progress Arc
                        val currentSweep = (animatedSweep * 220f).coerceAtLeast(1f)
                        drawArc(
                            color = gaugeColor,
                            startAngle = 160f,
                            sweepAngle = currentSweep,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )

                        // 4. Central Circular Hub
                        val hubRadius = 46.dp.toPx()
                        drawCircle(
                            color = hubBg,
                            radius = hubRadius,
                            center = center
                        )
                        drawCircle(
                            color = hubBorder,
                            radius = hubRadius,
                            center = center,
                            style = Stroke(width = 0.8.dp.toPx())
                        )
                    }

                    // ── Content Inside Center Hub ──
                    Column(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                activeMode = when (activeMode) {
                                    BudgetGaugeMode.USAGE -> BudgetGaugeMode.RUNWAY
                                    BudgetGaugeMode.RUNWAY -> BudgetGaugeMode.PACE
                                    BudgetGaugeMode.PACE -> BudgetGaugeMode.USAGE
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        when (activeMode) {
                            BudgetGaugeMode.USAGE -> {
                                Text(
                                    text = "USED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp,
                                    color = gaugeColor
                                )
                                Text(
                                    text = "${(progress * 100).toInt()}%",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(spent)} / $currencySymbol${"%,.0f".format(total)}",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                                )
                            }
                            BudgetGaugeMode.RUNWAY -> {
                                Text(
                                    text = "REMAINING",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp,
                                    color = Color(0xFF10B981)
                                )
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(remaining)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "$daysLeft days left",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                                )
                            }
                            BudgetGaugeMode.PACE -> {
                                Text(
                                    text = "DAILY SAFE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.1.sp,
                                    color = Color(0xFF38BDF8)
                                )
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(safeToSpendDaily)}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Today: $currencySymbol${"%,.0f".format(spentToday)}",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                                )
                            }
                        }
                    }
                }

                // ── Interactive Perspective Filter Pills ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chipModifiers = Modifier.weight(1f)

                    BudgetModePill(
                        title = "Usage: ${(progress * 100).toInt()}%",
                        isSelected = activeMode == BudgetGaugeMode.USAGE,
                        accentColor = gaugeColor,
                        isDark = isDark,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeMode = BudgetGaugeMode.USAGE
                        },
                        modifier = chipModifiers
                    )

                    BudgetModePill(
                        title = "Runway: $daysLeft d",
                        isSelected = activeMode == BudgetGaugeMode.RUNWAY,
                        accentColor = Color(0xFF10B981),
                        isDark = isDark,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeMode = BudgetGaugeMode.RUNWAY
                        },
                        modifier = chipModifiers
                    )

                    BudgetModePill(
                        title = "Safe: $currencySymbol${"%,.0f".format(safeToSpendDaily)}/d",
                        isSelected = activeMode == BudgetGaugeMode.PACE,
                        accentColor = Color(0xFF38BDF8),
                        isDark = isDark,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            activeMode = BudgetGaugeMode.PACE
                        },
                        modifier = chipModifiers
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetModePill(
    title: String,
    isSelected: Boolean,
    accentColor: Color,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) {
            accentColor.copy(alpha = if (isDark) 0.16f else 0.10f)
        } else {
            if (isDark) Color(0xFF161924) else Color(0xFFF1F5F9)
        },
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isSelected) accentColor.copy(alpha = 0.50f) else Color.Transparent
        ),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 7.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 10.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) {
                    if (isDark) Color(0xFFF1F5F9) else accentColor
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
// 4. INTERACTIVE CATEGORY SPENDING RING CARD (Home Screen Spending Overview)
// ═════════════════════════════════════════════════════════════════════════════

@Composable
fun BudgiInteractiveSpendingRingCard(
    summary: AnalyticsSummary,
    onSeeInsights: () -> Unit,
    currencySymbol: String = "₹",
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current

    val categoryBreakdown = summary.categoryBreakdown
    val totalSpending = summary.monthlySpending

    var focusedCategory by remember { mutableStateOf<String?>(null) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 4.dp else 2.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.35f),
                spotColor = Color.Transparent
            ),
        shape = RoundedCornerShape(22.dp),
        color = if (isDark) Color(0xFF13161C) else Color(0xFFFFFFFF),
        border = androidx.compose.foundation.BorderStroke(
            0.8.dp,
            if (isDark) Color(0xFF222630) else Color.Black.copy(alpha = 0.08f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Spending Overview",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "This Month's Breakdown",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSeeInsights()
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF181C26) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isDark) Color(0xFF282D3C) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Analytics",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            if (categoryBreakdown.isEmpty() || totalSpending <= 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PieChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "No spending recorded this month",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val total = totalSpending.coerceAtLeast(1.0)
                val topCategories = remember(categoryBreakdown) {
                    val sorted = categoryBreakdown.entries.sortedByDescending { it.value }
                    val top4 = sorted.take(4)
                    val rest = sorted.drop(4)
                    val list = top4.map {
                        Triple(it.key, it.value, ((it.value / total) * 100).roundToInt())
                    }.toMutableList()
                    if (rest.isNotEmpty()) {
                        val otherSum = rest.sumOf { it.value }
                        list.add(Triple("Other", otherSum, ((otherSum / total) * 100).roundToInt()))
                    }
                    list
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // ── Interactive Segmented Ring Canvas ──
                    Box(
                        modifier = Modifier.size(136.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val trackColor = if (isDark) Color(0xFF181C26) else Color(0xFFE2E8F0)
                        val hubBg = if (isDark) Color(0xFF14161F) else Color(0xFFFFFFFF)
                        val hubBorder = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(topCategories) {
                                    detectTapGestures { offset ->
                                        val center = Offset(size.width / 2f, size.height / 2f)
                                        val dx = offset.x - center.x
                                        val dy = offset.y - center.y
                                        val dist = sqrt(dx * dx + dy * dy)
                                        val hubR = 44.dp.toPx()

                                        if (dist < hubR) {
                                            focusedCategory = null
                                        } else {
                                            val deg = (Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f + 360f) % 360f
                                            var currentAngle = 0f
                                            var matched: String? = null
                                            for ((cat, _, pct) in topCategories) {
                                                val sweep = (pct / 100f) * 360f
                                                if (deg >= currentAngle && deg <= currentAngle + sweep) {
                                                    matched = cat
                                                    break
                                                }
                                                currentAngle += sweep
                                            }
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            focusedCategory = if (focusedCategory == matched) null else matched
                                        }
                                    }
                                }
                        ) {
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val strokeWidth = 12.dp.toPx()
                            val arcRadius = (min(size.width, size.height) - strokeWidth - 10.dp.toPx()) / 2f

                            val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                            val arcSize = Size(arcRadius * 2f, arcRadius * 2f)

                            // Track
                            drawArc(
                                color = trackColor,
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )

                            // Category Segments
                            var currentAngle = -90f
                            topCategories.forEach { (catName, _, pct) ->
                                val sweep = (pct / 100f) * 360f
                                if (sweep > 0f) {
                                    val catColor = getCategoryColor(catName)
                                    val isFocused = focusedCategory == catName
                                    val isDimmed = focusedCategory != null && !isFocused

                                    val finalStroke = if (isFocused) strokeWidth + 4.dp.toPx() else strokeWidth
                                    val finalColor = if (isDimmed) catColor.copy(alpha = 0.35f) else catColor

                                    drawArc(
                                        color = finalColor,
                                        startAngle = currentAngle + 2f,
                                        sweepAngle = (sweep - 4f).coerceAtLeast(2f),
                                        useCenter = false,
                                        topLeft = if (isFocused) Offset(center.x - (arcRadius + 2.dp.toPx()), center.y - (arcRadius + 2.dp.toPx())) else arcTopLeft,
                                        size = if (isFocused) Size((arcRadius + 2.dp.toPx()) * 2f, (arcRadius + 2.dp.toPx()) * 2f) else arcSize,
                                        style = Stroke(width = finalStroke, cap = StrokeCap.Round)
                                    )
                                    currentAngle += sweep
                                }
                            }

                            // Center Hub
                            val hubR = 44.dp.toPx()
                            drawCircle(color = hubBg, radius = hubR, center = center)
                            drawCircle(color = hubBorder, radius = hubR, center = center, style = Stroke(width = 0.8.dp.toPx()))
                        }

                        // Center Content
                        val focusedItem = topCategories.find { it.first == focusedCategory }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            if (focusedItem != null) {
                                Text(
                                    text = focusedItem.first.take(9).uppercase(),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.8.sp,
                                    color = getCategoryColor(focusedItem.first),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(focusedItem.second)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "${focusedItem.third}%",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                                )
                            } else {
                                Text(
                                    text = "TOTAL",
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "$currencySymbol${"%,.0f".format(totalSpending)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "This Month",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }

                    // ── Category Legend Column ──
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        topCategories.forEach { (catName, _, pct) ->
                            val catColor = getCategoryColor(catName)
                            val isFocused = focusedCategory == catName

                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    focusedCategory = if (focusedCategory == catName) null else catName
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isFocused) {
                                    catColor.copy(alpha = if (isDark) 0.15f else 0.10f)
                                } else {
                                    if (isDark) Color(0xFF161922) else Color(0xFFF8FAFC)
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    0.8.dp,
                                    if (isFocused) catColor.copy(alpha = 0.45f) else Color.Transparent
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(catColor)
                                        )
                                        Text(
                                            text = catName,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Text(
                                        text = "$pct%",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isFocused) catColor else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
