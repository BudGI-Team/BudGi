package com.app.presentation.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.app.presentation.viewmodel.DateFilter
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.app.R
import com.app.domain.model.AutonomousActionProposal
import com.app.presentation.ui.theme.*

/**
 * Premium Shimmer Effect
 * Creates a sweeping linear gradient that animates infinitely across the component.
 */
fun Modifier.shimmer(): Modifier = composed {
    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    )

    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    background(
        brush = Brush.linearGradient(
            colors = shimmerColors,
            start = Offset.Zero,
            end = Offset(x = translateAnim, y = translateAnim)
        )
    )
}

@Composable
fun SkeletonCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(16.dp))
            .shimmer()
    )
}

@Composable
fun SkeletonList(count: Int = 3, modifier: Modifier = Modifier) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        repeat(count) {
            SkeletonCard()
        }
    }
}

fun getCategoryIcon(category: String): ImageVector {
    val normalized = category.lowercase()
    return when {
        normalized.contains("food") || normalized.contains("dining") || normalized.contains("restaurant") || 
        normalized.contains("swiggy") || normalized.contains("zomato") || normalized.contains("cafe") || 
        normalized.contains("coffee") || normalized.contains("eats") || normalized.contains("burger") || 
        normalized.contains("pizza") || normalized.contains("bakery") || normalized.contains("snack") -> Icons.Default.Restaurant

        normalized.contains("vegetable") || normalized.contains("mandi") || normalized.contains("sabzi") || 
        normalized.contains("sabji") || normalized.contains("fruits") -> Icons.Default.Eco

        normalized.contains("dairy") || normalized.contains("milk") || normalized.contains("doodh") -> Icons.Default.LocalDrink

        normalized.contains("grocery") || normalized.contains("groceries") || normalized.contains("blinkit") || 
        normalized.contains("zepto") || normalized.contains("instamart") || normalized.contains("bigbasket") || 
        normalized.contains("dmart") || normalized.contains("supermarket") || normalized.contains("kirana") -> Icons.Default.LocalGroceryStore

        normalized.contains("travel") || normalized.contains("transport") || normalized.contains("cab") || 
        normalized.contains("uber") || normalized.contains("ola") || normalized.contains("rapido") || 
        normalized.contains("metro") || normalized.contains("fuel") || normalized.contains("petrol") || 
        normalized.contains("diesel") || normalized.contains("auto") || normalized.contains("taxi") -> Icons.Default.DirectionsCar

        normalized.contains("flight") || normalized.contains("airline") || normalized.contains("indigo") || 
        normalized.contains("air india") || normalized.contains("makemytrip") || normalized.contains("hotel") || 
        normalized.contains("trip") || normalized.contains("vacation") || normalized.contains("tour") -> Icons.Default.Flight

        normalized.contains("shopping") || normalized.contains("amazon") || normalized.contains("flipkart") || 
        normalized.contains("myntra") || normalized.contains("ajio") || normalized.contains("clothing") || 
        normalized.contains("fashion") || normalized.contains("mall") || normalized.contains("store") -> Icons.Default.ShoppingBag

        normalized.contains("utilities") || normalized.contains("bill") || normalized.contains("recharge") || 
        normalized.contains("electricity") || normalized.contains("bolt") || normalized.contains("power") || 
        normalized.contains("bescom") || normalized.contains("water") || normalized.contains("gas") || 
        normalized.contains("broadband") || normalized.contains("wifi") || normalized.contains("airtel") || 
        normalized.contains("jio") -> Icons.Default.Bolt

        normalized.contains("home") || normalized.contains("house") || normalized.contains("rent") ||
        normalized.contains("flat") || normalized.contains("maintenance") || normalized.contains("apartment") -> Icons.Default.Home

        normalized.contains("health") || normalized.contains("medical") || normalized.contains("hospital") || 
        normalized.contains("pharmacy") || normalized.contains("apollo") || normalized.contains("1mg") || 
        normalized.contains("pharmeasy") || normalized.contains("doctor") || normalized.contains("clinic") -> Icons.Default.MedicalServices

        normalized.contains("fitness") || normalized.contains("gym") || normalized.contains("workout") || 
        normalized.contains("cult") || normalized.contains("sports") || normalized.contains("yoga") -> Icons.Default.FitnessCenter

        normalized.contains("gaming") || normalized.contains("game") || normalized.contains("steam") ||
        normalized.contains("playstation") || normalized.contains("xbox") -> Icons.Default.SportsEsports

        normalized.contains("entertainment") || normalized.contains("movie") || normalized.contains("netflix") || 
        normalized.contains("hotstar") || normalized.contains("prime") || normalized.contains("spotify") || 
        normalized.contains("pvr") || normalized.contains("inox") || normalized.contains("bookmyshow") ||
        normalized.contains("music") -> Icons.Default.Movie

        normalized.contains("education") || normalized.contains("course") || normalized.contains("udemy") || 
        normalized.contains("coursera") || normalized.contains("books") || normalized.contains("school") || 
        normalized.contains("college") || normalized.contains("tuition") || normalized.contains("exam") -> Icons.Default.School

        normalized.contains("investment") || normalized.contains("stocks") || normalized.contains("mutual fund") || 
        normalized.contains("groww") || normalized.contains("zerodha") || normalized.contains("sip") || 
        normalized.contains("crypto") || normalized.contains("gold") || normalized.contains("share") -> Icons.AutoMirrored.Filled.TrendingUp

        normalized.contains("salary") || normalized.contains("income") || normalized.contains("deposit") || 
        normalized.contains("dividend") || normalized.contains("cashback") || normalized.contains("refund") ||
        normalized.contains("bonus") -> Icons.Default.Payments

        normalized.contains("transfer") || normalized.contains("upi") || normalized.contains("sent") || 
        normalized.contains("received") || normalized.contains("p2p") || normalized.contains("gpay") || 
        normalized.contains("phonepe") || normalized.contains("paytm") -> Icons.Default.SwapHoriz

        normalized.contains("salon") || normalized.contains("beauty") || normalized.contains("spa") ||
        normalized.contains("grooming") || normalized.contains("haircut") || normalized.contains("barber") -> Icons.Default.Spa

        normalized.contains("domestic") || normalized.contains("maid") || normalized.contains("bai") || 
        normalized.contains("cook") || normalized.contains("laundry") || normalized.contains("dhobi") || 
        normalized.contains("wages") || normalized.contains("cleaner") -> Icons.Default.Handyman

        normalized.contains("pooja") || normalized.contains("puja") || normalized.contains("temple") || 
        normalized.contains("mandir") || normalized.contains("devotion") -> Icons.Default.VolunteerActivism

        normalized.contains("pet") || normalized.contains("vet") || normalized.contains("dog") ||
        normalized.contains("cat") -> Icons.Default.Pets

        normalized.contains("gift") || normalized.contains("donation") || normalized.contains("charity") ||
        normalized.contains("present") -> Icons.Default.CardGiftcard

        normalized.contains("bank") || normalized.contains("atm") || normalized.contains("interest") || 
        normalized.contains("penalty") || normalized.contains("fee") || normalized.contains("charges") ||
        normalized.contains("tax") || normalized.contains("gst") -> Icons.Default.AccountBalance

        else -> {
            val hash = kotlin.math.abs(category.trim().lowercase().hashCode())
            val fallbackIcons = listOf(
                Icons.Default.Category,
                Icons.Default.LocalOffer,
                Icons.Default.Stars,
                Icons.Default.BookmarkBorder,
                Icons.Default.TrackChanges
            )
            fallbackIcons[hash % fallbackIcons.size]
        }
    }
}

