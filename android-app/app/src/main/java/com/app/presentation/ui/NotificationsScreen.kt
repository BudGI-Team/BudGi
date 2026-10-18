package com.app.presentation.ui

import android.text.format.DateUtils
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.app.data.local.entity.NotificationEntity
import com.app.presentation.viewmodel.NotificationsViewModel
import com.app.presentation.ui.theme.*

/**
 * Premium Obsidian-grade Notifications Screen.
 * Polished, clean, professional activity center matching BudGi's dark luxury theme:
 * - Crisp fintech vector icon badges (no childish emojis)
 * - Category filter rail with clean counts
 * - Obsidian glass cards with left unread accent strip and refined typography
 * - Smooth swipe-to-dismiss with matched geometry
 * - Confirmation dialog on Clear All to prevent accidental data loss
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val notifications by viewModel.notifications.collectAsState()
    val unreadCount by viewModel.unreadCount.collectAsState()
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val haptic = LocalHapticFeedback.current

    val filterOptions = listOf("All", "Transactions", "Budgets", "Security", "Insights", "Goals")

    // Filter notifications based on tab
    val filteredNotifications = remember(notifications, selectedFilter) {
        when (selectedFilter) {
            "Transactions" -> notifications.filter { it.type == "transaction" || it.type == "sync" }
            "Budgets" -> notifications.filter { it.type.contains("budget", true) }
            "Security" -> notifications.filter { it.type.contains("smishing", true) || it.type.contains("security", true) }
            "Insights" -> notifications.filter { it.type.contains("insight", true) || it.type.contains("summary", true) }
            "Goals" -> notifications.filter { it.type.contains("goal", true) }
            else -> notifications
        }
    }

    // Counts per filter category for chip badges
    val filterCounts = remember(notifications) {
        mapOf(
            "All" to notifications.size,
            "Transactions" to notifications.count { it.type == "transaction" || it.type == "sync" },
            "Budgets" to notifications.count { it.type.contains("budget", true) },
            "Security" to notifications.count { it.type.contains("smishing", true) || it.type.contains("security", true) },
            "Insights" to notifications.count { it.type.contains("insight", true) || it.type.contains("summary", true) },
            "Goals" to notifications.count { it.type.contains("goal", true) }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Notifications",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (unreadCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$unreadCount unread",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    if (unreadCount > 0) {
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.markAllAsRead()
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Mark all read",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    if (notifications.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showClearDialog = true
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Horizontal Segmented Filter Rail
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterOptions) { filter ->
                    val isSelected = selectedFilter == filter
                    val count = filterCounts[filter] ?: 0

                    val chipBg = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isDark -> Color(0xFF141721)
                        else -> Color(0xFFF1F5F9)
                    }
                    val chipBorder = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        isDark -> Color(0xFF252A38)
                        else -> Color(0xFFE2E8F0)
                    }
                    val textColor = when {
                        isSelected -> Color.White
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(chipBg)
                            .border(1.dp, chipBorder, RoundedCornerShape(14.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedFilter = filter
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = filter,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = textColor
                            )
                            if (count > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) Color.White.copy(alpha = 0.22f)
                                            else if (isDark) Color(0xFF222838)
                                            else Color(0xFFE2E8F0)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (filteredNotifications.isEmpty()) {
                // Polished Context-Aware Empty State
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        val (emptyIcon, emptyTitle, emptyDesc) = when (selectedFilter) {
                            "Transactions" -> Triple(
                                Icons.AutoMirrored.Outlined.ReceiptLong,
                                "No Transactions Logged",
                                "Automatic SMS-tracked expenses and income will appear here."
                            )
                            "Budgets" -> Triple(
                                Icons.Outlined.PieChart,
                                "No Budget Alerts",
                                "Great pacing! All expenses are comfortably within your budget limits."
                            )
                            "Security" -> Triple(
                                Icons.Outlined.Shield,
                                "No Security Warnings",
                                "Your account is secure. No suspicious activity or scam attempts detected."
                            )
                            "Insights" -> Triple(
                                Icons.Outlined.AutoAwesome,
                                "No Insights Yet",
                                "As you record transactions, AI analytics will generate financial recommendations."
                            )
                            "Goals" -> Triple(
                                Icons.Outlined.EmojiEvents,
                                "No Goal Alerts",
                                "Track savings goals to receive milestone alerts and encouragement."
                            )
                            else -> Triple(
                                Icons.Outlined.NotificationsNone,
                                "All Caught Up",
                                "You have no unread notifications. New alerts, insights, and sync status will appear here."
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    if (isDark) Color(0xFF141824)
                                    else Color(0xFFF1F5F9)
                                )
                                .border(
                                    1.dp,
                                    if (isDark) Color(0xFF222838)
                                    else Color(0xFFE2E8F0),
                                    RoundedCornerShape(22.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = emptyIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(18.dp))
                        Text(
                            text = emptyTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = emptyDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 20.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp)
                ) {
                    items(
                        items = filteredNotifications,
                        key = { it.id }
                    ) { notification ->
                        val dismissState = rememberSwipeToDismissBoxState(
                            confirmValueChange = {
                                if (it == SwipeToDismissBoxValue.EndToStart || it == SwipeToDismissBoxValue.StartToEnd) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.deleteNotification(notification.id)
                                    true
                                } else false
                            }
                        )

                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFFDC2626).copy(alpha = 0.90f))
                                        .padding(horizontal = 22.dp),
                                    contentAlignment = Alignment.CenterEnd
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = "Delete",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        ) {
                            PolishedNotificationCard(
                                item = notification,
                                isDark = isDark,
                                onClick = {
                                    if (!notification.isRead) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.markAsRead(notification.id)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Confirmation dialog before clearing all
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Clear All Notifications?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will permanently remove all notifications from your history. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearAll()
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Cancel")
                }
            },
            containerColor = if (isDark) Color(0xFF161A25) else MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

/**
 * Minimalist, elite fintech notification card:
 * - Left vertical unread status indicator strip
 * - High-grade Material vector iconography tailored to notification type
 * - Crisp category badge pill
 * - Clean typographic hierarchy with no clipped text
 */
