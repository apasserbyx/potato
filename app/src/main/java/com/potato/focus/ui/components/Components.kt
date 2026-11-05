package com.potato.focus.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.potato.focus.data.DayStat
import com.potato.focus.ui.theme.Potato
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun PotatoCard(
    modifier: Modifier = Modifier,
    color: Color = Potato.palette.surface,
    radius: Dp = 24.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(radius),
        color = color,
        border = androidx.compose.foundation.BorderStroke(1.dp, Potato.palette.line)
    ) {
        content()
    }
}

@Composable
fun PotatoMark(modifier: Modifier = Modifier, size: Dp = 30.dp) {
    val palette = Potato.palette
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawOval(
            color = palette.accent,
            topLeft = Offset(w * 0.08f, h * 0.24f),
            size = androidx.compose.ui.geometry.Size(w * 0.74f, h * 0.68f)
        )
        drawCircle(
            color = palette.breakColor,
            radius = w * 0.11f,
            center = Offset(w * 0.68f, h * 0.27f)
        )
        drawCircle(
            color = palette.breakColor,
            radius = w * 0.07f,
            center = Offset(w * 0.82f, h * 0.21f)
        )
        drawCircle(
            color = palette.accentSoft,
            radius = w * 0.06f,
            center = Offset(w * 0.34f, h * 0.42f)
        )
    }
}

@Composable
fun TimerRing(
    timeText: String,
    label: String,
    progress: Float,
    color: Color,
    running: Boolean,
    modifier: Modifier = Modifier
) {
    val palette = Potato.palette
    val transition = rememberInfiniteTransition(label = "breath")
    val glow by transition.animateFloat(
        initialValue = 0.18f,
        targetValue = 0.38f,
        animationSpec = infiniteRepeatable(
            animation = tween(3200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )
    Box(
        modifier = modifier.semantics {
            contentDescription = "$label，$timeText"
        },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val glowRadius = size.minDimension / 2f - 14.dp.toPx()
            if (running) {
                drawCircle(
                    color = color.copy(alpha = 0.055f * glow),
                    radius = glowRadius + 12.dp.toPx()
                )
            }
            drawCircle(
                color = palette.line,
                radius = size.minDimension / 2f - stroke,
                style = Stroke(width = stroke)
            )
            val sweep = (360f * progress.coerceIn(0f, 1f))
            if (sweep > 0.2f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
                )
                val angle = Math.toRadians((-90f + sweep).toDouble())
                val radius = size.minDimension / 2f - 7.dp.toPx() / 2f
                drawCircle(
                    color = color,
                    radius = 4.5.dp.toPx(),
                    center = Offset(
                        x = center.x + radius * cos(angle).toFloat(),
                        y = center.y + radius * sin(angle).toFloat()
                    )
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeText,
                style = if (timeText.length > 5) {
                    MaterialTheme.typography.displayMedium
                } else {
                    MaterialTheme.typography.displayLarge
                },
                color = palette.ink,
                maxLines = 1
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted,
                maxLines = 1
            )
        }
    }
}

@Composable
fun ModeSwitch(
    stopwatch: Boolean,
    enabled: Boolean,
    onModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = Potato.palette
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(palette.surfaceRaised)
            .border(1.dp, palette.line, CircleShape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModePill(
            text = "土豆钟",
            selected = !stopwatch,
            enabled = enabled,
            onClick = { onModeChange(false) }
        )
        ModePill(
            text = "正计时",
            selected = stopwatch,
            enabled = enabled,
            onClick = { onModeChange(true) }
        )
    }
}

@Composable
private fun ModePill(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val palette = Potato.palette
    val bg by animateColorAsState(
        if (selected) palette.ink else Color.Transparent,
        label = "modeBg"
    )
    val fg by animateColorAsState(
        if (selected) palette.surface else palette.muted,
        label = "modeFg"
    )
    Text(
        text = text,
        color = if (enabled) fg else fg.copy(alpha = 0.45f),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    )
}

@Composable
fun TagPill(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    val palette = Potato.palette
    val color = tagColor(text, palette)
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

private fun tagColor(tag: String, palette: com.potato.focus.ui.theme.PotatoPalette): Color {
    return when (tag) {
        "工作" -> palette.focus
        "学习" -> palette.breakColor
        "创作" -> palette.accent
        "生活" -> Color(0xFF7A8794)
        else -> palette.muted
    }
}

@Composable
fun SectionLabel(text: String, trailing: String? = null, onTrailing: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = Potato.palette.ink
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = Potato.palette.muted,
                modifier = if (onTrailing != null) Modifier.clickable(onClick = onTrailing) else Modifier
            )
        }
    }
}

