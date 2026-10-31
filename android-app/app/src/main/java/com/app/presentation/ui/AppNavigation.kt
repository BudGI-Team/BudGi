package com.app.presentation.ui

import android.content.Context

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.app.domain.repository.AuthRepository
import com.app.presentation.viewmodel.DashboardViewModel
import com.app.presentation.viewmodel.NotificationsViewModel

import com.app.R
import com.app.presentation.ui.theme.*

private data class AppDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val destinations = listOf(
    AppDestination("dashboard", R.string.dashboard_title, Icons.Filled.Home, Icons.Outlined.Home),
    AppDestination("transactions", R.string.transactions_title, Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    AppDestination("goals", R.string.goals_title, Icons.Filled.Flag, Icons.Outlined.Flag),
    AppDestination("budget", R.string.budget_title, Icons.Filled.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    AppDestination("ai_chat", R.string.copilot_title, Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome)
)

/**
 * Continuous 3D flipping AI coin icon that alternates between the BudGi Logo
 * and glowing capital letters "AI", giving a rich, futuristic AI identity in the dock.
 */
/**
 * Precision Developer-Friendly Copilot Icon
 * Clean, geometric obsidian terminal node with subtle emerald telemetry ring when active.
 * Zero tacky flipping 3D letters or artificial AI gimmicks.
 */
@Composable
fun BudGiCopilotCoinIcon(
    selected: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val emerald = Color(0xFF10B981)
    val cyan = Color(0xFF06B6D4)

    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = if (isDark) Color(0xFF0F172A) else Color.White,
            border = androidx.compose.foundation.BorderStroke(
                width = if (selected) 1.5.dp else 1.dp,
                brush = if (selected) {
                    Brush.sweepGradient(listOf(emerald, cyan, emerald))
                } else {
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )
                    )
                }
            ),
            shadowElevation = if (selected) 4.dp else 0.dp,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.budgi_logo),
                    contentDescription = "BudGi Copilot",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }
    }
}

// Increment this to force all users to see the onboarding again
private const val ONBOARDING_VERSION = 2

