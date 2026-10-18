package com.app.presentation.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import com.app.R
import com.app.presentation.ui.theme.*
import com.app.presentation.viewmodel.DashboardUiState
import com.app.presentation.viewmodel.DateFilter
import com.app.utils.DateUtils
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableFloatStateOf

import java.util.Calendar
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.pager.PageSize
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.math.*
import androidx.compose.runtime.rememberCoroutineScope
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    windowSizeClass: WindowSizeClass,
    state: DashboardUiState,
    unreviewedTransactions: List<com.app.domain.model.Transaction> = emptyList(),
    unreadCount: Int,
    smsPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    onReadSmsClicked: () -> Unit,
    onSeeAllTransactions: () -> Unit,
    onNavigateToTransactions: () -> Unit = onSeeAllTransactions,
    onNavigateToBudget: () -> Unit = {},
    onNavigateToInsights: () -> Unit = {},
    onNavigateToAIChat: () -> Unit = {},
    onNavigateToSpaces: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    onReviewTransaction: (transactionId: String, newCategory: String, alias: String?) -> Unit = { _, _, _ -> },
    onDismissReview: (transactionId: String) -> Unit = {},
    onApplyProposal: (com.app.domain.model.AutonomousActionProposal) -> Unit = {},
    onDismissProposal: (com.app.domain.model.AutonomousActionProposal) -> Unit = {},
    onDateFilterChanged: (DateFilter) -> Unit,
    onNavigateToProfileRoute: (String) -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenDrawer: () -> Unit = {}
) {
    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity
        val dataUri = activity?.intent?.dataString
        if (dataUri == "budgi://sync") {
            onReadSmsClicked()
            activity.intent?.data = null
        }
    }
    
    // Snackbar state
    val snackbarHostState = remember { SnackbarHostState() }
    
    LaunchedEffect(state.error, state.syncMessage) {
        if (state.error != null) {
            val result = snackbarHostState.showSnackbar(
                message = state.error,
                actionLabel = "Retry",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) {
                if (smsPermissionGranted) onReadSmsClicked() else onRequestPermission()
            }
        } else if (state.syncMessage != null) {
            snackbarHostState.showSnackbar(
                message = state.syncMessage,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Dynamic Greeting based on time
    val greetingResId = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> R.string.good_morning
            in 12..16 -> R.string.good_afternoon
            else -> R.string.good_evening
        }
    }
    val greeting = stringResource(greetingResId)
    
    // Last Synced state
    var lastSyncText by remember { mutableStateOf("Last synced: Just now") }
    LaunchedEffect(state.syncing) {
        if (!state.syncing && !state.loading) {
            lastSyncText = "Last synced: Just now"
        }
    }
    
    // Periodically update the "Just now" to "X mins ago"
    LaunchedEffect(state.syncing) {
        if (!state.syncing) {
            val syncTime = System.currentTimeMillis()
            while(true) {
                kotlinx.coroutines.delay(60000) // update every minute
                val mins = (System.currentTimeMillis() - syncTime) / 60000
                lastSyncText = "Last synced: $mins min${if (mins == 1L) "" else "s"} ago"
            }
        }
    }
    
    // Currency symbol
    val currencySymbol = remember(state.user?.currency) {
        when (state.user?.currency?.uppercase()) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "AED" -> "د.إ "
            else -> "₹"
        }
    }

    // Scroll-aware collapsing header and floating quick bar state
    var isHeaderVisible by remember { mutableStateOf(true) }
    var isQuickBarExpanded by remember { mutableStateOf(true) }
    var previousScrollValue by remember { mutableStateOf(0) }

    LaunchedEffect(scrollState.value) {
        val currentScroll = scrollState.value
        val delta = currentScroll - previousScrollValue
        if (delta > 12 && currentScroll > 40) {
            isHeaderVisible = false
            isQuickBarExpanded = false
        } else if (delta < -8 || currentScroll <= 20) {
            isHeaderVisible = true
            isQuickBarExpanded = true
        }
        previousScrollValue = currentScroll
    }

    // Manual sync spin animation
    var isManualSync by remember { mutableStateOf(false) }
    LaunchedEffect(state.syncing) {
        if (!state.syncing) isManualSync = false
    }
    val infiniteTransition = rememberInfiniteTransition(label = "sync_spin")
    val syncAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_spin"
    )
    val shouldSpin = state.syncing && isManualSync

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {},
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            floatingActionButton = { }
        ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val maxWidth = maxWidth
            val contentPadding = if (maxWidth > 600.dp) Dimens.paddingExtraLarge else Dimens.paddingMedium
            val currencySymbol = remember(state.user?.currency) {
                when (state.user?.currency?.uppercase()) {
                    "USD" -> "$"
                    "EUR" -> "€"
                    "GBP" -> "£"
                    "AED" -> "د.إ "
                    else -> "₹"
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .statusBarsPadding()
                    .padding(top = 10.dp, start = contentPadding, end = contentPadding),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingLarge)
            ) {
                state.activeScamAlert?.let { alert ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(Dimens.cardCornerRadius),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(Dimens.paddingMedium),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMedium)
                        ) {
                            Text(alert.icon, style = MaterialTheme.typography.headlineSmall)
                            Column {
                                Text(
                                    text = alert.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = alert.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }
                
                if (isExpanded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLarge)
                    ) {
                        Column(modifier = Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(Dimens.spacingLarge)) {
                            HomeScreenTotalBalanceCard(
                                state = state,
                                unreadCount = unreadCount,
                                onOpenDrawer = onOpenDrawer,
                                onNavigateToNotifications = onNavigateToNotifications
                            )
                            if (unreviewedTransactions.isNotEmpty()) {
                                MicroSpendCatchUpCard(
                                    unreviewedTransactions = unreviewedTransactions,
                                    onReviewTransaction = onReviewTransaction,
                                    onDismissReview = onDismissReview,
                                    currencySymbol = currencySymbol
                                )
                            }
                            RecentActivitySection(state, onSeeAllTransactions)
                        }
                        
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Dimens.spacingLarge)) {
                            DateFilterSelector(
                                currentFilter = state.dateFilter,
                                onFilterSelected = onDateFilterChanged
                            )
                            HomeScreenSpendingOverviewCard(
                                summary = state.analyticsSummary,
                                onSeeInsights = onNavigateToInsights,
                                currencySymbol = currencySymbol
                            )
                            state.activeProposal?.let { proposal ->
                                CopilotDashboardActionCard(
                                    proposal = proposal,
                                    onApply = onApplyProposal,
                                    onDiscuss = { onNavigateToAIChat() },
                                    onDismiss = onDismissProposal,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            val healthScore = state.analyticsSummary.healthScore
                            AISmartInsightCard(
                                insight = getInsightMessage(state.analyticsSummary, state.transactions),
                                score = healthScore,
                                onCopilotClick = onNavigateToAIChat
                            )
                            DailySpendingPatternCard(
                                spendingByDay = state.analyticsSummary.spendingByDay,
                                spentToday = state.analyticsSummary.spentToday
                            )
                            if (state.analyticsSummary.totalBudget > 0) {
                                BudgetSection(
                                    spent = state.analyticsSummary.monthlySpending, 
                                    total = state.analyticsSummary.totalBudget,
                                    spentToday = state.analyticsSummary.spentToday,
                                    safeToSpendDaily = state.analyticsSummary.safeToSpendDaily,
                                    dailyBurnRate = state.analyticsSummary.dailyBurnRate
                                )
                            }
                            val upcomingSubs = remember(state.transactions) {
                                extractSubscriptions(state.transactions)
                            }
                            SubscriptionsWidget(
                                subscriptions = upcomingSubs,
                                onSyncClick = {
                                    if (smsPermissionGranted) onReadSmsClicked() else onRequestPermission()
                                }
                            )
                        }
                    }
                } else {
                    HomeScreenTotalBalanceCard(
                        state = state,
                        unreadCount = unreadCount,
                        onOpenDrawer = onOpenDrawer,
                        onNavigateToNotifications = onNavigateToNotifications
                    )
                    if (unreviewedTransactions.isNotEmpty()) {
                        MicroSpendCatchUpCard(
                            unreviewedTransactions = unreviewedTransactions,
                            onReviewTransaction = onReviewTransaction,
                            onDismissReview = onDismissReview,
                            currencySymbol = currencySymbol
                        )
                    }
                    RecentActivitySection(state, onSeeAllTransactions)

                    DateFilterSelector(
                        currentFilter = state.dateFilter,
                        onFilterSelected = onDateFilterChanged
                    )
                    HomeScreenSpendingOverviewCard(
                        summary = state.analyticsSummary,
                        onSeeInsights = onNavigateToInsights,
                        currencySymbol = currencySymbol
                    )
                    DailySpendingPatternCard(
                        spendingByDay = state.analyticsSummary.spendingByDay,
                        spentToday = state.analyticsSummary.spentToday
                    )
                    val mobileHealthScore = remember(state.analyticsSummary) {
                        val budget = state.analyticsSummary.totalBudget
                        val spent = state.analyticsSummary.monthlySpending
                        if (budget > 0) {
                            val ratio = spent / budget
                            when {
                                ratio <= 0.5 -> 94
                                ratio <= 0.75 -> 88
                                ratio <= 0.95 -> 78
                                else -> 64
                            }
                        } else 86
                    }
                    state.activeProposal?.let { proposal ->
                        CopilotDashboardActionCard(
                            proposal = proposal,
                            onApply = onApplyProposal,
                            onDiscuss = { onNavigateToAIChat() },
                            onDismiss = onDismissProposal,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    AISmartInsightCard(
                        insight = getInsightMessage(state.analyticsSummary, state.transactions),
                        score = mobileHealthScore,
                        onCopilotClick = onNavigateToAIChat,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // ── Interactive Monthly Budget Radial Gauge (Positioned above subscriptions) ──
                    BudgiMonthlyBudgetArcCard(
                        spent = state.analyticsSummary.monthlySpending,
                        total = state.analyticsSummary.totalBudget,
                        spentToday = state.analyticsSummary.spentToday,
                        safeToSpendDaily = state.analyticsSummary.safeToSpendDaily,
                        dailyBurnRate = state.analyticsSummary.dailyBurnRate,
                        currencySymbol = currencySymbol,
                        onManageBudget = onNavigateToBudget
                    )

                    val upcomingSubs = remember(state.transactions) {
                        extractSubscriptions(state.transactions)
                    }
                    SubscriptionsWidget(
                        subscriptions = upcomingSubs,
                        onSyncClick = {
                            if (smsPermissionGranted) onReadSmsClicked() else onRequestPermission()
                        }
                    )

                    // ── BudGi Ecosystem Swappable Cards ──
                    BudgiEcosystemCardsSection(
                        onNavigateToAIChat = onNavigateToAIChat,
                        onNavigateToSpaces = onNavigateToSpaces,
                        onManageSmsSync = {
                            if (smsPermissionGranted) onReadSmsClicked() else onRequestPermission()
                        }
                    )
                }

                if (state.loading) {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(134.dp))
            }

            // ── Top Loading Sync Animation Capsule ──
            val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
            AnimatedVisibility(
                visible = state.syncing || state.loading,
                enter = slideInVertically(
                    initialOffsetY = { -it },
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.82f, stiffness = 420f)
                ) + fadeIn(tween(180)),
                exit = slideOutVertically(
                    targetOffsetY = { -it },
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 400f)
                ) + fadeOut(tween(200)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(24.dp),
                            ambientColor = Color.Black.copy(alpha = 0.40f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        ),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (state.syncing) "Syncing BudGi transactions..." else "Refreshing data...",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // ── Floating Quick Action Pill placed just above bottom navbar with clean distance ──
            if (!isExpanded) {
                DashboardFloatingQuickBar(
                    isSyncing = state.syncing,
                    hasPendingSpaces = false,
                    isExpanded = isQuickBarExpanded,
                    onSyncClick = {
                        if (smsPermissionGranted) {
                            onReadSmsClicked()
                        } else {
                            onRequestPermission()
                        }
                    },
                    onSpacesClick = onNavigateToSpaces,
                    onQuickClick = onNavigateToTransactions,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 78.dp)
                )
            }
        }
    }
    }
}

