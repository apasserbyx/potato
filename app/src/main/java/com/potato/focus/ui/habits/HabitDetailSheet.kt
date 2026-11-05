package com.potato.focus.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.potato.focus.data.Habit
import com.potato.focus.data.HabitLog
import com.potato.focus.data.HabitLogStatus
import com.potato.focus.data.HabitStats
import com.potato.focus.data.HabitType
import com.potato.focus.data.isScheduledOn
import com.potato.focus.ui.components.PotatoCard
import com.potato.focus.ui.theme.Potato
import com.potato.focus.util.TimeFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

@Composable
fun HabitDetailContent(
    habit: Habit,
    logs: List<HabitLog>,
    today: LocalDate,
    reminderText: String,
    onEdit: () -> Unit,
    onSkipToday: () -> Unit,
    onArchive: () -> Unit,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = Potato.palette
    val accent = habitColor(habit.color)
    var rangeDays by remember { mutableIntStateOf(30) }

    val logsByDate = remember(logs) {
        logs.mapNotNull { log ->
            runCatching { LocalDate.parse(log.date) }.getOrNull()?.let { it to log }
        }.toMap()
    }
    val currentStreak = remember(habit, logsByDate, today) {
        HabitStats.streak(habit, logsByDate, today)
    }
    val bestStreak = remember(habit, logsByDate, today) {
        HabitStats.bestStreak(habit, logsByDate, today)
    }
    val completionRate = remember(habit, logsByDate, rangeDays, today) {
        HabitStats.completionRate(habit, logsByDate, rangeDays, today)
    }
    val totalFocusedSeconds = remember(logs) { logs.sumOf { it.focusedSeconds } }
    val totalCount = remember(logs) { logs.sumOf { it.countValue } }
    val todayLog = logsByDate[today]
    val canSkip = habit.isScheduledOn(today) &&
        !HabitStats.isCompleted(todayLog) &&
        !HabitStats.isSkipped(todayLog)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitIconVisual(
                    iconType = habit.iconType,
                    icon = habit.icon,
                    imagePath = habit.iconImagePath,
                    contentDescription = habit.name,
                    size = 58.dp,
                    background = accent.copy(alpha = 0.14f),
                    tint = accent
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        habit.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        HabitStats.targetText(habit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted
                    )
                    Text(
                        reminderText,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted
                    )
                }
                if (onClose != null) {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = palette.muted
                        )
                    }
                }
            }

            if (habit.note.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    habit.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = "当前连续",
                    value = "$currentStreak 天",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = "最佳连续",
                    value = "$bestStreak 天",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(
                    label = "${rangeDays} 天完成率",
                    value = "${(completionRate * 100).toInt()}%",
                    modifier = Modifier.weight(1f)
                )
                StatTile(
                    label = if (habit.type == HabitType.TIMED) "累计专注" else "累计次数",
                    value = if (habit.type == HabitType.TIMED) {
                        TimeFormat.durationText(totalFocusedSeconds.toLong())
                    } else {
                        "$totalCount ${habit.unit.ifBlank { "次" }}"
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(18.dp))
            PotatoCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "完成热力图",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.ink
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                heatmapRangeText(today, rangeDays),
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.muted
                            )
                        }
                        RangeChip("30 天", rangeDays == 30, accent) { rangeDays = 30 }
                        Spacer(Modifier.width(6.dp))
                        RangeChip("90 天", rangeDays == 90, accent) { rangeDays = 90 }
                    }
                    Spacer(Modifier.height(16.dp))
                    HeatmapGrid(
                        habit = habit,
                        logsByDate = logsByDate,
                        today = today,
                        days = rangeDays,
                        accent = accent
                    )
                    Spacer(Modifier.height(12.dp))
                    HeatmapLegend(accent)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("最近记录", style = MaterialTheme.typography.titleMedium, color = palette.ink)
            Spacer(Modifier.height(8.dp))
            if (logs.isEmpty()) {
                Text(
                    "还没有记录。完成一次习惯后，这里会出现第一条。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted
                )
            } else {
                PotatoCard(Modifier.fillMaxWidth()) {
                    Column {
                        logs.take(12).forEachIndexed { index, log ->
                            RecentLogRow(habit = habit, log = log)
                            if (index != logs.take(12).lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .height(1.dp)
                                        .background(palette.line)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onEdit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.focus,
                    contentColor = palette.surface
                )
            ) {
                Text("编辑习惯")
            }
            if (canSkip) {
                TextButton(
                    onClick = onSkipToday,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("今天跳过", color = palette.muted)
                }
            }
            TextButton(
                onClick = onArchive,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("移除这个习惯", color = palette.danger)
            }
            Spacer(Modifier.height(20.dp))
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitDetailSheet(
    habit: Habit,
    logs: List<HabitLog>,
    today: LocalDate,
    reminderText: String,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onSkipToday: () -> Unit,
    onArchive: () -> Unit
) {
    val palette = Potato.palette
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = palette.surface,
        sheetMaxWidth = 680.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(palette.line)
            )
        }
    ) {
        HabitDetailContent(
            habit = habit,
            logs = logs,
            today = today,
            reminderText = reminderText,
            onEdit = onEdit,
            onSkipToday = onSkipToday,
            onArchive = onArchive,
            onClose = onDismiss,
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
        )
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val palette = Potato.palette
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surfaceRaised)
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted
            )
            Spacer(Modifier.height(4.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = palette.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RangeChip(text: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val palette = Potato.palette
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) accent else palette.muted,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) accent.copy(alpha = 0.14f) else palette.surfaceRaised)
            .border(1.dp, if (selected) accent else palette.line, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun HeatmapGrid(
    habit: Habit,
    logsByDate: Map<LocalDate, HabitLog>,
    today: LocalDate,
    days: Int,
    accent: Color
) {
    val palette = Potato.palette
    val cellSize = if (days <= 30) 24.dp else 16.dp
    val cellGap = if (days <= 30) 4.dp else 2.dp
    val rangeStart = today.minusDays((days - 1).toLong())
    val created = TimeFormat.localDate(habit.createdAt)
    val gridStart = rangeStart.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekCount = (ChronoUnit.DAYS.between(gridStart, today).toInt() / 7) + 1
    val weekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
            weekdayLabels.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .width(12.dp)
                        .height(cellSize),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (index % 2 == 0) {
                        Text(
                            label,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.muted
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(cellGap)) {
            repeat(weekCount) { week ->
                Column(verticalArrangement = Arrangement.spacedBy(cellGap)) {
                    repeat(7) { day ->
                        val date = gridStart.plusDays(week * 7L + day)
                        HeatmapCell(
                            habit = habit,
                            date = date,
                            log = logsByDate[date],
                            today = today,
                            rangeStart = rangeStart,
                            created = created,
                            accent = accent,
                            cellSize = cellSize
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeatmapCell(
    habit: Habit,
    date: LocalDate,
    log: HabitLog?,
    today: LocalDate,
    rangeStart: LocalDate,
    created: LocalDate,
    accent: Color,
    cellSize: Dp
) {
    val palette = Potato.palette
    val inRange = !date.isBefore(rangeStart) && !date.isAfter(today)
    val scheduled = inRange && !date.isBefore(created) && habit.isScheduledOn(date)
    val completed = HabitStats.isCompleted(log)
    val skipped = HabitStats.isSkipped(log)
    val progress = logProgress(habit, log)
    val fill = when {
        !inRange -> Color.Transparent
        !scheduled -> palette.line.copy(alpha = 0.35f)
        completed -> accent
        skipped -> palette.muted.copy(alpha = 0.28f)
        log != null -> accent.copy(alpha = 0.25f + 0.55f * progress)
        date == today -> palette.surfaceRaised
        else -> palette.line.copy(alpha = 0.8f)
    }
    val statusText = when {
        !scheduled -> "无计划"
        completed -> "已完成"
        skipped -> "跳过"
        log != null -> "未完成"
        date == today -> "今天待完成"
        else -> "未记录"
    }
    Box(
        modifier = Modifier
            .size(cellSize)
            .clip(RoundedCornerShape(4.dp))
            .background(fill)
            .then(
                if (scheduled && date == today && !completed) {
                    Modifier.border(1.dp, accent, RoundedCornerShape(4.dp))
                } else {
                    Modifier
                }
            )
            .semantics {
                contentDescription = "${TimeFormat.shortDate(TimeFormat.startOfDay(date))}，$statusText"
            }
    )
}

@Composable
private fun HeatmapLegend(accent: Color) {
    val palette = Potato.palette
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(accent, "完成")
        LegendItem(accent.copy(alpha = 0.45f), "部分")
        LegendItem(palette.muted.copy(alpha = 0.28f), "跳过")
        LegendItem(palette.line.copy(alpha = 0.8f), "未记录")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    val palette = Potato.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = palette.muted)
    }
}

@Composable
private fun RecentLogRow(habit: Habit, log: HabitLog) {
    val palette = Potato.palette
    val date = remember(log.date) {
        runCatching { LocalDate.parse(log.date) }.getOrNull()
    }
    val completed = log.status == HabitLogStatus.COMPLETED
    val skipped = log.status == HabitLogStatus.SKIPPED
    val dateLabel = date?.let {
        "${TimeFormat.shortDate(TimeFormat.startOfDay(it))} · ${TimeFormat.weekdayShort(it)}"
    } ?: log.date
    val detail = when {
        skipped -> "今天休息"
        completed && habit.type == HabitType.TIMED && log.focusedSeconds > 0 ->
            "${log.focusedSeconds / 60} 分钟"
        completed && habit.type == HabitType.COUNT && log.countValue > 0 ->
            "${log.countValue} ${habit.unit.ifBlank { "次" }}"
        completed -> "手动完成"
        else -> progressLabel(habit, log)
    }
    val status = when {
        completed -> "完成"
        skipped -> "跳过"
        else -> "未完成"
    }
    val statusColor = when {
        completed -> palette.breakColor
        skipped -> palette.muted
        else -> palette.accent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                dateLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = palette.ink
            )
            Spacer(Modifier.height(2.dp))
            Text(
                detail,
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted
            )
        }
        Text(
            status,
            style = MaterialTheme.typography.labelMedium,
            color = statusColor
        )
    }
}

private fun progressLabel(habit: Habit, log: HabitLog?): String = when (habit.type) {
    HabitType.TIMED -> "${(log?.focusedSeconds ?: 0) / 60} / ${habit.targetSeconds / 60} 分钟"
    HabitType.COUNT -> "${log?.countValue ?: 0} / ${habit.targetCount} ${habit.unit.ifBlank { "次" }}"
}

private fun logProgress(habit: Habit, log: HabitLog?): Float = when (habit.type) {
    HabitType.TIMED -> (log?.focusedSeconds ?: 0).toFloat() /
        habit.targetSeconds.coerceAtLeast(1)
    HabitType.COUNT -> (log?.countValue ?: 0).toFloat() /
        habit.targetCount.coerceAtLeast(1)
}.coerceIn(0f, 1f)

private fun heatmapRangeText(today: LocalDate, days: Int): String {
    val start = today.minusDays((days - 1).toLong())
    val formatter = java.time.format.DateTimeFormatter.ofPattern("M月d日")
    return "${start.format(formatter)} - ${today.format(formatter)}"
}