@Composable
fun FinanceAppRoot(
    windowSizeClass: WindowSizeClass,
    smsPermissionGranted: Boolean,
    onRequestPermission: () -> Unit,
    authRepository: AuthRepository,
    onCheckForUpdates: () -> Unit = {}
) {
    val navController = rememberNavController()
    val viewModel: DashboardViewModel = hiltViewModel()
    val notificationsViewModel: NotificationsViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val unreviewedTransactions by viewModel.unreviewedTransactions.collectAsStateWithLifecycle()
    val unreadCount by notificationsViewModel.unreadCount.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val haptic = LocalHapticFeedback.current
    var showProfileDrawer by remember { mutableStateOf(false) }
    var returnToSidebarOnBack by remember { mutableStateOf(false) }
    val themePreference by viewModel.themePreference.collectAsStateWithLifecycle()

    DisposableEffect(navController) {
        val listener = androidx.navigation.NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.route == "dashboard" && returnToSidebarOnBack) {
                showProfileDrawer = true
                returnToSidebarOnBack = false
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose {
            navController.removeOnDestinationChangedListener(listener)
        }
    }

    val isExpanded = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val isMedium = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Medium
    val showNavigationRail = isMedium || isExpanded

    // Auto-hide bottom dock on scroll down, show on scroll up
    var isDockScrollVisible by remember { mutableStateOf(true) }
    LaunchedEffect(currentDestination?.route) {
        isDockScrollVisible = true
    }

    val dockNestedScrollConnection = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: androidx.compose.ui.input.nestedscroll.NestedScrollSource
            ): Offset {
                val delta = available.y
                if (delta < -8f) { // Scrolling down -> dock disappears
                    if (isDockScrollVisible) {
                        isDockScrollVisible = false
                    }
                } else if (delta > 8f) { // Scrolling up -> dock reappears
                    if (!isDockScrollVisible) {
                        isDockScrollVisible = true
                    }
                }
                return Offset.Zero
            }
        }
    }

    val isLoggedIn = remember { authRepository.isLoggedIn }
    val hasSeenOnboarding = remember {
        val prefs = navController.context.getSharedPreferences("budgi_prefs", Context.MODE_PRIVATE)
        prefs.getInt("onboarding_version", 0) >= ONBOARDING_VERSION
    }
    val startDestination = "splash"

    // If logged in but hasn't seen new onboarding, navigate there
    // Redirect logic moved to AnimatedSplashScreen onSplashComplete callback

    // Determine if we're on a main-graph screen (show bottom nav)
    val isMainScreen = currentDestination?.route !in listOf("login", "signup", "onboarding", "forgot_password", "splash")

    WalletoBackground {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        Row(modifier = Modifier.fillMaxSize()) {
            if (showNavigationRail && isMainScreen) {
                val railBorderColor = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier
                        .fillMaxHeight()
                        .drawBehind {
                            drawLine(
                                color = railBorderColor,
                                start = Offset(size.width, 0f),
                                end = Offset(size.width, size.height),
                                strokeWidth = 0.8.dp.toPx()
                            )
                        },
                    header = {
                        Spacer(modifier = Modifier.height(24.dp))
                        BudGiLogo(iconSize = 32.dp, showText = false)
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                ) {
                    destinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { 
                            it.route == destination.route || 
                            (destination.route == "profile_graph" && it.route?.startsWith("profile") == true) ||
                            (destination.route == "spaces_graph" && it.route?.startsWith("spaces") == true) 
                        } == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (!selected) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                if (destination.route == "ai_chat") {
                                    BudGiCopilotCoinIcon(selected = selected, isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f)
                                } else {
                                    Icon(
                                        if (selected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = androidx.compose.ui.res.stringResource(destination.labelRes)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = androidx.compose.ui.res.stringResource(destination.labelRes),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onSurface,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                            )
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                val drawerTransition = updateTransition(
                    targetState = showProfileDrawer,
                    label = "profileDrawerTransition"
                )

                // 3D Canvas dynamic depth for main screen content behind drawer
                val bgScale by drawerTransition.animateFloat(
                    transitionSpec = {
                        if (targetState) {
                            spring(dampingRatio = 0.82f, stiffness = 420f)
                        } else {
                            tween(durationMillis = 240, easing = FastOutLinearInEasing)
                        }
                    },
                    label = "bgScale"
                ) { if (it) 0.94f else 1.0f }

                val bgCorner by drawerTransition.animateDp(
                    transitionSpec = {
                        if (targetState) {
                            spring(dampingRatio = 0.82f, stiffness = 420f)
                        } else {
                            tween(durationMillis = 240, easing = FastOutLinearInEasing)
                        }
                    },
                    label = "bgCorner"
                ) { if (it) 22.dp else 0.dp }

                val bgShift by drawerTransition.animateDp(
                    transitionSpec = {
                        if (targetState) {
                            spring(dampingRatio = 0.82f, stiffness = 420f)
                        } else {
                            tween(durationMillis = 240, easing = FastOutLinearInEasing)
                        }
                    },
                    label = "bgShift"
                ) { if (it) 24.dp else 0.dp }

                val scrimAlpha by drawerTransition.animateFloat(
                    transitionSpec = {
                        if (targetState) {
                            tween(durationMillis = 260, easing = LinearOutSlowInEasing)
                        } else {
                            tween(durationMillis = 220, easing = FastOutLinearInEasing)
                        }
                    },
                    label = "scrimAlpha"
                ) { if (it) 0.68f else 0.0f }

                val drawerSlideFraction by drawerTransition.animateFloat(
                    transitionSpec = {
                        if (targetState) {
                            spring(dampingRatio = 0.80f, stiffness = 450f)
                        } else {
                            tween(durationMillis = 240, easing = FastOutLinearInEasing)
                        }
                    },
                    label = "drawerSlideFraction"
                ) { if (it) 0.0f else -1.0f }

                // Content Canvas (NavHost + Floating Dock with 3D Depth)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = bgScale
                            scaleY = bgScale
                            translationX = bgShift.toPx()
                            clip = bgCorner > 0.dp
                            shape = RoundedCornerShape(bgCorner)
                        }
                        .nestedScroll(dockNestedScrollConnection)
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.fillMaxSize(),
                    enterTransition = { 
                        fadeIn(tween(200, easing = LinearOutSlowInEasing)) + 
                        scaleIn(initialScale = 0.95f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                    },
                    exitTransition = { 
                        fadeOut(tween(200, easing = LinearOutSlowInEasing)) + 
                        scaleOut(targetScale = 1.05f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                    },
                    popEnterTransition = {
                        fadeIn(tween(200, easing = LinearOutSlowInEasing)) + 
                        scaleIn(initialScale = 1.05f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                    },
                    popExitTransition = {
                        fadeOut(tween(200, easing = LinearOutSlowInEasing)) + 
                        scaleOut(targetScale = 0.95f, animationSpec = tween(200, easing = FastOutSlowInEasing))
                    }
                ) {
                    composable("splash") {
                        AnimatedSplashScreen(
                            onSplashComplete = {
                                val nextDest = if (isLoggedIn) {
                                    if (hasSeenOnboarding) "main_graph" else "onboarding"
                                } else {
                                    "auth_graph"
                                }
                                navController.navigate(nextDest) {
                                    popUpTo("splash") { inclusive = true }
                                }
                            }
                        )
                    }

                    navigation(startDestination = "login", route = "auth_graph") {
                        composable("login") {
                            LoginScreen(
                                onNavigateToSignUp = { navController.navigate("signup") },
                                onNavigateToForgotPassword = { navController.navigate("forgot_password") },
                                onLoginSuccess = {
                                    navController.navigate("onboarding") {
                                        popUpTo("auth_graph") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("signup") {
                            SignUpScreen(
                                onNavigateToLogin = { navController.popBackStack() },
                                onSignUpSuccess = {
                                    navController.navigate("onboarding") {
                                        popUpTo("auth_graph") { inclusive = true }
                                    }
                                }
                            )
                        }
                        composable("forgot_password") {
                            ForgotPasswordScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }

                    // Onboarding — top-level route accessible from auth and main
                    composable("onboarding") {
                        OnboardingScreen(
                            onComplete = {
                                // Mark onboarding as seen
                                navController.context.getSharedPreferences("budgi_prefs", Context.MODE_PRIVATE)
                                    .edit().putInt("onboarding_version", ONBOARDING_VERSION).apply()
                                navController.navigate("main_graph") {
                                    popUpTo("onboarding") { inclusive = true }
                                }
                            }
                        )
                    }

                    navigation(startDestination = "dashboard", route = "main_graph") {
                        composable(
                            route = "dashboard",
                            deepLinks = listOf(
                                navDeepLink { uriPattern = "budgi://sync" }
                            )
                        ) {
                            DashboardScreen(
                                windowSizeClass = windowSizeClass,
                                state = state,
                                unreviewedTransactions = unreviewedTransactions,
                                unreadCount = unreadCount,
                                smsPermissionGranted = smsPermissionGranted,
                                onRequestPermission = onRequestPermission,
                                onReadSmsClicked = viewModel::readAndProcessSms,
                                onSeeAllTransactions = { navController.navigate("transactions") },
                                onNavigateToTransactions = { navController.navigate("transactions") },
                                onNavigateToBudget = { navController.navigate("budget") },
                                onNavigateToInsights = { navController.navigate("insights") },
                                onNavigateToAIChat = { navController.navigate("ai_chat") },
                                onNavigateToSpaces = { navController.navigate("spaces_graph") },
                                onNavigateToNotifications = { navController.navigate("profile_notifications") },
                                onReviewTransaction = viewModel::quickReviewTransaction,
                                onDismissReview = viewModel::dismissReview,
                                onApplyProposal = viewModel::executeProposal,
                                onDismissProposal = viewModel::dismissProposal,
                                onDateFilterChanged = viewModel::setDateFilter,
                                onNavigateToProfileRoute = { route -> navController.navigate(route) },
                                onLogout = {
                                    navController.navigate("auth_graph") {
                                        popUpTo("main_graph") { inclusive = true }
                                    }
                                },
                                onOpenDrawer = { showProfileDrawer = true }
                            )
                        }
                        composable(
                            route = "transactions",
                            deepLinks = listOf(
                                navDeepLink { uriPattern = "budgi://add_transaction" },
                                navDeepLink { uriPattern = "budgi://transactions" }
                            )
                        ) {
                            TransactionsScreen(
                                windowSizeClass = windowSizeClass,
                                transactions = state.transactions,
                                isLoading = state.loading,
                                onClearActivity = viewModel::clearActivity,
                                onAddTransaction = viewModel::addTransaction,
                                onEditCategory = viewModel::applyCategoryToMerchant,
                                onScanReceipt = viewModel::scanReceipt,
                                onImportCsv = viewModel::importCsv
                            )
                        }
                        composable(
                            route = "insights",
                            deepLinks = listOf(navDeepLink { uriPattern = "budgi://insights" })
                        ) {
                            val navCurrencySymbol = remember(state.user?.currency) {
                                when (state.user?.currency?.uppercase()) {
                                    "USD" -> "$"
                                    "EUR" -> "€"
                                    "GBP" -> "£"
                                    "AED" -> "د.إ "
                                    else -> "₹"
                                }
                            }
                            ReportsScreen(
                                windowSizeClass = windowSizeClass,
                                summary = state.analyticsSummary,
                                currencySymbol = navCurrencySymbol,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            route = "budget",
                            deepLinks = listOf(navDeepLink { uriPattern = "budgi://budget" })
                        ) {
                            BudgetScreen(windowSizeClass = windowSizeClass)
                        }
                        composable(
                            route = "goals",
                            deepLinks = listOf(navDeepLink { uriPattern = "budgi://goals" })
                        ) {
                            val goalsCurrencySymbol = remember(state.user?.currency) {
                                when (state.user?.currency?.uppercase()) {
                                    "USD" -> "$"
                                    "EUR" -> "€"
                                    "GBP" -> "£"
                                    "AED" -> "د.إ "
                                    else -> "₹"
                                }
                            }
                            GoalsScreen(
                                windowSizeClass = windowSizeClass,
                                currencySymbol = goalsCurrencySymbol
                            )
                        }
                        composable(
                            route = "ai_chat",
                            deepLinks = listOf(
                                navDeepLink { uriPattern = "budgi://ai_chat" }
                            )
                        ) {
                            AIChatScreen(
                                windowSizeClass = windowSizeClass,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        
                        navigation(startDestination = "spaces", route = "spaces_graph") {
                            composable("spaces") {
                                com.app.presentation.ui.spaces.CollaborativeSpacesScreen(
                                    windowSizeClass = windowSizeClass,
                                    onNavigateToSpace = { id -> navController.navigate("space_detail/$id") }
                                )
                            }
                            composable("space_detail/{spaceId}") { backStackEntry ->
                                val spaceId = backStackEntry.arguments?.getString("spaceId") ?: return@composable
                                com.app.presentation.ui.spaces.SpaceDetailScreen(
                                    spaceId = spaceId,
                                    windowSizeClass = windowSizeClass,
                                    onBack = { navController.popBackStack() },
                                    onInvite = { navController.navigate("space_invite/$spaceId") }
                                )
                            }
                            composable("space_invite/{spaceId}") { backStackEntry ->
                                val spaceId = backStackEntry.arguments?.getString("spaceId") ?: return@composable
                                com.app.presentation.ui.spaces.InviteMemberScreen(
                                    spaceId = spaceId,
                                    onBack = { navController.popBackStack() }
                                )
                            }
                            composable(
                                route = "join_space?code={code}",
                                deepLinks = listOf(navDeepLink { uriPattern = "budgi://invite?code={code}" })
                            ) { backStackEntry ->
                                val code = backStackEntry.arguments?.getString("code") ?: ""
                                com.app.presentation.ui.spaces.JoinSpaceScreen(
                                    inviteCode = code,
                                    onJoined = {
                                        navController.navigate("spaces") {
                                            popUpTo("spaces_graph") { inclusive = true }
                                        }
                                    },
                                    onCancel = {
                                        navController.navigate("dashboard") {
                                            popUpTo("dashboard") { inclusive = true }
                                        }
                                    }
                                )
                            }
                        }

                        navigation(startDestination = "profile", route = "profile_graph") {
                            composable("profile") {
                                ProfileScreen(
                                    windowSizeClass = windowSizeClass,
                                    onNavigate = { route -> navController.navigate(route) },
                                    onLogout = {
                                        navController.navigate("auth_graph") {
                                            popUpTo("main_graph") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("profile_security") {
                                SecurityScreen(
                                    onBack = { navController.popBackStack() },
                                    onAccountDeleted = {
                                        navController.navigate("auth_graph") {
                                            popUpTo("main_graph") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("profile_password") { PasswordScreen(onBack = { navController.popBackStack() }) }
                            composable("profile_help") { HelpCenterScreen(onBack = { navController.popBackStack() }) }
                            composable("profile_about") { AboutScreen(onBack = { navController.popBackStack() }) }
                            composable("profile_notifications") { NotificationsScreen(onBack = { navController.popBackStack() }) }
                            composable("profile_account") { AccountScreen(onBack = { navController.popBackStack() }) }
                            composable("profile_device") { DeviceScreen(onBack = { navController.popBackStack() }) }
                            composable("profile_banks") { BanksScreen(onBack = { navController.popBackStack() }) }
                            composable("profile_settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
                        }
                    }
                }

                // ─── Elevated Modern Floating Dock ───
                val primaryTabs = remember { setOf("dashboard", "transactions", "goals", "budget", "profile_graph", "profile", "spaces", "ai_chat") }
                val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
                val isDockVisible = !showNavigationRail && (currentDestination?.route in primaryTabs) && !showProfileDrawer && !isImeVisible && isDockScrollVisible
                androidx.compose.animation.AnimatedVisibility(
                    visible = isDockVisible,
                    enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(220, easing = LinearOutSlowInEasing)) + fadeIn(tween(180)),
                    exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(180, easing = FastOutLinearInEasing)) + fadeOut(tween(140)),
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    val dockHaptic = LocalHapticFeedback.current
                    var lastClickTime by remember { mutableLongStateOf(0L) }
                    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                    val scrimColor = MaterialTheme.colorScheme.background

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        // ── Clean Theme-Matching Bottom Scrim / Shadow Veil ──
                        // Smoothly fades background content scrolling behind the dock so it never looks cluttered
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(148.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.Transparent,
                                            0.30f to (if (isDark) scrimColor.copy(alpha = 0.45f) else scrimColor.copy(alpha = 0.55f)),
                                            0.65f to (if (isDark) scrimColor.copy(alpha = 0.88f) else scrimColor.copy(alpha = 0.92f)),
                                            1.0f to scrimColor
                                        )
                                    )
                                )
                        )

                        // ── Elevated Modern Floating Dock ──
                        Box(
                            modifier = Modifier
                                .navigationBarsPadding()
                                .padding(start = 18.dp, end = 18.dp, bottom = 12.dp)
                                .widthIn(max = 520.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(58.dp)
                                    .shadow(
                                        elevation = 16.dp,
                                        shape = RoundedCornerShape(29.dp),
                                        spotColor = Color.Black.copy(alpha = if (isDark) 0.65f else 0.16f),
                                        ambientColor = Color.Black.copy(alpha = if (isDark) 0.50f else 0.10f)
                                    )
                                    .clip(RoundedCornerShape(29.dp))
                                    .background(if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surface)
                                    .border(
                                        width = 0.8.dp,
                                        color = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(29.dp)
                                    )
                                    .padding(horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                            destinations.forEach { destination ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.route == destination.route || 
                                    (destination.route == "profile_graph" && it.route?.startsWith("profile") == true) ||
                                    (destination.route == "spaces_graph" && it.route?.startsWith("spaces") == true)
                                } == true

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.90f)
                                            .fillMaxHeight(0.85f)
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(
                                                if (selected) {
                                                    if (isDark) DarkColors.SurfaceContainerHighest
                                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                                } else Color.Transparent
                                            )
                                            .then(
                                                if (selected && isDark) Modifier.border(0.6.dp, DarkColors.BorderStrong, RoundedCornerShape(20.dp))
                                                else Modifier
                                            )
                                            .bouncyClick(scaleDown = 0.88f) {
                                                val now = System.currentTimeMillis()
                                                if (now - lastClickTime > 350) {
                                                    lastClickTime = now
                                                    dockHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    if (!selected) {
                                                        returnToSidebarOnBack = false
                                                        navController.navigate(destination.route) {
                                                            popUpTo(navController.graph.findStartDestination().id) {
                                                                saveState = true
                                                            }
                                                            launchSingleTop = true
                                                            restoreState = true
                                                        }
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            if (destination.route == "ai_chat") {
                                                BudGiCopilotCoinIcon(selected = selected, isDark = isDark)
                                            } else {
                                                Icon(
                                                    imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                                    contentDescription = androidx.compose.ui.res.stringResource(destination.labelRes),
                                                    tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))

                                            Text(
                                                text = androidx.compose.ui.res.stringResource(destination.labelRes),
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1
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

                // ── BackHandler to close drawer smoothly with system back ──
                BackHandler(enabled = showProfileDrawer) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showProfileDrawer = false
                }

                // ── High-Performance GPU-Accelerated Profile Sidebar Drawer Overlay ──
                if (drawerTransition.currentState || drawerTransition.targetState) {
                    // Scrim with smooth fade & touch dismiss
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = scrimAlpha
                            }
                            .background(Color.Black)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showProfileDrawer = false
                            }
                    )

                    // Drawer sliding panel
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = drawerSlideFraction * size.width
                            }
                    ) {
                        ProfileSidebarDrawer(
                            state = state,
                            onClose = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showProfileDrawer = false
                            },
                            onNavigate = { route ->
                                returnToSidebarOnBack = true
                                showProfileDrawer = false
                                navController.navigate(route)
                            },
                            onLogout = {
                                showProfileDrawer = false
                                viewModel.logout {
                                    navController.navigate("auth_graph") {
                                        popUpTo("main_graph") { inclusive = true }
                                    }
                                }
                            },
                            onDeleteAccount = {
                                showProfileDrawer = false
                                viewModel.deleteAccount {
                                    navController.navigate("auth_graph") {
                                        popUpTo("main_graph") { inclusive = true }
                                    }
                                }
                            },
                            isDarkModePreference = themePreference,
                            onToggleDarkMode = { enabled ->
                                viewModel.setDarkMode(enabled)
                            },
                            onThemePreferenceChange = { pref ->
                                viewModel.setThemePreference(pref)
                            },
                            onCheckUpdates = {
                                showProfileDrawer = false
                                onCheckForUpdates()
                            }
                        )
                    }
                }
            }
        }
    }
}