fun getCategoryColor(category: String): Color {
    val normalized = category.lowercase().trim()
    return when {
        normalized.contains("food") || normalized.contains("dining") || normalized.contains("swiggy") || 
        normalized.contains("zomato") || normalized.contains("restaurant") || normalized.contains("cafe") || 
        normalized.contains("pizza") || normalized.contains("burger") || normalized.contains("bakery") -> CategoryColors.Food

        normalized.contains("vegetable") || normalized.contains("mandi") || normalized.contains("sabzi") ||
        normalized.contains("sabji") || normalized.contains("fruits") -> CategoryColors.Vegetables

        normalized.contains("dairy") || normalized.contains("milk") || normalized.contains("doodh") -> CategoryColors.Dairy

        normalized.contains("grocery") || normalized.contains("groceries") || normalized.contains("blinkit") || 
        normalized.contains("zepto") || normalized.contains("instamart") || normalized.contains("bigbasket") || 
        normalized.contains("dmart") || normalized.contains("supermarket") || normalized.contains("kirana") -> CategoryColors.Groceries

        normalized.contains("transport") || normalized.contains("travel") || normalized.contains("cab") || 
        normalized.contains("uber") || normalized.contains("ola") || normalized.contains("rapido") || 
        normalized.contains("metro") || normalized.contains("fuel") || normalized.contains("petrol") || 
        normalized.contains("diesel") || normalized.contains("auto") -> CategoryColors.Transport

        normalized.contains("flight") || normalized.contains("hotel") || normalized.contains("trip") || 
        normalized.contains("airline") || normalized.contains("vacation") || normalized.contains("tour") -> CategoryColors.Flight

        normalized.contains("shopping") || normalized.contains("amazon") || normalized.contains("flipkart") || 
        normalized.contains("myntra") || normalized.contains("ajio") || normalized.contains("clothing") || 
        normalized.contains("fashion") -> CategoryColors.Shopping

        normalized.contains("entertainment") || normalized.contains("movie") || normalized.contains("netflix") || 
        normalized.contains("spotify") || normalized.contains("hotstar") || normalized.contains("prime") ||
        normalized.contains("music") -> CategoryColors.Entertainment

        normalized.contains("gaming") || normalized.contains("game") || normalized.contains("steam") ||
        normalized.contains("playstation") -> CategoryColors.Gaming

        normalized.contains("bill") || normalized.contains("utilities") || normalized.contains("recharge") || 
        normalized.contains("electricity") || normalized.contains("broadband") || normalized.contains("wifi") || 
        normalized.contains("water") || normalized.contains("gas") -> CategoryColors.Bills

        normalized.contains("home") || normalized.contains("house") || normalized.contains("rent") ||
        normalized.contains("flat") || normalized.contains("maintenance") -> CategoryColors.Housing

        normalized.contains("health") || normalized.contains("medical") || normalized.contains("pharmacy") || 
        normalized.contains("hospital") || normalized.contains("doctor") || normalized.contains("clinic") -> CategoryColors.Health

        normalized.contains("fitness") || normalized.contains("gym") || normalized.contains("workout") || 
        normalized.contains("cult") || normalized.contains("sports") -> CategoryColors.Fitness

        normalized.contains("education") || normalized.contains("school") || normalized.contains("course") || 
        normalized.contains("college") || normalized.contains("books") || normalized.contains("tuition") -> CategoryColors.Education

        normalized.contains("investment") || normalized.contains("stock") || normalized.contains("mutual fund") || 
        normalized.contains("groww") || normalized.contains("zerodha") || normalized.contains("crypto") || 
        normalized.contains("gold") -> CategoryColors.Investments

        normalized.contains("salary") || normalized.contains("income") || normalized.contains("deposit") || 
        normalized.contains("bonus") || normalized.contains("dividend") || normalized.contains("cashback") -> CategoryColors.Salary

        normalized.contains("transfer") || normalized.contains("upi") || normalized.contains("sent") || 
        normalized.contains("received") || normalized.contains("gpay") || normalized.contains("phonepe") -> CategoryColors.Transfer

        normalized.contains("salon") || normalized.contains("beauty") || normalized.contains("spa") ||
        normalized.contains("grooming") -> CategoryColors.PersonalCare

        normalized.contains("domestic") || normalized.contains("maid") || normalized.contains("bai") ||
        normalized.contains("cook") || normalized.contains("laundry") || normalized.contains("dhobi") ||
        normalized.contains("wages") -> CategoryColors.DomesticStaff

        normalized.contains("pooja") || normalized.contains("puja") || normalized.contains("temple") ||
        normalized.contains("mandir") || normalized.contains("devotion") -> CategoryColors.Devotion

        normalized.contains("pet") || normalized.contains("vet") || normalized.contains("dog") ||
        normalized.contains("cat") -> CategoryColors.Pets

        normalized.contains("subscription") || normalized.contains("membership") || normalized.contains("recurring") -> CategoryColors.Subscriptions

        else -> {
            val dynamicPalette = listOf(
                Color(0xFF5E72E4), // BudGi Slate Blue
                Color(0xFF38A169), // Calm Sage Green
                Color(0xFFE07A5F), // Calm Terracotta
                Color(0xFF4A90E2), // Slate Sky Blue
                Color(0xFF0284C7), // BudGi Calm Sky
                Color(0xFFD97706), // Warm Ochre Amber
                Color(0xFF2A9D8F), // Calming Seafoam Teal
                Color(0xFFD95D74), // Soft Dusty Rose
                Color(0xFF3A9AB7), // Tranquil Cyan
                Color(0xFF5C768D), // Subtle Steel Slate
                Color(0xFF0D9488), // Tranquil Deep Teal
                Color(0xFF2563EB)  // Crisp Slate Cobalt
            )
            val hash = kotlin.math.abs(category.trim().lowercase().hashCode())
            dynamicPalette[hash % dynamicPalette.size]
        }
    }
}

