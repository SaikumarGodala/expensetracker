package com.saikumar.expensetracker.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

/**
 * Composable wrapper for MPAndroidChart LineChart
 * Displays a line chart with Material Design 3 styling
 */
@Composable
fun LineChartComposable(
    entries: List<Entry>,
    xAxisLabels: List<String>,
    lineColor: Color = Color(0xFF2196F3),
    fillColor: Color = Color(0xFF2196F3),
    modifier: Modifier = Modifier.fillMaxWidth().height(200.dp)
) {
    var previousEntries by remember { mutableStateOf<List<Entry>?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            LineChart(context).apply {
                // Chart styling
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(false)
                setPinchZoom(false)
                setDrawGridBackground(false)
                
                // X-Axis configuration
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    granularity = 1f
                    textColor = AndroidColor.GRAY
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return xAxisLabels.getOrNull(value.toInt()) ?: ""
                        }
                    }
                }
                
                // Left Y-Axis configuration
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = AndroidColor.LTGRAY
                    textColor = AndroidColor.GRAY
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "₹${(value / 1000).toInt()}k"
                        }
                    }
                }
                
                // Right Y-Axis - disabled
                axisRight.isEnabled = false
                
                animateX(800)
            }
        },
        update = { chart ->
            val dataSet = LineDataSet(entries, "").apply {
                color = lineColor.toArgb()
                lineWidth = 2.5f
                setCircleColor(lineColor.toArgb())
                circleRadius = 4f
                circleHoleRadius = 2f
                setDrawValues(false)
                setDrawFilled(true)
                setFillColor(fillColor.toArgb())
                fillAlpha = 50
                mode = LineDataSet.Mode.CUBIC_BEZIER
                cubicIntensity = 0.2f
            }
            
            chart.data = LineData(dataSet)

            
            if (previousEntries != entries) {
                chart.animateX(800)
                previousEntries = entries
            }
            
            chart.invalidate()
        }
    )
}
