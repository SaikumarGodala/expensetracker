package com.saikumar.expensetracker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment

// Complete Default Color Schemes with Surface Containers
private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = Color(0xFF1C1B1F),
    surface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFF0F0D13),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF211F26),
    surfaceContainerHigh = Color(0xFF2B2930),
    surfaceContainerHighest = Color(0xFF36343B),
    onPrimary = Color(0xFF381E72),
    onSecondary = Color(0xFF332D41),
    onTertiary = Color(0xFF492532),
    onBackground = Color(0xFFE6E1E5),
    onSurface = Color(0xFFE6E1E5),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F)
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFE7E0EC),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F2FA),
    surfaceContainer = Color(0xFFF3EDF7),
    surfaceContainerHigh = Color(0xFFECE6F0),
    surfaceContainerHighest = Color(0xFFE6E0E9),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0)
)

// Ocean Schemes
private val OceanLightScheme = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Pink40,
    background = Color(0xFFFDFBFF),
    surface = Color(0xFFFDFBFF),
    surfaceVariant = Color(0xFFE0E2EC),
    surfaceContainerHigh = Color(0xFFE8EAF4),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1C1E),
    onSurface = Color(0xFF1A1C1E),
    onSurfaceVariant = Color(0xFF44474E)
)
private val OceanDarkScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Pink80,
    background = Color(0xFF1A1C1E),
    surface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFF0D0F11),
    surfaceContainerLow = Color(0xFF1C1F22),
    surfaceContainer = Color(0xFF202326),
    surfaceContainerHigh = Color(0xFF2A2D31),
    surfaceContainerHighest = Color(0xFF35383C),
    onPrimary = Color(0xFF003062),
    onSecondary = Color(0xFF2E3340),
    onBackground = Color(0xFFE2E2E6),
    onSurface = Color(0xFFE2E2E6),
    onSurfaceVariant = Color(0xFFC4C6CF),
    outline = Color(0xFF8E9099)
)

// Forest Schemes
private val ForestLightScheme = lightColorScheme(
    primary = Green40,
    secondary = GreenGrey40,
    tertiary = Pink40,
    background = Color(0xFFFCFDF7),
    surface = Color(0xFFFCFDF7),
    surfaceVariant = Color(0xFFDEE5D9),
    surfaceContainerHigh = Color(0xFFE6EDE0),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF1A1C19),
    onSurface = Color(0xFF1A1C19),
    onSurfaceVariant = Color(0xFF424940)
)
private val ForestDarkScheme = darkColorScheme(
    primary = Green80,
    secondary = GreenGrey80,
    tertiary = Pink80,
    background = Color(0xFF1A1C19),
    surface = Color(0xFF1A1C19),
    surfaceVariant = Color(0xFF424940),
    surfaceContainerLowest = Color(0xFF0C0F0A),
    surfaceContainerLow = Color(0xFF1D201C),
    surfaceContainer = Color(0xFF212420),
    surfaceContainerHigh = Color(0xFF2B2E2A),
    surfaceContainerHighest = Color(0xFF363935),
    onPrimary = Color(0xFF003913),
    onSecondary = Color(0xFF2C3329),
    onBackground = Color(0xFFE2E3DD),
    onSurface = Color(0xFFE2E3DD),
    onSurfaceVariant = Color(0xFFC2C9BD),
    outline = Color(0xFF8C9388)
)

// Sunset Schemes
private val SunsetLightScheme = lightColorScheme(
    primary = Orange40,
    secondary = OrangeGrey40,
    tertiary = Pink40,
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF5DDD3),
    surfaceContainerHigh = Color(0xFFFBE6DC),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF211A17),
    onSurface = Color(0xFF211A17),
    onSurfaceVariant = Color(0xFF53433D)
)
private val SunsetDarkScheme = darkColorScheme(
    primary = Orange80,
    secondary = OrangeGrey80,
    tertiary = Pink80,
    background = Color(0xFF211A17),
    surface = Color(0xFF211A17),
    surfaceVariant = Color(0xFF53433D),
    surfaceContainerLowest = Color(0xFF140E0B),
    surfaceContainerLow = Color(0xFF241D1A),
    surfaceContainer = Color(0xFF28211E),
    surfaceContainerHigh = Color(0xFF332B28),
    surfaceContainerHighest = Color(0xFF3E3633),
    onPrimary = Color(0xFF5C1900),
    onSecondary = Color(0xFF3E2D28),
    onBackground = Color(0xFFEDE0DB),
    onSurface = Color(0xFFEDE0DB),
    onSurfaceVariant = Color(0xFFD8C2BA),
    outline = Color(0xFFA08D85)
)

// Snow Schemes
private val SnowLightScheme = lightColorScheme(
    primary = SnowBlue40,
    secondary = SnowBlueGrey40,
    tertiary = Blue40,
    background = SnowBackgroundLight,
    surface = SnowBackgroundLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = SnowTextBlue, // Deep blue text
    onSurface = SnowTextBlue,    // Deep blue text
    surfaceVariant = Color.White.copy(alpha = 0.4f), // Cards background (Frosted / Translucent)
    surfaceContainerLow = Color.White.copy(alpha = 0.4f), // Transaction items (Frosted / Translucent)
    tertiaryContainer = Color(0xFFF0F8FF).copy(alpha = 0.4f) // very pale blue (Frosted / Translucent)
)
private val SnowDarkScheme = darkColorScheme(
    primary = SnowBlue80,
    secondary = SnowBlueGrey80,
    tertiary = Blue80,
    background = Color(0xFF102A43), // Deep Blue Night
    surface = Color(0xFF102A43),
    onPrimary = Color(0xFF102A43), // Dark text on primary
    surfaceVariant = Color.White.copy(alpha = 0.15f), // Glassy Dark Cards
    surfaceContainerLow = Color.White.copy(alpha = 0.15f), // Glassy Dark items
    tertiaryContainer = Color.White.copy(alpha = 0.15f) // Glassy Dark accents
)

@Composable
fun ExpenseTrackerTheme(
    themeMode: Int = 0, // 0=System, 1=Light, 2=Dark
    colorPalette: String = "DYNAMIC",
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }

    val context = LocalContext.current
    
    val colorScheme = when (colorPalette) {
        "DYNAMIC" -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else {
                if (darkTheme) DarkColorScheme else LightColorScheme
            }
        }
        "OCEAN" -> if (darkTheme) OceanDarkScheme else OceanLightScheme
        "FOREST" -> if (darkTheme) ForestDarkScheme else ForestLightScheme
        "SUNSET" -> if (darkTheme) SunsetDarkScheme else SunsetLightScheme
        "SNOW" -> if (darkTheme) SnowDarkScheme else SnowLightScheme
        else -> if (darkTheme) DarkColorScheme else LightColorScheme
    }

    // Provide the dark theme state via CompositionLocal
    androidx.compose.runtime.CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = {
                androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                    content()
                    if (colorPalette == "SNOW") {
                        SnowfallAnimation(
                            modifier = Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter),
                            snowColor = if (darkTheme) Color.White.copy(alpha = 0.8f) else SnowBlueGrey40
                        )
                        WinterWaves(
                            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp),
                            waveColor = if (darkTheme) SnowBlue80.copy(alpha=0.3f) else SnowBlue40.copy(alpha=0.3f)
                        )
                    }
                }
            }
        )
    }
}

/**
 * CompositionLocal to access the actual dark theme state as determined by the app theme settings.
 * Use this instead of isSystemInDarkTheme() to respect app-level theme preference.
 */
val LocalIsDarkTheme = androidx.compose.runtime.compositionLocalOf { false }