private fun extractSubscriptions(transactions: List<com.app.domain.model.Transaction>): List<com.app.domain.model.Transaction> {
    val subKeywords = setOf(
        "netflix", "spotify", "prime", "hotstar", "disney", "youtube", "apple", "google",
        "icloud", "chatgpt", "openai", "github", "patreon", "gym", "wifi", "broadband",
        "jio", "airtel", "fiber", "playstation", "xbox", "audible", "nyt", "medium",
        "substack", "zee5", "sonyliv", "swiggy", "zomato", "subscription", "recurring"
    )

    val validExpenses = transactions.filter { tx ->
        (tx.type.equals("expense", ignoreCase = true) || tx.type.equals("debit", ignoreCase = true)) &&
        !tx.category.equals("Transfer", ignoreCase = true)
    }

    val explicitRecurring = validExpenses.filter {
        it.isRecurring || it.category.contains("subscription", ignoreCase = true) || it.category.contains("recurring", ignoreCase = true)
    }

    val keywordMatches = validExpenses.filter { tx ->
        subKeywords.any { kw ->
            (tx.merchant?.contains(kw, ignoreCase = true) == true) ||
            tx.description.contains(kw, ignoreCase = true)
        }
    }

    // Frequency-based recurring detection: same merchant, same amount ~monthly
    val frequencyRecurring = validExpenses
        .groupBy { (it.merchant ?: it.sender).lowercase().trim() }
        .filter { (_, txs) ->
            if (txs.size >= 2) {
                val sorted = txs.sortedByDescending { it.timestamp }
                val daysDiff = kotlin.math.abs(sorted[0].timestamp - sorted[1].timestamp) / (1000 * 60 * 60 * 24)
                val sameAmount = kotlin.math.abs(sorted[0].amount - sorted[1].amount) < 1.0
                sameAmount && daysDiff in 25..35
            } else false
        }
        .values
        .mapNotNull { it.maxByOrNull { tx -> tx.timestamp } }

    return (explicitRecurring + keywordMatches + frequencyRecurring)
        .groupBy { (it.merchant ?: it.description).lowercase().trim() }
        .map { entry -> entry.value.maxByOrNull { it.timestamp } ?: entry.value.first() }
        .take(5)
}

private fun getInsightMessage(
    summary: com.app.domain.model.AnalyticsSummary,
    transactions: List<com.app.domain.model.Transaction> = emptyList()
): String {
    val recentAnomaly = transactions.firstOrNull {
        it.isAnomaly && (System.currentTimeMillis() - it.timestamp) < 14L * 24 * 3600 * 1000
    }
    if (recentAnomaly != null) {
        val title = recentAnomaly.merchant ?: recentAnomaly.description
        return "Spending Spike Detected: ₹${"%,.0f".format(recentAnomaly.amount)} at $title is significantly higher than your typical ${recentAnomaly.category} spend."
    }

    val topCat = summary.categoryBreakdown.maxByOrNull { it.value }
    return when {
        summary.monthlySpending == 0.0 ->
            "Welcome to BudGi! Sync your SMS to automatically generate real-time spending intelligence."
        summary.totalBudget > 0 && summary.monthlySpending > summary.totalBudget ->
            "Budget Alert: You've exceeded your monthly limit by ₹${"%,.0f".format(summary.monthlySpending - summary.totalBudget)}. Burn rate is ₹${"%,.0f".format(summary.dailyBurnRate)}/day."
        topCat != null && summary.monthlySpending > 0 -> {
            val catPct = ((topCat.value / summary.monthlySpending) * 100).toInt()
            "Top Expense: ${topCat.key} accounts for $catPct% of your monthly spend (₹${"%,.0f".format(topCat.value)}). Safe daily spend is ₹${"%,.0f".format(summary.safeToSpendDaily)}."
        }
        summary.spendingChangePercentage > 10 ->
            "Spending Spike: You've spent ${"%.1f".format(summary.spendingChangePercentage)}% more than last month. Safe daily spend is ₹${"%,.0f".format(summary.safeToSpendDaily)}."
        summary.spendingChangePercentage < -5 ->
            "Excellent savings pace! You're saving ${"%.1f".format(-summary.spendingChangePercentage)}% more compared to last month."
        else ->
            "Steady pacing: Your current daily burn rate is ₹${"%,.0f".format(summary.dailyBurnRate)}/day with ₹${"%,.0f".format(summary.safeToSpendDaily)} safe to spend."
    }
}

