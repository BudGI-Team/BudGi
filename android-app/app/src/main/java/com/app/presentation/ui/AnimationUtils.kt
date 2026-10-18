package com.app.presentation.ui

import android.view.MotionEvent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * Premium Spring Animations & Interactive Modifiers
 */
object PremiumSprings {
    // Dynamic bouncy return on release
    val Bouncy = spring<Float>(
        dampingRatio = 0.52f,
        stiffness = 380f
    )
    // Instant, tactile compress on finger down
    val PressDown = spring<Float>(
        dampingRatio = 0.75f,
        stiffness = 800f
    )
    val Snappy = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
    val Fluid = spring<Float>(
        dampingRatio = 0.8f,
        stiffness = 200f
    )
}

/**
 * 3D Tilt Effect: Tilts the element slightly backward and scales it down when pressed.
 */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.tiltOnPress(
    depth: Float = 10f,
    scaleDown: Float = 0.95f
): Modifier = composed {
    val coroutineScope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val rotationX = remember { Animatable(0f) }
    val rotationY = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current

    val reducedMotion = isReducedMotionEnabled()

    if (reducedMotion) {
        this
    } else {
        this
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                this.rotationX = rotationX.value
                this.rotationY = rotationY.value
                cameraDistance = 8 * density
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitFirstDown(requireUnconsumed = false)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        coroutineScope.launch {
                            scale.animateTo(scaleDown, PremiumSprings.PressDown)
                            rotationX.animateTo(depth, PremiumSprings.PressDown)
                        }
                        
                        waitForUpOrCancellation()
                        coroutineScope.launch {
                            scale.animateTo(1f, PremiumSprings.Bouncy)
                            rotationX.animateTo(0f, PremiumSprings.Bouncy)
                            rotationY.animateTo(0f, PremiumSprings.Bouncy)
                        }
                    }
                }
            }
    }
}

/**
 * Bouncy Click: Compresses down snappily on touch, then rebounds with playful rubber-band bounce.
 */
fun Modifier.bouncyClick(
    scaleDown: Float = 0.91f,
    onClick: () -> Unit
): Modifier = composed {
    val reducedMotion = isReducedMotionEnabled()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) scaleDown else 1f,
        animationSpec = if (isPressed) PremiumSprings.PressDown else PremiumSprings.Bouncy,
        label = "bouncy_click_scale"
    )

    LaunchedEffect(isPressed) {
        if (isPressed && !reducedMotion) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            role = androidx.compose.ui.semantics.Role.Button,
            onClick = {
                if (!reducedMotion) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                onClick()
            }
        )
}

/**
 * Bouncy Press: Pure transformation modifier that makes any existing Button or Card feel bouncy.
 */
fun Modifier.bouncyPress(
    interactionSource: MutableInteractionSource,
    scaleDown: Float = 0.92f
): Modifier = composed {
    val reducedMotion = isReducedMotionEnabled()
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) scaleDown else 1f,
        animationSpec = if (isPressed) PremiumSprings.PressDown else PremiumSprings.Bouncy,
        label = "bouncy_press_scale"
    )

    LaunchedEffect(isPressed) {
        if (isPressed && !reducedMotion) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Staggered Entrance Animation for list/grid items.
 */
@Composable
fun Modifier.animateItemAppearance(index: Int): Modifier {
    val reducedMotion = isReducedMotionEnabled()
    if (reducedMotion) return this

    val animatedProgress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        // Stagger delay based on index
        kotlinx.coroutines.delay((index * 50).toLong())
        animatedProgress.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.6f,
                stiffness = 300f
            )
        )
    }

    return this.graphicsLayer {
        alpha = animatedProgress.value
        val scale = 0.9f + (0.1f * animatedProgress.value)
        scaleX = scale
        scaleY = scale
        translationY = 50f * (1f - animatedProgress.value)
    }
}

/**
 * Checks if reduced motion (Remove animations) is enabled in system settings.
 */
@Composable
fun isReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        val durationScale = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        durationScale == 0f
    }
}
