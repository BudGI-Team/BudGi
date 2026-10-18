package com.app.presentation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * BudGi Shapes — Liquid Glass / Water-Drop Pill aesthetic.
 * High-radius pill shapes for cards, buttons, inputs — creating
 * a smooth, organic, water-drop feel throughout the UI.
 */
val BudgiShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),   // Chips, tags, badges
    small = RoundedCornerShape(16.dp),        // Small cards, inputs
    medium = RoundedCornerShape(20.dp),       // Standard cards
    large = RoundedCornerShape(24.dp),        // Hero cards, glass panels
    extraLarge = RoundedCornerShape(32.dp),   // Full-width banners, modals
)

// Additional shape constants for the liquid glass aesthetic
object BudgiRadius {
    val Small = 12.dp      // Chips, tags, badges
    val Medium = 16.dp     // Inputs, small cards
    val Large = 20.dp      // Cards, dialogs
    val ExtraLarge = 24.dp // Hero cards, glass panels
    val Pill = 9999.dp     // Pills, FABs, bottom nav, buttons
}