@Composable
private fun BudgetSection(
    spent: Double, 
    total: Double, 
    spentToday: Double = 0.0,
    safeToSpendDaily: Double = 0.0,
    dailyBurnRate: Double = 0.0
) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium)) {
        Text(
            stringResource(R.string.monthly_budget),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        
        // The Budget Progress Card with Safe to Spend
        BudgetProgressCard(
            spent = spent,
            total = total,
            spentToday = spentToday,
            safeToSpendDaily = safeToSpendDaily,
            dailyBurnRate = dailyBurnRate,
            modifier = Modifier.tiltOnPress()
        )
    }
}

@Composable
private fun RecentActivitySection(state: DashboardUiState, onSeeAllTransactions: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium)) {
        SectionHeader(title = stringResource(R.string.recent_activity), onSeeAllClick = onSeeAllTransactions)
        
        if (state.loading && state.transactions.isEmpty()) {
            SkeletonList(count = 3)
        } else if (state.transactions.isEmpty()) {
            EmptyState(message = "No transactions found yet. Sync your SMS to see magic!")
        } else {
            val displayList = remember(state.transactions) { state.transactions.take(5) }
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                displayList.forEach { transaction ->
                    TransactionItem(
                        merchant = transaction.merchant ?: transaction.sender,
                        category = transaction.category,
                        amount = "%,.0f".format(transaction.amount),
                        date = DateUtils.formatDisplayTime(transaction.date, transaction.timestamp),
                        type = transaction.type,
                        splitDetails = transaction.splitDetails,
                        categorySource = transaction.categorySource,
                        isRecurring = transaction.isRecurring,
                        isAnomaly = transaction.isAnomaly,
                        onClick = onSeeAllTransactions
                    )
                }
            }
        }
    }
}

