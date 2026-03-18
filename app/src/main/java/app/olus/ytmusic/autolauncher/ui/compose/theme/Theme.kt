package app.olus.ytmusic.autolauncher.ui.compose.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// YouTube Music inspired dark palette
val YTBlack = Color(0xFF0F0F0F)
val YTDarkGray = Color(0xFF1A1A1A)
val YTMediumGray = Color(0xFF282828)
val YTLightGray = Color(0xFF3E3E3E)
val YTTextPrimary = Color(0xFFFFFFFF)
val YTTextSecondary = Color(0xFFAAAAAA)
val YTRed = Color(0xFFFF0033)
val YTRedDark = Color(0xFFCC0029)
val YTRedSoft = Color(0xFFFF2D55)
val YTAccentBlue = Color(0xFF3EA6FF)
val YTSurface = Color(0xFF212121)
val YTSurfaceVariant = Color(0xFF2A2A2A)

private val DarkColorScheme = darkColorScheme(
    primary = YTRed,
    onPrimary = Color.White,
    primaryContainer = YTRedDark,
    onPrimaryContainer = Color.White,
    secondary = YTAccentBlue,
    onSecondary = Color.White,
    tertiary = YTRedSoft,
    background = YTBlack,
    onBackground = YTTextPrimary,
    surface = YTDarkGray,
    onSurface = YTTextPrimary,
    surfaceVariant = YTSurfaceVariant,
    onSurfaceVariant = YTTextSecondary,
    error = Color(0xFFCF6679),
    outline = YTLightGray,
    outlineVariant = YTMediumGray,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFCC0029),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF1A73E8),
    background = Color(0xFFF8F8F8),
    onBackground = Color(0xFF1C1B1F),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF49454F),
)

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun YTMusicAutoLauncherTheme(
    darkTheme: Boolean = isSystemInDarkTheme(), // Respect system theme
    dynamicColor: Boolean = false, // Use our custom palette
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Proper edge-to-edge: transparent bars, let content draw behind
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}
