package com.example.smartlens.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.smartlens.util.ThemeManager

// Esquemas de colores para temas básicos Light/Dark
private val LightColorScheme = lightColorScheme(
    primary = md_theme_light_primary,
    onPrimary = md_theme_light_onPrimary,
    primaryContainer = md_theme_light_primaryContainer,
    onPrimaryContainer = md_theme_light_onPrimaryContainer,
    secondary = md_theme_light_secondary,
    onSecondary = md_theme_light_onSecondary,
    secondaryContainer = md_theme_light_secondaryContainer,
    onSecondaryContainer = md_theme_light_onSecondaryContainer,
    tertiary = md_theme_light_tertiary,
    onTertiary = md_theme_light_onTertiary,
    tertiaryContainer = md_theme_light_tertiaryContainer,
    onTertiaryContainer = md_theme_light_onTertiaryContainer,
    error = md_theme_light_error,
    errorContainer = md_theme_light_errorContainer,
    onError = md_theme_light_onError,
    onErrorContainer = md_theme_light_onErrorContainer,
    background = md_theme_light_background,
    onBackground = md_theme_light_onBackground,
    surface = md_theme_light_surface,
    onSurface = md_theme_light_onSurface,
    surfaceVariant = md_theme_light_surfaceVariant,
    onSurfaceVariant = md_theme_light_onSurfaceVariant,
    outline = md_theme_light_outline,
    inverseOnSurface = md_theme_light_inverseOnSurface,
    inverseSurface = md_theme_light_inverseSurface,
    inversePrimary = md_theme_light_inversePrimary,
    surfaceTint = md_theme_light_surfaceTint,
    outlineVariant = md_theme_light_outlineVariant,
    scrim = md_theme_light_scrim,
)

private val DarkColorScheme = darkColorScheme(
    primary = md_theme_dark_primary,
    onPrimary = md_theme_dark_onPrimary,
    primaryContainer = md_theme_dark_primaryContainer,
    onPrimaryContainer = md_theme_dark_onPrimaryContainer,
    secondary = md_theme_dark_secondary,
    onSecondary = md_theme_dark_onSecondary,
    secondaryContainer = md_theme_dark_secondaryContainer,
    onSecondaryContainer = md_theme_dark_onSecondaryContainer,
    tertiary = md_theme_dark_tertiary,
    onTertiary = md_theme_dark_onTertiary,
    tertiaryContainer = md_theme_dark_tertiaryContainer,
    onTertiaryContainer = md_theme_dark_onTertiaryContainer,
    error = md_theme_dark_error,
    errorContainer = md_theme_dark_errorContainer,
    onError = md_theme_dark_onError,
    onErrorContainer = md_theme_dark_onErrorContainer,
    background = md_theme_dark_background,
    onBackground = md_theme_dark_onBackground,
    surface = md_theme_dark_surface,
    onSurface = md_theme_dark_onSurface,
    surfaceVariant = md_theme_dark_surfaceVariant,
    onSurfaceVariant = md_theme_dark_onSurfaceVariant,
    outline = md_theme_dark_outline,
    inverseOnSurface = md_theme_dark_inverseOnSurface,
    inverseSurface = md_theme_dark_inverseSurface,
    inversePrimary = md_theme_dark_inversePrimary,
    surfaceTint = md_theme_dark_surfaceTint,
    outlineVariant = md_theme_dark_outlineVariant,
    scrim = md_theme_dark_scrim,
)

// Tema OLED (negro puro)
private val OledDarkColorScheme = darkColorScheme(
    primary = Color(0xFF66D9BF),        // Turquesa brillante
    onPrimary = Color(0xFF000000),      // Negro
    primaryContainer = Color(0xFF004D40),// Verde oscuro
    onPrimaryContainer = Color(0xFF89F8CF),// Verde claro
    secondary = Color(0xFFA0CFEC),      // Azul claro
    onSecondary = Color(0xFF000000),    // Negro
    secondaryContainer = Color(0xFF1A3353),// Azul muy oscuro
    onSecondaryContainer = Color(0xFFCFE9DB),// Verde muy claro
    tertiary = Color(0xFFB39DDB),       // Lavanda
    onTertiary = Color(0xFF000000),     // Negro
    tertiaryContainer = Color(0xFF311B92),// Púrpura oscuro
    onTertiaryContainer = Color(0xFFD1C4E9),// Lavanda claro
    error = Color(0xFFCF6679),          // Rosa error
    errorContainer = Color(0xFF93000A), // Rojo oscuro
    onError = Color(0xFF000000),        // Negro
    onErrorContainer = Color(0xFFFFDAD6),// Rosa muy claro
    background = Color(0xFF000000),     // Negro puro OLED
    onBackground = Color(0xFFE0E0E0),   // Gris muy claro
    surface = Color(0xFF000000),        // Negro puro OLED
    onSurface = Color(0xFFE0E0E0),      // Gris muy claro
    surfaceVariant = Color(0xFF101010), // Negro casi puro
    onSurfaceVariant = Color(0xFFBFC9C0),// Gris claro
    outline = Color(0xFF89938D),        // Gris medio
    inverseOnSurface = Color(0xFF121212),// Negro casi puro
    inverseSurface = Color(0xFFE0E0E0), // Gris claro
    inversePrimary = Color(0xFF006C4F), // Verde oscuro
    surfaceTint = Color(0xFF66D9BF),    // Turquesa
    outlineVariant = Color(0xFF404943), // Gris oscuro
    scrim = Color(0xFF000000),          // Negro
)