@Composable
fun BudgetProgressCard(
    spent: Double, 
    total: Double, 
    spentToday: Double = 0.0,
    safeToSpendDaily: Double = 0.0,
    dailyBurnRate: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showDetailedBurn by remember { mutableStateOf(false) }
    val progress = (spent / total).toFloat().coerceIn(0f, 1f)
    val color = when {
        progress >= 0.9f -> MaterialTheme.colorScheme.error
        progress >= 0.7f -> DarkColors.Amber
        else -> MaterialTheme.colorScheme.tertiary
    }
    val remainingBudget = (total - spent).coerceAtLeast(0.0)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassCard()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                showDetailedBurn = !showDetailedBurn
            }
            .padding(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top Row: Safe To Spend vs Spent Today (or Toggled Detail)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = if (showDetailedBurn) "Remaining Budget" else "Spent Today",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.3.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (showDetailedBurn) "₹${"%,.0f".format(remainingBudget)}" else "₹${"%,.0f".format(spentToday)}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (showDetailedBurn) "Burn Pace" else "Safe to Spend / Day",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.3.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Tap to toggle",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = if (showDetailedBurn) {
                            if (dailyBurnRate > 0) "₹${"%,.0f".format(dailyBurnRate)}/day" else "On track"
                        } else {
                            "₹${"%,.0f".format(safeToSpendDaily)}"
                        },
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        ),
                        color = if (showDetailedBurn && dailyBurnRate > safeToSpendDaily && safeToSpendDaily > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
            }

            LiquidProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = color,
                trackColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            )

            // Bottom Row: Total Month progress & Burn Rate warning
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Month: ₹${"%,.0f".format(spent)} / ₹${"%,.0f".format(total)} (${(progress * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (dailyBurnRate > safeToSpendDaily && safeToSpendDaily > 0) {
                    Text(
                        text = "Spending ⚡ fast",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "Tap to flip breakdown ↻",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }
        }
    }
}

data class DailySpendItem(
    val date: String,
    val label: String,
    val amount: Double,
    val isToday: Boolean
)

@Composable
fun DailySpendingPatternCard(
    spendingByDay: List<com.app.domain.model.DailySpend>,
    spentToday: Double,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val today = remember { LocalDate.now() }
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE", Locale.getDefault()) }
    val isoFormatter = remember { DateTimeFormatter.ISO_LOCAL_DATE }

    var selectedDayIndex by remember { mutableStateOf<Int?>(null) }

    val weeklyItems = remember(spendingByDay, today) {
        (6 downTo 0).map { dayOffset ->
            val date = today.minusDays(dayOffset.toLong())
            val dateStr = date.format(isoFormatter)
            val spend = spendingByDay.find { it.date == dateStr }?.amount ?: 0.0
            val isToday = (dayOffset == 0)
            val label = if (isToday) "Today" else date.format(dayFormatter)
            DailySpendItem(dateStr, label, spend, isToday)
        }
    }

    val weeklyTotal = weeklyItems.sumOf { it.amount }
    val dailyAvg = weeklyTotal / 7.0
    val maxAmount = weeklyItems.maxOfOrNull { it.amount }?.coerceAtLeast(1.0) ?: 1.0
    val peakItem = weeklyItems.maxByOrNull { it.amount }
    val hasSpend = weeklyTotal > 0.0

    val isDark = LocalIsDarkTheme.current
    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Row: Title & Pill Metric
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Daily Spending Pattern",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (selectedDayIndex != null) "Tap bar to inspect • Tap to dismiss" else "Touch bars to inspect exact spend",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (selectedDayIndex != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Metric Pill Badge (Updates on Bar Selection)
                val activeItem = selectedDayIndex?.let { weeklyItems.getOrNull(it) }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (activeItem != null || spentToday > 0) {
                                if (isDark) Color(0xFF181C26) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                            } else if (isDark) DarkColors.SurfaceContainerHighest
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                        .border(
                            0.8.dp,
                            if (activeItem != null || spentToday > 0) {
                                if (isDark) Color(0xFF282D3C) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            } else if (isDark) DarkColors.BorderStrong
                            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable {
                            if (selectedDayIndex != null) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedDayIndex = null
                            }
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (activeItem != null) {
                            "${activeItem.label}: ₹${"%,.0f".format(activeItem.amount)}"
                        } else if (spentToday > 0) {
                            "Today: ₹${"%,.0f".format(spentToday)}"
                        } else {
                            "Avg: ₹${"%,.0f".format(dailyAvg)}/d"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (activeItem != null || spentToday > 0) {
                            if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        } else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Interactive Chart Row (7 Day Bars with Touch & Scrubbing)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(138.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val barWidthPx = size.width / 7f
                                val idx = (offset.x / barWidthPx).toInt().coerceIn(0, 6)
                                if (selectedDayIndex != idx) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedDayIndex = idx
                                }
                            },
                            onHorizontalDrag = { change, _ ->
                                val barWidthPx = size.width / 7f
                                val idx = (change.position.x / barWidthPx).toInt().coerceIn(0, 6)
                                if (selectedDayIndex != idx) {
                                    change.consume()
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedDayIndex = idx
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    weeklyItems.forEachIndexed { index, item ->
                        val isSelected = selectedDayIndex == index
                        val isPeak = peakItem != null && peakItem.amount > 0 && peakItem.date == item.date
                        val heightFraction = if (hasSpend) (item.amount / maxAmount).toFloat().coerceIn(0f, 1f) else 0f
                        val animatedFraction by animateFloatAsState(
                            targetValue = heightFraction,
                            animationSpec = tween(500, delayMillis = index * 35),
                            label = "daily_bar_$index"
                        )
                        val barHeight = if (hasSpend) (80.dp * animatedFraction).coerceAtLeast(8.dp) else 8.dp
                        val animatedBarWidth by animateDpAsState(
                            targetValue = if (isSelected) 26.dp else 22.dp,
                            animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
                            label = "bar_width_$index"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedDayIndex = if (selectedDayIndex == index) null else index
                                }
                        ) {
                            // Amount tag or Active Tooltip above bar
                            AnimatedVisibility(
                                visible = isSelected || (selectedDayIndex == null && item.amount > 0 && (item.isToday || isPeak)),
                                enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.85f),
                                exit = fadeOut(tween(100)) + scaleOut(targetScale = 0.85f)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    border = androidx.compose.foundation.BorderStroke(
                                        0.5.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                    ),
                                    shadowElevation = if (isSelected) 4.dp else 0.dp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                ) {
                                    Text(
                                        text = "₹${"%,.0f".format(item.amount)}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                        maxLines = 1
                                    )
                                }
                            }

                            if (!isSelected && !(selectedDayIndex == null && item.amount > 0 && (item.isToday || isPeak))) {
                                Spacer(modifier = Modifier.height(20.dp))
                            }

                            // The Bar with animated width, selection glow, and tactile touch feel
                            Box(
                                modifier = Modifier
                                    .width(animatedBarWidth)
                                    .height(barHeight)
                                    .graphicsLayer {
                                        alpha = if (selectedDayIndex != null && !isSelected) 0.45f else 1.0f
                                        scaleX = if (isSelected) 1.05f else 1.0f
                                    }
                                    .shadow(
                                        elevation = if (isSelected) 8.dp else 0.dp,
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp),
                                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                    .background(
                                        when {
                                            isSelected -> Brush.verticalGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
                                                )
                                            )
                                            !hasSpend -> SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                                            item.isToday -> SolidColor(MaterialTheme.colorScheme.primary)
                                            isPeak -> SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f))
                                            item.amount > 0 -> SolidColor(MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
                                            else -> SolidColor(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                                        }
                                    )
                                    .border(
                                        width = if (isSelected) 1.2.dp else 0.dp,
                                        color = if (isSelected) Color.White.copy(alpha = 0.6f) else Color.Transparent,
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp)
                                    )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Day Label with interactive active color
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (isSelected || item.isToday) 11.sp else 10.sp
                                ),
                                fontWeight = if (isSelected || item.isToday) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else if (item.isToday) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Dashed daily average indicator line
                if (hasSpend && maxAmount > 0 && dailyAvg > 0) {
                    val avgFraction = (dailyAvg / maxAmount).toFloat().coerceIn(0.08f, 0.92f)
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    ) {
                        val y = size.height * (1f - avgFraction)
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = androidx.compose.ui.geometry.Offset(0f, y),
                            end = androidx.compose.ui.geometry.Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                        )
                    }
                }

                // If zero spend across the entire week, render an encouraging banner
                if (!hasSpend) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isDark) DarkColors.SurfaceContainerHighest else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                            .border(0.8.dp, if (isDark) DarkColors.BorderStrong else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Zero expenses this week • Safe & on track",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                thickness = 0.5.dp
            )

            // Bottom Metrics Row (Interactive: highlights selected day comparison)
            val selectedItem = selectedDayIndex?.let { weeklyItems.getOrNull(it) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedItem != null) {
                    val diffFromAvg = selectedItem.amount - dailyAvg
                    val diffPct = if (dailyAvg > 0) ((diffFromAvg / dailyAvg) * 100).toInt() else 0
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Selected: ${selectedItem.label} (${selectedItem.date})",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "₹${"%,.0f".format(selectedItem.amount)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (diffFromAvg > 0) DarkColors.Amber.copy(alpha = 0.15f) else Color(0xFF1DBF81).copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = if (diffFromAvg > 0) "+$diffPct% vs avg" else if (diffFromAvg < 0) "$diffPct% vs avg" else "Exact avg",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (diffFromAvg > 0) DarkColors.Amber else Color(0xFF1DBF81),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                            Text(
                                text = "Clear ✕",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedDayIndex = null
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text("7-Day Total", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "₹${"%,.0f".format(weeklyTotal)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        Column {
                            Text("Daily Avg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "₹${"%,.0f".format(dailyAvg)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    if (peakItem != null && peakItem.amount > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Peak Day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${peakItem.label} (₹${"%,.0f".format(peakItem.amount)})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = DarkColors.Amber
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── BudGi Home Screen Components ───────────────────────────────────────

@Composable
private fun BudGiTopographyBackdrop(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val guillocheColor = if (isDark) Color(0xFF38BDF8).copy(alpha = 0.035f) else Color(0xFF0284C7).copy(alpha = 0.040f)
    val ambientGlowColor = if (isDark) Color(0xFF0284C7).copy(alpha = 0.12f) else Color(0xFFFFFFFF).copy(alpha = 0.45f)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        // Subtle studio ceiling spotlight
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    ambientGlowColor,
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.20f),
                radius = w * 0.85f
            ),
            radius = w * 0.85f,
            center = Offset(w * 0.5f, h * 0.20f)
        )

        // Minimal, calm harmonic contour lines
        val stroke = Stroke(width = 0.8.dp.toPx(), cap = StrokeCap.Round)
        val waveCount = 4
        for (i in 0 until waveCount) {
            val progress = i.toFloat() / (waveCount - 1)
            val yBase = h * (0.15f + progress * 0.65f)
            val amplitude = h * 0.03f

            val path = Path().apply {
                moveTo(0f, yBase)
                cubicTo(
                    w * 0.35f, yBase - amplitude,
                    w * 0.65f, yBase + amplitude,
                    w, yBase - (amplitude * 0.4f)
                )
            }
            drawPath(path, guillocheColor, style = stroke)
        }
    }
}

@Composable
fun HomeScreenTotalBalanceCard(
    state: DashboardUiState,
    unreadCount: Int = 0,
    onOpenDrawer: () -> Unit = {},
    onNavigateToNotifications: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isBalanceVisible by rememberSaveable { mutableStateOf(true) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgColor = MaterialTheme.colorScheme.background
    val haptic = LocalHapticFeedback.current

    val balanceAmount = remember(state.transactions, state.analyticsSummary) {
        val latestAvailableBalance = state.transactions
            .filter { it.availableBalance != null && it.availableBalance > 0.0 }
            .maxByOrNull { it.timestamp }
            ?.availableBalance

        if (latestAvailableBalance != null) {
            latestAvailableBalance
        } else if (state.transactions.isNotEmpty()) {
            val income = state.transactions.filter { it.type.equals("income", ignoreCase = true) || it.type.equals("credit", ignoreCase = true) }.sumOf { it.amount }
            val expense = state.transactions.filter { it.type.equals("expense", ignoreCase = true) || it.type.equals("debit", ignoreCase = true) }.sumOf { it.amount }
            income - expense
        } else if (state.analyticsSummary.monthlyIncome > 0) {
            state.analyticsSummary.monthlyIncome - state.analyticsSummary.monthlySpending
        } else {
            0.0
        }
    }
    val monthlyIncome = remember(state.transactions, state.analyticsSummary) {
        val inc = state.transactions.filter { it.type.equals("income", ignoreCase = true) || it.type.equals("credit", ignoreCase = true) }.sumOf { it.amount }
        if (inc > 0.0) inc else state.analyticsSummary.monthlyIncome
    }
    val monthlyExpense = remember(state.transactions, state.analyticsSummary) {
        val exp = state.transactions.filter { it.type.equals("expense", ignoreCase = true) || it.type.equals("debit", ignoreCase = true) }.sumOf { it.amount }
        if (exp > 0.0) exp else state.analyticsSummary.monthlySpending
    }
    val realAccountLast4 = remember(state.transactions) {
        state.transactions.firstOrNull { !it.accountLast4.isNullOrBlank() }?.accountLast4
    }
    val realBankName = remember(state.transactions) {
        state.transactions.firstOrNull { it.sender.isNotBlank() }?.let {
            val s = it.sender.uppercase()
            when {
                s.contains("HDFC") -> "HDFC Bank"
                s.contains("SBI") -> "State Bank of India"
                s.contains("ICICI") -> "ICICI Bank"
                s.contains("AXIS") -> "Axis Bank"
                s.contains("KOTAK") -> "Kotak Mahindra"
                s.contains("PNB") -> "Punjab National Bank"
                s.contains("BOB") || s.contains("BARODA") -> "Bank of Baroda"
                else -> null
            }
        } ?: "BudGi Primary"
    }

    val changePercent = state.analyticsSummary.spendingChangePercentage
    val isPositive = changePercent <= 0.0
    val currencySymbol = remember(state.user?.currency) {
        when (state.user?.currency?.uppercase()) {
            "USD" -> "$"
            "EUR" -> "€"
            "GBP" -> "£"
            "AED" -> "د.إ "
            else -> "₹"
        }
    }

    // ── Full-Bleed Panoramic Container with Seamless Bottom Merge ──
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = if (isDark) listOf(
                        DarkColors.BgBase,
                        DarkColors.BgSurface.copy(alpha = 0.75f),
                        bgColor
                    ) else listOf(
                        Color(0xFFF8FAFC),
                        Color(0xFFEEF2F6),
                        bgColor
                    )
                )
            )
    ) {
        // 1. Natural Minimal Topography Backdrop
        BudGiTopographyBackdrop(
            isDark = isDark,
            modifier = Modifier
                .fillMaxWidth()
                .matchParentSize()
        )

        // 2. Seamless gradient transition that softly dissolves into Recent Activity below
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            bgColor
                        )
                    )
                )
        )

        // 3. Structured Content: Top Navigation Header + Master Titanium Card
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Top Header Row: User Avatar & Greeting (Left) + Sync Badge & Notification Bell (Right) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Avatar + Personal Greeting
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onOpenDrawer()
                            }
                    ) {
                        val profileImage = state.user?.profileImage
                        if (!profileImage.isNullOrBlank()) {
                            AsyncImage(
                                model = profileImage,
                                contentDescription = "Profile",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            MoodAvatar(
                                mood = state.user?.avatar?.ifBlank { "zen_saver" } ?: "zen_saver",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                            )
                        }
                        // Active status indicator dot
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(CategoryColors.Groceries)
                                .border(2.dp, if (isDark) DarkColors.BgSurface else Color(0xFFF3F5FA), CircleShape)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            text = "Welcome back,",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = state.user?.displayName?.ifBlank { "BudGi Member" } ?: "BudGi Member",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Frosted Notification Bell with Unread Badge
                Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isDark) DarkColors.BgSurface.copy(alpha = 0.85f) else Color.White)
                            .border(
                                1.dp,
                                if (isDark) DarkColors.Border else LightColors.Border,
                                CircleShape
                            )
                            .clickable { onNavigateToNotifications() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-6).dp, y = 6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            )
                        }
                    }
            }

            // ── Elevated Tactile Hard Plastic Bank Card (ISO-Standard True Payment Physical Card) ──
            // ── Elevated Tactile Hard Plastic Bank Card (ISO-Standard True Payment Physical Card) ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(218.dp)
                    .shadow(
                        elevation = if (isDark) 10.dp else 8.dp,
                        shape = RoundedCornerShape(18.dp),
                        spotColor = Color.Black.copy(alpha = if (isDark) 0.50f else 0.18f),
                        ambientColor = Color.Black.copy(alpha = if (isDark) 0.30f else 0.10f)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isDark) Color(0xFF131722) else Color(0xFF1B2333)
                    )
            ) {
                // Tactile physical card surface overlays (Dual-tone laminated plastic edge, inner hairline lip & satin ambient sheen)
                TactileCardSurface(isDark = isDark)

                // Foreground Physical Card Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // 1. Card Top Row: Real EMV Chip + Contactless Wave (Left) & Real Bank Identity (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TactileEMVChip()
                            TactileContactlessWave()
                        }

                        // Real Bank Name with authentic embossed silver foil style
                        Text(
                            text = realBankName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.90f),
                            letterSpacing = 1.sp
                        )
                    }

                    // 2. Card Center Zone: Balance Label & Big Tabular Balance (Left) + Velocity Capsules (Upper Right)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "TOTAL BALANCE",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            letterSpacing = 1.4.sp,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color.White.copy(alpha = 0.65f)
                                    )
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            isBalanceVisible = !isBalanceVisible
                                        },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isBalanceVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                            contentDescription = "Toggle Balance",
                                            tint = Color.White.copy(alpha = 0.70f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                AnimatedContent(
                                    targetState = isBalanceVisible,
                                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                                    label = "hero_balance"
                                ) { visible ->
                                    if (visible) {
                                        Text(
                                            text = "$currencySymbol${"%,.2f".format(balanceAmount)}",
                                            style = MaterialTheme.typography.displaySmall.copy(
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = (-0.5).sp
                                            ),
                                            color = Color.White
                                        )
                                    } else {
                                        Text(
                                            text = "$currencySymbol ••••••••",
                                            style = MaterialTheme.typography.displaySmall.copy(
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 2.sp
                                            ),
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // Right: Income & Expense velocity capsules arranged neatly in upper zone
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF10B981).copy(alpha = 0.18f))
                                        .border(0.8.dp, Color(0xFF10B981).copy(alpha = 0.38f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 9.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                                            contentDescription = null,
                                            tint = Color(0xFF34D399),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = if (isBalanceVisible) "+$currencySymbol ${"%,.0f".format(monthlyIncome)}" else "+$currencySymbol ••••",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.3.sp
                                            ),
                                            color = Color(0xFF34D399),
                                            maxLines = 1
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFFEF4444).copy(alpha = 0.18f))
                                        .border(0.8.dp, Color(0xFFEF4444).copy(alpha = 0.38f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 9.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.TrendingDown,
                                            contentDescription = null,
                                            tint = Color(0xFFF87171),
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Text(
                                            text = if (isBalanceVisible) "-$currencySymbol ${"%,.0f".format(monthlyExpense)}" else "-$currencySymbol ••••",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.3.sp
                                            ),
                                            color = Color(0xFFF87171),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        // Authentic PAN Number (Masked 16-Digit Silk-Screen)
                        Text(
                            text = "••••  ••••  ••••  ${realAccountLast4 ?: "4820"}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 12.5.sp,
                                letterSpacing = 2.4.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }

                    // 3. Card Bottom Row: Cardholder Name + Expiry (Left) & Network Logo (Right)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "CARDHOLDER",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        letterSpacing = 1.2.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color.White.copy(alpha = 0.50f)
                                )
                                Text(
                                    text = (state.user?.displayName?.ifBlank { "BUDGI MEMBER" } ?: "BUDGI MEMBER").uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.90f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "VALID THRU",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        letterSpacing = 1.2.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = Color.White.copy(alpha = 0.50f)
                                )
                                Text(
                                    text = "08/29",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp
                                    ),
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            }
                        }

                        TactileCardNetworkLogo()
                    }
                }
            }
        }
    }
}

