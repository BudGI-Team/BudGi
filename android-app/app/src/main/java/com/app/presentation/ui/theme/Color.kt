package com.app.presentation.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════
// BUDGI DESIGN SYSTEM — Clean Minimal Theme
// Refined obsidian minimal theme with cyan primary,
// teal secondary, green accent — clean developer-grade aesthetic
// ═══════════════════════════════════════════════════

// ── Dark Theme Colors (Primary — Liquid Glass) ────────

object DarkColors {
    val BgBase = Color(0xFF08090C)           // True deep obsidian pitch dark canvas
    val BgSurface = Color(0xFF0E1015)        // Subdued surface elevation
    val BgElevated = Color(0xFF13161C)       // Layered charcoal card surface
    val BgOverlay = Color(0xFF181C24)        // Overlay / dialogs
    val BgCard = Color(0xFF13161C)           // Card interior
    val DialogBg = Color(0xFF181C24)         // Dialog background

    // Surface container hierarchy (Material3 elevation levels)
    val SurfaceContainer = Color(0xFF0E1015)
    val SurfaceContainerHigh = Color(0xFF181C24)    // For dialogs, sheets
    val SurfaceContainerHighest = Color(0xFF1E232E)  // Highest elevation (pills, badges, squircles)

    val Accent = Color(0xFF38BDF8)          // BudGi Sky/Cyan primary (replaces legacy purple)
    val AccentBright = Color(0xFF7DD3FC)     // Light sky
    val AccentDim = Color(0x3338BDF8)       // Sky (20% opacity)

    val Saffron = Color(0xFF38BDF8)         // Cyan/Sky alias
    val SaffronDim = Color(0x3338BDF8)

    val Secondary = Color(0xFF60BCD4)        // Teal / Cyan secondary
    val SecondaryDim = Color(0x3360BCD4)

    val Green = Color(0xFF1DBF81)           // Accent green — income / success
    val GreenDim = Color(0x221DBF81)
    val Red = Color(0xFFFF453A)             // Red — expense / error
    val RedDim = Color(0x22FF453A)
    val Amber = Color(0xFFFF9F0A)           // Amber — warning
    val AmberDim = Color(0x22FF9F0A)
    val Blue = Color(0xFF38BDF8)            // Sky — info (BudGi signature)
    val BlueDim = Color(0x2238BDF8)

    val TextPrimary = Color(0xFFF9FAFB)     // Clean pure near-white (Slate 50)
    val TextSecondary = Color(0xFF9CA3AF)   // Clean neutral gray (Gray 400 - zero blue tint)
    val TextMuted = Color(0xFF6B7280)       // Clean muted gray (Gray 500)

    val Border = Color(0xFF222630)          // Crisp hairline border (0.8dp)
    val BorderStrong = Color(0xFF2B313E)    // Slightly more pronounced border (20%)
    val BorderAccent = Color(0x3338BDF8)    // Cyan/Sky border (20%)

    // Solid charcoal surface tokens — razor-clean rendering without milky translucency
    val GlassBg = Color(0xFF13161C)
    val GlassBgStrong = Color(0xFF181C24)
    val GlassBorder = Color(0xFF222630)
    val GlassBorderStrong = Color(0xFF2B313E)
    val GlassCard = Color(0xFF13161C)
}

// ── Light Theme Colors (Clean White — Glassmorphic) ────

object LightColors {
    val BgBase = Color(0xFFF1F3F7)          // Softer, soothing light canvas tone — clean separation from white cards
    val BgSurface = Color(0xFFFFFFFF)       // Pure White
    val BgElevated = Color(0xFFFFFFFF)      // Pure White
    val BgOverlay = Color(0xFFE8EBF2)       // Soft neutral light gray surface
    val BgCard = Color(0xFFFFFFFF)          // Solid card background
    val DialogBg = Color(0xFFFFFFFF)        // Solid dialog background

    // Surface container hierarchy
    val SurfaceContainer = Color(0xFFE6E9F0) // Softer, subtle container
    val SurfaceContainerHigh = Color(0xFFFFFFFF)
    val SurfaceContainerHighest = Color(0xFFFFFFFF)

    val Accent = Color(0xFF0284C7)          // BudGi Deep Cyan/Sky (replaces legacy purple)
    val AccentBright = Color(0xFF38BDF8)    // Light sky
    val AccentDim = Color(0x1A0284C7)       // Sky (10%)

    val Saffron = Color(0xFF0284C7)         // Sky alias
    val Green = Color(0xFF1DBF81)           // Green
    val Red = Color(0xFFFF3B30)             // Red
    val Amber = Color(0xFFFF9500)           // Orange
    val Blue = Color(0xFF0284C7)            // Sky

