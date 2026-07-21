package com.example.expensetracker.ui.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.expensetracker.data.Category
import com.example.expensetracker.data.MainViewModel
import com.example.expensetracker.data.TimePeriod
import com.example.expensetracker.data.TransactionType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// ── Color palette for pie chart slices ──────────────────────────────────────
private val sliceColors = listOf(
    Color(0xFFFF6B6B),  // red-ish
    Color(0xFF4ECDC4),  // teal
    Color(0xFFFFE66D),  // yellow
    Color(0xFF95E1D3),  // mint
    Color(0xFFF38181),  // salmon
    Color(0xFFAA96DA)   // lavender
)

// ── Period selector for analytics ───────────────────────────────────────────
enum class AnalyticsPeriod(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly")
}

// ── Main analytics screen ───────────────────────────────────────────────────
@Composable
fun AnalyticsScreen(
    viewModel: MainViewModel,
    contentBottomPadding: Dp = 120.dp
) {
    val transactions by viewModel.transactions.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()

    // 3-way period selector: Daily / Weekly / Monthly
    var selectedPeriod by remember { mutableStateOf(AnalyticsPeriod.WEEKLY) }

    // ── Map AnalyticsPeriod → TimePeriod for filtering ──
    val timePeriod = when (selectedPeriod) {
        AnalyticsPeriod.DAILY -> TimePeriod.TODAY
        AnalyticsPeriod.WEEKLY -> TimePeriod.THIS_WEEK
        AnalyticsPeriod.MONTHLY -> TimePeriod.THIS_MONTH
    }

    // ── Computed analytics data (filtered by selected period) ──
    val categoryData = viewModel.getExpensesByCategory(transactions, timePeriod)
    val barData = when (selectedPeriod) {
        AnalyticsPeriod.DAILY -> viewModel.getDailyExpenses(transactions)
        AnalyticsPeriod.WEEKLY -> viewModel.getWeeklyExpenses(transactions)
        AnalyticsPeriod.MONTHLY -> viewModel.getMonthlyExpenses(transactions)
    }

    val totalExpense = categoryData.values.sum()
    val topCategory = categoryData.maxByOrNull { it.value }
    val filteredExpenses = viewModel.getFilteredTransactions(transactions, timePeriod)
        .filter { it.type == TransactionType.EXPENSE }
    val expenseCount = filteredExpenses.size
    val avgPerDay = when (selectedPeriod) {
        AnalyticsPeriod.DAILY -> totalExpense
        AnalyticsPeriod.WEEKLY -> totalExpense / 7.0
        AnalyticsPeriod.MONTHLY -> totalExpense / 30.0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = contentBottomPadding + 16.dp)
    ) {
        // ── Header ──
        Text(
            "Analytics",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
        )

        // ── Period Filter Chips ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AnalyticsPeriod.entries.forEach { period ->
                FilterChip(
                    selected = selectedPeriod == period,
                    onClick = { selectedPeriod = period },
                    label = { Text(period.label, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        // ── Bento Grid ──
        BentoGrid(
            categoryData = categoryData,
            barData = barData,
            totalExpense = totalExpense,
            topCategory = topCategory,
            expenseCount = expenseCount,
            avgPerDay = avgPerDay,
            currencySymbol = currencySymbol,
            selectedPeriod = selectedPeriod
        )

        // ── Category Legend ──
        if (categoryData.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Category Breakdown",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(12.dp))
                    categoryData.entries.forEachIndexed { index, (category, amount) ->
                        val percentage = if (totalExpense > 0) (amount / totalExpense * 100) else 0.0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Color dot
                            Canvas(modifier = Modifier.size(12.dp)) {
                                drawCircle(color = sliceColors[index % sliceColors.size])
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                category.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "$currencySymbol${"%.2f".format(amount)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${"%.1f".format(percentage)}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Bento Grid Container ────────────────────────────────────────────────────
@Composable
fun BentoGrid(
    categoryData: Map<Category, Double>,
    barData: List<Pair<String, Double>>,
    totalExpense: Double,
    topCategory: Map.Entry<Category, Double>?,
    expenseCount: Int,
    avgPerDay: Double,
    currencySymbol: String,
    selectedPeriod: AnalyticsPeriod
) {
    val tileShape = RoundedCornerShape(16.dp)
    val tileColor = MaterialTheme.colorScheme.surfaceVariant
    val gap = 8.dp

    // Outer container — the "single rounded box" of compartments
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(gap),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            // ── ROW 1 & 2: Pie chart (2 cols) + 4 stat tiles (2 cols) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(gap)
            ) {
                // Left: Pie chart tile — spans 2 of 4 columns, square aspect
                PieChartTile(
                    data = categoryData,
                    currencySymbol = currencySymbol,
                    totalExpense = totalExpense,
                    modifier = Modifier
                        .weight(2f)
                        .fillMaxHeight(),
                    shape = tileShape,
                    color = tileColor
                )

                // Right: 2×2 grid of stat tiles — spans 2 of 4 columns
                Column(
                    modifier = Modifier.weight(2f),
                    verticalArrangement = Arrangement.spacedBy(gap)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        StatTile(
                            title = "Total",
                            value = "$currencySymbol${"%.0f".format(totalExpense)}",
                            icon = "▼",
                            modifier = Modifier.weight(1f),
                            shape = tileShape,
                            color = tileColor
                        )
                        StatTile(
                            title = "Top",
                            value = topCategory?.key?.label ?: "—",
                            icon = "★",
                            modifier = Modifier.weight(1f),
                            shape = tileShape,
                            color = tileColor
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap)
                    ) {
                        StatTile(
                            title = "Count",
                            value = "$expenseCount",
                            icon = "#",
                            modifier = Modifier.weight(1f),
                            shape = tileShape,
                            color = tileColor
                        )
                        StatTile(
                            title = "Avg/Day",
                            value = "$currencySymbol${"%.0f".format(avgPerDay)}",
                            icon = "≈",
                            modifier = Modifier.weight(1f),
                            shape = tileShape,
                            color = tileColor
                        )
                    }
                }
            }

            // ── ROW 3 & 4: Bar chart — full width (all 4 columns) ──
            BarChartTile(
                data = barData,
                label = when (selectedPeriod) {
                    AnalyticsPeriod.DAILY -> "Today"
                    AnalyticsPeriod.WEEKLY -> "This Week"
                    AnalyticsPeriod.MONTHLY -> "This Month"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                shape = tileShape,
                color = tileColor
            )
        }
    }
}

// ── Pie Chart Tile ──────────────────────────────────────────────────────────
@Composable
fun PieChartTile(
    data: Map<Category, Double>,
    currencySymbol: String,
    totalExpense: Double,
    modifier: Modifier,
    shape: Shape,
    color: Color
) {
    val total = data.values.sum()

    Surface(shape = shape, color = color, modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            if (data.isEmpty()) {
                Text(
                    "No expenses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                    val diameter = size.minDimension
                    val strokeWidth = diameter * 0.22f
                    val arcSize = Size(diameter - strokeWidth, diameter - strokeWidth)
                    val arcOffset = Offset(
                        (size.width - arcSize.width) / 2,
                        (size.height - arcSize.height) / 2
                    )

                    var startAngle = -90f
                    data.entries.forEachIndexed { index, (_, amount) ->
                        val sweep = (amount / total * 360f).toFloat()
                        drawArc(
                            color = sliceColors[index % sliceColors.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = arcOffset,
                            size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )
                        startAngle += sweep
                    }
                }

                // Center label inside donut
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$currencySymbol${"%.0f".format(totalExpense)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Spent",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ── Bar Chart Tile ──────────────────────────────────────────────────────────
@Composable
fun BarChartTile(
    data: List<Pair<String, Double>>,
    label: String,
    modifier: Modifier,
    shape: Shape,
    color: Color
) {
    val maxVal = data.maxOfOrNull { it.second } ?: 1.0
    val barColor = Color(0xFF338FE4)
    val textPaint = android.graphics.Paint().apply {
        this.color = android.graphics.Color.GRAY
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }

    Surface(shape = shape, color = color, modifier = modifier) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header label
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            // Bar chart canvas
            Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (data.isEmpty()) return@Canvas

                val barCount = data.size
                val totalBarAreaWidth = size.width
                val barSpacing = totalBarAreaWidth / (barCount * 2f)
                val barWidth = barSpacing
                val maxBarHeight = size.height * 0.75f
                val labelY = size.height - 4f

                data.forEachIndexed { i, (dayLabel, value) ->
                    val barHeight = if (maxVal > 0) (value / maxVal * maxBarHeight).toFloat() else 0f
                    val x = i * (barWidth + barSpacing) + barSpacing / 2

                    // Draw bar
                    if (barHeight > 0) {
                        drawRoundRect(
                            color = barColor,
                            topLeft = Offset(x, size.height - barHeight - 36f),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                    }

                    // Draw label below bar
                    drawContext.canvas.nativeCanvas.drawText(
                        dayLabel,
                        x + barWidth / 2,
                        labelY,
                        textPaint
                    )
                }
            }
        }
    }
}

// ── Stat Tile (small 1×1 card) ──────────────────────────────────────────────
@Composable
fun StatTile(
    title: String,
    value: String,
    icon: String,
    modifier: Modifier,
    shape: Shape,
    color: Color
) {
    Surface(shape = shape, color = color, modifier = modifier) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                icon,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