fun getCategoryEmoji(category: String): String {
    val normalized = category.lowercase().trim()
    return when {
        normalized.contains("food") || normalized.contains("dining") || normalized.contains("swiggy") || normalized.contains("zomato") || normalized.contains("restaurant") -> "🍕"
        normalized.contains("vegetable") || normalized.contains("mandi") || normalized.contains("sabzi") || normalized.contains("sabji") -> "🥦"
        normalized.contains("dairy") || normalized.contains("milk") || normalized.contains("doodh") -> "🥛"
        normalized.contains("grocery") || normalized.contains("groceries") || normalized.contains("blinkit") || normalized.contains("zepto") -> "🛒"
        normalized.contains("flight") || normalized.contains("air") || normalized.contains("trip") || normalized.contains("hotel") -> "✈️"
        normalized.contains("transport") || normalized.contains("cab") || normalized.contains("uber") || normalized.contains("ola") || normalized.contains("fuel") -> "🚗"
        normalized.contains("shopping") || normalized.contains("amazon") || normalized.contains("flipkart") || normalized.contains("myntra") -> "🛍️"
        normalized.contains("entertainment") || normalized.contains("movie") || normalized.contains("netflix") || normalized.contains("cinema") -> "🍿"
        normalized.contains("gaming") || normalized.contains("game") -> "🎮"
        normalized.contains("bill") || normalized.contains("electricity") || normalized.contains("recharge") || normalized.contains("wifi") -> "⚡"
        normalized.contains("home") || normalized.contains("rent") || normalized.contains("house") -> "🏡"
        normalized.contains("health") || normalized.contains("medical") || normalized.contains("pharmacy") || normalized.contains("doctor") -> "💊"
        normalized.contains("fitness") || normalized.contains("gym") || normalized.contains("workout") -> "💪"
        normalized.contains("education") || normalized.contains("school") || normalized.contains("course") || normalized.contains("college") -> "🎓"
        normalized.contains("investment") || normalized.contains("stock") || normalized.contains("mutual fund") || normalized.contains("crypto") -> "📈"
        normalized.contains("salary") || normalized.contains("income") || normalized.contains("bonus") -> "💰"
        normalized.contains("transfer") || normalized.contains("upi") || normalized.contains("sent") || normalized.contains("received") -> "🔄"
        normalized.contains("salon") || normalized.contains("beauty") || normalized.contains("spa") || normalized.contains("grooming") || normalized.contains("barber") -> "✂️"
        normalized.contains("domestic") || normalized.contains("maid") || normalized.contains("cook") || normalized.contains("laundry") || normalized.contains("dhobi") -> "🧹"
        normalized.contains("pooja") || normalized.contains("puja") || normalized.contains("temple") || normalized.contains("mandir") || normalized.contains("devotion") -> "🪔"
        normalized.contains("pet") || normalized.contains("vet") -> "🐾"
        normalized.contains("subscription") -> "🔁"
        normalized.contains("gift") || normalized.contains("donation") -> "🎁"
        normalized.contains("bank") || normalized.contains("atm") || normalized.contains("tax") -> "🏦"
        else -> "🏷️"
    }
}

/**
 * Minimal Sleek Category Avatar / Badge
 * BudGi signature category badge: subtle 12% tint background, hairline border, clean tactile interaction.
 */
@Composable
fun CategoryBadgeIcon(
    category: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(14.dp)
) {
    val catColor = getCategoryColor(category)
    val icon = getCategoryIcon(category)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(catColor.copy(alpha = 0.12f))
            .border(1.dp, catColor.copy(alpha = 0.22f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = category,
            tint = catColor,
            modifier = Modifier.size(iconSize)
        )
    }
}

/**
 * Minimal Sleek Category Pill
 * Compact rounded pill badge with micro-icon and subtle category tint.
 */
