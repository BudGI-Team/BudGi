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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.domain.model.Budget
import com.app.presentation.ui.theme.*
import com.app.presentation.viewmodel.BudgetViewModel
import kotlin.math.roundToInt

/**
 * Budgets Screen
 * Faithfully matches Screen 10 ("Budgets") from the design mockup.
 * Features:
 * - Top bar: Back arrow + "Budgets" title + "+" add budget action
 * - Monthly Budget card with 71% circular progress meter and total spent / budget
 * - Tab pills: [Categories] and [Spending]
 * - Category budget cards with colored squircle badges, amounts, progress bars, percentages, and chevron
 * - Bottom full-width monochrome pill button: "+ Add Budget"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    windowSizeClass: WindowSizeClass,
    onBack: (() -> Unit)? = null,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Categories, 1 = Spending
    val haptic = LocalHapticFeedback.current

    var isButtonVisible by remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -8f) {
                    if (isButtonVisible) isButtonVisible = false
                } else if (available.y > 8f) {
                    if (!isButtonVisible) isButtonVisible = true
                }
                return Offset.Zero
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Budgets",
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
                        showAddDialog = true
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
                                contentDescription = "Add Budget",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = Dimens.bottomDockHeight + 76.dp)
            ) {
                // ── Monthly Budget Card with Circular Progress ──
                item {
                    MonthlyBudgetOverviewCard(
                        totalBudget = state.totalBudget,
                        totalSpent = state.totalSpent
                    )
                }

                item {
                    val isDark = LocalIsDarkTheme.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .border(
                                width = 0.8.dp,
                                color = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(22.dp)
                            )
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("Categories", "Spending").forEachIndexed { index, title ->
                            val isSelected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(18.dp))
                                    .then(
                                        if (isSelected) {
                                            Modifier.background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary,
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                                    )
                                                )
                                            )
                                        } else Modifier
                                    )
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
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // ── Autonomous Self-Healing Calibration Proposal ──
                state.calibrationProposal?.let { proposal ->
                    item {
                        SelfHealingBudgetCalibrationBanner(
                            proposal = proposal,
                            onApply = {
                                viewModel.rebalanceBudgets(
                                    proposal.fromCategory,
                                    proposal.toCategory,
                                    proposal.amount
                                )
                            },
                            onDismiss = {
                                viewModel.dismissCalibration(proposal)
                            }
                        )
                    }
                }

                // ── Category Budgets List ──
                if (state.isLoading && state.budgets.isEmpty()) {
                    item {
                        SkeletonList(count = 4)
                    }
                } else if (state.budgets.isNotEmpty()) {
                    val displayBudgets = if (selectedTab == 1) {
                        state.budgets.sortedByDescending { it.progress }
                    } else {
                        state.budgets
                    }
                    items(displayBudgets, key = { it.id }) { budget ->
                        CategoryBudgetCard(
                            budget = budget,
                            onDelete = { viewModel.deleteBudget(budget.id) }
                        )
                    }
                } else {
                    item {
                        EmptyBudgetsCard(onAddClick = { showAddDialog = true })
                    }
                }
            }

            // ── Floating Bottom Button: "+ Add Budget" (Scroll-aware) ──
            AnimatedVisibility(
                visible = isButtonVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f)
                ) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, bottom = Dimens.bottomDockHeight + 12.dp)
            ) {
                BudgiPrimaryPillButton(
                    text = "Add Budget",
                    onClick = {
                        showAddDialog = true
                    },
                    icon = {
                        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = if (isDark) Color.Black else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                )
            }
        }
    }

    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()
    if (showAddDialog) {
        AddBudgetDialog(
            customCategories = customCategories.map { it.name },
            onAddCustomCategory = viewModel::addCustomCategory,
            onDismiss = { showAddDialog = false },
            onConfirm = { category, limit ->
                viewModel.addOrUpdateBudget(category, limit)
                showAddDialog = false
            }
        )
    }
}

// ── BudGi Monthly Budget Card with Circular Meter ─────────────────────

@Composable
private fun MonthlyBudgetOverviewCard(totalBudget: Double, totalSpent: Double) {
    val hasBudget = totalBudget > 0.0
    val displayBudget = if (hasBudget) totalBudget else 0.0
    val displaySpent = totalSpent
    val progress = if (hasBudget) (displaySpent / displayBudget).toFloat().coerceIn(0f, 1f) else 0f
    val percentage = (progress * 100).roundToInt()

    val primaryColor = MaterialTheme.colorScheme.primary
    val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.70f)

    val isDark = LocalIsDarkTheme.current
    val cardBg = if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val cardBorder = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(cardBg)
            .border(
                width = 0.8.dp,
                color = cardBorder,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = "Monthly Budget",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "₹ ${"%,.0f".format(displaySpent)}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (hasBudget) "/ ₹ ${"%,.0f".format(displayBudget)}" else "/ No limit set",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            // Circular Progress Meter
            Box(
                modifier = Modifier.size(68.dp),
                contentAlignment = Alignment.Center
            ) {
                val trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 7.dp.toPx()
                    // Track
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    // Progress
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(primaryColor, accentColor, primaryColor)
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

// ── BudGi Category Budget Card ────────────────────────────────────────

@Composable
private fun CategoryBudgetCard(
    budget: Budget,
    onDelete: () -> Unit
) {
    val progress = (budget.spentAmount / budget.effectiveLimit.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val percentage = (progress * 100).roundToInt()
    val categoryColor = getCategoryColor(budget.category)
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val isDark = LocalIsDarkTheme.current
    val cardBg = if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val cardBorder = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(
                0.8.dp,
                cardBorder,
                RoundedCornerShape(20.dp)
            )
            .clickable { showDeleteConfirm = true }
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(categoryColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getCategoryIcon(budget.category),
                            contentDescription = null,
                            tint = categoryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = budget.category,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "₹ ${"%,.0f".format(budget.spentAmount)} / ₹ ${"%,.0f".format(budget.effectiveLimit)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Liquid progress bar
            LiquidProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = categoryColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Budget", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Are you sure you want to remove the budget for ${budget.category}?", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyBudgetsCard(onAddClick: () -> Unit) {
    val isDark = LocalIsDarkTheme.current
    val cardBg = if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val cardBorder = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(
                0.8.dp,
                cardBorder,
                RoundedCornerShape(20.dp)
            )
            .clickable { onAddClick() }
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = "No Budgets Set Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Tap here or '+ Add Budget' below to set category limits and keep your spending on track.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

// ── Add Budget Dialog (Liquid Glass Design) ──────────────────────────
data class BudgetCategoryOption(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val lightBg: Color,
    val darkBg: Color,
    val tint: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBudgetDialog(
    customCategories: List<String> = emptyList(),
    onAddCustomCategory: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (String, Double) -> Unit
) {
    var selectedCategory by rememberSaveable { mutableStateOf("Food & Dining") }
    var amount by rememberSaveable { mutableStateOf("") }
    var selectedPeriod by rememberSaveable { mutableStateOf("This Month") }
    var periodExpanded by remember { mutableStateOf(false) }
    var showCustomInput by rememberSaveable { mutableStateOf(false) }
    var customCategoryText by rememberSaveable { mutableStateOf("") }
    val haptic = LocalHapticFeedback.current

    val categories = remember(customCategories, selectedCategory) {
        val base = mutableListOf(
            BudgetCategoryOption(
                title = "Food & Dining",
                icon = Icons.Default.Restaurant,
                lightBg = CategoryColors.Food.copy(alpha = 0.12f),
                darkBg = CategoryColors.Food.copy(alpha = 0.18f),
                tint = CategoryColors.Food
            ),
            BudgetCategoryOption(
                title = "Transport",
                icon = Icons.Default.DirectionsCar,
                lightBg = CategoryColors.Transport.copy(alpha = 0.12f),
                darkBg = CategoryColors.Transport.copy(alpha = 0.18f),
                tint = CategoryColors.Transport
            ),
            BudgetCategoryOption(
                title = "Shopping",
                icon = Icons.Default.ShoppingBag,
                lightBg = CategoryColors.Shopping.copy(alpha = 0.12f),
                darkBg = CategoryColors.Shopping.copy(alpha = 0.18f),
                tint = CategoryColors.Shopping
            ),
            BudgetCategoryOption(
                title = "Entertainment",
                icon = Icons.Outlined.SportsEsports,
                lightBg = CategoryColors.Entertainment.copy(alpha = 0.12f),
                darkBg = CategoryColors.Entertainment.copy(alpha = 0.18f),
                tint = CategoryColors.Entertainment
            ),
            BudgetCategoryOption(
                title = "Health & Fitness",
                icon = Icons.Default.Favorite,
                lightBg = CategoryColors.Health.copy(alpha = 0.12f),
                darkBg = CategoryColors.Health.copy(alpha = 0.18f),
                tint = CategoryColors.Health
            ),
            BudgetCategoryOption(
                title = "Others",
                icon = Icons.Default.MoreHoriz,
                lightBg = CategoryColors.Others.copy(alpha = 0.12f),
                darkBg = CategoryColors.Others.copy(alpha = 0.18f),
                tint = CategoryColors.Others
            )
        )

        customCategories.forEach { catName ->
            if (catName.isNotBlank() && base.none { it.title.equals(catName, ignoreCase = true) }) {
                val color = getCategoryColor(catName)
                val icon = getCategoryIcon(catName)
                base.add(
                    BudgetCategoryOption(
                        title = catName.trim(),
                        icon = icon,
                        lightBg = color.copy(alpha = 0.12f),
                        darkBg = color.copy(alpha = 0.18f),
                        tint = color
                    )
                )
            }
        }

        if (selectedCategory.isNotBlank() && base.none { it.title.equals(selectedCategory, ignoreCase = true) }) {
            val color = getCategoryColor(selectedCategory)
            val icon = getCategoryIcon(selectedCategory)
            base.add(
                BudgetCategoryOption(
                    title = selectedCategory.trim(),
                    icon = icon,
                    lightBg = color.copy(alpha = 0.12f),
                    darkBg = color.copy(alpha = 0.18f),
                    tint = color
                )
            )
        }
        base
    }

    val periods = listOf("This Month", "Next Month", "Quarterly", "Annual")
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Subtle ambient glows
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .offset(x = (-60).dp, y = (-40).dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                Color.Transparent
                            )
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .size(320.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 80.dp, y = 60.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
            )

            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(12.dp))

                // Top Bar with Back icon and Wallet illustration
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // 3D Liquid Wallet Icon Badge
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                RoundedCornerShape(20.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountBalanceWallet,
                            contentDescription = "Budget Wallet",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Title & Subtitle
                Text(
                    text = "Add Budget",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Set your monthly budget and stay in control of your finances.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(26.dp))

                // Section: Budget Category
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Budget Category",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(
                        onClick = { showCustomInput = !showCustomInput },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (showCustomInput) "Close" else "+ Custom",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Inline Custom Category Input
                AnimatedVisibility(visible = showCustomInput) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    RoundedCornerShape(16.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                androidx.compose.foundation.text.BasicTextField(
                                    value = customCategoryText,
                                    onValueChange = { customCategoryText = it },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        if (customCategoryText.isEmpty()) {
                                            Text(
                                                text = "e.g. Rent, Groceries, Gym, Tuition...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                        inner()
                                    }
                                )
                                Button(
                                    onClick = {
                                        val trimmed = customCategoryText.trim()
                                        if (trimmed.isNotBlank()) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedCategory = trimmed
                                            onAddCustomCategory(trimmed)
                                            showCustomInput = false
                                            customCategoryText = ""
                                        }
                                    },
                                    shape = RoundedCornerShape(BudgiRadius.Pill),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(34.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text("Add", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // 2x3 Grid of Categories
                val chunked = categories.chunked(3)
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    chunked.forEach { rowCategories ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            rowCategories.forEach { option ->
                                val isSelected = selectedCategory == option.title
                                val cardBg = if (isDark) option.darkBg else option.lightBg

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(86.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            if (isSelected) cardBg else MaterialTheme.colorScheme.surface
                                        )
                                        .border(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) option.tint else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                            shape = RoundedCornerShape(18.dp)
                                        )
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedCategory = option.title
                                        }
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(cardBg),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = option.icon,
                                                contentDescription = option.title,
                                                tint = option.tint,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = option.title,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) option.tint else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Section: Monthly Budget Amount
                Text(
                    text = "Monthly Budget Amount",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Amount Pill Input Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "₹",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = amount,
                            onValueChange = { input ->
                                val clean = input.filter { it.isDigit() }
                                amount = clean
                            },
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            decorationBox = { innerTextField ->
                                if (amount.isEmpty()) {
                                    Text(
                                        text = "0",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Section: Budget Period
                Text(
                    text = "Budget Period",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Budget Period Dropdown Card
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { periodExpanded = !periodExpanded }
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = selectedPeriod,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Select Period",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = periodExpanded,
                        onDismissRequest = { periodExpanded = false },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        periods.forEach { period ->
                            DropdownMenuItem(
                                text = { Text(period, fontWeight = FontWeight.Medium) },
                                onClick = {
                                    selectedPeriod = period
                                    periodExpanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Create Budget Gradient Button
                val parsedAmount = amount.toDoubleOrNull() ?: 0.0
                val canSubmit = selectedCategory.isNotEmpty() && parsedAmount > 0.0

                BudgiPrimaryPillButton(
                    text = "Create Budget",
                    enabled = canSubmit,
                    onClick = {
                        if (canSubmit) {
                            onConfirm(selectedCategory, parsedAmount)
                        }
                    },
                    icon = {
                        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = if (canSubmit) (if (isDark) Color.Black else Color.White) else (if (isDark) Color.White.copy(alpha = 0.40f) else Color(0xFF0F172A).copy(alpha = 0.40f)),
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(180f)
                        )
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