// Tema Retro (aspecto vintage)
private val RetroDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF6B4A),        // Naranja retro
    onPrimary = Color(0xFF000000),      // Negro
    primaryContainer = Color(0xFF8B3D2A),// Marrón rojizo
    onPrimaryContainer = Color(0xFFFFDAD1),// Beige claro
    secondary = Color(0xFFF4CA40),      // Amarillo mostaza
    onSecondary = Color(0xFF000000),    // Negro
    secondaryContainer = Color(0xFF704E10),// Marrón dorado
    onSecondaryContainer = Color(0xFFFFF2D4),// Beige muy claro
    tertiary = Color(0xFF90CAF9),       // Azul claro
    onTertiary = Color(0xFF000000),     // Negro
    tertiaryContainer = Color(0xFF375A7F),// Azul oscuro
    onTertiaryContainer = Color(0xFFD0F0FF),// Azul muy claro
    error = Color(0xFFFD8489),          // Coral
    errorContainer = Color(0xFF93000A), // Rojo oscuro
    onError = Color(0xFF000000),        // Negro
    onErrorContainer = Color(0xFFFFDAD6),// Rosa muy claro
    background = Color(0xFF2C2722),     // Marrón muy oscuro
    onBackground = Color(0xFFE5DED5),   // Beige
    surface = Color(0xFF2C2722),        // Marrón muy oscuro
    onSurface = Color(0xFFE5DED5),      // Beige
    surfaceVariant = Color(0xFF443B33), // Marrón oscuro
    onSurfaceVariant = Color(0xFFCFC0B0),// Beige medio
    outline = Color(0xFF9E8C7A),        // Marrón medio
    inverseOnSurface = Color(0xFF2C2722),// Marrón muy oscuro
    inverseSurface = Color(0xFFE5DED5), // Beige
    inversePrimary = Color(0xFFD84019), // Naranja oscuro
    surfaceTint = Color(0xFFFF6B4A),    // Naranja retro
    outlineVariant = Color(0xFF635749), // Marrón grisáceo
    scrim = Color(0xFF000000),          // Negro
)

// Tema Nature (colores naturales)
private val NatureColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),        // Verde bosque
    onPrimary = Color(0xFFFFFFFF),      // Blanco
    primaryContainer = Color(0xFFA5D6A7),// Verde claro
    onPrimaryContainer = Color(0xFF1B5E20),// Verde muy oscuro
    secondary = Color(0xFF689F38),      // Verde limón
    onSecondary = Color(0xFFFFFFFF),    // Blanco
    secondaryContainer = Color(0xFFDCEDC8),// Verde muy claro
    onSecondaryContainer = Color(0xFF33691E),// Verde oscuro
    tertiary = Color(0xFF0288D1),       // Azul cielo
    onTertiary = Color(0xFFFFFFFF),    // Blanco
    tertiaryContainer = Color(0xFFB3E5FC),// Azul muy claro
    onTertiaryContainer = Color(0xFF01579B),// Azul oscuro
    error = Color(0xFFC62828),         // Rojo
    errorContainer = Color(0xFFFFCDD2), // Rosa muy claro
    onError = Color(0xFFFFFFFF),       // Blanco
    onErrorContainer = Color(0xFF8E0000),// Rojo oscuro
    background = Color(0xFFF1F8E9),    // Verde muy claro
    onBackground = Color(0xFF212121),  // Casi negro
    surface = Color(0xFFF1F8E9),       // Verde muy claro
    onSurface = Color(0xFF212121),     // Casi negro
    surfaceVariant = Color(0xFFDCEDC8), // Verde claro
    onSurfaceVariant = Color(0xFF424242),// Gris oscuro
    outline = Color(0xFF9E9E9E),       // Gris
    inverseOnSurface = Color(0xFFF1F8E9),// Verde muy claro
    inverseSurface = Color(0xFF212121), // Casi negro
    inversePrimary = Color(0xFFB9F6CA), // Verde menta
    surfaceTint = Color(0xFF2E7D32),   // Verde bosque
    outlineVariant = Color(0xFFBDBDBD), // Gris claro
    scrim = Color(0xFF000000),         // Negro
)