@Composable
fun CategoryPill(
    category: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val catColor = getCategoryColor(category)
    val icon = getCategoryIcon(category)
    val bg = if (isSelected) catColor.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    val border = if (isSelected) catColor.copy(alpha = 0.50f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    val textColor = if (isSelected) catColor else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, border, CircleShape)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) catColor else textColor.copy(alpha = 0.8f),
            modifier = Modifier.size(13.dp)
        )
        Text(
            text = category,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1
        )
    }
}

// ── App Background ─────────────────────────────────────────────

/**
 * Clean, solid background surface for the app.
 * Uses the Material3 theme background color with no animated blobs or glow effects.
 */
@Composable
fun WalletoBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            content = content
        )
    }
}

// ── Clean Card Modifier ──────────────────────────────────────────

/**
 * A clean, elevated card modifier with solid surface colors and subtle borders.
 * Professional, developer-grade — no glass refraction or animated sheens.
 */
fun Modifier.glassCard(
    cornerRadius: androidx.compose.ui.unit.Dp = Dimens.cardCornerRadius,
    @Suppress("UNUSED_PARAMETER") borderAlpha: Float = 0.12f
): Modifier = composed {
    val isDark = LocalIsDarkTheme.current
    val bgColor = if (isDark) DarkColors.BgElevated else LightColors.BgSurface
    val borderColor = if (isDark) DarkColors.Border else LightColors.Border

    this
        .shadow(
            elevation = if (isDark) 0.dp else 1.5.dp,
            shape = RoundedCornerShape(cornerRadius),
            spotColor = Color.Black.copy(alpha = if (isDark) 0.08f else 0.04f),
            ambientColor = Color.Black.copy(alpha = if (isDark) 0.05f else 0.02f)
        )
        .clip(RoundedCornerShape(cornerRadius))
        .background(bgColor)
        .border(
            width = 0.8.dp,
            color = borderColor,
            shape = RoundedCornerShape(cornerRadius)
        )
}

@Composable
fun LiquidProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(trackColor)
    ) {
        val animatedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 500),
            label = "progress_anim"
        )

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .background(color, shape = CircleShape)
        )
    }
}

// ── BudGi Logo ───────────────────────────────────────────────────

@Composable
fun BudGiLogo(
    modifier: Modifier = Modifier,
    iconSize: androidx.compose.ui.unit.Dp = 40.dp,
    showText: Boolean = true
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.budgi_logo),
            contentDescription = "BudGi Logo",
            modifier = Modifier
                .size(iconSize)
                .clip(CircleShape)
        )
        if (showText) {
            Text(
                text = "BudGi",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontFamily = com.app.presentation.ui.theme.BillabongFont,
                    fontWeight = FontWeight.Normal,
                    fontSize = (iconSize.value * 0.72f).coerceAtLeast(22f).sp,
                    letterSpacing = 0.6.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    }
}

// ── EMV Chip & Contactless logo for realistic physical card feel ─────

@Composable
fun CardChip() {
    Box(
        modifier = Modifier
            .size(width = 38.dp, height = 28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFE2D1A6), // Gold sheen highlights
                        Color(0xFFC5A059),
                        Color(0xFFF1E4C3),
                        Color(0xFFB38F4D)
                    )
                )
            )
            .border(0.8.dp, Color(0xFF8A6F37).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            
            // Draw standard card chip divisions
            drawLine(
                color = Color(0xFF5A441A).copy(alpha = 0.35f),
                start = Offset(0f, h / 2),
                end = Offset(w, h / 2),
                strokeWidth = 1f
            )
            drawLine(
                color = Color(0xFF5A441A).copy(alpha = 0.35f),
                start = Offset(w * 0.32f, 0f),
                end = Offset(w * 0.32f, h),
                strokeWidth = 1f
            )
            drawLine(
                color = Color(0xFF5A441A).copy(alpha = 0.35f),
                start = Offset(w * 0.68f, 0f),
                end = Offset(w * 0.68f, h),
                strokeWidth = 1f
            )
            
            drawRoundRect(
                color = Color(0xFF5A441A).copy(alpha = 0.25f),
                topLeft = Offset(w * 0.32f, h * 0.25f),
                size = androidx.compose.ui.geometry.Size(w * 0.36f, h * 0.5f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(width = 1f)
            )
        }
    }
}