@Composable
private fun TactileCardSurface(isDark: Boolean) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cornerPx = 18.dp.toPx()

        // 1. Dual-tone tactile edge: Top & Left catch ambient light, Bottom & Right cast shadow
        val edgeBrush = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) 0.28f else 0.35f),
                Color.White.copy(alpha = 0.08f),
                Color.Black.copy(alpha = 0.55f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w, h)
        )
        drawRoundRect(
            brush = edgeBrush,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
            style = Stroke(width = 1.1.dp.toPx())
        )

        // 2. Machine-pressed inner hairline lip (replicates authentic 0.76mm laminated plastic rim)
        val lipInset = 1.8.dp.toPx()
        drawRoundRect(
            color = Color.White.copy(alpha = 0.05f),
            topLeft = Offset(lipInset, lipInset),
            size = androidx.compose.ui.geometry.Size(w - lipInset * 2, h - lipInset * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx - lipInset, cornerPx - lipInset),
            style = Stroke(width = 0.75.dp.toPx())
        )

        // 3. Natural satin specular light bounce across matte plastic face
        val satinSheen = Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.025f),
                Color.Transparent,
                Color.White.copy(alpha = 0.015f)
            ),
            start = Offset(0f, 0f),
            end = Offset(w * 0.85f, h * 0.85f)
        )
        drawRoundRect(
            brush = satinSheen,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx)
        )
    }
}