// Tema Elegant (look premium)
private val ElegantDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB39DDB),        // Lavanda/Púrpura
    onPrimary = Color(0xFF000000),      // Negro
    primaryContainer = Color(0xFF673AB7),// Púrpura medio
    onPrimaryContainer = Color(0xFFEDE7F6),// Lavanda muy claro
    secondary = Color(0xFF90A4AE),      // Gris azulado
    onSecondary = Color(0xFF000000),    // Negro
    secondaryContainer = Color(0xFF546E7A),// Gris azulado oscuro
    onSecondaryContainer = Color(0xFFECEFF1),// Gris muy claro
    tertiary = Color(0xFF8C9EFF),       // Azul violáceo
    onTertiary = Color(0xFF000000),     // Negro
    tertiaryContainer = Color(0xFF3F51B5),// Azul índigo
    onTertiaryContainer = Color(0xFFE8EAF6),// Azul muy claro
    error = Color(0xFFFFB4AB),          // Rosa claro
    errorContainer = Color(0xFF8D0B0B), // Rojo oscuro
    onError = Color(0xFF690005),        // Rojo muy oscuro
    onErrorContainer = Color(0xFFFFDAD6),// Rosa muy claro
    background = Color(0xFF121212),     // Negro casi puro
    onBackground = Color(0xFFE1E3DF),   // Gris muy claro
    surface = Color(0xFF121212),        // Negro casi puro
    onSurface = Color(0xFFE1E3DF),      // Gris muy claro
    surfaceVariant = Color(0xFF1E1E1E), // Negro con tono gris
    onSurfaceVariant = Color(0xFFBFC9C0),// Gris claro
    outline = Color(0xFF8A8C8A),        // Gris medio
    inverseOnSurface = Color(0xFF121212),// Negro casi puro
    inverseSurface = Color(0xFFE1E3DF), // Gris claro
    inversePrimary = Color(0xFF7E57C2), // Púrpura oscuro
    surfaceTint = Color(0xFFB39DDB),    // Lavanda
    outlineVariant = Color(0xFF404943), // Gris oscuro
    scrim = Color(0xFF000000),          // Negro
)

@Composable
fun SmartLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    // Obtener el tipo de tema actual
    val currentTheme by ThemeManager.currentTheme.collectAsState()

    // Determinar si usar colores dinámicos (Android 12+)
    val dynamicColor = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
            && currentTheme == ThemeManager.ThemeType.SYSTEM

    // Seleccionar esquema de colores según el tema actual
    val colorScheme = when (currentTheme) {
        ThemeManager.ThemeType.SYSTEM -> {
            if (dynamicColor) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        ThemeManager.ThemeType.LIGHT -> LightColorScheme
        ThemeManager.ThemeType.DARK -> DarkColorScheme
        ThemeManager.ThemeType.OLED_BLACK -> OledDarkColorScheme
        ThemeManager.ThemeType.RETRO -> RetroDarkColorScheme
        ThemeManager.ThemeType.NATURE -> NatureColorScheme
        ThemeManager.ThemeType.ELEGANT -> ElegantDarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window

            // Establecer color de la barra de estado según el tema
            val statusBarColor = when (currentTheme) {
                ThemeManager.ThemeType.OLED_BLACK -> Color.Black.toArgb()
                ThemeManager.ThemeType.RETRO -> colorScheme.primaryContainer.toArgb()
                ThemeManager.ThemeType.ELEGANT -> Color(0xFF121212).toArgb()
                else -> colorScheme.primary.toArgb()
            }

            window.statusBarColor = statusBarColor

            // Establecer si los iconos de la barra de estado deben ser claros u oscuros
            val isDarkStatusBar = when (currentTheme) {
                ThemeManager.ThemeType.LIGHT, ThemeManager.ThemeType.NATURE -> true
                else -> false
            }

            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isDarkStatusBar
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}