@Composable
private fun PolishedNotificationCard(
    item: NotificationEntity,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val (accentColor, categoryTag, iconVector) = remember(item.type) {
        when {
            item.type == "smishing_alert" || item.type.contains("security", true) ->
                Triple(CategoryColors.Food, "SECURITY", Icons.Outlined.Shield)
            item.type.contains("budget_exceeded", true) ->
                Triple(CategoryColors.Housing, "BUDGET ALERT", Icons.Outlined.WarningAmber)
            item.type.contains("budget", true) ->
                Triple(CategoryColors.Housing, "BUDGET", Icons.Outlined.PieChart)
            item.type.contains("goal", true) ->
                Triple(CategoryColors.Groceries, "SAVINGS GOAL", Icons.Outlined.EmojiEvents)
            item.type == "transaction" ->
                Triple(CategoryColors.Transport, "TRANSACTION", Icons.AutoMirrored.Outlined.ReceiptLong)
            item.type == "sync" ->
                Triple(CategoryColors.Bills, "SYNC", Icons.Outlined.Sync)
            item.type.contains("insight", true) || item.type.contains("summary", true) ->
                Triple(CategoryColors.Shopping, "AI INSIGHT", Icons.Outlined.AutoAwesome)
            else ->
                Triple(CategoryColors.Transport, "NOTIFICATION", Icons.Outlined.Notifications)
        }
    }

    val timeAgo = remember(item.timestamp) {
        DateUtils.getRelativeTimeSpanString(
            item.timestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    val cardBg = when {
        !item.isRead -> if (isDark) DarkColors.BgElevated else LightColors.BgSurface
        else -> if (isDark) DarkColors.BgSurface else LightColors.SurfaceContainer
    }
    val cardBorder = when {
        !item.isRead -> if (isDark) DarkColors.BorderStrong else LightColors.BorderStrong
        else -> if (isDark) DarkColors.Border else LightColors.Border
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Subtle Left Unread Accent Strip
            if (!item.isRead) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(accentColor)
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Vector Icon Badge (No childish emojis)
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accentColor.copy(alpha = if (isDark) 0.12f else 0.08f))
                        .border(
                            1.dp,
                            accentColor.copy(alpha = if (isDark) 0.25f else 0.18f),
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // Category Pill Tag & Timestamp
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = categoryTag,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                letterSpacing = 0.5.sp
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = timeAgo,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f),
                                fontSize = 11.sp
                            )
                            if (!item.isRead) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(accentColor)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Title
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (!item.isRead) FontWeight.Bold else FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 19.sp
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Body
                    Text(
                        text = item.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
