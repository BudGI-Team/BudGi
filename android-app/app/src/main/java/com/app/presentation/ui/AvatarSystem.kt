package com.app.presentation.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

@Composable
fun MoodAvatar(
    mood: String,
    modifier: Modifier = Modifier.size(100.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "avatar_anim")
    
    // Smooth breathing float animation
    val floatOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float"
    )

    // Pulse scale for particles/halos
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    // Rotation angle for orbits
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2 + floatOffset
        val r = w * 0.35f

        // Draw based on Mood selection
        when (mood.lowercase().trim()) {
            "zen_saver", "neutral", "zen", "calm" -> {
                // 1. ZEN SAVER (Calm, Sky/Cyan gradient, Glowing Halo)
                val haloRadius = r * 1.25f * pulseScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFF59E0B).copy(alpha = 0.25f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = haloRadius
                    ),
                    radius = haloRadius,
                    center = Offset(cx, cy)
                )

                // Head
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF38BDF8), Color(0xFF0369A1))
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )

                // Closed Meditating Eyes (curved paths)
                val eyeWidth = w * 0.08f
                val eyeHeight = h * 0.03f
                drawPath(
                    path = Path().apply {
                        moveTo(cx - w * 0.16f, cy - h * 0.05f)
                        quadraticBezierTo(cx - w * 0.12f, cy - h * 0.05f + eyeHeight, cx - w * 0.08f, cy - h * 0.05f)
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = Path().apply {
                        moveTo(cx + w * 0.08f, cy - h * 0.05f)
                        quadraticBezierTo(cx + w * 0.12f, cy - h * 0.05f + eyeHeight, cx + w * 0.16f, cy - h * 0.05f)
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )

                // Soft Smile
                drawPath(
                    path = Path().apply {
                        moveTo(cx - w * 0.06f, cy + h * 0.08f)
                        quadraticBezierTo(cx, cy + h * 0.12f, cx + w * 0.06f, cy + h * 0.08f)
                    },
                    color = Color.White.copy(alpha = 0.8f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            
            "wealthy_hype", "happy" -> {
                // 2. WEALTHY HYPE (Excited, Gold/Crimson, Sparkling orbits)
                val orbitRadius = r * 1.2f
                val orbitalX = cx + orbitRadius * kotlin.math.cos(Math.toRadians(rotationAngle.toDouble())).toFloat()
                val orbitalY = cy + orbitRadius * kotlin.math.sin(Math.toRadians(rotationAngle.toDouble())).toFloat()
                
                // Sparkle particles
                drawCircle(Color(0xFFF59E0B), radius = 6.dp.toPx() * pulseScale, center = Offset(orbitalX, orbitalY))
                drawCircle(Color(0xFF3B82F6), radius = 4.dp.toPx(), center = Offset(cx - orbitRadius * kotlin.math.cos(Math.toRadians(rotationAngle.toDouble() + 180)).toFloat(), cy - orbitRadius * kotlin.math.sin(Math.toRadians(rotationAngle.toDouble() + 180)).toFloat()))

                // Head
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFF59E0B), Color(0xFF7F1D1D))
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )

                // Star Eyes
                fun drawStar(x: Float, y: Float, size: Float) {
                    val path = Path().apply {
                        moveTo(x, y - size)
                        lineTo(x + size * 0.3f, y - size * 0.3f)
                        lineTo(x + size, y)
                        lineTo(x + size * 0.3f, y + size * 0.3f)
                        lineTo(x, y + size)
                        lineTo(x - size * 0.3f, y + size * 0.3f)
                        lineTo(x - size, y)
                        lineTo(x - size * 0.3f, y - size * 0.3f)
                        close()
                    }
                    drawPath(path, color = Color.White)
                }
                drawStar(cx - w * 0.12f, cy - h * 0.05f, w * 0.06f)
                drawStar(cx + w * 0.12f, cy - h * 0.05f, w * 0.06f)

                // Excited Mouth
                drawPath(
                    path = Path().apply {
                        moveTo(cx - w * 0.08f, cy + h * 0.06f)
                        quadraticBezierTo(cx, cy + h * 0.18f, cx + w * 0.08f, cy + h * 0.06f)
                        close()
                    },
                    color = Color.White
                )
            }
            
            "stressed_spender", "sad" -> {
                // 3. STRESSED SPENDER (Worried, Red/Charcoal, dripping sweat)
                // Head
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFFEF4444), Color(0xFF111827))
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )

                // Angled Worried Eyes (lines in V shape)
                drawPath(
                    path = Path().apply {
                        moveTo(cx - w * 0.16f, cy - h * 0.03f)
                        lineTo(cx - w * 0.10f, cy - h * 0.06f)
                        lineTo(cx - w * 0.06f, cy - h * 0.03f)
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
                drawPath(
                    path = Path().apply {
                        moveTo(cx + w * 0.06f, cy - h * 0.03f)
                        lineTo(cx + w * 0.10f, cy - h * 0.06f)
                        lineTo(cx + w * 0.16f, cy - h * 0.03f)
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )

                // Dripping Sweat Drop
                val sweatY = cy - h * 0.05f + (pulseScale * 8.dp.toPx())
                drawPath(
                    path = Path().apply {
                        moveTo(cx + w * 0.15f, sweatY)
                        quadraticBezierTo(cx + w * 0.18f, sweatY + h * 0.06f, cx + w * 0.15f, sweatY + h * 0.08f)
                        quadraticBezierTo(cx + w * 0.11f, sweatY + h * 0.06f, cx + w * 0.15f, sweatY)
                        close()
                    },
                    color = Color(0xFF60A5FA)
                )

                // Wavy Worried Mouth
                drawPath(
                    path = Path().apply {
                        moveTo(cx - w * 0.08f, cy + h * 0.09f)
                        quadraticBezierTo(cx - w * 0.04f, cy + h * 0.06f, cx, cy + h * 0.09f)
                        quadraticBezierTo(cx + w * 0.04f, cy + h * 0.12f, cx + w * 0.08f, cy + h * 0.09f)
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            
            "calculated_brain", "excited" -> {
                // 4. CALCULATED BRAIN (Emerald, Glasses, Glowing lightbulb/idea aura)
                val bulbRadius = 8.dp.toPx() * pulseScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF34D399).copy(alpha = 0.3f), Color.Transparent),
                        center = Offset(cx, cy - r - 12.dp.toPx()),
                        radius = 24.dp.toPx()
                    ),
                    radius = 24.dp.toPx(),
                    center = Offset(cx, cy - r - 12.dp.toPx())
                )
                // Small glow bulb above head
                drawCircle(Color(0xFF34D399), radius = bulbRadius, center = Offset(cx, cy - r - 12.dp.toPx()))

                // Head
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF10B981), Color(0xFF064E3B))
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )

                // Glasses Frames
                val glassesY = cy - h * 0.04f
                val glassesRadius = w * 0.09f
                drawCircle(Color.White, radius = glassesRadius, center = Offset(cx - w * 0.12f, glassesY), style = Stroke(width = 3.dp.toPx()))
                drawCircle(Color.White, radius = glassesRadius, center = Offset(cx + w * 0.12f, glassesY), style = Stroke(width = 3.dp.toPx()))
                drawLine(Color.White, start = Offset(cx - w * 0.03f, glassesY), end = Offset(cx + w * 0.03f, glassesY), strokeWidth = 3.dp.toPx())

                // Tiny dot eyes inside glasses
                drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(cx - w * 0.12f, glassesY))
                drawCircle(Color.White, radius = 2.5.dp.toPx(), center = Offset(cx + w * 0.12f, glassesY))

                // Calculated thin line mouth
                drawLine(
                    color = Color.White.copy(alpha = 0.8f),
                    start = Offset(cx - w * 0.06f, cy + h * 0.08f),
                    end = Offset(cx + w * 0.06f, cy + h * 0.08f),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
            
            "budgi_mascot", "saving" -> {
                // 5. BUDGI MASCOT (Cute, Cyan/Sapphire, Cute Blush + Sprout Leaf)
                // Sprout leaf
                val sproutAngle = floatOffset * 2f
                drawPath(
                    path = Path().apply {
                        moveTo(cx, cy - r)
                        quadraticBezierTo(cx - w * 0.08f, cy - r - 12.dp.toPx(), cx - w * 0.04f, cy - r - 20.dp.toPx())
                        quadraticBezierTo(cx + w * 0.02f, cy - r - 14.dp.toPx(), cx, cy - r)
                    },
                    color = Color(0xFF10B981)
                )

                // Head
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF06B6D4), Color(0xFF1E3A8A))
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )

                // Big Cute Anime Eyes (Large circle with small highlights)
                val eyeRadius = w * 0.05f
                drawCircle(Color.White, radius = eyeRadius, center = Offset(cx - w * 0.12f, cy - h * 0.04f))
                drawCircle(Color.White, radius = eyeRadius, center = Offset(cx + w * 0.12f, cy - h * 0.04f))
                
                // Black pupils
                drawCircle(Color(0xFF0F172A), radius = eyeRadius * 0.8f, center = Offset(cx - w * 0.12f, cy - h * 0.04f))
                drawCircle(Color(0xFF0F172A), radius = eyeRadius * 0.8f, center = Offset(cx + w * 0.12f, cy - h * 0.04f))
                
                // White sparkle reflections
                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(cx - w * 0.14f, cy - h * 0.05f))
                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(cx + w * 0.10f, cy - h * 0.05f))

                // Pink blushing cheeks
                drawCircle(Color(0xFFF472B6).copy(alpha = 0.5f), radius = 5.dp.toPx(), center = Offset(cx - w * 0.2f, cy + h * 0.02f))
                drawCircle(Color(0xFFF472B6).copy(alpha = 0.5f), radius = 5.dp.toPx(), center = Offset(cx + w * 0.2f, cy + h * 0.02f))

                // Cat-like smile mouth
                drawPath(
                    path = Path().apply {
                        moveTo(cx - w * 0.05f, cy + h * 0.06f)
                        quadraticBezierTo(cx - w * 0.025f, cy + h * 0.09f, cx, cy + h * 0.06f)
                        quadraticBezierTo(cx + w * 0.025f, cy + h * 0.09f, cx + w * 0.05f, cy + h * 0.06f)
                    },
                    color = Color.White,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            else -> {
                // Default fallback: Always display BudGi Mascot avatar
                val sproutAngle = floatOffset * 2f
                drawPath(
                    path = Path().apply {
                        moveTo(cx, cy - r)
                        quadraticBezierTo(cx - w * 0.08f, cy - r - 12.dp.toPx(), cx - w * 0.04f, cy - r - 20.dp.toPx())
                        quadraticBezierTo(cx + w * 0.02f, cy - r - 14.dp.toPx(), cx, cy - r)
                    },
                    color = Color(0xFF10B981)
                )

                // Head
                drawCircle(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF06B6D4), Color(0xFF1E3A8A))
                    ),
                    radius = r,
                    center = Offset(cx, cy)
                )

                // Big Cute Anime Eyes (Large circle with small highlights)
                val eyeRadius = w * 0.05f
                drawCircle(Color.White, radius = eyeRadius, center = Offset(cx - w * 0.12f, cy - h * 0.04f))
                drawCircle(Color.White, radius = eyeRadius, center = Offset(cx + w * 0.12f, cy - h * 0.04f))
                
                // Black pupils
                drawCircle(Color(0xFF0F172A), radius = eyeRadius * 0.8f, center = Offset(cx - w * 0.12f, cy - h * 0.04f))
                drawCircle(Color(0xFF0F172A), radius = eyeRadius * 0.8f, center = Offset(cx + w * 0.12f, cy - h * 0.04f))
                
                // White sparkle reflections
                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(cx - w * 0.14f, cy - h * 0.05f))
                drawCircle(Color.White, radius = 2.dp.toPx(), center = Offset(cx + w * 0.10f, cy - h * 0.05f))

                // Pink blushing cheeks
                drawCircle(Color(0xFFF472B6).copy(alpha = 0.5f), radius = 5.dp.toPx(), center = Offset(cx - w * 0.2f, cy + h * 0.02f))
                drawCircle(Color(0xFFF472B6).copy(alpha = 0.5f), radius = 5.dp.toPx(), center = Offset(cx + w * 0.2f, cy + h * 0.02f))

                // Cat-like smile mouth
                drawPath(
                    path = Path().apply {
                        moveTo(cx - w * 0.05f, cy + h * 0.06f)
                        quadraticBezierTo(cx - w * 0.025f, cy + h * 0.09f, cx, cy + h * 0.06f)
                        quadraticBezierTo(cx + w * 0.025f, cy + h * 0.09f, cx + w * 0.05f, cy + h * 0.06f)
                    },
                    color = Color.White,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}