@Composable
private fun TactileEMVChip(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(width = 38.dp, height = 28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE5C86C),
                        Color(0xFFC7A845),
                        Color(0xFFEED488),
                        Color(0xFFB89635)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                )
            )
            .border(
                width = 0.8.dp,
                color = Color(0xFF6B5216).copy(alpha = 0.60f),
                shape = RoundedCornerShape(6.dp)
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val strokeWidth = 0.75.dp.toPx()
            val laserCut = Color(0xFF533E0F).copy(alpha = 0.65f)

            // Outer recessed shadow (chip cavity)
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.25f),
                topLeft = Offset(0f, 0f),
                size = size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // Horizontal center dividing cut
            drawLine(laserCut, Offset(0f, h * 0.5f), Offset(w, h * 0.5f), strokeWidth = strokeWidth)

            // Left vertical cut
            drawLine(laserCut, Offset(w * 0.28f, 0f), Offset(w * 0.28f, h), strokeWidth = strokeWidth)

            // Right vertical cut
            drawLine(laserCut, Offset(w * 0.72f, 0f), Offset(w * 0.72f, h), strokeWidth = strokeWidth)

            // Central contact capsule
            drawRoundRect(
                color = laserCut,
                topLeft = Offset(w * 0.28f, h * 0.22f),
                size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.56f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                style = Stroke(width = strokeWidth)
            )

            // Micro specular sheen across gold surface
            val sheen = Brush.linearGradient(
                colors = listOf(
                    Color.Transparent,
                    Color.White.copy(alpha = 0.25f),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(w * 0.8f, h)
            )
            drawRect(sheen)
        }
    }
}

@Composable
private fun TactileContactlessWave(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(19.dp)) {
        val stroke = Stroke(width = 1.35.dp.toPx(), cap = StrokeCap.Round)
        val color = Color.White.copy(alpha = 0.75f)

        val arcRatios = listOf(0.20f, 0.44f, 0.68f, 0.92f)
        arcRatios.forEach { ratio ->
            val arcSize = size.width * ratio
            val offset = (size.width - arcSize) / 2f
            drawArc(
                color = color,
                startAngle = -42f,
                sweepAngle = 84f,
                useCenter = false,
                topLeft = Offset(offset, offset),
                size = androidx.compose.ui.geometry.Size(arcSize, arcSize),
                style = stroke
            )
        }
    }
}

@Composable
private fun TactileCardNetworkLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 34.dp, height = 20.dp)) {
        val r = size.height * 0.46f
        val centerY = size.height * 0.5f
        val leftX = size.width * 0.38f
        val rightX = size.width * 0.62f

        // Left Red circle
        drawCircle(
            color = Color(0xFFEB001B).copy(alpha = 0.90f),
            radius = r,
            center = Offset(leftX, centerY)
        )
        // Right Yellow/Amber circle
        drawCircle(
            color = Color(0xFFF79E1B).copy(alpha = 0.85f),
            radius = r,
            center = Offset(rightX, centerY)
        )
        // Overlapping intersection
        drawCircle(
            color = Color(0xFFFF5F00).copy(alpha = 0.90f),
            radius = r * 0.70f,
            center = Offset(size.width * 0.5f, centerY)
        )
    }
}

