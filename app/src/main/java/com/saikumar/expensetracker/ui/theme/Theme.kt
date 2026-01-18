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

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

// Ocean Schemes
private val OceanLightScheme = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Pink40
)
private val OceanDarkScheme = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Pink80
)

// Forest Schemes
private val ForestLightScheme = lightColorScheme(
    primary = Green40,
    secondary = GreenGrey40,
    tertiary = Pink40
)
private val ForestDarkScheme = darkColorScheme(
    primary = Green80,
    secondary = GreenGrey80,
    tertiary = Pink80
)

// Sunset Schemes
private val SunsetLightScheme = lightColorScheme(
    primary = Orange40,
    secondary = OrangeGrey40,
    tertiary = Pink40
)
private val SunsetDarkScheme = darkColorScheme(
    primary = Orange80,
    secondary = OrangeGrey80,
    tertiary = Pink80
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = {
            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize()) {
                content()
                if (colorPalette == "SNOW") {
                    SnowfallAnimation(
                        modifier = Modifier.fillMaxWidth().height(120.dp).align(Alignment.TopCenter), // Only header area
                        snowColor = if (darkTheme) Color.White.copy(alpha = 0.8f) else SnowBlueGrey40 // Thick Blue for light mode
                    )
                    WinterWaves(
                        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp),
                        waveColor = if (darkTheme) SnowBlue80.copy(alpha=0.3f) else SnowBlue40.copy(alpha=0.3f) // Blue waves in light mode
                    )
                }
            }
        }
    )
}