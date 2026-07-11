package com.saikumar.expensetracker.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Shared spacing/sizing scale so every screen reads as one app instead of four
 * independently-tuned ones. Loosely follows Material 3's shape scale
 * (small/large/extra-large) with one deliberate exception noted below.
 *
 * Corner radius tiers:
 * - [RadiusChip] (8dp)   - pills, badges, small tags (M3 "small")
 * - [RadiusCard] (16dp)  - the default for cards, list rows, sections (M3 "large") -
 *                          used almost everywhere: category cards, dialogs, settings rows
 * - [RadiusFeedRow] (20dp) - the primary transaction feed row (Home + filtered lists) -
 *                          intentionally a touch rounder as the app's signature list treatment
 * - [RadiusHero] (28dp)  - the singular hero balance card only (M3 "extra-large")
 * - [RadiusPill]         - fully-rounded pills (segmented controls, filter capsules)
 */
object Dimens {
    // Spacing
    val ScreenPadding = 16.dp
    val SectionSpacing = 16.dp
    val CardPadding = 16.dp
    val ItemSpacing = 8.dp

    // Corner radii
    val RadiusChip = 8.dp
    val RadiusAvatar = 12.dp
    val RadiusCard = 16.dp
    val RadiusFeedRow = 20.dp
    val RadiusHero = 28.dp
    val RadiusPill = 50 // percent, for RoundedCornerShape(percent = RadiusPill)

    // Icon avatar sizes
    val AvatarSizeSmall = 36.dp
    val AvatarSizeMedium = 44.dp
}
