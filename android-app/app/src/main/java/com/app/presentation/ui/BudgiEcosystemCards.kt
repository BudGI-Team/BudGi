package com.app.presentation.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue

/**
 * Ecosystem Card Definition.
 * Simple, grounded, developer-ready copy without AI marketing exaggeration.
 */
data class EcosystemCardItem(
    val id: String,
    val categoryBadge: String,
    val title: String,
    val description: String,
    val ctaText: String,
    val accentColor: Color,
    val iconVector: ImageVector,
    val technicalTag: String,
    val actionType: CardActionType
)

enum class CardActionType {
    COPILOT,
    WEB_PORTAL,
    SHARED_SPACES,
    SMS_SYNC
}

/**
 * High-quality swappable cards showcasing the BudGi ecosystem.
 * - Non-AI generated look: uses custom vector typography and developer-grade telemetry badges
 * - Smooth swipe physics with scale/alpha interpolation and haptic feedback
 * - Adaptive light/dark palette with tactile feedback and deep-link actions
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BudgiEcosystemCardsSection(
    onNavigateToAIChat: () -> Unit,
    onNavigateToSpaces: () -> Unit,
    onManageSmsSync: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    var showWebPortalDialog by remember { mutableStateOf(false) }

    val cards = remember {
        listOf(
            EcosystemCardItem(
                id = "copilot",
                categoryBadge = "AI FINANCIAL COPILOT",
                title = "Ask your ledger anything",
                description = "Check daily safe limits, spot recurring charge spikes, and ask financial questions in plain text.",
                ctaText = "Chat with Copilot",
                accentColor = Color(0xFF38BDF8), // BudGi Signature Sky Cyan
                iconVector = Icons.Outlined.Terminal,
                technicalTag = "AUTONOMOUS",
                actionType = CardActionType.COPILOT
            ),
            EcosystemCardItem(
                id = "sync",
                categoryBadge = "CROSS-DEVICE SYNC",
                title = "One ledger across web & mobile",
                description = "Log transactions on mobile; analyze deep reports on desktop. Real-time updates over WebSocket.",
                ctaText = "Web Dashboard",
                accentColor = Color(0xFF60BCD4), // BudGi Steel Cyan
                iconVector = Icons.Outlined.Devices,
                technicalTag = "REALTIME",
                actionType = CardActionType.WEB_PORTAL
            ),
            EcosystemCardItem(
                id = "spaces",
                categoryBadge = "SHARED SPACES",
                title = "Group budgets & split tabs",
                description = "Manage shared household bills, trip funds, and settle group balances without manual spreadsheets.",
                ctaText = "Explore Spaces",
                accentColor = Color(0xFF1DBF81), // BudGi Muted Green
                iconVector = Icons.Outlined.Groups,
                technicalTag = "COLLABORATIVE",
                actionType = CardActionType.SHARED_SPACES
            ),
            EcosystemCardItem(
                id = "privacy",
                categoryBadge = "LOCAL & PRIVATE",
                title = "Zero-touch SMS transaction sync",
                description = "Bank SMS alerts are parsed locally on your device. Your financial logs never leave your phone.",
                ctaText = "Manage Sync",
                accentColor = Color(0xFFE2B774), // Warm Muted Gold
                iconVector = Icons.Outlined.Security,
                technicalTag = "LOCAL_AES",
                actionType = CardActionType.SMS_SYNC
            )
        )
    }

    val pagerState = rememberPagerState(pageCount = { cards.size })

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // ── Section Header & Animated Page Indicator ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(cards[pagerState.currentPage].accentColor)
                )
                Text(
                    text = "BUDGI ECOSYSTEM",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.70f)
                )
            }

            // Smooth Pill Pager Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                cards.indices.forEach { index ->
                    val isSelected = pagerState.currentPage == index
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 18.dp else 5.dp,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 420f),
                        label = "indicator_w"
                    )
                    val color by animateColorAsState(
                        targetValue = if (isSelected) {
                            cards[index].accentColor
                        } else {
                            if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f)
                        },
                        animationSpec = tween(220),
                        label = "indicator_c"
                    )

                    Box(
                        modifier = Modifier
                            .height(4.5.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ── Swappable Cards Pager ──
        HorizontalPager(
            state = pagerState,
            pageSize = PageSize.Fill,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            val card = cards[page]

            // Calculate swipe offset for smooth scaling and opacity
            val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
            val scale = lerp(0.94f, 1f, 1f - pageOffset.coerceIn(0f, 1f))
            val alpha = lerp(0.75f, 1f, 1f - pageOffset.coerceIn(0f, 1f))

            val cardBg = if (isDark) Color(0xFF13161C) else Color(0xFFFFFFFF)
            val borderStroke = if (isDark) {
                BorderStroke(
                    0.8.dp,
                    Brush.horizontalGradient(
                        listOf(
                            card.accentColor.copy(alpha = 0.22f),
                            Color.White.copy(alpha = 0.05f)
                        )
                    )
                )
            } else {
                BorderStroke(0.8.dp, Color.Black.copy(alpha = 0.08f))
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        this.alpha = alpha
                    }
                    .shadow(
                        elevation = if (isDark) 4.dp else 2.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Color.Black.copy(alpha = 0.40f),
                        spotColor = Color.Transparent
                    ),
                shape = RoundedCornerShape(20.dp),
                color = cardBg,
                border = borderStroke
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Column: Category badge, title, concise description, action button
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    ) {
                        // Badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isDark) Color(0xFF181C26) else card.accentColor.copy(alpha = 0.08f),
                            border = BorderStroke(0.6.dp, card.accentColor.copy(alpha = 0.30f))
                        ) {
                            Text(
                                text = card.categoryBadge,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                color = card.accentColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Title
                        Text(
                            text = card.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 19.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Grounded developer description
                        Text(
                            text = card.description,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            lineHeight = 16.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Tactile CTA Button
                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                when (card.actionType) {
                                    CardActionType.COPILOT -> onNavigateToAIChat()
                                    CardActionType.WEB_PORTAL -> showWebPortalDialog = true
                                    CardActionType.SHARED_SPACES -> onNavigateToSpaces()
                                    CardActionType.SMS_SYNC -> onManageSmsSync()
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDark) Color(0xFF181C26) else card.accentColor.copy(alpha = 0.10f),
                            border = BorderStroke(0.8.dp, card.accentColor.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = card.ctaText,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isDark) Color(0xFFF1F5F9) else card.accentColor
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = card.accentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    // Right Column: Authentic Obsidian Vector Badge (Anti-AI developer aesthetic)
                    Box(
                        modifier = Modifier
                            .size(width = 100.dp, height = 110.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isDark) {
                                    Brush.radialGradient(
                                        colors = listOf(
                                            card.accentColor.copy(alpha = 0.18f),
                                            Color(0xFF0F1117)
                                        )
                                    )
                                } else {
                                    Brush.radialGradient(
                                        colors = listOf(
                                            card.accentColor.copy(alpha = 0.12f),
                                            Color(0xFFF1F5F9)
                                        )
                                    )
                                }
                            )
                            .border(
                                1.dp,
                                Brush.verticalGradient(
                                    listOf(
                                        card.accentColor.copy(alpha = 0.40f),
                                        Color.Transparent
                                    )
                                ),
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(card.accentColor.copy(alpha = 0.15f))
                                    .border(1.dp, card.accentColor.copy(alpha = 0.35f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = card.iconVector,
                                    contentDescription = null,
                                    tint = card.accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = card.technicalTag,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                color = card.accentColor.copy(alpha = 0.9f)
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Web Portal Connectivity Dialog ──
    if (showWebPortalDialog) {
        AlertDialog(
            onDismissRequest = { showWebPortalDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.Devices,
                    contentDescription = null,
                    tint = Color(0xFF06B6D4),
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "BudGi Web Portal",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Access your full financial cockpit on any computer browser. Enjoy multi-column data grids, interactive 3D wealth visualizations, and instant synchronization with your mobile ledger.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Local Web Client: http://localhost:5173",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWebPortalDialog = false
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://localhost:5173"))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Launch Browser", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWebPortalDialog = false }) {
                    Text("Close")
                }
            },
            shape = RoundedCornerShape(18.dp),
            containerColor = if (isDark) Color(0xFF161922) else MaterialTheme.colorScheme.surface
        )
    }
}
