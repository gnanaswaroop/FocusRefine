package com.focusrefine.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun FocusRefineTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    val savedThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
    
    val isDark = when (savedThemeMode) {
        AppCompatDelegate.MODE_NIGHT_NO -> false
        AppCompatDelegate.MODE_NIGHT_YES -> true
        else -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (isDark) darkColorScheme() else lightColorScheme(),
        content = content
    )
}