@Composable
fun ContactlessLogo(modifier: Modifier = Modifier, tint: Color = Color.White.copy(alpha = 0.8f)) {
    Canvas(modifier = modifier.size(16.dp)) {
        val w = size.width
        val h = size.height
        
        drawArc(
            color = tint,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(-w * 0.3f, 0f),
            size = androidx.compose.ui.geometry.Size(w * 0.8f, h),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = tint,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(-w * 0.05f, h * 0.15f),
            size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.7f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = tint,
            startAngle = -45f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = Offset(w * 0.2f, h * 0.3f),
            size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.4f),
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun EmbossedText(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Text(
            text = text,
            style = style.copy(color = Color.Black.copy(alpha = 0.4f)),
            modifier = Modifier.offset(x = 1.dp, y = 1.dp)
        )
        Text(
            text = text,
            style = style.copy(color = Color.White.copy(alpha = 0.25f)),
            modifier = Modifier.offset(x = (-0.5).dp, y = (-0.5).dp)
        )
        Text(
            text = text,
            style = style.copy(color = color)
        )
    }
}

// ── Finance Card (3D Slate Sapphire Debit Card) ──────────────────────

@Composable
fun FinanceCard(
    balance: String,
    income: String,
    expense: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "finance_card_3d")
    val tiltX by infiniteTransition.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(4000, easing = androidx.compose.animation.core.EaseInOutSine), RepeatMode.Reverse),
        label = "tilt_x"
    )
    val tiltY by infiniteTransition.animateFloat(
        initialValue = -1.0f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(5000, easing = androidx.compose.animation.core.EaseInOutSine), RepeatMode.Reverse),
        label = "tilt_y"
    )
    val sheenTranslateX by infiniteTransition.animateFloat(
        initialValue = -500f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "sheen_x"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .graphicsLayer {
                rotationX = tiltX
                rotationY = tiltY
                cameraDistance = 16f * density
                shadowElevation = 24.dp.toPx()
                shape = RoundedCornerShape(28.dp)
            },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF151820), // Obsidian charcoal card
                            Color(0xFF191D27),
                            Color(0xFF101217)
                        )
                    )
                )
                .drawBehind {
                    // Liquid glass micro-texture lines
                    val strokeW = 1.dp.toPx()
                    for (i in -10..20) {
                        drawLine(
                            color = Color.White.copy(alpha = 0.02f),
                            start = Offset(i * 50f, 0f),
                            end = Offset(i * 50f - 100f, size.height),
                            strokeWidth = strokeW
                        )
                    }
                    
                    // Subtle radial sheen
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.04f), Color.Transparent),
                            radius = size.width * 0.7f,
                            center = Offset(sheenTranslateX, size.height * 0.4f)
                        ),
                        radius = size.width * 0.7f,
                        center = Offset(sheenTranslateX, size.height * 0.4f)
                    )
                }
                .border(
                    width = 0.8.dp,
                    color = DarkColors.BorderStrong,
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(Dimens.paddingLarge)
                .semantics(mergeDescendants = true) {
                    contentDescription = "Total Balance is $balance. Income is $income. Expense is $expense."
                }
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "BudGi Platinum",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            "DEBIT",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.6f),
                            letterSpacing = 1.sp
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ContactlessLogo(tint = Color.White.copy(alpha = 0.7f))
                        CardChip()
                    }
                }

                Column {
                    Text(
                        "AVAILABLE BALANCE",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                        letterSpacing = 1.sp
                    )
                    Text(
                        balance,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = (-1).sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            "VALUED CUSTOMER",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "EXP 09/31",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 0.5.sp
                        )
                    }
                    EmbossedText(
                        text = "•••• •••• •••• 8820",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
    }
}

