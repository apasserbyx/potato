package com.potato.focus.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.potato.focus.PotatoApp
import com.potato.focus.data.FocusSession
import com.potato.focus.data.SessionMode
import com.potato.focus.data.SessionStatus
import com.potato.focus.data.StatsCalculator
import com.potato.focus.data.StatsSummary
import com.potato.focus.ui.components.EmptyHint
import com.potato.focus.ui.components.PotatoCard
import com.potato.focus.ui.components.SectionLabel
import com.potato.focus.ui.components.StatBarChart
import com.potato.focus.ui.components.TagPill
import com.potato.focus.ui.theme.Potato
import com.potato.focus.util.TimeFormat

private enum class StatsPeriod(val label: String, val days: Int) {
    DAY("日", 1),
    WEEK("周", 7),
    MONTH("月", 30)
}

@Composable
fun StatsScreen(refreshKey: Int) {
    val context = LocalContext.current
    val app = context.applicationContext as PotatoApp
    val palette = Potato.palette
    var period by remember { mutableStateOf(StatsPeriod.WEEK) }
    var allSessions by remember { mutableStateOf<List<FocusSession>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(refreshKey, period) {
        allSessions = app.sessions.allSessions()
        loaded = true
    }

    val now = System.currentTimeMillis()
    val periodStart = when (period) {
        StatsPeriod.DAY -> TimeFormat.startOfToday(now)
        StatsPeriod.WEEK -> TimeFormat.startOfDay(TimeFormat.localDate(now).minusDays(6))
        StatsPeriod.MONTH -> TimeFormat.startOfDay(TimeFormat.localDate(now).minusDays(29))
    }
    val periodSessions = allSessions.filter { it.startedAt >= periodStart }
    val summary = StatsCalculator.summary(periodSessions, now)
        .copy(streak = StatsCalculator.streak(allSessions, now))
    val days = StatsCalculator.daily(allSessions, period.days, now)
    val goal = app.settings.dailyGoalMinutes
    val averageMinutes = summary.focusSeconds / 60f / period.days
    val averageText = if (period.days == 1) {
        "${TimeFormat.roundedMinutes(summary.focusSeconds)} 分钟"
    } else {
        java.util.Locale.US.let { locale ->
            String.format(locale, "%.1f 分钟", averageMinutes)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("数据", style = MaterialTheme.typography.headlineLarge, color = palette.ink)
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(palette.surfaceRaised)
                        .padding(4.dp)
                ) {
                    StatsPeriod.entries.forEach { item ->
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (period == item) palette.surface else palette.muted,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (period == item) palette.ink else Color.Transparent)
                                .clickable { period = item }
                                .padding(horizontal = 13.dp, vertical = 6.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "看得见的时间，才更容易被安排。",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted
            )
        }

        item {
            PotatoCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text(
                                text = when (period) {
                                    StatsPeriod.DAY -> "今天专注"
                                    StatsPeriod.WEEK -> "最近 7 天"
                                    StatsPeriod.MONTH -> "最近 30 天"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.muted
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                text = TimeFormat.durationText(summary.focusSeconds),
                                style = MaterialTheme.typography.headlineLarge,
                                color = palette.ink
                            )
                        }
                        if (summary.streak > 0) {
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(palette.accentSoft)
                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = palette.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "连续 ${summary.streak} 天",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        Metric("土豆", "${summary.tomatoCount}", "颗")
                        Metric("自由专注", "${summary.openCount}", "次")
                        Metric("完成率", "${(summary.completionRate * 100).toInt()}", "%")
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (period == StatsPeriod.DAY) "每日目标 $goal 分钟" else "日均 $averageText",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.muted
                        )
                        Text(
                            if (period == StatsPeriod.DAY) "${(summary.focusSeconds / 60)} / $goal" else "目标 $goal / 天",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.muted
                        )
                    }
                    Spacer(Modifier.height(7.dp))
                    GoalBar(
                        progress = if (period == StatsPeriod.DAY) {
                            (summary.focusSeconds / 60f / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
                        } else {
                            (averageMinutes / goal.coerceAtLeast(1)).coerceIn(0f, 1f)
                        }
                    )
                }
            }
        }

        if (period != StatsPeriod.DAY) {
            item {
                PotatoCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        SectionLabel("专注趋势")
                        Spacer(Modifier.height(16.dp))
                        StatBarChart(
                            days = days,
                            goalMinutes = goal,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (summary.tagSlices.isNotEmpty()) {
            item {
                PotatoCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp)) {
                        SectionLabel("时间花在哪")
                        Spacer(Modifier.height(14.dp))
                        summary.tagSlices.take(5).forEach { slice ->
                            val percent = if (summary.focusSeconds == 0L) 0
                            else ((slice.focusSeconds.toFloat() / summary.focusSeconds) * 100).toInt()
                            val tagColor = tagColor(slice.tag)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = slice.tag,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = palette.ink,
                                    modifier = Modifier.width(64.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(palette.line)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(percent / 100f)
                                            .height(8.dp)
                                            .clip(CircleShape)
                                            .background(tagColor)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    "$percent%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.muted,
                                    modifier = Modifier.width(34.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionLabel(
                text = "记录明细",
                trailing = if (periodSessions.any { it.isFocus }) "共 ${periodSessions.count { it.isFocus }} 条" else null
            )
        }

        val records = periodSessions
            .filter {
                it.isFocus &&
                    it.status != SessionStatus.RUNNING &&
                    it.status != SessionStatus.DISCARDED
            }
            .take(if (period == StatsPeriod.DAY) 30 else 8)
        if (!loaded) {
            item { EmptyHint("正在读取记录…") }
        } else if (records.isEmpty()) {
            item {
                PotatoCard(Modifier.fillMaxWidth()) {
                    EmptyHint(
                        "这里还空着。完成第一颗土豆或一次正计时，记录会自动出现在这里。",
                        Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            items(records, key = { it.id }) { session ->
                SessionRow(session)
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String) {
    val palette = Potato.palette
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = palette.muted)
        Spacer(Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = palette.ink
            )
            Spacer(Modifier.width(3.dp))
            Text(
                unit,
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun GoalBar(progress: Float) {
    val palette = Potato.palette
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(7.dp)
            .clip(CircleShape)
            .background(palette.focusSoft)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress)
                .height(7.dp)
                .clip(CircleShape)
                .background(palette.focus)
        )
    }
}

@Composable
private fun SessionRow(session: FocusSession) {
    val palette = Potato.palette
    val isOpen = session.mode == SessionMode.OPEN
    val isHabit = session.mode == SessionMode.HABIT
    val badgeColor = if (isOpen || isHabit) palette.accent else palette.focus
    val badgeBg = if (isOpen || isHabit) palette.accentSoft else palette.focusSoft
    PotatoCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        isOpen -> "自"
                        isHabit -> "习"
                        else -> "番"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = badgeColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.taskTitle.ifBlank { if (isOpen) "自由专注" else "专注" },
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (session.tag.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        TagPill(session.tag)
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${TimeFormat.shortDate(session.startedAt)} · ${TimeFormat.clockTime(session.startedAt)}" +
                        if (session.status == SessionStatus.PARTIAL) " · 部分完成" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = TimeFormat.durationText(session.focusedSeconds.toLong()),
                style = MaterialTheme.typography.labelLarge,
                color = palette.ink,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun tagColor(tag: String): Color {
    val palette = Potato.palette
    return when (tag) {
        "工作" -> palette.focus
        "学习" -> palette.breakColor
        "创作" -> palette.accent
        "生活" -> Color(0xFF7A8794)
        else -> palette.muted
    }
}
