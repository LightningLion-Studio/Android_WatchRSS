package com.lightningstudio.watchrss.ui.screen.rss

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.db.LlmTokenUsageByProviderPojo
import com.lightningstudio.watchrss.data.db.LlmTokenUsageDailyPojo
import com.lightningstudio.watchrss.data.db.LlmTokenUsageEntity
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.theme.watchColorResource
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.viewmodel.LlmTokenUsageViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LlmTokenUsageScreen(
    viewModel: LlmTokenUsageViewModel,
    onNavigateBack: () -> Unit
) {
    val recentRecords by viewModel.recentRecords.collectAsState()
    val statistics by viewModel.statistics.collectAsState()
    val byProvider by viewModel.byProvider.collectAsState()
    val daily by viewModel.daily.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    InstallDigitalCrownScrollHandler(scrollState)

    WatchSurface(pureBlack = true) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    horizontal = watchDimensionResource(R.dimen.watch_safe_padding),
                    vertical = watchDimensionResource(R.dimen.watch_safe_padding)
                ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ScreenHeader(title = "Token 消耗")
            StatisticsCard(
                totalCalls = statistics?.totalCalls ?: 0L,
                totalTokens = statistics?.totalTokens ?: 0L,
                promptTokens = statistics?.totalPromptTokens ?: 0L,
                completionTokens = statistics?.totalCompletionTokens ?: 0L
            )

            if (daily.size > 1) {
                DailyTrendLineChartCard(daily = daily)
                InputOutputBarChartCard(daily = daily)
            }

            if (byProvider.isNotEmpty()) {
                ProviderDistributionChartCard(byProvider = byProvider)
            }

            byProvider.forEach { provider ->
                ProviderSummaryChip(provider)
            }

            Spacer(modifier = Modifier.height(4.dp))
            ActionChip(
                icon = Icons.Outlined.Delete,
                label = "清空记录",
                onClick = { confirmClear = true }
            )
            ActionChip(
                icon = Icons.Outlined.Refresh,
                label = "返回",
                onClick = onNavigateBack
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (recentRecords.isEmpty()) {
                Text(
                    text = "暂无记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            } else {
                recentRecords.forEach { record ->
                    RecordChip(record)
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空所有记录？") },
            text = { Text("此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAll()
                        confirmClear = false
                    }
                ) {
                    Text("清空")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun ScreenHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun StatisticsCard(
    totalCalls: Long,
    totalTokens: Long,
    promptTokens: Long,
    completionTokens: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "累计调用 $totalCalls 次",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$totalTokens tokens",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "输入 $promptTokens · 输出 $completionTokens",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DailyTrendLineChartCard(daily: List<LlmTokenUsageDailyPojo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "近7天趋势",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            val primary = MaterialTheme.colorScheme.primary
            val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
            Canvas(modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)) {
                if (daily.isEmpty()) return@Canvas
                val maxVal = daily.maxOf { it.totalTokens ?: 0L }.toFloat().coerceAtLeast(1f)
                val paddingX = 8.dp.toPx()
                val width = size.width - 2 * paddingX
                val height = size.height
                val step = width / (daily.size - 1).coerceAtLeast(1)
                val points = daily.mapIndexed { index, day ->
                    val x = paddingX + index * step
                    val y = height - ((day.totalTokens ?: 0L).toFloat() / maxVal) * (height * 0.8f) - 4.dp.toPx()
                    Offset(x, y)
                }
                for (i in 0 until points.lastIndex) {
                    drawLine(
                        color = primary,
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                points.forEach { p ->
                    drawCircle(color = primary, radius = 2.dp.toPx(), center = p)
                }
                val first = daily.first().dayTimestamp
                val last = daily.last().dayTimestamp
                val dateFmt = SimpleDateFormat("MM/dd", Locale.getDefault())
                drawIntoCanvas { canvas ->
                    val paint = android.graphics.Paint().apply {
                        color = onSurfaceVariant.toArgb()
                        textSize = 8.dp.toPx()
                    }
                    canvas.nativeCanvas.drawText(
                        dateFmt.format(Date(first)),
                        paddingX,
                        height - 2.dp.toPx(),
                        paint
                    )
                    canvas.nativeCanvas.drawText(
                        dateFmt.format(Date(last)),
                        size.width - paddingX - 24.dp.toPx(),
                        height - 2.dp.toPx(),
                        paint
                    )
                }
            }
        }
    }
}

@Composable
private fun InputOutputBarChartCard(daily: List<LlmTokenUsageDailyPojo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "输入 vs 输出",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            val promptColor = MaterialTheme.colorScheme.primary
            val completionColor = MaterialTheme.colorScheme.tertiary
            Canvas(modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)) {
                if (daily.isEmpty()) return@Canvas
                val maxVal = daily.maxOf { (it.promptTokens ?: 0L) + (it.completionTokens ?: 0L) }
                    .toFloat().coerceAtLeast(1f)
                val paddingX = 8.dp.toPx()
                val width = size.width - 2 * paddingX
                val height = size.height - 12.dp.toPx()
                val groupWidth = width / daily.size.coerceAtLeast(1)
                val barWidth = groupWidth * 0.35f
                daily.forEachIndexed { index, day ->
                    val groupX = paddingX + index * groupWidth
                    val promptH = ((day.promptTokens ?: 0L).toFloat() / maxVal) * height
                    val completionH = ((day.completionTokens ?: 0L).toFloat() / maxVal) * height
                    drawRect(
                        color = promptColor,
                        topLeft = Offset(groupX + groupWidth * 0.15f, height - promptH),
                        size = Size(barWidth, promptH)
                    )
                    drawRect(
                        color = completionColor,
                        topLeft = Offset(groupX + groupWidth * 0.55f, height - completionH),
                        size = Size(barWidth, completionH)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                ColorDot(color = promptColor)
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = "输入", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                ColorDot(color = completionColor)
                Spacer(modifier = Modifier.width(2.dp))
                Text(text = "输出", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ColorDot(color: Color) {
    Canvas(modifier = Modifier.size(6.dp)) {
        drawCircle(color = color, radius = size.minDimension / 2f)
    }
}

@Composable
private fun ProviderDistributionChartCard(byProvider: List<LlmTokenUsageByProviderPojo>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "供应商分布",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            val colors = listOf(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.tertiary,
                Color(0xFFEF5350),
                Color(0xFF66BB6A),
                Color(0xFFFFCA28)
            )
            val holeColor = MaterialTheme.colorScheme.surfaceVariant
            Canvas(modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)) {
                val total = byProvider.sumOf { it.totalTokens ?: 0L }.toFloat().coerceAtLeast(1f)
                var startAngle = -90f
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = (size.minDimension / 2f) - 4.dp.toPx()
                byProvider.forEachIndexed { index, provider ->
                    val sweep = ((provider.totalTokens ?: 0L).toFloat() / total) * 360f
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )
                    startAngle += sweep
                }
                drawCircle(
                    color = holeColor,
                    radius = radius * 0.55f,
                    center = center
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                byProvider.take(5).forEachIndexed { index, provider ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(modifier = Modifier.size(6.dp)) {
                            drawCircle(color = colors[index % colors.size], radius = size.minDimension / 2f)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${provider.provider} · ${provider.totalTokens ?: 0}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderSummaryChip(provider: LlmTokenUsageByProviderPojo) {
    Card(
        onClick = { },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = provider.provider,
                style = MaterialTheme.typography.bodyMedium
            )
            val detail = "${provider.model} · ${provider.calls} 次 · ${provider.totalTokens ?: 0} tokens"
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecordChip(record: LlmTokenUsageEntity) {
    val displayTotal = record.totalTokens
        ?: record.inputTokens?.plus(record.outputTokens ?: 0)
        ?: record.promptTokenCount?.plus(record.candidatesTokenCount ?: 0)
        ?: record.totalTokenCount
        ?: 0
    val displayPrompt = record.promptTokens ?: record.inputTokens ?: record.promptTokenCount ?: 0
    val displayCompletion = record.completionTokens ?: record.outputTokens ?: record.candidatesTokenCount ?: 0
    val reasoning = record.reasoningTokens

    Card(
        onClick = { },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = "${record.provider} · ${record.model}",
                style = MaterialTheme.typography.bodySmall
            )
            val detail = buildString {
                append("$displayPrompt / $displayCompletion")
                if (reasoning != null) append(" · 思考 $reasoning")
                append(" = $displayTotal")
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alpha(0.8f)
            )
        }
    }
}

@Composable
private fun ActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
