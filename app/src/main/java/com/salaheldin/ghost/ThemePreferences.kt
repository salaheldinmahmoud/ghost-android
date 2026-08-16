package com.salaheldin.ghost

import android.content.Context
import com.salaheldin.ghost.ui.theme.ThemeMode

private const val PREFS_NAME = "ghost_prefs"
private const val KEY_THEME_MODE = "theme_mode"

fun saveThemeMode(context: Context, mode: ThemeMode) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
}

fun loadThemeMode(context: Context): ThemeMode {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val saved = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
    return try {
        ThemeMode.valueOf(saved ?: ThemeMode.SYSTEM.name)
    } catch (e: IllegalArgumentException) {
        ThemeMode.SYSTEM
    }
}