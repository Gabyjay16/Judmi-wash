package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = OceanBlueDark,
    onPrimary = Color(0xFF00344F),
    primaryContainer = OceanBlueContainerDark,
    onPrimaryContainer = Color(0xFFBCE9FF),
    secondary = MintTealDark,
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005047),
    tertiary = Color(0xFFFFB77C),
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceCard,
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0)
)

private val LightColorScheme = lightColorScheme(
    primary = OceanBlue,
    onPrimary = Color.White,
    primaryContainer = OceanBlueLight,
    onPrimaryContainer = Color(0xFF001F31),
    secondary = MintTeal,
    onSecondary = Color.White,
    secondaryContainer = MintTealContainer,
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = WarmCoral,
    onTertiary = Color.White,
    tertiaryContainer = WarmCoralContainer,
    background = SoftBackground,
    surface = SoftSurface,
    surfaceVariant = SurfaceCard,
    onBackground = Color(0xFF1E293B),
    onSurface = Color(0xFF1E293B)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
