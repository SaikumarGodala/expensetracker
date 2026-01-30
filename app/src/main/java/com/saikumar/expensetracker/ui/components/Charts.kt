package com.saikumar.expensetracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import com.saikumar.expensetracker.data.db.CategorySpending
import com.saikumar.expensetracker.data.db.MonthlySpending
import com.saikumar.expensetracker.data.db.YearlySpending
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// Semantic Colors Mapping
fun getCategoryColor(name: String): Color {
    return when (name.lowercase(Locale.ROOT)) {
        "food", "dining" -> Color(0xFFFF7043) // Orange
        "groceries" -> Color(0xFF66BB6A) // Green
        "transport", "taxi", "fuel" -> Color(0xFF42A5F5) // Blue
        "rent", "housing" -> Color(0xFFEF5350) // Red
        "shopping", "clothing" -> Color(0xFFEC407A) // Pink
        "entertainment", "movies" -> Color(0xFFAB47BC) // Purple
        "utilities", "bills" -> Color(0xFFFFA726) // Amber
        "health", "medical" -> Color(0xFF26C6DA) // Cyan
        "salary", "income" -> Color(0xFF43A047) // Dark Green
        "investment" -> Color(0xFF5C6BC0) // Indigo
        else -> {
            // Deterministic fallback based on hash
            val hash = name.hashCode()
            val hue = kotlin.math.abs(hash % 360).toFloat()
            Color.hsv(hue, 0.6f, 0.8f)
        }
    }
}

@Composable
fun PieChart(
    data: List<CategorySpending>,
    modifier: Modifier = Modifier,
    onCategoryClick: (CategorySpending) -> Unit = {}
) {
    val total = data.sumOf { it.totalAmount }

    // Empty state: no data or zero total
    if (data.isEmpty() || total == 0L) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "No spending data for this period",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }
    
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
                    val color = getCategoryColor(item.categoryName)
                    
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
        
        // Leaderboard (Interactive Legend) - Collapsible
        // State for expanding list
        var isExpanded by remember { mutableStateOf(false) }
        val displayData = if (isExpanded) data else data.take(5)
        
        Column(modifier = Modifier.fillMaxWidth()) {
            displayData.forEachIndexed { index, item ->
                val percent = (item.totalAmount.toFloat() / total.toFloat()) * 100
                val color = getCategoryColor(item.categoryName)
                
                // Detect "bad" categories
                val isNeedsAttention = item.categoryName.lowercase(java.util.Locale.ROOT) in listOf("uncategorized", "miscellaneous", "general", "other")
                val rowColor = if (isNeedsAttention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onCategoryClick(item) }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color Indicator: Warning Icon if bad, Circle otherwise
                    if (isNeedsAttention) {
                         androidx.compose.material3.Icon(
                            androidx.compose.material.icons.Icons.Default.Warning,
                            contentDescription = "Needs Attention",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Canvas(modifier = Modifier.size(16.dp)) {
                            drawCircle(color = color)
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Name and Bar
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.categoryName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isNeedsAttention) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium,
                            color = rowColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Mini bar chart representation
                        Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                            // Background track
                            drawRoundRect(
                                color = color.copy(alpha = 0.2f),
                                size = size,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                            )
                            // Progress
                            drawRoundRect(
                                color = color,
                                size = Size(size.width * (percent / 100f), size.height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Amount and %
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = formatCurrencyCompacted(item.totalAmount),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "%.1f%%".format(percent),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Chevron for affordance
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                // Thin separator
                if (index < displayData.size - 1) {
                    androidx.compose.material3.HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(start = 36.dp) // Indent to align with text
                    )
                }
            }
            
            if (data.size > 5) {
                androidx.compose.material3.TextButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text(if (isExpanded) "Show Less" else "View All (${data.size})")
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Icon(
                        if (isExpanded) androidx.compose.material.icons.Icons.Default.ExpandLess else androidx.compose.material.icons.Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Composable
fun BarChart(
    data: List<YearlySpending>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary,
    currentYear: Int = java.time.LocalDate.now().year
) {
    if (data.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("No yearly data available", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val maxAmount = data.maxOf { it.totalAmount }
    
    Canvas(modifier = modifier.padding(16.dp)) {
        val barWidth = size.width / (data.size * 3)
        val space = barWidth
        
        data.forEachIndexed { index, item ->
            val barHeight = (item.totalAmount.toFloat() / maxAmount.toFloat()) * (size.height - 60f) // Keep room for text
            val x = index * (barWidth + space) + space / 2
            val y = size.height - 40f - barHeight // Bottom aligned
            
            val isCurrentYear = item.year == currentYear.toString()
            val actualBarColor = if (isCurrentYear) barColor.copy(alpha = 0.5f) else barColor
            
            // Draw Flat Bar with Rounded Top
            drawRoundRect(
                color = actualBarColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx(), 8.dp.toPx())
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
                
                // YTD Badge for current year
                if (isCurrentYear) {
                    drawText(
                        "YTD",
                        x + barWidth / 2,
                        size.height + 30f, 
                        android.graphics.Paint().apply {
                            this.color = android.graphics.Color.LTGRAY
                            textSize = 20f
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                    )
                }
                
                // Value (Top of bar)
                drawText(
                    formatCurrencyCompacted(item.totalAmount),
                    x + barWidth / 2,
                    y - 10f,
                    android.graphics.Paint().apply {
                        this.color = android.graphics.Color.BLACK
                        textSize = 25f
                        textAlign = android.graphics.Paint.Align.CENTER
                        this.typeface = android.graphics.Typeface.DEFAULT_BOLD
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
