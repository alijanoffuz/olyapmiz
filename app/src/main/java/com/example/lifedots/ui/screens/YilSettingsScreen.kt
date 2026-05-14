package com.example.lifedots.ui.screens

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.lifedots.ModernSettingsContent
import com.example.lifedots.preferences.Goal
import com.example.lifedots.preferences.LifeDotsPreferences
import com.example.lifedots.preferences.WallpaperSettings
import kotlinx.coroutines.CoroutineScope

/**
 * Thin wrapper around the existing ModernSettingsContent that renders
 * every existing setting. Exists so the SettingsActivity dispatcher
 * (Task 14) can call YilSettingsScreen / UmrSettingsScreen
 * symmetrically. A future PR can move section composables in here.
 */
@Composable
fun YilSettingsScreen(
    settings: WallpaperSettings,
    preferences: LifeDotsPreferences,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope,
    onAddGoal: () -> Unit,
    onEditGoal: (Goal) -> Unit,
    modifier: Modifier = Modifier,
) {
    ModernSettingsContent(
        settings = settings,
        preferences = preferences,
        snackbarHostState = snackbarHostState,
        scope = scope,
        onAddGoal = onAddGoal,
        onEditGoal = onEditGoal,
        modifier = modifier,
    )
}
