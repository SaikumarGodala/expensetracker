package com.saikumar.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.saikumar.expensetracker.data.db.CategorySpending
import com.saikumar.expensetracker.data.db.MonthlySpending
import com.saikumar.expensetracker.data.db.YearlySpending
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PieChart(
    data: List<CategorySpending>,
    modifier: Modifier = Modifier,
    onCategoryClick: (CategorySpending) -> Unit = {}
) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No data available", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val total = data.sumOf { it.totalAmount }
    val colors = listOf(
        Color(0xFFEF5350), Color(0xFFEC407A), Color(0xFFAB47BC), Color(0xFF7E57C2),
        Color(0xFF5C6BC0), Color(0xFF42A5F5), Color(0xFF29B6F6), Color(0xFF26C6DA),
        Color(0xFF26A69A), Color(0xFF66BB6A), Color(0xFF9CCC65), Color(0xFFD4E157),
        Color(0xFFFFEE58), Color(0xFFFFCA28), Color(0xFFFF7043), Color(0xFF8D6E63)
    )

    // Remember surface color for Canvas (dark mode support)
    val surfaceColorValue = MaterialTheme.colorScheme.surface
    
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(220.dp)) {
                val pieSize = size.minDimension
                val radius = pieSize / 2
                val donutHoleRadius = radius * 0.6f
                val centerX = size.width / 2
                val centerY = size.height / 2
                val surfaceColor = surfaceColorValue
                
                var startAngle = -90f
                
                // Draw Donut Segments
                data.forEachIndexed { index, item ->
                    val angle = (item.totalAmount.toFloat() / total.toFloat()) * 360f
                    val color = colors[index % colors.size]
                    
                    // Draw Arc
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = angle,
                        useCenter = true, // We draw full wedge then cover center
                        topLeft = Offset(centerX - radius, centerY - radius),
                        size = Size(pieSize, pieSize)
                    )
                    
                    startAngle += angle
                }
                
                // Draw Hole (Use surface color for dark mode support)
                drawCircle(
                    color = surfaceColor,
                    radius = donutHoleRadius,
                    center = Offset(centerX, centerY)
                )
            }
            
            // Center Text (Total)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatCurrencyCompacted(total),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Legends
        Column(modifier = Modifier.fillMaxWidth()) {
            data.prefix(5).forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategoryClick(item) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = colors[index % colors.size])
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = item.categoryName,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatCurrency(item.totalAmount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "%.1f%%".format((item.totalAmount.toFloat() / total.toFloat()) * 100),
                        style = MaterialTheme.typography.labelSmall,
                         color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun BarChart(
    data: List<YearlySpending>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No yearly data available", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val maxAmount = data.maxOf { it.totalAmount }
    
    Canvas(modifier = modifier.padding(16.dp)) {
        val barWidth = size.width / (data.size * 2)
        val space = barWidth
        val depth = barWidth * 0.6f // 3D Depth
        
        data.forEachIndexed { index, item ->
            val barHeight = (item.totalAmount.toFloat() / maxAmount.toFloat()) * (size.height - 60f) // Keep room for text
            val x = index * (barWidth + space) + space / 2
            val y = size.height - 40f - barHeight // Bottom aligned
            
            val color = Color(0xFF42A5F5)
            val topColor = Color(0xFF90CAF9)
            val sideColor = Color(0xFF1E88E5)
            
            // 1. Draw Side Face (Right Side)
            val sidePath = Path().apply {
                moveTo(x + barWidth, y) // Top Right Front
                lineTo(x + barWidth + depth, y - depth) // Top Right Back
                lineTo(x + barWidth + depth, size.height - 40f - depth) // Bottom Right Back
                lineTo(x + barWidth, size.height - 40f) // Bottom Right Front
                close()
            }
            drawPath(sidePath, sideColor)
            
            // 2. Draw Top Face
            val topPath = Path().apply {
                moveTo(x, y) // Top Left Front
                lineTo(x + depth, y - depth) // Top Left Back
                lineTo(x + barWidth + depth, y - depth) // Top Right Back
                lineTo(x + barWidth, y) // Top Right Front
                close()
            }
            drawPath(topPath, topColor)
            
            // 3. Draw Front Face
            drawRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight)
            )
            
            // Text Labels
            drawContext.canvas.nativeCanvas.apply {
                // Year (Bottom)
                drawText(
                    item.year,
                    x + barWidth / 2,
                    size.height,
                    android.graphics.Paint().apply {
                        this.color = android.graphics.Color.GRAY
                        textSize = 30f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
                
                // Value (Top of 3D bar)
                drawText(
                    formatCurrencyCompacted(item.totalAmount),
                    x + barWidth / 2 + depth/2,
                    y - depth - 10f,
                    android.graphics.Paint().apply {
                        this.color = android.graphics.Color.BLACK
                        textSize = 25f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
    }
}

@Composable
fun LineChart(
    data: List<MonthlySpending>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No trend data available", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val maxAmount = data.maxOf { it.totalAmount }.coerceAtLeast(1)
    val color = Color(0xFF66BB6A)
    val gradientColors = listOf(color.copy(alpha = 0.4f), color.copy(alpha = 0.0f))
    
    Canvas(modifier = modifier.padding(16.dp)) {
        val spacing = size.width / (data.size - 1).coerceAtLeast(1)
        val points = data.mapIndexed { index, item ->
            val x = index * spacing
            val y = size.height - 40f - (item.totalAmount.toFloat() / maxAmount.toFloat()) * (size.height - 60f)
            Offset(x, y)
        }
        
        // 1. Build Smooth Path (Cubic Bezier)
        val path = Path()
        if (points.isNotEmpty()) {
            path.moveTo(points[0].x, points[0].y)
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                
                // Control points for smooth curve
                val controlX1 = p1.x + (p2.x - p1.x) / 2
                val controlY1 = p1.y
                val controlX2 = p1.x + (p2.x - p1.x) / 2
                val controlY2 = p2.y
                
                path.cubicTo(controlX1, controlY1, controlX2, controlY2, p2.x, p2.y)
            }
        }
        
        // 2. Draw Gradient Fill Area
        val fillPath = Path()
        fillPath.addPath(path)
        fillPath.lineTo(points.last().x, size.height - 40f)
        fillPath.lineTo(points.first().x, size.height - 40f) // Close loop at bottom
        fillPath.close()
        
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = gradientColors,
                startY = 0f,
                endY = size.height
            )
        )
        
        // 3. Draw Line Stroke
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        )
        
        // 4. Draw Points & Labels
        points.forEachIndexed { index, point ->
            // Draw larger glow
            drawCircle(
                color = color.copy(alpha = 0.2f),
                center = point,
                radius = 8.dp.toPx()
            )
            // Solid center
            drawCircle(
                color = color,
                center = point,
                radius = 4.dp.toPx()
            )
            
            // Label (Month) - Convert "01" to "Jan"
            val monthNum = data[index].month.takeLast(2).toIntOrNull() ?: 1
            val monthName = java.time.Month.of(monthNum).getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
            drawContext.canvas.nativeCanvas.apply {
                // Value (Top of point)
                val valueText = formatCurrencyCompacted(data[index].totalAmount)
                drawText(
                    valueText,
                    point.x,
                    point.y - 20f,
                    android.graphics.Paint().apply {
                         this.color = android.graphics.Color.BLACK
                         textSize = 24f
                         textAlign = android.graphics.Paint.Align.CENTER
                         this.typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                )

                // Month Label
                drawText(
                    monthName,
                    point.x,
                    size.height,
                    android.graphics.Paint().apply {
                        this.color = android.graphics.Color.GRAY
                        textSize = 28f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }
        }
    }
}

fun formatCurrency(paisa: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return format.format(paisa / 100.0)
}

fun formatCurrencyCompacted(paisa: Long): String {
    val amount = paisa / 100.0
    return when {
        amount >= 10000000 -> "%.1fCr".format(amount / 10000000)
        amount >= 100000 -> "%.1fL".format(amount / 100000)
        amount >= 1000 -> "%.1fK".format(amount / 1000)
        else -> "%.0f".format(amount)
    }
}

fun <T> List<T>.prefix(n: Int): List<T> = this.take(n)
