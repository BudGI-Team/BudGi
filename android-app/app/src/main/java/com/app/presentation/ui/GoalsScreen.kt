package com.app.presentation.ui

import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.R
import com.app.data.local.entity.GoalEntity
import com.app.presentation.ui.theme.*
import com.app.presentation.viewmodel.GoalViewModel
import kotlin.math.roundToInt

/**
 * Goals Screen
 * Clean, developer-ready implementation matching the BudGi design system:
 * - Removed all "AI Design" artifacts and artificial gradients
 * - Standard Material 3 LinearProgressIndicator with rounded ends
 * - High-quality Material SVG vector icons and genuine BudGi branding
 * - Strict adherence to theme tokens: MaterialTheme.colorScheme and DarkColors.Green
 * - Standardized buttons with Dimens.buttonCornerRadius and primary action coloring
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    windowSizeClass: WindowSizeClass,
    onBack: (() -> Unit)? = null,
    viewModel: GoalViewModel = hiltViewModel(),
    currencySymbol: String = "₹"
) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddSavingsDialog by remember { mutableStateOf<GoalEntity?>(null) }
    var prefillGoalTitle by remember { mutableStateOf("") }
    var prefillGoalAmount by remember { mutableStateOf("") }
    var prefillGoalCategory by remember { mutableStateOf("savings") }

    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val isDark = LocalIsDarkTheme.current

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

    LaunchedEffect(uiState.message, uiState.error) {
        val msg = uiState.message ?: uiState.error
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = "Goals",
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
                        prefillGoalTitle = ""
                        prefillGoalAmount = ""
                        prefillGoalCategory = "savings"
                        showCreateDialog = true
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
                                contentDescription = "New Goal",
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = Dimens.bottomDockHeight + 84.dp)
            ) {
                // ── Hero Concentric Progress Rings Chart (Reference Image 2) ──
                item {
                    BudgiConcentricGoalsChart(
                        goals = goals,
                        currencySymbol = currencySymbol,
                        onAddNewGoal = {
                            prefillGoalTitle = ""
                            prefillGoalAmount = ""
                            prefillGoalCategory = "savings"
                            showCreateDialog = true
                        }
                    )
                }

                // ── "Your Goals" Header ──
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Your Milestones",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (goals.isNotEmpty()) {
                            Text(
                                text = "${goals.size} Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // ── Goals Cards List ──
                if (uiState.isLoading && goals.isEmpty()) {
                    item {
                        SkeletonList(count = 3)
                    }
                } else if (goals.isNotEmpty()) {
                    items(goals, key = { it.id }) { goal ->
                        GoalItemCard(
                            goal = goal,
                            onAddSavings = { showAddSavingsDialog = goal },
                            onDelete = { viewModel.deleteGoal(goal) }
                        )
                    }
                } else {
                    item {
                        EmptyGoalsCard(
                            onSelectPreset = { title, amount, category ->
                                prefillGoalTitle = title
                                prefillGoalAmount = amount
                                prefillGoalCategory = category
                                showCreateDialog = true
                            },
                            onCreateCustom = {
                                prefillGoalTitle = ""
                                prefillGoalAmount = ""
                                prefillGoalCategory = "savings"
                                showCreateDialog = true
                            }
                        )
                    }
                }
            }

            // ── Floating Bottom Button: "+ Create New Goal" (Scroll-aware) ──
            AnimatedVisibility(
                visible = isButtonVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f)
                ) + fadeIn(),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.8f, stiffness = 400f)
                ) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = Dimens.bottomDockHeight + 12.dp, start = 20.dp, end = 20.dp)
            ) {
                BudgiPrimaryPillButton(
                    text = "Create New Goal",
                    onClick = {
                        prefillGoalTitle = ""
                        prefillGoalAmount = ""
                        prefillGoalCategory = "savings"
                        showCreateDialog = true
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

    // Create Goal Dialog
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()
    if (showCreateDialog) {
        CreateGoalDialog(
            initialTitle = prefillGoalTitle,
            initialAmount = prefillGoalAmount,
            initialCategory = prefillGoalCategory,
            customCategories = customCategories.map { it.name },
            onAddCustomCategory = viewModel::addCustomCategory,
            onDismiss = { showCreateDialog = false },
            onCreate = { title, amount, deadline, category, color, icon ->
                viewModel.createGoal(title, amount, deadline, category, color, icon)
                showCreateDialog = false
            }
        )
    }

    // Add Savings Dialog
    showAddSavingsDialog?.let { goal ->
        AddSavingsDialog(
            goalTitle = goal.title,
            currentAmount = goal.currentAmount,
            targetAmount = goal.targetAmount,
            onDismiss = { showAddSavingsDialog = null },
            onAdd = { amount ->
                viewModel.addSavings(goal, amount)
                showAddSavingsDialog = null
            }
        )
    }
}

// ── Goal Category Badge ──────────────────────────────────────────────────

@Composable
private fun GoalCategoryBadge(
    category: String,
    isCompleted: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp
) {
    val icon = remember(category) {
        val normalized = category.lowercase().trim()
        when {
            normalized.contains("saving") -> Icons.Default.Savings
            normalized.contains("emergency") || normalized.contains("shield") -> Icons.Default.Shield
            normalized.contains("vacation") || normalized.contains("travel") || normalized.contains("trip") || normalized.contains("flight") -> Icons.Default.Flight
            normalized.contains("gadget") || normalized.contains("tech") || normalized.contains("device") || normalized.contains("laptop") -> Icons.Default.Devices
            normalized.contains("vehicle") || normalized.contains("car") || normalized.contains("bike") -> Icons.Default.DirectionsCar
            normalized.contains("home") || normalized.contains("house") -> Icons.Default.Home
            normalized.contains("education") || normalized.contains("school") || normalized.contains("college") -> Icons.Default.School
            normalized.contains("investment") || normalized.contains("stock") -> Icons.AutoMirrored.Filled.TrendingUp
            normalized.contains("shopping") -> Icons.Default.ShoppingBag
            else -> Icons.Default.Savings
        }
    }
    val badgeColor = if (isCompleted) DarkColors.Green else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(badgeColor.copy(alpha = 0.12f))
            .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category,
            tint = badgeColor,
            modifier = Modifier.size((size.value * 0.48f).dp)
        )
    }
}

// ── Smart & Robust Goal Item Card ───────────────────────────────────────

@Composable
private fun GoalItemCard(
    goal: GoalEntity,
    onAddSavings: () -> Unit,
    onDelete: () -> Unit
) {
    val progress = (goal.currentAmount / goal.targetAmount.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    val percentage = (progress * 100).roundToInt()
    val isCompleted = goal.isCompleted || goal.currentAmount >= goal.targetAmount
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isDark = LocalIsDarkTheme.current

    // Smart Deadline & Savings Rate Calculation
    val smartProjectionText = remember(goal.deadline, goal.currentAmount, goal.targetAmount, isCompleted) {
        if (isCompleted) {
            "Target Achieved! Outstanding discipline"
        } else if (!goal.deadline.isNullOrBlank()) {
            try {
                val deadlineDate = java.time.LocalDate.parse(goal.deadline)
                val today = java.time.LocalDate.now()
                val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, deadlineDate)
                val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)

                when {
                    daysLeft > 30 -> {
                        val months = (daysLeft / 30.0).coerceAtLeast(1.0)
                        val monthlyReq = remaining / months
                        "Need ₹${"%,.0f".format(monthlyReq)}/mo • $daysLeft days left"
                    }
                    daysLeft > 0 -> {
                        val dailyReq = remaining / daysLeft.coerceAtLeast(1)
                        "Need ₹${"%,.0f".format(dailyReq)}/day • $daysLeft days left"
                    }
                    daysLeft == 0L -> "Target deadline is today!"
                    else -> "Deadline passed by ${kotlin.math.abs(daysLeft)} days • ₹${"%,.0f".format(remaining)} remaining"
                }
            } catch (_: Exception) {
                val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
                "₹${"%,.0f".format(remaining)} remaining to target"
            }
        } else {
            val remaining = (goal.targetAmount - goal.currentAmount).coerceAtLeast(0.0)
            "₹${"%,.0f".format(remaining)} remaining to reach goal"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cardCornerRadius))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                0.8.dp,
                if (isCompleted) DarkColors.Green.copy(alpha = 0.4f)
                else if (isDark) DarkColors.Border
                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                RoundedCornerShape(Dimens.cardCornerRadius)
            )
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Top Row: Category Icon, Title, Status & Menu
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
                    GoalCategoryBadge(
                        category = goal.category,
                        isCompleted = isCompleted,
                        size = 46.dp
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = goal.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = goal.category.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (isCompleted) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(Dimens.buttonCornerRadius))
                                        .background(DarkColors.Green.copy(alpha = 0.15f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "DONE",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = DarkColors.Green
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$percentage%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isCompleted) DarkColors.Green else MaterialTheme.colorScheme.primary
                    )
                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Amount Progress Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "₹${"%,.0f".format(goal.currentAmount)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Goal: ₹${"%,.0f".format(goal.targetAmount)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Progress Bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (isCompleted) DarkColors.Green else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            // Smart Helper Text & Quick Add Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = smartProjectionText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isCompleted) DarkColors.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!isCompleted) {
                    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    Button(
                        onClick = onAddSavings,
                        shape = RoundedCornerShape(BudgiRadius.Pill),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                            contentColor = if (isDark) Color.Black else Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = if (isDark) Color.Black else Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Deposit",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.Black else Color.White
                        )
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Goal", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Are you sure you want to remove \"${goal.title}\"? This cannot be undone.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    shape = RoundedCornerShape(Dimens.buttonCornerRadius),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.onError, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

// ── Dialogs & Components ──────────────────────────────────────────

private data class GoalCategoryOption(
    val name: String,
    val id: String,
    val icon: ImageVector,
    val fallbackHex: String = "#0284C7"
)

private val GOAL_CATEGORY_OPTIONS = listOf(
    GoalCategoryOption("Savings", "savings", Icons.Default.Savings),
    GoalCategoryOption("Emergency", "emergency", Icons.Default.Shield),
    GoalCategoryOption("Vacation", "vacation", Icons.Default.Flight),
    GoalCategoryOption("Gadget", "gadget", Icons.Default.Devices),
    GoalCategoryOption("Vehicle", "vehicle", Icons.Default.DirectionsCar),
    GoalCategoryOption("Home", "home", Icons.Default.Home),
    GoalCategoryOption("Education", "education", Icons.Default.School),
    GoalCategoryOption("Investment", "investment", Icons.AutoMirrored.Filled.TrendingUp),
    GoalCategoryOption("Shopping", "shopping", Icons.Default.ShoppingBag)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateGoalDialog(
    initialTitle: String = "",
    initialAmount: String = "",
    initialCategory: String = "savings",
    customCategories: List<String> = emptyList(),
    onAddCustomCategory: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onCreate: (title: String, amount: Double, deadline: String?, category: String, color: String, icon: String) -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var amount by rememberSaveable { mutableStateOf(initialAmount) }
    var deadline by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable {
        mutableStateOf(
            GOAL_CATEGORY_OPTIONS.find { it.id.equals(initialCategory, ignoreCase = true) }?.id ?: initialCategory.ifBlank { "savings" }
        )
    }
    var showCustomCategoryInput by rememberSaveable { mutableStateOf(false) }
    var customCategoryText by rememberSaveable { mutableStateOf("") }

    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = java.time.Instant.ofEpochMilli(millis)
                            .atZone(java.time.ZoneId.of("UTC"))
                            .toLocalDate()
                        deadline = date.toString()
                    }
                    showDatePicker = false
                }) {
                    Text("OK", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val allCategoryOptions = remember(customCategories, selectedCategory) {
        val builtIn = GOAL_CATEGORY_OPTIONS.toMutableList()
        val custom = customCategories
            .filter { name -> name.isNotBlank() && builtIn.none { it.id.equals(name, ignoreCase = true) } }
            .map { name ->
                GoalCategoryOption(
                    name = name.trim().replaceFirstChar { it.uppercase() },
                    id = name.trim().lowercase(),
                    icon = getCategoryIcon(name)
                )
            }
        val customSelected = if (selectedCategory.isNotBlank() &&
            builtIn.none { it.id.equals(selectedCategory, ignoreCase = true) } &&
            custom.none { it.id.equals(selectedCategory, ignoreCase = true) }) {
            listOf(
                GoalCategoryOption(
                    name = selectedCategory.trim().replaceFirstChar { it.uppercase() },
                    id = selectedCategory.trim().lowercase(),
                    icon = getCategoryIcon(selectedCategory)
                )
            )
        } else emptyList()
        builtIn + custom + customSelected
    }

    val currentCategoryOption = remember(selectedCategory, allCategoryOptions) {
        allCategoryOptions.find { it.id.equals(selectedCategory, ignoreCase = true) }
            ?: GoalCategoryOption(
                name = selectedCategory.ifBlank { "Savings" }.replaceFirstChar { it.uppercase() },
                id = selectedCategory.ifBlank { "savings" }.lowercase(),
                icon = getCategoryIcon(selectedCategory)
            )
    }

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
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp)
                    .verticalScroll(scrollState)
            ) {
                Spacer(modifier = Modifier.height(14.dp))

                // Top Bar with Back Icon and Selected Category Badge
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

                    // Category Visual Icon Avatar
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                RoundedCornerShape(14.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = currentCategoryOption.icon,
                            contentDescription = currentCategoryOption.name,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Title & Subtitle
                Text(
                    text = "Create Savings Goal",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Track your savings progress with real-time milestones and projections.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Section 1: Goal Name
                Text(
                    text = "Goal Name",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerTextField ->
                            if (title.isEmpty()) {
                                Text(
                                    text = "e.g. Emergency Fund, Trip to Japan",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 2: Category Selector Chips
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(allCategoryOptions, key = { it.id }) { opt ->
                        val isSelected = selectedCategory.equals(opt.id, ignoreCase = true)

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Dimens.buttonCornerRadius))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    RoundedCornerShape(Dimens.buttonCornerRadius)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedCategory = opt.id
                                    if (title.isBlank()) {
                                        title = "${opt.name} Goal"
                                    }
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = opt.icon,
                                    contentDescription = opt.name,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = opt.name,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // "+ Custom" Category Chip
                    item {
                        val isCustomActive = showCustomCategoryInput || !GOAL_CATEGORY_OPTIONS.any { it.id.equals(selectedCategory, ignoreCase = true) }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(Dimens.buttonCornerRadius))
                                .background(
                                    if (isCustomActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (isCustomActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    RoundedCornerShape(Dimens.buttonCornerRadius)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    showCustomCategoryInput = !showCustomCategoryInput
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Custom Category",
                                    tint = if (isCustomActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Custom",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isCustomActive) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCustomActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Inline Custom Category Input
                AnimatedVisibility(visible = showCustomCategoryInput) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
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
                                                text = "e.g. Wedding, Freelance, Gym...",
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
                                            selectedCategory = trimmed.lowercase()
                                            onAddCustomCategory(trimmed)
                                            if (title.isBlank()) {
                                                title = "${trimmed.replaceFirstChar { it.uppercase() }} Goal"
                                            }
                                            showCustomCategoryInput = false
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
                                    Text("Apply", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 3: Target Amount
                Text(
                    text = "Target Amount (₹)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "₹ ",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        androidx.compose.foundation.text.BasicTextField(
                            value = amount,
                            onValueChange = { input ->
                                amount = input.filter { it.isDigit() }
                            },
                            textStyle = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (amount.isEmpty()) {
                                    Text(
                                        text = "50,000",
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                                innerTextField()
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Amount Chips
                val quickAmounts = listOf("10000" to "10K", "25000" to "25K", "50000" to "50K", "100000" to "1L", "250000" to "2.5L")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickAmounts.forEach { (value, label) ->
                        val isPicked = amount == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(Dimens.buttonCornerRadius))
                                .background(
                                    if (isPicked) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (isPicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                    RoundedCornerShape(Dimens.buttonCornerRadius)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    amount = value
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "₹$label",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Medium,
                                color = if (isPicked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Section 4: Target Date
                Text(
                    text = "Target Date (Optional)",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { showDatePicker = true }
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Date",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (deadline.isNotBlank()) deadline else "Select target deadline",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (deadline.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        if (deadline.isNotBlank()) {
                            IconButton(
                                onClick = { deadline = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear Date",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Choose Date",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))

                // Bottom CTA: Create Goal
                val parsedAmount = amount.toDoubleOrNull() ?: 0.0
                val canSubmit = title.isNotBlank() && parsedAmount > 0.0

                BudgiPrimaryPillButton(
                    text = "Create Goal",
                    enabled = canSubmit,
                    onClick = {
                        if (canSubmit) {
                            onCreate(
                                title.trim(),
                                parsedAmount,
                                deadline.ifBlank { null },
                                currentCategoryOption.id,
                                currentCategoryOption.fallbackHex,
                                currentCategoryOption.id
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun AddSavingsDialog(
    goalTitle: String,
    currentAmount: Double,
    targetAmount: Double,
    onDismiss: () -> Unit,
    onAdd: (Double) -> Unit
) {
    var amount by rememberSaveable { mutableStateOf("") }
    val remaining = (targetAmount - currentAmount).coerceAtLeast(0.0)
    val haptic = LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Deposit to Goal",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = goalTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Milestone Info Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Saved",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹${"%,.0f".format(currentAmount)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Remaining",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹${"%,.0f".format(remaining)}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Amount Input Field
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Deposit Amount (₹)") },
                    prefix = { Text("₹ ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp)
                )

                // Quick Suggestions Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = listOf("500", "1000", "2000", "5000")
                    presets.forEach { presetVal ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(Dimens.buttonCornerRadius))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    RoundedCornerShape(Dimens.buttonCornerRadius)
                                )
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val currentParsed = amount.toDoubleOrNull() ?: 0.0
                                    amount = (currentParsed + presetVal.toDouble()).toInt().toString()
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+₹$presetVal",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (remaining > 0.0) {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            amount = remaining.toInt().toString()
                        },
                        modifier = Modifier.align(Alignment.End),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "Deposit Full Remaining (₹${"%,.0f".format(remaining)})",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            val amt = amount.toDoubleOrNull() ?: 0.0
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onAdd(amt)
                },
                enabled = amt > 0.0,
                shape = RoundedCornerShape(BudgiRadius.Pill),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDark) Color.White else Color(0xFF0F172A),
                    contentColor = if (isDark) Color.Black else Color.White,
                    disabledContainerColor = if (isDark) Color.White.copy(alpha = 0.25f) else Color(0xFF0F172A).copy(alpha = 0.20f),
                    disabledContentColor = if (isDark) Color.White.copy(alpha = 0.40f) else Color(0xFF0F172A).copy(alpha = 0.40f)
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text(
                    "Deposit", 
                    fontWeight = FontWeight.Bold, 
                    color = if (amt > 0.0) (if (isDark) Color.Black else Color.White) else (if (isDark) Color.White.copy(alpha = 0.40f) else Color(0xFF0F172A).copy(alpha = 0.40f))
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )
}

private data class GoalPresetItem(
    val title: String,
    val amount: String,
    val category: String,
    val subtitle: String,
    val icon: ImageVector
)

@Composable
private fun EmptyGoalsCard(
    onSelectPreset: (title: String, amount: String, category: String) -> Unit,
    onCreateCustom: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.cardCornerRadius))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                RoundedCornerShape(Dimens.cardCornerRadius)
            )
            .padding(22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.budgi_logo),
                    contentDescription = "BudGi Logo",
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text = "Start Your First Goal",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Pick a popular savings milestone or create a custom goal.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }

        // Quick Preset Cards
        val presets = listOf(
            GoalPresetItem("Emergency Fund", "50000", "emergency", "3-6 months safety buffer", Icons.Default.Shield),
            GoalPresetItem("Vacation Trip", "75000", "vacation", "Flights, hotel & memories", Icons.Default.Flight),
            GoalPresetItem("Tech Gadget", "100000", "gadget", "New laptop or mobile", Icons.Default.Devices),
            GoalPresetItem("Vehicle Downpayment", "200000", "vehicle", "Auto milestone fund", Icons.Default.DirectionsCar)
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            presets.forEach { item ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                            RoundedCornerShape(16.dp)
                        )
                        .clickable { onSelectPreset(item.title, item.amount, item.category) }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.title,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = item.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "₹${"%,.0f".format(item.amount.toDouble())}",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        BudgiPrimaryPillButton(
            text = "Create Custom Goal",
            onClick = onCreateCustom,
            icon = {
                val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isDark) Color.Black else Color.White
                )
            }
        )
    }
}