@Composable
fun HomeScreenQuickActionsRow(
    onNavigateToTransactions: () -> Unit,
    onNavigateToBudget: () -> Unit,
    onNavigateToInsights: () -> Unit,
    onNavigateToAIChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current
    val pillBg = MaterialTheme.colorScheme.surface
    val pillBorder = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Deposit Pill Button
        Box(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(pillBg)
                .border(1.dp, pillBorder, RoundedCornerShape(25.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateToTransactions()
                }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(CategoryColors.Groceries.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Deposit",
                        tint = CategoryColors.Groceries,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "Deposit",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 2. Center Transfer / AI Copilot Circular Button
        val centerBg = if (isDark) {
            Brush.linearGradient(
                listOf(
                    DarkColors.SurfaceContainerHighest,
                    DarkColors.BgElevated
                )
            )
        } else {
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                )
            )
        }
        val centerBorder = if (isDark) DarkColors.BorderStrong else Color.Transparent
        val centerIconTint = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White
        val centerSpotColor = if (isDark) Color.Black.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)

        Box(
            modifier = Modifier
                .size(54.dp)
                .shadow(
                    elevation = if (isDark) 4.dp else 10.dp,
                    shape = CircleShape,
                    spotColor = centerSpotColor
                )
                .clip(CircleShape)
                .background(centerBg)
                .border(0.8.dp, centerBorder, CircleShape)
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateToAIChat()
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Insights,
                contentDescription = "BudGi Insights",
                tint = centerIconTint,
                modifier = Modifier.size(24.dp)
            )
        }

        // 3. Withdraw Pill Button
        Box(
            modifier = Modifier
                .weight(1f)
                .height(50.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(pillBg)
                .border(1.dp, pillBorder, RoundedCornerShape(25.dp))
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onNavigateToTransactions()
                }
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(CategoryColors.Food.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Withdraw",
                        tint = CategoryColors.Food,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "Withdraw",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ── BudGi Micro-Spend Catch-Up Card (Zero Hardcoded Array Dummy Data) ─────
@Composable
fun MicroSpendCatchUpCard(
    unreviewedTransactions: List<com.app.domain.model.Transaction>,
    onReviewTransaction: (transactionId: String, newCategory: String, alias: String?) -> Unit,
    onDismissReview: (transactionId: String) -> Unit,
    modifier: Modifier = Modifier,
    currencySymbol: String = "₹"
) {
    if (unreviewedTransactions.isEmpty()) return

    val currentTx = unreviewedTransactions.first()
    val friendlyName = remember(currentTx.id, currentTx.clientId, currentTx.merchant, currentTx.upiId) {
        currentTx.alias?.takeIf { it.isNotBlank() }
            ?: com.app.data.sms.MerchantNormalizer.extractFriendlyName(currentTx.merchant, currentTx.upiId)
    }

    val haptic = LocalHapticFeedback.current
    val isDark = LocalIsDarkTheme.current
    var customAlias by remember(currentTx.id, currentTx.clientId) { mutableStateOf(friendlyName) }
    var isEditingName by remember(currentTx.id, currentTx.clientId) { mutableStateOf(false) }
    var confirmedMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(confirmedMessage) {
        if (confirmedMessage != null) {
            kotlinx.coroutines.delay(2200)
            confirmedMessage = null
        }
    }

    val cardBg = if (isDark) Color(0xFF13161C) else MaterialTheme.colorScheme.surface
    val cardBorder = if (isDark) Color(0xFF222630) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(22.dp))
            .padding(18.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Micro-Spend Catch-Up: ${unreviewedTransactions.size} transactions to categorize"
            }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Row: Minimal Icon + Title + Count Pill + Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDark) Color(0xFF181C26) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .border(1.dp, if (isDark) Color(0xFF282D3C) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Micro-Spend Catch-Up",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFFF59E0B).copy(alpha = 0.35f))
                            ) {
                                Text(
                                    text = "${unreviewedTransactions.size} new",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "1-tap categorize • Learns vendor permanently",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val idToDismiss = currentTx.clientId?.ifEmpty { currentTx.id } ?: currentTx.id
                        onDismissReview(idToDismiss)
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Current Transaction Details Card Plate
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isDark) Color(0xFF161922) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .border(0.8.dp, if (isDark) Color(0xFF222632) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isEditingName) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                OutlinedTextField(
                                    value = customAlias,
                                    onValueChange = { customAlias = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                )
                                IconButton(
                                    onClick = { isEditingName = false },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Done",
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = customAlias.ifBlank { friendlyName },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = "Nickname vendor",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clickable { isEditingName = true }
                                )
                            }
                        }

                        val timeText = remember(currentTx.date, currentTx.timestamp) {
                            com.app.utils.DateUtils.formatDisplayTime(currentTx.date, currentTx.timestamp)
                        }
                        val subtitleTag = buildString {
                            if (!currentTx.upiId.isNullOrBlank()) {
                                append(currentTx.upiId)
                                append(" • ")
                            } else if (!currentTx.accountLast4.isNullOrBlank()) {
                                append("...${currentTx.accountLast4} • ")
                            }
                            append(timeText)
                        }
                        Text(
                            text = subtitleTag,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = "-$currencySymbol${"%,.0f".format(currentTx.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            // Quick 1-Tap Category Pills
            val quickCategories = remember {
                listOf(
                    Pair("☕ Chai/Food", "Food & Dining"),
                    Pair("🥦 Grocery", "Groceries"),
                    Pair("🛺 Travel", "Transport"),
                    Pair("🛍️ Shopping", "Shopping"),
                    Pair("🍿 Fun", "Entertainment")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickCategories.forEach { (label, category) ->
                    val txId = currentTx.clientId?.ifEmpty { currentTx.id } ?: currentTx.id
                    val finalAlias = customAlias.trim().ifBlank { friendlyName }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onReviewTransaction(txId, category, finalAlias)
                            confirmedMessage = "✓ Learned: Future payments to $finalAlias auto-categorize as $category"
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Color(0xFF1B1E26) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(
                            0.8.dp,
                            if (isDark) Color(0xFF282D3C) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 2.dp)
                        )
                    }
                }
            }

            // Confirmation banner with smooth breathing feedback
            AnimatedVisibility(
                visible = confirmedMessage != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF10B981).copy(alpha = 0.12f),
                    border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFF10B981).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = confirmedMessage ?: "",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF34D399),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreenSpendingOverviewCard(
    summary: com.app.domain.model.AnalyticsSummary,
    onSeeInsights: () -> Unit,
    modifier: Modifier = Modifier,
    currencySymbol: String = "₹"
) {
    BudgiInteractiveSpendingRingCard(
        summary = summary,
        onSeeInsights = onSeeInsights,
        currencySymbol = currencySymbol,
        modifier = modifier
    )
}


// ── BudGi Floating Quick Action Pill Bar ─────────────────────────────────