@Composable
private fun FinanceStatItem(label: String, value: String, icon: ImageVector, iconColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMedium)) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconColor.copy(alpha = 0.15f))
                .border(1.dp, iconColor.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(18.dp))
        }
        Column {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text(value, color = MaterialTheme.colorScheme.onBackground, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

// ── Transaction Item (Glass) ─────────────────────────────────────

@Composable
fun TransactionItem(
    merchant: String,
    category: String,
    amount: String,
    date: String,
    type: String,
    splitDetails: List<com.app.domain.model.SplitDetail> = emptyList(),
    categorySource: String = "Default",
    isRecurring: Boolean = false,
    isAnomaly: Boolean = false,
    method: String = "",
    accountLast4: String? = null,
    source: String = "",
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isExpense = type.lowercase() == "debit" || type.lowercase() == "expense"
    val catColor = getCategoryColor(category)
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .bouncyClick { onClick() }
            .glassCard(cornerRadius = 16.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "Transaction at $merchant for $amount on $date, category $category."
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CategoryBadgeIcon(category = category, size = 42.dp, iconSize = 20.dp)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = merchant,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    // Sleek minimal category micro-pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(catColor.copy(alpha = 0.12f))
                            .border(0.6.dp, catColor.copy(alpha = 0.25f), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = category,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                            fontWeight = FontWeight.Medium,
                            color = catColor,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Bank Account Last 4 Digits
                    if (!accountLast4.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "••$accountLast4",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Recurring indicator (subtle icon + text)
                    if (isRecurring) {
                        Surface(
                            color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.12f),
                            shape = CircleShape
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Repeat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(9.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "RECURRING",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.tertiary,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    } else if (isAnomaly) {
                        Surface(
                            color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "SPIKE",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, fontWeight = FontWeight.Bold),
                                color = Color(0xFFF59E0B),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else if (splitDetails.isNotEmpty()) {
                        val settledCount = splitDetails.count { it.settled }
                        val allSettled = settledCount == splitDetails.size
                        Surface(
                            color = if (allSettled) CategoryColors.Groceries.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = if (allSettled) "SPLIT (✓)" else "SPLIT ($settledCount/${splitDetails.size})",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp, fontWeight = FontWeight.SemiBold),
                                color = if (allSettled) CategoryColors.Groceries else MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else if (categorySource == "UserLearned") {
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "LEARNED",
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (isExpense) "-₹$amount" else "+₹$amount",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = if (isExpense) MaterialTheme.colorScheme.onBackground else CategoryColors.Groceries,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

// ── Section Header ───────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, onSeeAllClick: () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.semantics { heading() }
        )
        TextButton(onClick = onSeeAllClick) {
            Text(
                "See All",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ── Insight Card (Clean Minimal) ─────────────────────────────────

enum class InsightLens { OVERVIEW, DRIVERS, ACTION }

@Composable
private fun ScoreMetricBar(label: String, value: Int, isDark: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$value%",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                fontWeight = FontWeight.Bold,
                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
            )
        }
        LinearProgressIndicator(
            progress = { value / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
            trackColor = if (isDark) Color(0xFF222632) else MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
fun AISmartInsightCard(
    insight: String,
    modifier: Modifier = Modifier,
    score: Int = 84,
    onCopilotClick: () -> Unit = {}
) {
    val haptic = LocalHapticFeedback.current
    val isDark = isSystemInDarkTheme()
    var selectedLens by remember { mutableStateOf(InsightLens.OVERVIEW) }
    var showScoreDetails by remember { mutableStateOf(false) }

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
                contentDescription = "Financial Intelligence: $insight"
            }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Row: Geometric Icon + Title + Score Pill (Monochrome Obsidian)
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF181C26) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .border(1.dp, if (isDark) Color(0xFF282D3C) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Analytics,
                            contentDescription = "Intelligence",
                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Financial Intelligence",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Real-time engine",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Health Score Pill (Clean White & Monochrome Obsidian)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF181C26) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .border(1.dp, if (isDark) Color(0xFF282D3C) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showScoreDetails = !showScoreDetails
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color.White else MaterialTheme.colorScheme.onSurface)
                        )
                        Text(
                            text = "Score $score",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Expandable Financial Health Telemetry Breakdown
            androidx.compose.animation.AnimatedVisibility(
                visible = showScoreDetails,
                enter = androidx.compose.animation.fadeIn(tween(200)) + androidx.compose.animation.expandVertically(tween(250)),
                exit = androidx.compose.animation.fadeOut(tween(150)) + androidx.compose.animation.shrinkVertically(tween(200))
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isDark) Color(0xFF0F1117) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isDark) Color(0xFF1F2430) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Financial Health Telemetry",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (score >= 80) "Optimal Range" else "Action Needed",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = if (score >= 80) Color(0xFF10B981) else Color(0xFFF59E0B)
                            )
                        }

                        val discipline = (score * 1.05f).toInt().coerceIn(60, 98)
                        val savings = (score * 0.95f).toInt().coerceIn(55, 95)
                        val stability = (score * 1.0f).toInt().coerceIn(50, 96)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ScoreMetricBar(label = "Budget Discipline", value = discipline, isDark = isDark)
                            ScoreMetricBar(label = "Savings Cushion", value = savings, isDark = isDark)
                            ScoreMetricBar(label = "Outflow Stability", value = stability, isDark = isDark)
                        }
                    }
                }
            }

            // Interactive Segmented Lens Strip
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF0F1117) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(
                    0.8.dp,
                    if (isDark) Color(0xFF1F2430) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    InsightLens.values().forEach { lens ->
                        val isSelected = selectedLens == lens
                        val label = when (lens) {
                            InsightLens.OVERVIEW -> "Pulse"
                            InsightLens.DRIVERS -> "Drivers"
                            InsightLens.ACTION -> "Target"
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(
                                    if (isSelected) {
                                        if (isDark) Color(0xFF222632) else MaterialTheme.colorScheme.surface
                                    } else Color.Transparent
                                )
                                .clickable {
                                    if (selectedLens != lens) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedLens = lens
                                    }
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(if (isDark) Color.White else MaterialTheme.colorScheme.onSurface)
                                    )
                                }
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) {
                                        if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                    } else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Interactive Content Body with Crossfade Transition
            androidx.compose.animation.Crossfade(
                targetState = selectedLens,
                animationSpec = tween(220),
                label = "insight_lens_crossfade"
            ) { lens ->
                when (lens) {
                    InsightLens.OVERVIEW -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = insight,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                lineHeight = 21.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDark) Color(0xFF181C26) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (score >= 80) "Runway: Safe" else "Pace: Moderated",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isDark) Color(0xFF181C26) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "Cycle: Real-Time",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    InsightLens.DRIVERS -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Spending velocity indicates discretionary categories are primary drivers this period.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                lineHeight = 21.sp
                            )
                            // Clean minimal progress meter
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Adherence Index",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "$score / 100",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (score / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                    trackColor = if (isDark) Color(0xFF222632) else MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                        }
                    }
                    InsightLens.ACTION -> {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (score >= 80)
                                    "Safe allowance is locked. Keep discretionary weekend outflow capped to finish the month in net surplus."
                                else
                                    "Consider pacing dining and entertainment purchases to preserve safety runway for upcoming bills.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                lineHeight = 21.sp
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Auto-calibrated with active transactions",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Footer Action Row: Minimal Obsidian Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showScoreDetails) "Score reflects 30-day inflow/outflow balance" else "Tap lenses to toggle views",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )

                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCopilotClick()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xFF181C26) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isDark) Color(0xFF282D3C) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Ask BudGi",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Autonomous Copilot Proposal Card for Home/Dashboard.
 * High-aesthetic Swiss Obsidian design with 1-tap native execution.
 */
