package com.saikumar.expensetracker.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.isActive
import java.util.concurrent.ThreadLocalRandom
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class Snowflake(
    var x: Float,
    var y: Float,
    var radius: Float,
    var speed: Float,
    var wind: Float,
    var alpha: Float,
    var rotation: Float,
    var rotationSpeed: Float
)

@Composable
fun SnowfallAnimation(
    modifier: Modifier = Modifier,
    snowflakeCount: Int = 50,
    snowColor: Color = Color.White.copy(alpha = 0.8f)
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    
    // Initialize snowflakes
    val snowflakes = remember(size, snowflakeCount) {
        if (size.width == 0 || size.height == 0) return@remember emptyList()
        
        List(snowflakeCount) {
             createRandomSnowflake(size.width, size.height)
        }
    }

    // Animation loop
    var time by remember { mutableFloatStateOf(0f) }
    
    LaunchedEffect(snowflakes) {
        while (isActive) {
            withFrameNanos { nanos ->
                time = nanos / 1_000_000_000f
                snowflakes.forEach { flake ->
                    flake.y += flake.speed
                    flake.x += sin(time + flake.wind) * 0.5f // Slight wobble
                    flake.rotation += flake.rotationSpeed // Animate rotation
                    
                    // Reset if falls off screen
                    if (flake.y > size.height) {
                        resetSnowflake(flake, size.width)
                    }
                }
            }
        }
    }

    Canvas(modifier = modifier.onSizeChanged { size = it }) {
        // Force redraw on every frame update by reading the state
        @Suppress("UNUSED_VARIABLE")
        val animationTime = time
        snowflakes.forEach { flake ->
            // Draw larger flakes as 6-pointed stars, smaller ones as soft dots
            if (flake.radius > 6.0f) {
                // Rotate the canvas context for this snowflake
                rotate(degrees = flake.rotation, pivot = Offset(flake.x, flake.y)) {
                    drawSnowflake(
                        center = Offset(flake.x, flake.y),
                        radius = flake.radius,
                        alpha = flake.alpha,
                        color = snowColor
                    )
                }
            } else {
                drawCircle(
                    color = snowColor.copy(alpha = flake.alpha),
                    radius = flake.radius,
                    center = Offset(flake.x, flake.y)
                )
            }
        }
    }
}

private fun createRandomSnowflake(width: Int, height: Int): Snowflake {
    val random = ThreadLocalRandom.current()
    return Snowflake(
        x = random.nextFloat() * width,
        y = random.nextFloat() * height,
        radius = random.nextFloat() * 12f + 4f, // Much bigger: 4f to 16f
        speed = random.nextFloat() * 2f + 1f,
        wind = random.nextFloat() * 2f,
        alpha = random.nextFloat() * 0.4f + 0.2f, // More translucent: 0.2 to 0.6
        rotation = random.nextFloat() * 360f,
        rotationSpeed = random.nextFloat() * 2f - 1f // -1 to 1 degree per frame
    )
}

private fun resetSnowflake(flake: Snowflake, width: Int) {
    val random = ThreadLocalRandom.current()
    flake.x = random.nextFloat() * width
    flake.y = -random.nextFloat() * 50f // Start slightly above
    flake.speed = random.nextFloat() * 2f + 1f
    flake.alpha = random.nextFloat() * 0.4f + 0.2f // More translucent
    flake.radius = random.nextFloat() * 12f + 4f // Reshuffle size on reset
    flake.rotation = random.nextFloat() * 360f
    flake.rotationSpeed = random.nextFloat() * 2f - 1f
}

// Draw a simple 6-pointed snowflake
private fun DrawScope.drawSnowflake(
    center: Offset,
    radius: Float,
    alpha: Float,
    color: Color
) {
    val strokeWidth = radius * 0.4f
    val snowflakeColor = color.copy(alpha = alpha)
    
    // 3 lines crossing at 60 degrees
    for (i in 0 until 3) {
        val angle = i * 60.0 * (PI / 180.0)
        val start = Offset(
            x = center.x + (radius * cos(angle)).toFloat(),
            y = center.y + (radius * sin(angle)).toFloat()
        )
        val end = Offset(
            x = center.x - (radius * cos(angle)).toFloat(),
            y = center.y - (radius * sin(angle)).toFloat()
        )
        drawLine(
            color = snowflakeColor,
            start = start,
            end = end,
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun WinterWaves(
    modifier: Modifier = Modifier,
    waveColor: Color = Color(0xFFE1F5FE)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    
    // Slower, more organic phase animation
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing), // Slower for "sea" feel
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(modifier = modifier) {
        val height = size.height
        
        // Multi-layered waves for depth (Sea effect)
        // Back wave: Slow, tall, faint
        drawWave(
            frequency = 0.5f, 
            amplitude = height * 0.15f, 
            phase = phase, 
            color = waveColor.copy(alpha = 0.3f), 
            yOffset = height * 0.5f
        )
        
        // Middle wave: Slightly faster, offset
        drawWave(
            frequency = 0.7f, 
            amplitude = height * 0.12f, 
            phase = phase + 2f, 
            color = waveColor.copy(alpha = 0.5f), 
            yOffset = height * 0.65f
        )
        
        // Front wave: Detailed, main focus
        drawWave(
            frequency = 1.0f, 
            amplitude = height * 0.10f, 
            phase = phase + 4f, 
            color = waveColor.copy(alpha = 0.8f), 
            yOffset = height * 0.8f
        )
    }
}

private fun DrawScope.drawWave(
    frequency: Float,
    amplitude: Float,
    phase: Float,
    color: Color,
    yOffset: Float
) {
    val path = Path()
    val waveLength = size.width
    
    path.moveTo(0f, size.height)
    
    // Draw wave points with higher resolution for smoothness
    for (x in 0..size.width.toInt() step 5) {
        val xFloat = x.toFloat()
        // Combine sine waves for more "sea-like" irregularity
        val normalizedX = (xFloat / waveLength) * 2 * PI.toFloat()
        val sineArg = normalizedX * frequency + phase
        
        // Add a secondary harmonic for "choppiness"
        val y = yOffset + (sin(sineArg) * amplitude) + (sin(sineArg * 2.5f) * (amplitude * 0.2f))
        
        if (x == 0) {
            path.moveTo(xFloat, y)
        } else {
            path.lineTo(xFloat, y)
        }
    }
    
    // Close the path to fill the bottom
    path.lineTo(size.width, size.height)
    path.lineTo(0f, size.height)
    path.close()
    
    drawPath(path = path, color = color)
}
