package com.example.lifedots.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Brand palette for the app chrome (MainActivity + SettingsActivity).
 *
 * NOTE: this is NOT for the wallpaper renderer — the wallpaper still
 * reads its own `ThemeColors` derived from `ThemeOption`/`CustomColors`
 * in `LifeDotsPreferences`. These tokens are only for Home + Settings.
 */
object BrandColors {
    val AmberGold = Color(0xFFFFB300)
    val AmberGoldDark = Color(0xFFE89E00)
    val AmberGoldWash = Color(0x33FFB300)

    val InkBlack = Color(0xFF0A0A0A)
    val InkBlackElevated = Color(0xFF171717)
    val InkBlackDeep = Color(0xFF000000)

    val DarkAmber = Color(0xFF3D2900)
    val GoldenMuted = Color(0x99FFB300)
    val OffWhite = Color(0xFFF5F5F5)
    val HairlineGold = Color(0x40FFB300)
}