@Composable
fun CopilotDashboardActionCard(
    proposal: AutonomousActionProposal,
    onApply: (AutonomousActionProposal) -> Unit,
    onDiscuss: (AutonomousActionProposal) -> Unit,
    onDismiss: (AutonomousActionProposal) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current

    val cardBg = if (isDark) Color(0xFF11141A) else MaterialTheme.colorScheme.surface
    val cardBorder = if (isDark) Color(0xFF222634) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(20.dp))
            .padding(18.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${proposal.title}: ${proposal.description}"
            }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header Row: Agent Badge + Context Tag + Dismiss Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0xFF181D26) else MaterialTheme.colorScheme.surfaceVariant)
                            .border(0.8.dp, if (isDark) Color(0xFF2C3242) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_agent_lightning),
                                contentDescription = null,
                                tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "COPILOT ACTION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.8.sp
                                ),
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDark) Color(0xFF141822) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = proposal.subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss(proposal)
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss proposal",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Title & Description
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = proposal.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = proposal.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 19.sp
                )
            }

            // Monospace Telemetry / Metric Box
            when (proposal) {
                is AutonomousActionProposal.BudgetRebalanceProposal -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF0C0E14) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(
                            0.8.dp,
                            if (isDark) Color(0xFF1D2230) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${proposal.fromCategory}: -₹${"%,.0f".format(proposal.amount)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${proposal.toCategory}: +₹${"%,.0f".format(proposal.amount)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                is AutonomousActionProposal.SpaceDebtSettlementProposal -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF0C0E14) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(
                            0.8.dp,
                            if (isDark) Color(0xFF1D2230) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Received from ${proposal.memberName}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₹${"%,.0f".format(proposal.amount)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                                fontWeight = FontWeight.Bold,
                                color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                is AutonomousActionProposal.PaydayBlueprintProposal -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF0C0E14) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(
                            0.8.dp,
                            if (isDark) Color(0xFF1D2230) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• Living Budgets",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "₹${"%,.0f".format(proposal.livingAmount)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "• Savings Goals",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "+₹${"%,.0f".format(proposal.goalsAmount)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }
            }

            // Dual Action Row: [ Confirm & Apply ] + [ Discuss ]
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Primary 1-Tap Execution Pill
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onApply(proposal)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1.3f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_agent_lightning),
                            contentDescription = null,
                            tint = if (isDark) Color.Black else MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = proposal.actionButtonText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.Black else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                // Secondary Discuss Pill
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDiscuss(proposal)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xFF181C26) else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        0.8.dp,
                        if (isDark) Color(0xFF282D3C) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Discuss",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Autonomous Self-Healing Budget Calibration Banner for BudgetScreen.
 */
@Composable
fun SelfHealingBudgetCalibrationBanner(
    proposal: AutonomousActionProposal.BudgetRebalanceProposal,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = if (isDark) Color(0xFF12151D) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isDark) Color(0xFF242938) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_agent_rebalance),
                        contentDescription = null,
                        tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "SELF-HEALING BUDGET",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.8.sp
                        ),
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = "${proposal.toCategory} is straining monthly runway. Rebalance ₹${"%,.0f".format(proposal.amount)} from ${proposal.fromCategory} to preserve balanced month-end targets?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            // Monospace shift preview
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDark) Color(0xFF0C0E14) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                border = androidx.compose.foundation.BorderStroke(
                    0.8.dp,
                    if (isDark) Color(0xFF1E2230) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${proposal.fromCategory}: -₹${"%,.0f".format(proposal.amount)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "⟶",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${proposal.toCategory}: +₹${"%,.0f".format(proposal.amount)}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // 1-Tap Rebalance Button
            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onApply()
                },
                shape = RoundedCornerShape(10.dp),
                color = if (isDark) Color.White else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_agent_lightning),
                        contentDescription = null,
                        tint = if (isDark) Color.Black else MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Rebalance in 1-Tap",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isDark) Color.Black else MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

// ── Empty State ──────────────────────────────────────────────────

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "empty_state_float")
    val floatOffsetY by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = androidx.compose.animation.core.EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "empty_state_float_y"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.paddingLarge)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(Dimens.cardCornerRadius),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
            .clip(RoundedCornerShape(Dimens.cardCornerRadius))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.3f)
                    )
                )
            )
            .border(
                width = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                shape = RoundedCornerShape(Dimens.cardCornerRadius)
            )
            .padding(Dimens.paddingExtraLarge),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingLarge)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .offset(y = floatOffsetY.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Inbox,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Date Filter Selector (Glass Pill) ────────────────────────────

@Composable
fun DateFilterSelector(
    currentFilter: DateFilter,
    onFilterSelected: (DateFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .wrapContentSize(Alignment.TopEnd)
    ) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(Dimens.buttonCornerRadius),
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                0.5.dp,
                MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = Dimens.paddingMedium, vertical = Dimens.spacingSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSmall)
            ) {
                Text(
                    text = currentFilter.displayName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Select Date Filter",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            DateFilter.entries.forEach { filter ->
                DropdownMenuItem(
                    text = {
                        Text(
                            filter.displayName,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    },
                    onClick = {
                        onFilterSelected(filter)
                        expanded = false
                    }
                )
            }
        }
    }
}

// ── Subscriptions Widget (Glass) ─────────────────────────────────