@Composable
fun DashboardFloatingQuickBar(
    isSyncing: Boolean,
    hasPendingSpaces: Boolean,
    isExpanded: Boolean = true,
    onSyncClick: () -> Unit,
    onSpacesClick: () -> Unit,
    onQuickClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val infiniteTransition = rememberInfiniteTransition(label = "quick_sync_spin")
    val spinAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "quick_sync_spin"
    )

    val isDark = LocalIsDarkTheme.current
    val pillBg = MaterialTheme.colorScheme.surface
    val pillBorder = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    // ── Main Capsule Pill: [ Sync ] when collapsed, [ Sync | Spaces | Quick ] when expanded ──
    Surface(
        modifier = modifier
            .animateContentSize(spring(dampingRatio = 0.82f, stiffness = 420f))
            .shadow(
                elevation = if (isDark) 4.dp else 12.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.5f else 0.35f),
                spotColor = if (isDark) Color.Black.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            ),
        shape = RoundedCornerShape(20.dp),
        color = pillBg,
        border = androidx.compose.foundation.BorderStroke(width = 0.8.dp, color = pillBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 1. Sync Button (Always visible on scroll)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isSyncing) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else Color.Transparent
                    )
                    .bouncyClick(scaleDown = 0.92f) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSyncClick()
                    }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = "Sync SMS",
                    tint = if (isSyncing) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(13.dp)
                        .then(if (isSyncing) Modifier.rotate(spinAngle) else Modifier)
                )
                Text(
                    text = if (isSyncing) "Syncing" else "Sync",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Collapsible secondary actions: [ Spaces | Quick ]
            androidx.compose.animation.AnimatedVisibility(
                visible = isExpanded,
                enter = expandHorizontally(
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
                ) + fadeIn(tween(140)),
                exit = shrinkHorizontally(
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)
                ) + fadeOut(tween(100))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Vertical separator
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(13.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    )

                    // 2. Spaces Button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .bouncyClick(scaleDown = 0.92f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSpacesClick()
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                if (hasPendingSpaces) {
                                    Badge(
                                        containerColor = DarkColors.Amber,
                                        modifier = Modifier.size(5.dp)
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = "Spaces",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                        Text(
                            text = "Spaces",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Vertical separator
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(13.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                    )

                    // 3. Quick Action Button (with Chevron Up)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .bouncyClick(scaleDown = 0.92f) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onQuickClick()
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Quick",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Quick Menu",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Profile Sidebar Drawer (Developer-Friendly Obsidian Console) ─────────────────

@Composable
fun ProfileSidebarDrawer(
    state: DashboardUiState,
    onClose: () -> Unit,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit = {},
    isDarkModePreference: Boolean? = null,
    onToggleDarkMode: (Boolean) -> Unit = {},
    onThemePreferenceChange: (Boolean?) -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    var showLogoutConfirm by remember { mutableStateOf(false) }
    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    var dragAccumulated by remember { mutableFloatStateOf(0f) }

    // Clean tokens blended directly with background color
    val drawerBg = MaterialTheme.colorScheme.background
    val drawerBorder = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    val cardBg = if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surface
    val cardBorder = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)

    Box(
        modifier = Modifier
            .fillMaxHeight()
            .fillMaxWidth(0.86f)
            .statusBarsPadding()
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccumulated = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        dragAccumulated += dragAmount
                        if (dragAccumulated < -36f) {
                            change.consume()
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onClose()
                        }
                    }
                )
            }
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.10f),
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.05f)
            )
            .clip(RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp))
            .background(drawerBg)
            .border(
                width = 1.dp,
                color = drawerBorder,
                shape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── 1. Top Bar: Identity + Close ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BudGiLogo(iconSize = 28.dp, showText = false)
                    Text(
                        text = "Account & Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClose()
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(cardBg)
                        .border(1.dp, cardBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Drawer",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // ── 2. User Identity Card (Clean, Minimal & Integrated) ──
            Surface(
                onClick = { onNavigate("profile_account") },
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
                shadowElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val profileImage = state.user?.profileImage
                    if (!profileImage.isNullOrBlank()) {
                        AsyncImage(
                            model = profileImage,
                            contentDescription = "Profile Picture",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        MoodAvatar(
                            mood = state.user?.avatar?.ifBlank { "zen_saver" } ?: "zen_saver",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), CircleShape)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = state.user?.displayName?.ifBlank { "BudGi Member" } ?: "BudGi Member",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = state.user?.handle?.ifBlank { "@member" } ?: "@member",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Clean minimal streak badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(BudgiRadius.Pill))
                                    .background(CategoryColors.Housing.copy(alpha = 0.14f))
                                    .border(0.5.dp, CategoryColors.Housing.copy(alpha = 0.35f), RoundedCornerShape(BudgiRadius.Pill))
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "🔥 ${state.user?.streak ?: 1}d",
                                    color = CategoryColors.Housing,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "View Profile",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // ── Section 1: Finances & Spaces ──
            Text(
                text = "Finances & Spaces",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                ),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                border = androidx.compose.foundation.BorderStroke(0.8.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    DrawerNavRow(
                        icon = Icons.Default.Group,
                        title = "Collaborative Spaces",
                        subtitle = "Shared expenses & group budgets",
                        onClick = { onNavigate("spaces_graph") }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 62.dp, end = 14.dp)
                    )
                    DrawerNavRow(
                        icon = Icons.Outlined.AccountBalance,
                        title = "Linked Bank Accounts",
                        subtitle = "SMS detection & automated tracking",
                        onClick = { onNavigate("profile_banks") }
                    )
                }
            }

            // ── Section 2: Security & Appearance ──
            Text(
                text = "Security & Appearance",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                ),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                border = androidx.compose.foundation.BorderStroke(0.8.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    DrawerNavRow(
                        icon = Icons.Outlined.Person,
                        title = "Personal Information",
                        subtitle = "Name, email, phone & avatar",
                        onClick = { onNavigate("profile_account") }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 62.dp, end = 14.dp)
                    )
                    DrawerNavRow(
                        icon = Icons.Outlined.Fingerprint,
                        title = "Biometrics & Security",
                        subtitle = "App lock, PIN & local authentication",
                        onClick = { onNavigate("profile_security") }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 62.dp, end = 14.dp)
                    )
                    DrawerNavRow(
                        icon = Icons.Outlined.Notifications,
                        title = "Notifications & Alerts",
                        subtitle = "Budget limits & instant alerts",
                        onClick = { onNavigate("profile_notifications") }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 62.dp, end = 14.dp)
                    )
                    DrawerThemeSelector(
                        themePreference = isDarkModePreference,
                        onThemePreferenceChange = { pref ->
                            onThemePreferenceChange(pref)
                            if (pref != null) {
                                onToggleDarkMode(pref)
                            }
                        }
                    )
                }
            }

            // ── Section 3: Help & Support ──
            Text(
                text = "Help & Support",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                ),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                border = androidx.compose.foundation.BorderStroke(0.8.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    DrawerNavRow(
                        icon = Icons.AutoMirrored.Outlined.HelpOutline,
                        title = "Help & Support",
                        subtitle = "FAQs, user guides & direct support",
                        onClick = { onNavigate("profile_help") }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 62.dp, end = 14.dp)
                    )
                    DrawerNavRow(
                        icon = Icons.Outlined.Info,
                        title = "About BudGi",
                        subtitle = "Version, privacy policy & encryption",
                        onClick = { onNavigate("profile_about") }
                    )
                }
            }

            // ── Section 4: Account Actions (Separate Box with Standard Squircle Badges) ──
            Text(
                text = "Account Actions",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                ),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                border = androidx.compose.foundation.BorderStroke(0.8.dp, cardBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    DrawerNavRow(
                        icon = Icons.AutoMirrored.Outlined.Logout,
                        title = "Sign Out",
                        subtitle = "Sign out of your active session",
                        onClick = { showLogoutConfirm = true }
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 62.dp, end = 14.dp)
                    )
                    DrawerNavRow(
                        icon = Icons.Outlined.DeleteOutline,
                        title = "Delete Account Data",
                        subtitle = "Permanently erase account & all data",
                        onClick = { showDeleteAccountConfirm = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Minimal grab handle pill
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .size(width = 3.5.dp, height = 36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
        )
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = {
                Text(
                    text = "Sign Out?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to sign out of your BudGi account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Sign Out", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    // Delete Account Confirmation Dialog
    if (showDeleteAccountConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountConfirm = false },
            title = {
                Text(
                    text = "Delete Account Data?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete your BudGi account? All recorded transactions, budgets, spaces, and personal credentials will be permanently erased. This cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteAccountConfirm = false
                        onDeleteAccount()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete Account", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountConfirm = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

/**
 * 3-Option Appearance Theme Selector (System Default, Dark Mode, Light Mode)
 */
@Composable
fun DrawerThemeSelector(
    themePreference: Boolean?,
    onThemePreferenceChange: (Boolean?) -> Unit,
    modifier: Modifier = Modifier
) {
    val systemIsDark = isSystemInDarkTheme()
    var isExpanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    val currentLabel = when (themePreference) {
        null -> "System default (${if (systemIsDark) "Dark" else "Light"})"
        true -> "Dark mode"
        false -> "Light mode"
    }

    val currentIcon = when (themePreference) {
        null -> Icons.Outlined.PhoneAndroid
        true -> Icons.Outlined.DarkMode
        false -> Icons.Outlined.LightMode
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Main expandable row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isExpanded = !isExpanded
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
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
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDark) DarkColors.SurfaceContainerHighest else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f))
                        .border(0.5.dp, if (isDark) DarkColors.BorderStrong else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = currentIcon,
                        contentDescription = "App Theme",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = "App Theme",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = currentLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        // 3 Selectable Theme Options
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .border(
                        0.8.dp,
                        if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                        RoundedCornerShape(12.dp)
                    )
                    .padding(vertical = 4.dp)
            ) {
                // Option 1: System Default
                ThemeOptionItem(
                    title = "System Default",
                    subtitle = "Follows device appearance (${if (systemIsDark) "Dark" else "Light"})",
                    icon = Icons.Outlined.PhoneAndroid,
                    isSelected = themePreference == null,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onThemePreferenceChange(null)
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                // Option 2: Dark Mode
                ThemeOptionItem(
                    title = "Dark Mode",
                    subtitle = "Always use dark theme",
                    icon = Icons.Outlined.DarkMode,
                    isSelected = themePreference == true,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onThemePreferenceChange(true)
                    }
                )

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.12f),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                // Option 3: Light Mode
                ThemeOptionItem(
                    title = "Light Mode",
                    subtitle = "Always use light theme",
                    icon = Icons.Outlined.LightMode,
                    isSelected = themePreference == false,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onThemePreferenceChange(false)
                    }
                )
            }
        }
    }
}

@Composable
private fun ThemeOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Developer-friendly Drawer Toggle Row for Dark Mode with switch.
 */
@Composable
private fun DrawerToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(!isChecked)
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon in squircle badge (exact same styling)
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) DarkColors.SurfaceContainerHighest else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f))
                .border(0.5.dp, if (isDark) DarkColors.BorderStrong else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        // Title & Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Minimal Switch
        Switch(
            checked = isChecked,
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.surface,
                checkedTrackColor = MaterialTheme.colorScheme.onSurface,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            modifier = Modifier.scale(0.85f)
        )
    }
}

/**
 * Developer-friendly Drawer Nav Row with squircle icon badge and subtle typography.
 */
@Composable
private fun DrawerNavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Icon in squircle badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) DarkColors.SurfaceContainerHighest else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f))
                .border(0.5.dp, if (isDark) DarkColors.BorderStrong else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        // Title & Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Right chevron
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

