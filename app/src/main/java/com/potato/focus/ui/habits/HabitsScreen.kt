package com.potato.focus.ui.habits

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.potato.focus.PotatoApp
import com.potato.focus.data.Habit
import com.potato.focus.data.HabitIconType
import com.potato.focus.data.HabitLog
import com.potato.focus.data.HabitReminderMode
import com.potato.focus.data.HabitReminderSlot
import com.potato.focus.data.HabitType
import com.potato.focus.data.HabitStats
import com.potato.focus.data.SettingsStore
import com.potato.focus.data.isScheduledOn
import com.potato.focus.habits.HabitEvents
import com.potato.focus.habits.HabitReminderScheduler
import com.potato.focus.habits.HabitSounds
import com.potato.focus.timer.TimerController
import com.potato.focus.timer.TimerMath
import com.potato.focus.timer.TimerSnapshot
import com.potato.focus.ui.components.PotatoCard
import com.potato.focus.ui.components.SectionLabel
import com.potato.focus.ui.theme.Potato
import com.potato.focus.util.CustomSoundStorage
import com.potato.focus.util.HabitIconStorage
import com.potato.focus.util.TimeFormat
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.launch

private val slotOrder = listOf(
    HabitReminderSlot.MORNING,
    HabitReminderSlot.AFTERNOON,
    HabitReminderSlot.EVENING,
    HabitReminderSlot.NONE
)

private val habitColors = listOf("#96592F", "#5F7550", "#8F6224", "#8D6E52")