@Composable
fun SubscriptionsWidget(
    subscriptions: List<com.app.domain.model.Transaction>,
    modifier: Modifier = Modifier,
    onSyncClick: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = MaterialTheme.colorScheme.surface
    val cardBorder = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBg)
            .border(1.dp, cardBorder, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
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
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isDark) Color(0xFF181C26) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                            .border(1.dp, if (isDark) Color(0xFF282D3C) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = null,
                            tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Subscriptions",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = if (subscriptions.isNotEmpty()) "${subscriptions.size} active recurring bills" else "Recurring payments & utilities",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDark) Color(0xFF181C26) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .border(1.dp, if (isDark) Color(0xFF282D3C) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                        .clickable { onSyncClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync",
                        tint = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (subscriptions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "No recurring subscriptions detected yet",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Bills from Netflix, Spotify, gym or telecom appear automatically from your SMS.",
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    subscriptions.forEach { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val catColor = getCategoryColor(sub.category)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(catColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = sub.merchant?.take(1)?.uppercase() ?: "🔄",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = catColor
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sub.merchant ?: sub.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${sub.category} • Monthly",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "₹${"%,.0f".format(sub.amount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumCreditCard(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "premium_card_3d")
    val tiltX by infiniteTransition.animateFloat(
        initialValue = -2.5f, targetValue = 2.5f,
        animationSpec = infiniteRepeatable(tween(3500, easing = androidx.compose.animation.core.EaseInOutSine), RepeatMode.Reverse),
        label = "tilt_x"
    )
    val tiltY by infiniteTransition.animateFloat(
        initialValue = -2.5f, targetValue = 2.5f,
        animationSpec = infiniteRepeatable(tween(4500, easing = androidx.compose.animation.core.EaseInOutSine), RepeatMode.Reverse),
        label = "tilt_y"
    )
    val sheenOffsetX by infiniteTransition.animateFloat(
        initialValue = -800f, targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart),
        label = "sheen_offset"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .graphicsLayer {
                rotationX = tiltX
                rotationY = tiltY
                cameraDistance = 16f * density
                shadowElevation = 32.dp.toPx()
                shape = RoundedCornerShape(28.dp)
            },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF151820), // Obsidian charcoal luxury card
                            Color(0xFF191D27),
                            Color(0xFF101217)
                        )
                    )
                )
                .drawBehind {
                    // Subtle brushed metal diagonal texture
                    val h = size.height
                    val spacing = 6.dp.toPx()
                    for (i in -100..100) {
                        drawLine(
                            color = Color(0xFF1A1E26),
                            start = Offset(i * spacing, 0f),
                            end = Offset(i * spacing + h, h),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    
                    // Subtle monochrome sheen highlight
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.05f), Color.Transparent),
                            radius = size.width * 0.8f,
                            center = Offset(sheenOffsetX, size.height * 0.3f)
                        ),
                        radius = size.width * 0.8f,
                        center = Offset(sheenOffsetX, size.height * 0.3f)
                    )
                }
                .border(
                    width = 0.8.dp,
                    color = DarkColors.BorderStrong,
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(Dimens.paddingLarge)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            "BudGi Black", 
                            style = MaterialTheme.typography.titleLarge, 
                            fontWeight = FontWeight.ExtraBold, 
                            letterSpacing = 1.sp,
                            color = Color.White
                        )
                        Text(
                            "PREMIUM CREDIT",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ContactlessLogo(tint = Color.White.copy(alpha = 0.8f))
                        CardChip()
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        EmbossedText(
                            text = "•••• •••• •••• 4092", 
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "MEMBER SINCE 24     EXP 12/29", 
                            style = MaterialTheme.typography.labelMedium, 
                            letterSpacing = 1.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        "WORLD ELITE", 
                        style = MaterialTheme.typography.labelMedium, 
                        fontWeight = FontWeight.Black, 
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun BudGiStoryCard(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "story_card_3d")
    val tiltX by infiniteTransition.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(3800, easing = androidx.compose.animation.core.EaseInOutSine), RepeatMode.Reverse),
        label = "tilt_x"
    )
    val tiltY by infiniteTransition.animateFloat(
        initialValue = -1.5f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(4200, easing = androidx.compose.animation.core.EaseInOutSine), RepeatMode.Reverse),
        label = "tilt_y"
    )
    val blobTranslate by infiniteTransition.animateFloat(
        initialValue = -200f, targetValue = 400f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing), RepeatMode.Reverse),
        label = "blob_translate"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .graphicsLayer {
                rotationX = tiltX
                rotationY = tiltY
                cameraDistance = 16f * density
                shadowElevation = 24.dp.toPx()
                shape = RoundedCornerShape(28.dp)
            },
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0C1929), // Deep BudGi obsidian sky card
                            Color(0xFF0F172A)
                        )
                    )
                )
                .drawBehind {
                    // Animated liquid glass blobs
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF0284C7).copy(alpha = 0.35f), Color.Transparent),
                            center = Offset(blobTranslate, size.height * 0.8f),
                            radius = size.width * 0.6f
                        ),
                        radius = size.width * 0.6f,
                        center = Offset(blobTranslate, size.height * 0.8f)
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.3f), Color.Transparent),
                            center = Offset(size.width - blobTranslate, size.height * 0.2f),
                            radius = size.width * 0.5f
                        ),
                        radius = size.width * 0.5f,
                        center = Offset(size.width - blobTranslate, size.height * 0.2f)
                    )
                    
                    // Liquid glass circuit path lines
                    val w = size.width
                    val h = size.height
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(w * 0.1f, h * 0.1f),
                        end = Offset(w * 0.4f, h * 0.1f),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(w * 0.4f, h * 0.1f),
                        end = Offset(w * 0.6f, h * 0.5f),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(w * 0.6f, h * 0.5f),
                        end = Offset(w * 0.9f, h * 0.5f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFF38BDF8).copy(alpha = 0.4f), Color.Transparent, Color(0xFF0284C7).copy(alpha = 0.3f))
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(Dimens.paddingLarge),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f))
                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Lightbulb, 
                        contentDescription = null, 
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                EmbossedText(
                    text = "COPILOT ACTIVE", 
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.ExtraBold, 
                        letterSpacing = 1.sp
                    ),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "MODEL 3.5 FLASH • OFFLINE SECURED", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

/**
 * BudGi Signature Tactile Monochrome Pill Button
 * Exact design language from Auth and Onboarding screens:
 * - Solid Crisp White in Dark Mode with pitch black text
 * - Deep Obsidian Slate in Light Mode with crisp white text
 * - True 50% Pill Radius
 * - Flat tactile finish (0 elevation) with subtle press feedback
 */
@Composable
fun BudgiPrimaryPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    icon: @Composable (() -> Unit)? = null
) {
    val haptic = LocalHapticFeedback.current
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f

    Button(
        onClick = {
            if (!isLoading) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
        },
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (darkTheme) Color.White else Color(0xFF0F172A),
            contentColor = if (darkTheme) Color.Black else Color.White,
            disabledContainerColor = if (darkTheme) Color.White.copy(alpha = 0.25f) else Color(0xFF0F172A).copy(alpha = 0.20f),
            disabledContentColor = if (darkTheme) Color.White.copy(alpha = 0.40f) else Color(0xFF0F172A).copy(alpha = 0.40f)
        ),
        shape = RoundedCornerShape(BudgiRadius.Pill),
        elevation = ButtonDefaults.buttonElevation(0.dp),
        enabled = enabled && !isLoading
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = if (darkTheme) Color.Black else Color.White,
                strokeWidth = 2.5.dp,
                modifier = Modifier.size(22.dp)
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    icon()
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = text,
                    color = if (enabled) (if (darkTheme) Color.Black else Color.White) else (if (darkTheme) Color.White.copy(alpha = 0.40f) else Color(0xFF0F172A).copy(alpha = 0.40f)),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}
