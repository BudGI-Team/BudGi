package com.app.presentation.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.app.presentation.viewmodel.AIChatMessage
import com.app.domain.model.CashflowProjection
import com.app.domain.model.CopilotActionIntent
import com.app.domain.util.CopilotMessageParser
import com.app.presentation.viewmodel.AIChatViewModel
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.app.R
import com.app.presentation.ui.theme.*
import org.json.JSONObject
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(
    windowSizeClass: WindowSizeClass,
    onBack: () -> Unit = {},
    viewModel: AIChatViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var inputText by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            coroutineScope.launch {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                navigationIcon = {
                    val isDark = LocalIsDarkTheme.current
                    IconButton(onClick = onBack) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(0.8.dp, if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.budgi_logo),
                            contentDescription = "BudGi Copilot",
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                "BudGi Copilot",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val subtitle = if (uiState.healthScore != null) {
                                "Score: ${uiState.healthScore}/100 • ${uiState.healthLabel ?: "Active"}"
                            } else {
                                "Financial Terminal • Autonomous Copilot"
                            }
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.messages.isNotEmpty()) {
                        val isDark = LocalIsDarkTheme.current
                        var showDeleteDialog by remember { mutableStateOf(false) }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .border(0.8.dp, if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = "Clear history",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (showDeleteDialog) {
                            AlertDialog(
                                onDismissRequest = { showDeleteDialog = false },
                                title = { Text("Clear History") },
                                text = { Text("Are you sure you want to clear your Copilot session history? This cannot be undone.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        showDeleteDialog = false
                                        viewModel.clearHistory()
                                    }) {
                                        Text("Clear", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showDeleteDialog = false }) {
                                        Text("Cancel")
                                    }
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Messages
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.paddingMedium),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMedium),
                contentPadding = PaddingValues(vertical = Dimens.paddingMedium)
            ) {
                if (uiState.messages.isEmpty()) {
                    item {
                        EmptyStateHint(onSuggestionClick = { viewModel.sendSuggestion(it) })
                    }
                }

                items(uiState.messages, key = { it.id }) { message ->
                    // Entrance animation for chat messages
                    var isVisible by remember { mutableStateOf(false) }
                    LaunchedEffect(message.hashCode()) {
                        isVisible = true
                    }
                    
                    val reducedMotion = isReducedMotionEnabled()
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isVisible,
                        enter = if (reducedMotion) androidx.compose.animation.EnterTransition.None else (
                            androidx.compose.animation.fadeIn(tween(300)) + 
                            androidx.compose.animation.scaleIn(
                                initialScale = 0.8f,
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(if (message.role == "user") 1f else 0f, 1f),
                                animationSpec = spring(dampingRatio = 0.65f, stiffness = 400f)
                            )
                        )
                    ) {
                        ChatBubble(
                            message = message,
                            windowSizeClass = windowSizeClass,
                            onExecuteAction = { intent -> viewModel.executeActionIntent(message.id, intent) },
                            onDismissAction = { viewModel.dismissActionIntent(message.id) }
                        )
                    }
                }
            }

            val density = androidx.compose.ui.platform.LocalDensity.current
            val isImeOpen = WindowInsets.ime.getBottom(density) > 0

            // Input bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(bottom = if (isImeOpen) 0.dp else 68.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = Dimens.paddingMedium, vertical = 10.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        placeholder = { Text("Ask about spending, budgets, or projections...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        shape = RoundedCornerShape(20.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(Modifier.width(12.dp))
                    FilledIconButton(
                        onClick = {
                            if (inputText.isNotBlank() && !uiState.isLoading) {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            }
                        },
                        enabled = inputText.isNotBlank() && !uiState.isLoading,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = Color.White,
                            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            disabledContentColor = Color.White.copy(alpha = 0.50f)
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: AIChatMessage,
    windowSizeClass: WindowSizeClass,
    onExecuteAction: (CopilotActionIntent) -> Unit = {},
    onDismissAction: () -> Unit = {}
) {
    val isUser = message.role == "user"
    val isExpanded = windowSizeClass.widthSizeClass == androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Expanded
    val isDark = LocalIsDarkTheme.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.budgi_logo),
                    contentDescription = "BudGi Copilot",
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        val bubbleShape = if (isUser) {
            RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
        } else {
            RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)
        }

        val bubbleBg = if (isUser) {
            if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            if (isDark) DarkColors.BgElevated
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        }

        val bubbleBorder = if (isUser) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
        } else {
            if (isDark) DarkColors.Border
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(if (isExpanded) 0.65f else 0.84f)
                .pointerInput(message.content) {
                    detectTapGestures(
                        onLongPress = {
                            clipboardManager.setText(AnnotatedString(message.content))
                            android.widget.Toast.makeText(context, "Message copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                .clip(bubbleShape)
                .background(bubbleBg)
                .border(0.8.dp, bubbleBorder, bubbleShape)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (message.isLoading) {
                TypingDotsAnimation()
            } else {
                Column {
                    val parsed = remember(message.content) { CopilotMessageParser.parse(message.content) }
                    val effectiveIntent = message.actionIntent ?: parsed.intent
                    val textColor = MaterialTheme.colorScheme.onSurface
                    
                    if (parsed.cleanText.isNotBlank()) {
                        MarkdownText(
                            text = parsed.cleanText,
                            textColor = textColor
                        )
                    }
                    
                    if (parsed.chartJson != null && !isUser) {
                        Spacer(modifier = Modifier.height(12.dp))
                        AIChatChart(parsed.chartJson)
                    }
                    
                    if (message.cashflowProjection != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CashflowProjectionChart(message.cashflowProjection)
                    }

                    if (effectiveIntent != null && !isUser) {
                        Spacer(modifier = Modifier.height(12.dp))
                        CopilotActionCard(
                            intent = effectiveIntent,
                            isExecuted = message.isActionExecuted,
                            onExecute = { onExecuteAction(effectiveIntent) },
                            onDismiss = onDismissAction
                        )
                    }
                }
            }
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .border(0.8.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = "User",
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Animated typing indicator with three bouncing dots
 */
@Composable
private fun TypingDotsAnimation() {
    val reducedMotion = isReducedMotionEnabled()
    val dotCount = 3
    val infiniteTransition = rememberInfiniteTransition(label = "typing")

    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(dotCount) { index ->
            val animatedOffsetY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -6f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = index * 150, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_$index"
            )
            val offsetY = if (reducedMotion) 0f else animatedOffsetY

            val animatedAlpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = index * 150, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha_$index"
            )
            val alpha = if (reducedMotion) 0.8f else animatedAlpha

            val animatedDotScale by infiniteTransition.animateFloat(
                initialValue = 0.5f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = index * 150, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "dot_scale_$index"
            )
            val dotScale = if (reducedMotion) 1f else animatedDotScale

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(y = offsetY.dp)
                    .scale(dotScale)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = alpha)
                    )
            )
        }
    }
}

