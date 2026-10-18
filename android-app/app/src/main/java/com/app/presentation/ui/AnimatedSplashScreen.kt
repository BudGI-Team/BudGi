package com.app.presentation.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.R
import com.app.presentation.ui.theme.DarkColors
import com.app.presentation.ui.theme.Dimens
import kotlinx.coroutines.delay

/**
 * ─────────────────────────────────────────────────────────────
 *  BudGi Splash — Cinematic Brand Signature
 * ─────────────────────────────────────────────────────────────
 *  Design Language: BudGi signature cinematic entrance.
 *  - Organic, filmic pacing (~2.35s total duration) with confident hold.
 *  - Zero "AI Design" artifacts (no fake neon shockwaves, no awkward card borders).
 *  - Uses genuine BudGi branding logo with deep atmospheric radial light bloom.
 *  - Decisive filmic easing curves: smooth scale-in, elegant upward typographic reveal,
 *    and a subtle camera push-in dissolve.
 * ─────────────────────────────────────────────────────────────
 */

@Composable
fun AnimatedSplashScreen(onSplashComplete: () -> Unit) {
    // 0 = Initial, 1 = Emblem & Ambient Bloom, 2 = Typographic Reveal & Hold, 3 = Cinematic Dissolve, 4 = Done
    var stage by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    // ── Film-Paced Cinematic Motion Choreography ─────────────
    LaunchedEffect(Unit) {
        stage = 1 // Emblem & ambient bloom emerges smoothly
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        delay(700)

        stage = 2 // Typographic reveal with subtle tracking and confident hold
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        delay(1100)

        stage = 3 // Cinematic push-in dissolve
        delay(550)

        stage = 4 // Completed
        onSplashComplete()
    }

    // ── Smooth Cinematic Transitions ─────────────────────────
    val logoScale by animateFloatAsState(
        targetValue = when (stage) {
            0 -> 0.80f
            1, 2 -> 1.0f
            3, 4 -> 1.06f
            else -> 1.0f
        },
        animationSpec = if (stage <= 1) {
            tween(durationMillis = 700, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f))
        } else if (stage == 3) {
            tween(durationMillis = 550, easing = FastOutSlowInEasing)
        } else {
            tween(durationMillis = 300, easing = FastOutSlowInEasing)
        },
        label = "logo_scale"
    )

    val logoAlpha by animateFloatAsState(
        targetValue = when (stage) {
            0 -> 0f
            1, 2 -> 1f
            3, 4 -> 0f
            else -> 1f
        },
        animationSpec = tween(
            durationMillis = if (stage >= 3) 500 else 600,
            easing = if (stage >= 3) FastOutSlowInEasing else LinearOutSlowInEasing
        ),
        label = "logo_alpha"
    )

    // Cinematic ambient radial illumination alpha
    val ambientAlpha by animateFloatAsState(
        targetValue = when (stage) {
            0 -> 0f
            1, 2 -> 1f
            3, 4 -> 0f
            else -> 1f
        },
        animationSpec = tween(durationMillis = 800, easing = LinearOutSlowInEasing),
        label = "ambient_alpha"
    )

    // Dynamic Typography Tracking (Letter-Spacing)
    val trackingSp by animateFloatAsState(
        targetValue = if (stage >= 2) 2.4f else 4.0f,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "tracking_sp"
    )

    // Text alpha and translation
    val textAlpha by animateFloatAsState(
        targetValue = if (stage == 2) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (stage >= 3) 450 else 550,
            easing = FastOutSlowInEasing
        ),
        label = "text_alpha"
    )

    val textTranslationY by animateFloatAsState(
        targetValue = if (stage >= 2) 0f else 16f,
        animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing),
        label = "text_y"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val baseBg = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(baseBg),
        contentAlignment = Alignment.Center
    ) {
        // ── Layer 1: Natural Cinematic Depth ──────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f - 24.dp.toPx()
            val maxRadius = size.width * 0.85f

            // Bottom-left cyan ambient light
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF0284C7).copy(alpha = (if (isDark) 0.20f else 0.08f) * ambientAlpha),
                        Color.Transparent
                    ),
                    center = Offset(0f, size.height),
                    radius = size.width * 0.95f
                ),
                radius = size.width * 0.95f,
                center = Offset(0f, size.height)
            )
            // Bottom-right warm amber ambient light
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFEA580C).copy(alpha = (if (isDark) 0.16f else 0.06f) * ambientAlpha),
                        Color.Transparent
                    ),
                    center = Offset(size.width, size.height),
                    radius = size.width * 0.95f
                ),
                radius = size.width * 0.95f,
                center = Offset(size.width, size.height)
            )

            // Center brand bloom
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0.0f to primaryColor.copy(alpha = (if (isDark) 0.18f else 0.08f) * ambientAlpha),
                        0.40f to primaryColor.copy(alpha = (if (isDark) 0.06f else 0.02f) * ambientAlpha),
                        0.85f to Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = maxRadius
                ),
                radius = maxRadius,
                center = Offset(cx, cy)
            )
        }

        // ── Layer 2: Hero Emblem & Brand Typography ─────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .offset(y = (-16).dp)
                .graphicsLayer {
                    scaleX = logoScale
                    scaleY = logoScale
                    alpha = logoAlpha
                }
        ) {
            // Standalone Hero Emblem
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        spotColor = Color.Black.copy(alpha = 0.45f),
                        ambientColor = Color.Black.copy(alpha = 0.25f)
                    )
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color.White.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.budgi_logo),
                    contentDescription = "BudGi Logo",
                    modifier = Modifier
                        .size(86.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Typographic Hierarchy (Confident, clean brand identity)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha
                    translationY = textTranslationY
                }
            ) {
                Text(
                    text = "BudGi",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = com.app.presentation.ui.theme.BillabongFont,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.5.sp,
                        fontSize = 44.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Financial Intelligence",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