private val navItems = listOf(
    "专注" to Icons.Rounded.Timer,
    "习惯" to Icons.Rounded.CheckCircle,
    "数据" to Icons.Rounded.BarChart,
    "设置" to Icons.Rounded.Settings
)

@Composable
fun BottomNavBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = Potato.palette
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = palette.surface.copy(alpha = 0.97f),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, (label, icon) ->
                NavItem(label, icon, selected == index) { onSelect(index) }
            }
        }
    }
}

@Composable
fun SideNavBar(
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = Potato.palette
    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(88.dp),
        color = palette.surface.copy(alpha = 0.97f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 8.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            PotatoMark(size = 34.dp)
            Spacer(Modifier.height(18.dp))
            navItems.forEachIndexed { index, (label, icon) ->
                val selectedItem = selected == index
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (selectedItem) palette.focusSoft else Color.Transparent)
                        .clickable { onSelect(index) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (selectedItem) palette.focus else palette.muted,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selectedItem) palette.focus else palette.muted,
                        fontWeight = if (selectedItem) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, onClick: () -> Unit) {
    val palette = Potato.palette
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) palette.focus else palette.muted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) palette.focus else palette.muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun StatBarChart(
    days: List<DayStat>,
    goalMinutes: Int,
    modifier: Modifier = Modifier,
    showLabels: Boolean = true
) {
    val palette = Potato.palette
    if (days.isEmpty()) return
    val maxValue = maxOf(
        goalMinutes.toFloat(),
        days.maxOf { it.focusSeconds / 60f }.coerceAtLeast(30f)
    ) * 1.15f
    val goalFraction = (goalMinutes / maxValue).coerceIn(0f, 1f)
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(144.dp)
                .then(
                    Modifier.drawWithContent {
                        drawContent()
                        val baselineY = size.height - 0.5.dp.toPx()
                        drawLine(
                            color = palette.line.copy(alpha = 0.75f),
                            start = Offset(0f, baselineY),
                            end = Offset(size.width, baselineY),
                            strokeWidth = 1.dp.toPx()
                        )
                        val y = size.height * (1f - goalFraction)
                        drawLine(
                            color = palette.muted.copy(alpha = 0.30f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(6.dp.toPx(), 6.dp.toPx())
                            )
                        )
                    }
                )
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(if (days.size > 14) 2.dp else 10.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEach { day ->
                    val fraction = (day.focusSeconds / 60f / maxValue).coerceIn(0f, 1f)
                    val hasData = day.focusSeconds > 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(144.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(3.dp)
                                .background(palette.line.copy(alpha = 0.8f))
                        )
                        if (hasData) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(fraction.coerceAtLeast(0.035f))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        palette.focus.copy(
                                            alpha = if (day.tomatoCount > 0) 0.92f else 0.55f
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
        if (showLabels) {
            Spacer(Modifier.height(8.dp))
            if (days.size <= 7) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    days.forEach { day ->
                        Text(
                            text = com.potato.focus.util.TimeFormat.weekdayShort(day.date),
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                }
            } else {
                val labelIndices = monthLabelIndices(days.size)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    labelIndices.forEach { index ->
                        val date = days[index].date
                        Text(
                            text = "${date.monthValue}/${date.dayOfMonth}",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.muted,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

private fun monthLabelIndices(size: Int): List<Int> {
    if (size <= 1) return listOf(0)
    val target = minOf(6, size)
    return (0 until target)
        .map { it * (size - 1) / (target - 1) }
        .distinct()
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = Potato.palette.muted,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 20.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
}