    val TextPrimary = Color(0xFF111827)     // Clean near-black
    val TextSecondary = Color(0xFF4B5563)   // Clean neutral gray (Gray 600)
    val TextMuted = Color(0xFF9CA3AF)       // Clean muted gray (Gray 400)

    val Border = Color(0xFFE5E7EB)          // Smooth, soft slate border (Gray 200 - gentle and silky)
    val BorderStrong = Color(0xFFD1D5DB)    // Refined subtle border accent (Gray 300)
    val GlassBg = Color(0x99FFFFFF)         // Translucent White

    val GlassSurface = Color(0xFFF5F7FA)    // Slightly off-white glass
    val GlassSurfaceStrong = Color(0xFFECEFF4)
    val GlassBorder = Color(0xFFE5E7EB)     // Soft smooth border
    val GlassBorderStrong = Color(0xFFD1D5DB)
}



// ── Gradient Helpers ─────────────────────────────────────────────────

val AccentGradient = listOf(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFF60BCD4))
val AccentGradientLight = listOf(Color(0xFFF0F2F7), Color(0xFFE8EAF0), Color(0xFFD8DBE5))
val SaffronGradient = listOf(Color(0xFF0284C7), Color(0xFF38BDF8), Color(0xFF7DD3FC))
val GreenGradient = listOf(Color(0xFF1DBF81), Color(0xFF2DD4A0), Color(0xFF0EA672))
val GlassGradient = listOf(Color(0x33FFFFFF), Color(0x1AFFFFFF), Color(0x05FFFFFF))
val PurpleTealGradient = listOf(Color(0xFF0284C7), Color(0xFF60BCD4))

// ── BudGi Core Semantic Tokens ─────────────────────────────────────────
val BudgiWhite = DarkColors.TextPrimary
val BudgiBlack = DarkColors.BgBase
val BudgiSecondaryText = DarkColors.TextSecondary
val BudgiDivider = DarkColors.Border
val BudgiCard = DarkColors.GlassBg

// Legacy compatibility aliases
val UberWhite = BudgiWhite
val UberBlack = BudgiBlack
val UberSecondaryText = BudgiSecondaryText
val UberDivider = BudgiDivider
val UberCard = BudgiCard
@Deprecated("Use MaterialTheme.colorScheme.tertiary", ReplaceWith("MaterialTheme.colorScheme.tertiary"))
val SuccessGreen = DarkColors.Green
@Deprecated("Use MaterialTheme.colorScheme.error", ReplaceWith("MaterialTheme.colorScheme.error"))
val ErrorRed = DarkColors.Red
@Deprecated("Use MaterialTheme.colorScheme.errorContainer or similar", ReplaceWith("MaterialTheme.colorScheme.error"))
val WarningAmber = DarkColors.Amber
val Neutral600 = DarkColors.TextMuted

// ── Category Colors (BudGi Signature Calm Palette) ───────────────────

object CategoryColors {
    val Food = Color(0xFFE07A5F)         // Calm Terracotta
    val Groceries = Color(0xFF38A169)    // Calm Sage Green
    val Transport = Color(0xFF4A90E2)    // Slate Sky Blue
    val Flight = Color(0xFF3A9AB7)       // Tranquil Cyan
    val Shopping = Color(0xFF0284C7)     // BudGi Calm Sky
    val Bills = Color(0xFF5C768D)        // Subtle Steel Slate
    val Housing = Color(0xFFD97706)      // Warm Ochre Amber
    val Entertainment = Color(0xFFD95D74)// Soft Dusty Rose
    val Gaming = Color(0xFF0D9488)       // Tranquil Deep Teal
    val Health = Color(0xFF2A9D8F)       // Calming Seafoam Teal
    val Fitness = Color(0xFF38A169)      // Fresh Sage
    val Education = Color(0xFF2563EB)    // Crisp Slate Cobalt
    val Investments = Color(0xFF2E7D5E)  // Forest Green
    val Salary = Color(0xFF34A853)       // BudGi Emerald Mint
    val Transfer = Color(0xFF5A78A5)     // Slate Blue
    val PersonalCare = Color(0xFFD95D74) // Dusty Rose
    val Vegetables = Color(0xFF48BB78)   // Fresh Garden Green (Mandi / Sabzi)
    val Dairy = Color(0xFF2B9E9B)        // Fresh Milk / Dairy Teal
    val DomesticStaff = Color(0xFF8B6CB0)// Muted Slate Violet (Maid / Cook / Staff)
    val Devotion = Color(0xFFD97736)     // Warm Saffron Ochre (Pooja / Temple)
    val Pets = Color(0xFFD97706)         // Warm Ochre
    val Subscriptions = Color(0xFF475569)// Refined Neutral Slate
    val Others = Color(0xFF7E8B9B)       // Soft Pewter
}