private val EaseInOutCubic = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

@Composable
private fun EmptyStateHint(onSuggestionClick: (String) -> Unit = {}) {
    val isDark = LocalIsDarkTheme.current
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.budgi_logo),
                contentDescription = "BudGi Copilot",
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "BudGi Copilot",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Autonomous Financial Operating System & Command Terminal",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))

        Text(
            text = "Suggested Questions",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                letterSpacing = 0.5.sp
            ),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 12.dp)
        )

        val suggestions = listOf(
            Triple(
                Icons.Outlined.Shield,
                Color(0xFF10B981),
                "> /afford Can I spend ₹4,500 on weekend dining?"
            ),
            Triple(
                Icons.Outlined.PieChart,
                Color(0xFF38BDF8),
                "> /budget Auto-calibrate smart limits based on spending"
            ),
            Triple(
                Icons.Outlined.SwapHoriz,
                Color(0xFFF59E0B),
                "> /rebalance Rebalance strained budgets from surplus"
            ),
            Triple(
                Icons.AutoMirrored.Outlined.TrendingUp,
                Color(0xFFA855F7),
                "> /forecast 30-day cashflow runway & burn rate"
            )
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            suggestions.forEach { (icon, color, text) ->
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSuggestionClick(text)
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(color.copy(alpha = 0.12f))
                                .border(0.8.dp, color.copy(alpha = 0.25f), RoundedCornerShape(9.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = color,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CopilotActionCard(
    intent: CopilotActionIntent,
    isExecuted: Boolean,
    onExecute: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = LocalIsDarkTheme.current
    val haptic = LocalHapticFeedback.current

    val cardBg = if (isDark) DarkColors.BgSurface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val cardBorder = if (isDark) DarkColors.Border else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val telemetryBg = if (isDark) DarkColors.BgElevated else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(0.8.dp, cardBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Icon + Title + Action Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                val iconRes = when (intent) {
                    is CopilotActionIntent.AdjustBudget -> R.drawable.ic_agent_target
                    is CopilotActionIntent.RebalanceBudget -> R.drawable.ic_agent_rebalance
                    is CopilotActionIntent.CreateGoal -> R.drawable.ic_agent_target
                    is CopilotActionIntent.AffordabilityVerdict -> R.drawable.ic_agent_shield
                    is CopilotActionIntent.BulkCategorize -> R.drawable.ic_notif_edit
                }

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = intent.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isExecuted) Color(0xFF10B981).copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isExecuted) "APPLIED" else "AGENT ACTION",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isExecuted) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reason / Explanation
            Text(
                text = intent.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Telemetry / Diff Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(telemetryBg)
                    .border(0.6.dp, cardBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                when (intent) {
                    is CopilotActionIntent.AdjustBudget -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                intent.category,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (intent.currentLimit != null && intent.currentLimit > 0) {
                                    Text(
                                        "₹${"%,.0f".format(intent.currentLimit)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("➔", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    "₹${"%,.0f".format(intent.proposedLimit)}/mo",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    is CopilotActionIntent.RebalanceBudget -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    intent.fromCategory,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    "-₹${"%,.0f".format(intent.amount)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFEF4444),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    intent.toCategory,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    "+₹${"%,.0f".format(intent.amount)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    is CopilotActionIntent.CreateGoal -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                intent.goalTitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Target: ₹${"%,.0f".format(intent.targetAmount)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    is CopilotActionIntent.AffordabilityVerdict -> {
                        val verdictColor = when (intent.verdict.uppercase()) {
                            "SAFE" -> Color(0xFF10B981)
                            "CAUTION" -> Color(0xFFF59E0B)
                            else -> Color(0xFFEF4444)
                        }
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${intent.itemName} (₹${"%,.0f".format(intent.itemAmount)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    intent.verdict.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = verdictColor
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Safe run-rate: ₹${"%,.0f".format(intent.safeDailySpendAfter)}/day for next ${intent.daysUntilPayday} days",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is CopilotActionIntent.BulkCategorize -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "\"${intent.merchantKeyword}\" (${intent.count} txs)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "➔ ${intent.targetCategory}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            if (isExecuted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_notif_check),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Action Confirmed & Synced",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDismiss()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "Dismiss",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onExecute()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        elevation = ButtonDefaults.buttonElevation(0.dp)
                    ) {
                        Text(
                            if (intent is CopilotActionIntent.AffordabilityVerdict) "Got it" else "Confirm & Apply",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// --- AI Chat Parsing and Visualization Helpers ---

fun parseMessageContent(content: String): Pair<String, JSONObject?> {
    val jsonRegex = Regex("\\{.*\"type\"\\s*:\\s*\"chart\".*\\}", RegexOption.DOT_MATCHES_ALL)
    val match = jsonRegex.find(content)
    
    if (match != null) {
        val jsonStr = match.value
        val textStr = content.replace(jsonStr, "").trim()
        try {
            return Pair(textStr, JSONObject(jsonStr))
        } catch (e: Exception) {
            return Pair(content, null)
        }
    }
    return Pair(content, null)
}

@Composable
fun MarkdownText(text: String, textColor: Color) {
    val annotatedString = buildAnnotatedString {
        val lines = text.split("\n")
        for (i in lines.indices) {
            val line = lines[i]
            when {
                line.startsWith("# ") -> {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp, color = textColor)) {
                        append(line.substring(2))
                    }
                }
                line.startsWith("## ") -> {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, color = textColor)) {
                        append(line.substring(3))
                    }
                }
                line.startsWith("### ") -> {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, fontSize = 18.sp, color = textColor)) {
                        append(line.substring(4))
                    }
                }
                line.startsWith("- ") -> {
                    withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = textColor)) {
                        append("• ")
                    }
                    appendMarkdownLine(line.substring(2), textColor)
                }
                else -> {
                    appendMarkdownLine(line, textColor)
                }
            }
            if (i < lines.size - 1) append("\n")
        }
    }
    Text(
        text = annotatedString,
        color = textColor,
        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
    )
}

fun androidx.compose.ui.text.AnnotatedString.Builder.appendMarkdownLine(line: String, defaultColor: Color) {
    val boldRegex = Regex("\\*\\*(.*?)\\*\\*")
    val linkRegex = Regex("\\[(.*?)\\]\\(.*?\\)") // strip links to just show text, for simplicity
    
    // Simplistic pass: first strip links to raw text, then handle bold
    val strippedLine = line.replace(linkRegex, "$1")
    
    var lastIndex = 0
    val matches = boldRegex.findAll(strippedLine)
    for (match in matches) {
        append(strippedLine.substring(lastIndex, match.range.first))
        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
            append(match.groupValues[1])
        }
        lastIndex = match.range.last + 1
    }
    append(strippedLine.substring(lastIndex))
}

@Composable
fun AIChatChart(jsonObject: JSONObject) {
    val chartType = jsonObject.optString("chartType", "pie")
    val dataArray = jsonObject.optJSONArray("data")
    if (dataArray == null || dataArray.length() == 0) return

    val items = mutableMapOf<String, Double>()
    for (i in 0 until dataArray.length()) {
        val item = dataArray.optJSONObject(i)
        if (item != null) {
            val name = item.optString("name", "Unknown")
            val value = item.optDouble("value", 0.0)
            items[name] = value
        }
    }

    if (chartType == "pie") {
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = Dimens.spacingSmall),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Data Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(Dimens.spacingMedium))
            
            // Draw Pie Chart
            val total = items.values.sum()
            val chartDescription = remember(items) {
                "Pie chart: " + items.entries.joinToString(", ") { "${it.key}: ₹${"%.0f".format(it.value)}" }
            }
            if (total > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = chartDescription
                    }
                ) {
                    Canvas(modifier = Modifier.size(160.dp)) {
                        var startAngle = -90f
                        items.entries.toList().forEach { entry ->
                            val sweepAngle = (entry.value / total * 360f).toFloat()
                            val sliceColor = getCategoryColor(entry.key)
                            drawArc(
                                color = sliceColor,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = false,
                                style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
                            )
                            startAngle += sweepAngle
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(Dimens.spacingMedium))
            
            // Legend
            items.forEach { (name, value) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(getCategoryColor(name))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(name, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("₹${"%,.0f".format(value)}", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CashflowProjectionChart(projection: CashflowProjection) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = Dimens.spacingSmall),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("30-Day Cashflow Forecast", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Avg Burn: ₹${"%.0f".format(projection.averageDailyBurnRate)}/day | Recurring: ₹${"%.0f".format(projection.identifiedRecurringExpenses)}/mo",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLarge))

        val balances = projection.projectedBalances.map { it.projectedBalance }
        val maxBalance = (listOf(projection.currentBalance) + balances).maxOrNull() ?: 1.0
        val minBalance = (listOf(projection.currentBalance) + balances).minOrNull()?.coerceAtMost(0.0) ?: 0.0
        
        val range = (maxBalance - minBalance).coerceAtLeast(1.0)
        
        val lineColor = if (balances.lastOrNull() ?: 0.0 < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        
        Box(
            modifier = Modifier.fillMaxWidth().height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // Draw zero line if minBalance < 0
                if (minBalance < 0) {
                    val zeroY = height - ((0 - minBalance) / range * height).toFloat()
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.5f),
                        start = androidx.compose.ui.geometry.Offset(0f, zeroY),
                        end = androidx.compose.ui.geometry.Offset(width, zeroY),
                        strokeWidth = 2f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                    )
                }
                
                val path = Path()
                val stepX = width / projection.projectedBalances.size.coerceAtLeast(1).toFloat()
                
                // Start at day 0
                val startY = height - ((projection.currentBalance - minBalance) / range * height).toFloat()
                path.moveTo(0f, startY)
                
                projection.projectedBalances.forEachIndexed { index, daily ->
                    val x = (index + 1) * stepX
                    val y = height - ((daily.projectedBalance - minBalance) / range * height).toFloat()
                    path.lineTo(x, y)
                }
                
                drawPath(
                    path = path,
                    color = lineColor,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.height(Dimens.spacingMedium))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Today: ₹${"%.0f".format(projection.currentBalance)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Day 30: ₹${"%.0f".format(balances.lastOrNull() ?: 0.0)}", style = MaterialTheme.typography.labelSmall, color = lineColor, fontWeight = FontWeight.Bold)
        }
    }
}
