package com.alertamayor.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = BackgroundWhite,
    secondary = RedAlert,
    onSecondary = BackgroundWhite,
    background = BackgroundWhite,
    onBackground = TextPrimary,
    surface = BackgroundWhite,
    onSurface = TextPrimary,
    error = RedAlertDark,
)

private val DarkColors = darkColorScheme(
    primary = PrimaryLight,
    onPrimary = Color(0xFF00204B),
    secondary = RedAlert,
    onSecondary = BackgroundWhite,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    error = Color(0xFFEF5350),
)

/**
 * Tema de la app. [darkTheme] permite forzar claro/oscuro o seguir el sistema.
 * M-001 (ciclo 6): el selector de Configuración define [darkTheme]:
 * "system" -> isSystemInDarkTheme(), "light" -> false, "dark" -> true.
 * Se mantiene coherencia con los colores custom (RedAlert, GreenOnline, RedAlertDark).
 */
@Composable
fun AlertaMayorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}