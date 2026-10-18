package com.app.presentation.ui.theme

import androidx.compose.ui.unit.dp

/**
 * BudGi Spacing System — matched to website's space tokens.
 * Website: --space-1: 4px through --space-24: 96px
 */
object Dimens {
    // Spacing scale (matches website --space-N)
    val space1 = 4.dp        // --space-1
    val space2 = 8.dp        // --space-2
    val space3 = 12.dp       // --space-3
    val space4 = 16.dp       // --space-4
    val space5 = 20.dp       // --space-5
    val space6 = 24.dp       // --space-6
    val space8 = 32.dp       // --space-8
    val space10 = 40.dp      // --space-10
    val space12 = 48.dp      // --space-12
    val space16 = 64.dp      // --space-16
    val space20 = 80.dp      // --space-20

    // Semantic aliases
    val paddingSmall = space2     // 8dp
    val paddingMedium = space4    // 16dp
    val paddingLarge = space6     // 24dp
    val paddingExtraLarge = space8 // 32dp

    val spacingSmall = space1     // 4dp
    val spacingMedium = space2    // 8dp
    val spacingLarge = space4     // 16dp
    val spacingExtraLarge = space6 // 24dp

    val cardCornerRadius = 16.dp  // Clean, professional card corners
    val buttonCornerRadius = 9999.dp // --radius-full (pill buttons)

    val iconSizeSmall = 20.dp
    val iconSizeMedium = 24.dp
    val iconSizeLarge = 32.dp

    val topAppBarHeight = 60.dp   // --topbar-h

    // Bottom floating dock clearance — keeps content above the nav bar
    val bottomDockHeight = 104.dp
}