private data class HabitGroup(
    val key: String,
    val title: String,
    val timeLabel: String,
    val habits: List<Habit>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitsScreen(
    snapshot: TimerSnapshot,
    now: Long,
    refreshKey: Int,
    onStartFocus: () -> Unit,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as PotatoApp
    val palette = Potato.palette
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val haptics = LocalHapticFeedback.current
    val controller = remember { TimerController(context) }
    val today = TimeFormat.localDate(System.currentTimeMillis())

    var habits by remember { mutableStateOf<List<Habit>>(emptyList()) }
    var todayLogs by remember { mutableStateOf<Map<Long, HabitLog>>(emptyMap()) }
    var recentLogs by remember { mutableStateOf<List<HabitLog>>(emptyList()) }
    var editingHabit by remember { mutableStateOf<Habit?>(null) }
    var pendingArchive by remember { mutableStateOf<Habit?>(null) }
    var pendingStartHabit by remember { mutableStateOf<Habit?>(null) }
    var menuHabitId by remember { mutableLongStateOf(-1L) }
    var blockedByActiveTimer by remember { mutableStateOf(false) }
    var detailHabitId by remember { mutableStateOf<Long?>(null) }
    var detailLogs by remember { mutableStateOf<List<HabitLog>>(emptyList()) }

    fun reload() {
        habits = app.habits.activeHabits()
        todayLogs = app.habitLogs.logsForDate(today)
        recentLogs = app.habitLogs.logsSince(today.minusDays(180))
    }

    LaunchedEffect(refreshKey, snapshot.status, snapshot.phase, snapshot.habitId) {
        reload()
    }

    LaunchedEffect(Unit) {
        HabitEvents.version.collect { reload() }
    }

    LaunchedEffect(detailHabitId, recentLogs) {
        val id = detailHabitId
        detailLogs = if (id == null) emptyList() else app.habitLogs.logsForHabit(id)
    }

    val detailHabit = detailHabitId?.let { id -> habits.firstOrNull { it.id == id } }

    val logsByHabit = remember(recentLogs) { HabitStats.logsByHabit(recentLogs) }
    val scheduled = habits.filter { it.isScheduledOn(today) }
    val completedCount = scheduled.count { HabitStats.isCompleted(todayLogs[it.id]) }
    val progress = if (scheduled.isEmpty()) 0f else completedCount.toFloat() / scheduled.size
    val slotGroups = slotOrder.mapNotNull { slot ->
        val rows = scheduled
            .filter { it.reminderMode == HabitReminderMode.SLOT && it.reminderSlot == slot }
            .sortedWith(compareBy({ HabitStats.isCompleted(todayLogs[it.id]) }, { it.sortOrder }))
        if (rows.isEmpty()) {
            null
        } else {
            HabitGroup(
                key = slot.name,
                title = when (slot) {
                    HabitReminderSlot.MORNING -> "早上"
                    HabitReminderSlot.AFTERNOON -> "下午"
                    HabitReminderSlot.EVENING -> "晚上"
                    HabitReminderSlot.NONE -> "未安排时间"
                },
                timeLabel = if (slot == HabitReminderSlot.NONE) "" else formatSlotTime(app.settings.habitSlotMinutes(slot)),
                habits = rows
            )
        }
    }
    val independentRows = scheduled
        .filter { it.reminderMode == HabitReminderMode.INDEPENDENT }
        .sortedWith(compareBy({ HabitStats.isCompleted(todayLogs[it.id]) }, { it.reminderMinutes }, { it.sortOrder }))
    val groups = slotGroups + if (independentRows.isEmpty()) {
        emptyList()
    } else {
        listOf(HabitGroup(key = "independent", title = "独立提醒", timeLabel = "按设定时间", habits = independentRows))
    }

    fun startHabit(habit: Habit) {
        if (snapshot.isActive) {
            if (snapshot.isHabit && snapshot.habitId == habit.id) {
                onStartFocus()
            } else {
                blockedByActiveTimer = true
            }
            return
        }
        val log = todayLogs[habit.id]
        val remaining = HabitStats.remainingSeconds(habit, log)
        if (controller.startHabit(habit, remaining)) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onChanged()
            onStartFocus()
        } else {
            blockedByActiveTimer = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        pendingStartHabit?.let { startHabit(it) }
        pendingStartHabit = null
    }

    fun startHabitWithPermission(habit: Habit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            pendingStartHabit = habit
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startHabit(habit)
        }
    }

    fun requestReminderPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val listBlock: @Composable (Modifier) -> Unit = { modifier ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        item {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("习惯", style = MaterialTheme.typography.headlineLarge, color = palette.ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        TimeFormat.dateTitle(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted
                    )
                }
                IconButton(onClick = {
                    editingHabit = newHabitDraft(habits.size)
                }) {
                    Icon(Icons.Rounded.Add, contentDescription = "添加习惯", tint = palette.focus)
                }
            }
        }

        item {
            PotatoCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "今日习惯",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.muted
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "$completedCount / ${scheduled.size}",
                                style = MaterialTheme.typography.headlineLarge,
                                color = palette.ink
                            )
                        }
                        Text(
                            if (scheduled.isEmpty()) "今天还没有计划" else "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.accent
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(palette.accentSoft)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(7.dp)
                                .clip(CircleShape)
                                .background(palette.accent)
                        )
                    }
                }
            }
        }

        if (scheduled.isEmpty()) {
            item {
                PotatoCard(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            if (habits.isEmpty()) "今天想养成什么习惯？" else "今天没有安排习惯。",
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.ink
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (habits.isEmpty()) "先设一个 10 分钟的小习惯。" else "休息也是计划的一部分。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { editingHabit = newHabitDraft(habits.size) },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.focus,
                                contentColor = palette.surface
                            )
                        ) {
                            Text("添加习惯")
                        }
                    }
                }
            }
        }

        groups.forEach { group ->
            item(key = "header-${group.key}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        group.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.ink
                    )
                    if (group.timeLabel.isNotBlank()) {
                        Text(
                            group.timeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.muted
                        )
                    }
                }
            }
            items(group.habits, key = { it.id }) { habit ->
                HabitCard(
                    habit = habit,
                    log = todayLogs[habit.id],
                    logsByDate = logsByHabit[habit.id].orEmpty(),
                    today = today,
                    snapshot = snapshot,
                    now = now,
                    menuOpen = menuHabitId == habit.id,
                    onMenuOpen = { menuHabitId = habit.id },
                    onMenuDismiss = { menuHabitId = -1L },
                    onStart = { startHabitWithPermission(habit) },
                    onOpenFocus = onStartFocus,
                    onIncrement = {
                        app.habitLogs.adjustCount(habit.id, today, 1, habit.targetCount)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        reload()
                        onChanged()
                    },
                    onDecrement = {
                        app.habitLogs.adjustCount(habit.id, today, -1, habit.targetCount)
                        reload()
                        onChanged()
                    },
                    onManualComplete = {
                        app.habitLogs.markCompleted(habit.id, today)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        reload()
                        onChanged()
                    },
                    onSkip = {
                        app.habitLogs.markSkipped(habit.id, today)
                        reload()
                        onChanged()
                    },
                    onEdit = { editingHabit = habit },
                    onOpenDetail = { detailHabitId = habit.id }
                )
            }
        }

        if (groups.isNotEmpty()) {
            item {
                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = { editingHabit = newHabitDraft(habits.size) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("添加习惯", color = palette.focus)
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (isWide) {
        Row(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(0.56f)
                    .fillMaxHeight()
            ) {
                listBlock(Modifier)
            }
            Box(
                modifier = Modifier
                    .weight(0.44f)
                    .fillMaxHeight()
                    .padding(top = 12.dp, end = 24.dp, bottom = 12.dp)
            ) {
                val habit = detailHabit
                if (habit == null) {
                    PotatoCard(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "选择一个习惯",
                                style = MaterialTheme.typography.titleLarge,
                                color = palette.ink
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "查看连续天数、热力图和最近记录。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = palette.muted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    PotatoCard(Modifier.fillMaxSize()) {
                        HabitDetailContent(
                            habit = habit,
                            logs = detailLogs,
                            today = today,
                            reminderText = habitReminderText(habit, app.settings),
                            onEdit = {
                                detailHabitId = null
                                editingHabit = habit
                            },
                            onSkipToday = {
                                app.habitLogs.markSkipped(habit.id, today)
                                detailLogs = app.habitLogs.logsForHabit(habit.id)
                                reload()
                                onChanged()
                            },
                            onArchive = {
                                detailHabitId = null
                                pendingArchive = habit
                            },
                            onClose = { detailHabitId = null },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    } else {
        listBlock(Modifier)
    }

    if (!isWide) {
        detailHabit?.let { habit ->
            HabitDetailSheet(
                habit = habit,
                logs = detailLogs,
                today = today,
                reminderText = habitReminderText(habit, app.settings),
                onDismiss = { detailHabitId = null },
                onEdit = {
                    detailHabitId = null
                    editingHabit = habit
                },
                onSkipToday = {
                    app.habitLogs.markSkipped(habit.id, today)
                    detailLogs = app.habitLogs.logsForHabit(habit.id)
                    reload()
                    onChanged()
                },
                onArchive = {
                    detailHabitId = null
                    pendingArchive = habit
                }
            )
        }
    }

    editingHabit?.let { habit ->
        HabitEditorSheet(
            initial = habit,
            isEditing = habit.id > 0L,
            onDismiss = { editingHabit = null },
            onSave = { saved ->
                if (saved.id == 0L) {
                    app.habits.insert(saved)
                } else {
                    app.habits.update(saved)
                }
                HabitReminderScheduler.scheduleAll(context)
                if (saved.reminderEnabled &&
                    (saved.reminderMode == HabitReminderMode.INDEPENDENT ||
                        saved.reminderSlot != HabitReminderSlot.NONE)
                ) {
                    requestReminderPermissionIfNeeded()
                }
                editingHabit = null
                reload()
                onChanged()
            },
            onArchive = {
                editingHabit = null
                pendingArchive = habit
            }
        )
    }

    pendingArchive?.let { habit ->
        AlertDialog(
            onDismissRequest = { pendingArchive = null },
            containerColor = palette.surface,
            title = { Text("移除这个习惯？") },
            text = { Text("它会从今日清单和提醒中移除，历史记录仍会保留。") },
            confirmButton = {
                TextButton(onClick = {
                    app.habits.archive(habit.id)
                    HabitReminderScheduler.scheduleAll(context)
                    pendingArchive = null
                    reload()
                    onChanged()
                }) { Text("移除", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingArchive = null }) { Text("取消") }
            }
        )
    }

    if (blockedByActiveTimer) {
        AlertDialog(
            onDismissRequest = { blockedByActiveTimer = false },
            containerColor = palette.surface,
            title = { Text("当前有计时正在进行") },
            text = { Text("先回到专注页结束当前计时，再开始新的习惯。") },
            confirmButton = {
                TextButton(onClick = {
                    blockedByActiveTimer = false
                    onStartFocus()
                }) { Text("去专注页") }
            },
            dismissButton = {
                TextButton(onClick = { blockedByActiveTimer = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun HabitCard(
    habit: Habit,
    log: HabitLog?,
    logsByDate: Map<LocalDate, HabitLog>,
    today: LocalDate,
    snapshot: TimerSnapshot,
    now: Long,
    menuOpen: Boolean,
    onMenuOpen: () -> Unit,
    onMenuDismiss: () -> Unit,
    onStart: () -> Unit,
    onOpenFocus: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onManualComplete: () -> Unit,
    onSkip: () -> Unit,
    onEdit: () -> Unit,
    onOpenDetail: () -> Unit
) {
    val palette = Potato.palette
    val accent = habitColor(habit.color)
    val completed = HabitStats.isCompleted(log)
    val skipped = HabitStats.isSkipped(log)
    val isRunning = snapshot.isActive && snapshot.isHabit && snapshot.habitId == habit.id
    val streak = HabitStats.streak(habit, logsByDate, today)
    val count = log?.countValue ?: 0
    val focusedSeconds = log?.focusedSeconds ?: 0
    val progress = when (habit.type) {
        HabitType.TIMED -> (focusedSeconds.toFloat() / habit.targetSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
        HabitType.COUNT -> (count.toFloat() / habit.targetCount.coerceAtLeast(1)).coerceIn(0f, 1f)
    }

    PotatoCard(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (completed || skipped) 0.62f else 1f)
            .clickable(onClick = onOpenDetail)
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitIconVisual(
                    iconType = habit.iconType,
                    icon = habit.icon,
                    imagePath = habit.iconImagePath,
                    contentDescription = habit.name,
                    size = 42.dp,
                    background = accent.copy(alpha = 0.14f),
                    tint = accent
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val reminderSuffix = if (
                            habit.reminderMode == HabitReminderMode.INDEPENDENT &&
                            habit.reminderMinutes in 0..(24 * 60 - 1)
                        ) {
                            " · ${formatSlotTime(habit.reminderMinutes)}"
                        } else {
                            ""
                        }
                        Text(
                            (if (habit.type == HabitType.TIMED) {
                                "目标 ${habit.targetSeconds / 60} 分钟"
                            } else {
                                "目标 ${habit.targetCount} ${habit.unit.ifBlank { "次" }}"
                            }) + reminderSuffix,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.muted
                        )
                        if (streak > 0) {
                            Text(
                                "  ·  连续 $streak 天",
                                style = MaterialTheme.typography.labelMedium,
                                color = accent
                            )
                        }
                    }
                }
                Box {
                    IconButton(onClick = onMenuOpen) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "更多",
                            tint = palette.muted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = onMenuDismiss,
                        containerColor = palette.surface
                    ) {
                        if (!completed) {
                            DropdownMenuItem(
                                text = { Text("手动补完成") },
                                onClick = {
                                    onMenuDismiss()
                                    onManualComplete()
                                }
                            )
                            if (!skipped) {
                                DropdownMenuItem(
                                    text = { Text("今天跳过") },
                                    onClick = {
                                        onMenuDismiss()
                                        onSkip()
                                    }
                                )
                            }
                        }
                        DropdownMenuItem(
                            text = { Text("习惯详情") },
                            onClick = {
                                onMenuDismiss()
                                onOpenDetail()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("编辑习惯") },
                            onClick = {
                                onMenuDismiss()
                                onEdit()
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        when {
                            completed -> "已完成"
                            skipped -> "今天休息"
                            isRunning -> "进行中 · 剩余 ${
                                TimeFormat.timerText(
                                    (TimerMath.remainingMillis(snapshot, now) + 999) / 1000
                                )
                            }"
                            else -> HabitStats.progressText(habit, log)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (completed) palette.breakColor else palette.muted
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape)
                            .background(palette.line)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(if (completed) palette.breakColor else accent)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                when {
                    completed -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "已完成",
                                tint = palette.breakColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "完成",
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.breakColor
                            )
                        }
                    }
                    skipped -> {
                        Text(
                            "已跳过",
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.muted
                        )
                    }
                    habit.type == HabitType.TIMED -> {
                        if (isRunning) {
                            Button(
                                onClick = onOpenFocus,
                                shape = CircleShape,
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = palette.accent,
                                    contentColor = palette.surface
                                )
                            ) {
                                Text("进行中", style = MaterialTheme.typography.labelMedium)
                            }
                        } else {
                            Button(
                                onClick = onStart,
                                shape = CircleShape,
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = palette.surface
                                )
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(3.dp))
                                Text("开始", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    else -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (count > 0) {
                                IconButton(
                                    onClick = onDecrement,
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Text("−", style = MaterialTheme.typography.titleMedium, color = palette.muted)
                                }
                            }
                            Text(
                                "$count/${habit.targetCount}",
                                style = MaterialTheme.typography.labelLarge,
                                color = palette.ink
                            )
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = onIncrement,
                                shape = CircleShape,
                                modifier = Modifier.height(36.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = palette.surface
                                )
                            ) {
                                Text("+1", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitEditorSheet(
    initial: Habit,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSave: (Habit) -> Unit,
    onArchive: () -> Unit
) {
    val palette = Potato.palette
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var type by remember(initial.id) { mutableStateOf(initial.type) }
    var minutesText by remember(initial.id) {
        mutableStateOf(((initial.targetSeconds / 60).takeIf { it > 0 } ?: 25).toString())
    }
    var countText by remember(initial.id) {
        mutableStateOf((initial.targetCount.takeIf { it > 0 } ?: 8).toString())
    }
    var unit by remember(initial.id) { mutableStateOf(initial.unit.ifBlank { "次" }) }
    var weekdaysMask by remember(initial.id) { mutableIntStateOf(initial.weekdaysMask) }
    var slot by remember(initial.id) { mutableStateOf(initial.reminderSlot) }
    var reminderEnabled by remember(initial.id) { mutableStateOf(initial.reminderEnabled) }
    var reminderMode by remember(initial.id) { mutableStateOf(initial.reminderMode) }
    var reminderMinutes by remember(initial.id) {
        mutableIntStateOf(initial.reminderMinutes.takeIf { it in 0..(24 * 60 - 1) } ?: (8 * 60 + 30))
    }
    var soundKey by remember(initial.id) { mutableStateOf(initial.soundKey) }
    var showSoundPicker by remember { mutableStateOf(false) }
    var iconType by remember(initial.id) { mutableStateOf(initial.iconType) }
    var icon by remember(initial.id) {
        mutableStateOf(initial.icon.ifBlank { if (initial.iconType == HabitIconType.EMOJI) "🌱" else "book" })
    }
    var iconImagePath by remember(initial.id) { mutableStateOf(initial.iconImagePath) }
    var colorHex by remember(initial.id) { mutableStateOf(initial.color.ifBlank { habitColors.first() }) }
    var note by remember(initial.id) { mutableStateOf(initial.note) }
    var error by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = HabitIconStorage.saveFromUri(context, uri)
                if (path != null) {
                    iconImagePath = path
                    iconType = HabitIconType.IMAGE
                    error = ""
                } else {
                    error = "图片处理失败，请换一张试试"
                }
            }
        }
    }

    val imagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        imagePicker.launch("image/*")
    }

    val audioPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val key = CustomSoundStorage.import(context, uri)
                if (key != null) {
                    soundKey = key
                    error = ""
                } else {
                    error = "音频导入失败，请选择小于 5MB 的音频文件"
                }
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        audioPicker.launch(arrayOf("audio/*"))
    }

    fun openAudioPicker() {
        val permissions = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            Build.VERSION.SDK_INT <= 28 -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            audioPicker.launch(arrayOf("audio/*"))
        } else {
            audioPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun openImagePicker() {
        val permissions = when {
            Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
            else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            imagePicker.launch("image/*")
        } else {
            imagePermissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun pickReminderTime() {
        TimePickerDialog(
            context,
            { _, hour, minute -> reminderMinutes = hour * 60 + minute },
            reminderMinutes / 60,
            reminderMinutes % 60,
            true
        ).show()
    }

    fun save() {
        if (name.isBlank()) {
            error = "给习惯起一个名字"
            return
        }
        if (weekdaysMask == 0) {
            error = "至少选择一个重复日"
            return
        }
        if (iconType == HabitIconType.IMAGE && iconImagePath.isBlank()) {
            error = "请选择一张图标图片"
            return
        }
        val minutes = minutesText.toIntOrNull()?.coerceIn(1, 180) ?: 25
        val targetCount = countText.toIntOrNull()?.coerceIn(1, 999) ?: 8
        val updated = initial.copy(
            name = name.trim(),
            icon = icon,
            iconType = iconType,
            iconImagePath = if (iconType == HabitIconType.IMAGE) iconImagePath else "",
            color = colorHex,
            type = type,
            targetSeconds = if (type == HabitType.TIMED) minutes * 60 else 0,
            targetCount = if (type == HabitType.COUNT) targetCount else 0,
            unit = if (type == HabitType.COUNT) unit.trim().ifBlank { "次" } else "",
            weekdaysMask = weekdaysMask,
            reminderSlot = slot,
            reminderEnabled = reminderEnabled,
            reminderMode = reminderMode,
            reminderMinutes = if (reminderMode == HabitReminderMode.INDEPENDENT) {
                reminderMinutes.coerceIn(0, 24 * 60 - 1)
            } else {
                -1
            },
            soundKey = if (reminderMode == HabitReminderMode.INDEPENDENT) soundKey else "",
            note = note
        )
        onSave(updated)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Text(
                if (isEditing) "编辑习惯" else "新建习惯",
                style = MaterialTheme.typography.headlineMedium,
                color = palette.ink
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; error = "" },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("名称") },
                placeholder = { Text("阅读、冥想、喝水…") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypePill("计时型", type == HabitType.TIMED) { type = HabitType.TIMED }
                TypePill("次数型", type == HabitType.COUNT) { type = HabitType.COUNT }
            }
            Spacer(Modifier.height(12.dp))

            if (type == HabitType.TIMED) {
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter(Char::isDigit).take(3); error = "" },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("目标时长（分钟）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp)
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = countText,
                        onValueChange = { countText = it.filter(Char::isDigit).take(3); error = "" },
                        modifier = Modifier.weight(1f),
                        label = { Text("目标次数") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(14.dp)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it.take(4) },
                        modifier = Modifier.width(110.dp),
                        label = { Text("单位") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            Text("重复", style = MaterialTheme.typography.titleMedium, color = palette.ink)
            Spacer(Modifier.height(8.dp))
            WeekdaySelector(weekdaysMask) { weekdaysMask = it }
            Spacer(Modifier.height(14.dp))

            Text("提醒方式", style = MaterialTheme.typography.titleMedium, color = palette.ink)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TypePill("跟随时段", reminderMode == HabitReminderMode.SLOT) {
                    reminderMode = HabitReminderMode.SLOT
                }
                TypePill("独立提醒", reminderMode == HabitReminderMode.INDEPENDENT) {
                    reminderMode = HabitReminderMode.INDEPENDENT
                    reminderEnabled = true
                }
            }
            Spacer(Modifier.height(10.dp))

            if (reminderMode == HabitReminderMode.SLOT) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    SlotPill("早上", slot == HabitReminderSlot.MORNING) { slot = HabitReminderSlot.MORNING }
                    SlotPill("下午", slot == HabitReminderSlot.AFTERNOON) { slot = HabitReminderSlot.AFTERNOON }
                    SlotPill("晚上", slot == HabitReminderSlot.EVENING) { slot = HabitReminderSlot.EVENING }
                    SlotPill("不提醒", slot == HabitReminderSlot.NONE) { slot = HabitReminderSlot.NONE }
                }
                if (slot != HabitReminderSlot.NONE) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "参与该时段的汇总提醒",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = reminderEnabled,
                            onCheckedChange = { reminderEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = palette.focus,
                                uncheckedThumbColor = palette.muted,
                                uncheckedTrackColor = palette.line
                            )
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "每天提醒时间",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.ink
                        )
                        Text(
                            formatSlotTime(reminderMinutes),
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.focus
                        )
                    }
                    TextButton(onClick = ::pickReminderTime) {
                        Text("修改时间")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "开启提醒",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = palette.focus,
                            uncheckedThumbColor = palette.muted,
                            uncheckedTrackColor = palette.line
                        )
                    )
                }
                if (reminderEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "提示音",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.ink,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showSoundPicker = true }) {
                            Text(HabitSounds.label(soundKey))
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            Text("图标", style = MaterialTheme.typography.titleMedium, color = palette.ink)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                builtInHabitIcons.forEach { item ->
                    val selected = iconType == HabitIconType.BUILTIN && icon == item.key
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) palette.accentSoft else palette.surfaceRaised)
                            .border(
                                1.dp,
                                if (selected) palette.accent else palette.line,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable {
                                iconType = HabitIconType.BUILTIN
                                icon = item.key
                                error = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            item.image,
                            contentDescription = item.label,
                            tint = if (selected) palette.accent else palette.muted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                habitEmojis.forEach { item ->
                    val selected = iconType == HabitIconType.EMOJI && icon == item
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (selected) palette.accentSoft else palette.surfaceRaised)
                            .border(
                                1.dp,
                                if (selected) palette.accent else palette.line,
                                CircleShape
                            )
                            .clickable {
                                iconType = HabitIconType.EMOJI
                                icon = item
                                error = ""
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                HabitIconVisual(
                    iconType = iconType,
                    icon = icon,
                    imagePath = iconImagePath,
                    contentDescription = "当前习惯图标",
                    size = 50.dp,
                    background = habitColor(colorHex).copy(alpha = 0.14f),
                    tint = habitColor(colorHex)
                )
                Spacer(Modifier.width(10.dp))
                TextButton(onClick = { openImagePicker() }) {
                    Text("从相册选择")
                }
                if (iconImagePath.isNotBlank()) {
                    TextButton(onClick = {
                        iconImagePath = ""
                        if (iconType == HabitIconType.IMAGE) {
                            iconType = HabitIconType.BUILTIN
                            icon = "book"
                        }
                    }) {
                        Text("移除图片", color = palette.muted)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            Text("颜色", style = MaterialTheme.typography.titleMedium, color = palette.ink)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                habitColors.forEach { hex ->
                    val c = habitColor(hex)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(c)
                            .border(
                                if (colorHex == hex) 3.dp else 1.dp,
                                if (colorHex == hex) palette.ink else palette.line,
                                CircleShape
                            )
                            .clickable { colorHex = hex }
                    )
                }
            }
            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(80) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("备注（可选）") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )

            if (error.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(error, style = MaterialTheme.typography.labelMedium, color = palette.danger)
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = ::save,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.focus,
                    contentColor = palette.surface
                )
            ) {
                Text(if (isEditing) "保存修改" else "创建习惯")
            }
            if (isEditing) {
                TextButton(
                    onClick = {
                        if (slot != HabitReminderSlot.NONE) {
                            // Historical logs are preserved when archiving.
                        }
                        onArchive()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("移除这个习惯", color = palette.danger)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showSoundPicker) {
        HabitSoundPickerDialog(
            currentKey = soundKey,
            allowFollowGlobal = true,
            onDismiss = {
                showSoundPicker = false
                HabitSounds.stopPreview()
            },
            onSelect = { soundKey = it },
            onImportAudio = {
                showSoundPicker = false
                openAudioPicker()
            }
        )
    }
}

@Composable
internal fun HabitSoundPickerDialog(
    currentKey: String,
    allowFollowGlobal: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onImportAudio: (() -> Unit)? = null
) {
    val palette = Potato.palette
    val context = LocalContext.current
    val globalSoundKey = remember { SettingsStore(context).habitSoundKey }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text("习惯提示音") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (allowFollowGlobal) {
                    SoundOptionRow(
                        label = "跟随全局",
                        selected = currentKey.isBlank(),
                        onSelect = { onSelect("") },
                        onPreview = { HabitSounds.preview(context, globalSoundKey) }
                    )
                }
                HabitSounds.builtInOptions.forEach { option ->
                    SoundOptionRow(
                        label = option.label,
                        selected = currentKey == option.key,
                        onSelect = { onSelect(option.key) },
                        onPreview = { HabitSounds.preview(context, option.key) }
                    )
                }
                if (onImportAudio != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(palette.line)
                    )
                    TextButton(
                        onClick = {
                            HabitSounds.stopPreview()
                            onImportAudio()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("导入本地音频", color = palette.focus)
                    }
                }
                if (currentKey.startsWith("media:")) {
                    SoundOptionRow(
                        label = "自定义音频",
                        selected = true,
                        onSelect = { },
                        onPreview = { HabitSounds.preview(context, currentKey) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                HabitSounds.stopPreview()
                onDismiss()
            }) { Text("完成") }
        }
    )
}

@Composable
private fun SoundOptionRow(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
    onPreview: () -> Unit
) {
    val palette = Potato.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPreview) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "试听",
                tint = palette.muted,
                modifier = Modifier.size(19.dp)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = palette.ink,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "已选择",
                tint = palette.focus,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun TypePill(text: String, selected: Boolean, onClick: () -> Unit) {
    val palette = Potato.palette
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) palette.surface else palette.muted,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) palette.ink else palette.surfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    )
}

@Composable
private fun SlotPill(text: String, selected: Boolean, onClick: () -> Unit) {
    val palette = Potato.palette
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelMedium) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = palette.focusSoft,
            selectedLabelColor = palette.focus
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = palette.line,
            selectedBorderColor = Color.Transparent
        )
    )
}

@Composable
private fun WeekdaySelector(mask: Int, onChange: (Int) -> Unit) {
    val palette = Potato.palette
    val labels = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        labels.forEachIndexed { index, label ->
            val bit = 1 shl index
            val selected = mask and bit != 0
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (selected) palette.focusSoft else palette.surfaceRaised)
                    .border(
                        1.dp,
                        if (selected) palette.focus else palette.line,
                        CircleShape
                    )
                    .clickable {
                        onChange(if (selected) mask and bit.inv() else mask or bit)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) palette.focus else palette.muted,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

private fun newHabitDraft(sortOrder: Int): Habit = Habit(
    id = 0L,
    name = "",
    icon = "book",
    color = habitColors.first(),
    type = HabitType.TIMED,
    targetSeconds = 25 * 60,
    targetCount = 0,
    unit = "",
    weekdaysMask = 0b1111111,
    reminderSlot = HabitReminderSlot.NONE,
    reminderEnabled = true,
    reminderMode = HabitReminderMode.SLOT,
    reminderMinutes = -1,
    soundKey = "",
    iconType = HabitIconType.BUILTIN,
    iconImagePath = "",
    note = "",
    sortOrder = sortOrder,
    archivedAt = null,
    createdAt = System.currentTimeMillis()
)

private fun formatSlotTime(minutesOfDay: Int): String =
    String.format(Locale.US, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60)

private fun habitReminderText(habit: Habit, settings: SettingsStore): String = when {
    !habit.reminderEnabled -> "提醒已关闭"
    habit.reminderMode == HabitReminderMode.INDEPENDENT &&
        habit.reminderMinutes in 0..(24 * 60 - 1) ->
        "每天 ${formatSlotTime(habit.reminderMinutes)} 提醒"
    habit.reminderSlot != HabitReminderSlot.NONE -> {
        val title = when (habit.reminderSlot) {
            HabitReminderSlot.MORNING -> "早上"
            HabitReminderSlot.AFTERNOON -> "下午"
            HabitReminderSlot.EVENING -> "晚上"
            HabitReminderSlot.NONE -> ""
        }
        "$title ${formatSlotTime(settings.habitSlotMinutes(habit.reminderSlot))} 汇总提醒"
    }
    else -> "不提醒"
}
