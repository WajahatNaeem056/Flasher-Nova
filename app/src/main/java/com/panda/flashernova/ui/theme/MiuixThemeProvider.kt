package com.panda.flashernova.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

data class GlassTokens(
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceContainer: Color,
    val outline: Color,
    val outlineVariant: Color,
    val highlight: Color,
    val scrim: Color,
    val onSurface: Color,
    val navContainer: Color,
    val selectedContainer: Color,
)

val LocalGlassTokens = compositionLocalOf {
    GlassTokens(
        surface = Color.White,
        surfaceVariant = Color.White,
        surfaceContainer = Color.White,
        outline = Color.Black,
        outlineVariant = Color.Black,
        highlight = Color.White,
        scrim = Color.Black,
        onSurface = Color.Black,
        navContainer = Color.White,
        selectedContainer = Color.Black,
    )
}

private val MiuixLight = lightColorScheme(
    primary = Color(0xFF111111),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6E6),
    onPrimaryContainer = Color(0xFF111111),
    secondary = Color(0xFF5F5F5F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E6E6),
    onSecondaryContainer = Color(0xFF111111),
    tertiary = Color(0xFF5F5F5F),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE6E6E6),
    onTertiaryContainer = Color(0xFF111111),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111111),
    surfaceVariant = Color(0xFFF3F3F3),
    onSurfaceVariant = Color(0xFF666666),
    surfaceContainer = Color(0xFFF3F3F3),
    surfaceContainerHigh = Color(0xFFEBEBEB),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111111),
    outline = Color(0xFFCFCFCF),
    outlineVariant = Color(0xFFE2E2E2),
    error = Color(0xFFEA4335),
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFF8C1D18)
)

private val MiuixDark = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF222222),
    onPrimaryContainer = Color(0xFFF2F2F2),
    secondary = Color(0xFFBDBDBD),
    onSecondary = Color(0xFF111111),
    secondaryContainer = Color(0xFF222222),
    onSecondaryContainer = Color(0xFFF2F2F2),
    tertiary = Color(0xFFBDBDBD),
    onTertiary = Color(0xFF111111),
    tertiaryContainer = Color(0xFF222222),
    onTertiaryContainer = Color(0xFFF2F2F2),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFF2F2F2),
    surfaceVariant = Color(0xFF151515),
    onSurfaceVariant = Color(0xFF9A9A9A),
    surfaceContainer = Color(0xFF151515),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    background = Color(0xFF000000),
    onBackground = Color(0xFFF2F2F2),
    outline = Color(0xFF3A3A3A),
    outlineVariant = Color(0xFF2B2B2B),
    error = Color(0xFFF28B82),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC)
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.15.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.25.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
)

@Composable
fun MiuixThemeProvider(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> MiuixDark
        else -> MiuixLight
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    val glassTokens = if (darkTheme) {
        GlassTokens(
            surface = Color(0xCC151515),
            surfaceVariant = Color(0xB3151515),
            surfaceContainer = Color(0xD9151515),
            outline = Color(0x33FFFFFF),
            outlineVariant = Color(0x1FFFFFFF),
            highlight = Color(0x26FFFFFF),
            scrim = Color(0x52000000),
            onSurface = Color(0xFFF2F2F2),
            navContainer = Color(0xE6161616),
            selectedContainer = Color(0xFF2A2A2A),
        )
    } else {
        GlassTokens(
            surface = Color(0xCCFFFFFF),
            surfaceVariant = Color(0xB3F3F3F3),
            surfaceContainer = Color(0xE6F3F3F3),
            outline = Color(0x33000000),
            outlineVariant = Color(0x1F000000),
            highlight = Color(0xB3FFFFFF),
            scrim = Color(0x1A000000),
            onSurface = Color(0xFF111111),
            navContainer = Color(0xE6F3F3F3),
            selectedContainer = Color(0xFFDCDCDC),
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(LocalGlassTokens provides glassTokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = AppShapes,
            typography = AppTypography,
            content = content
        )
    }
}
