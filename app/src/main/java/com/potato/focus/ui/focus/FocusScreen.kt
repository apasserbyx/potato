package com.potato.focus.ui.focus

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.potato.focus.PotatoApp
import com.potato.focus.data.FocusSession
import com.potato.focus.data.Habit
import com.potato.focus.data.HabitLog
import com.potato.focus.data.HabitStats
import com.potato.focus.data.HabitType
import com.potato.focus.data.Preset
import com.potato.focus.data.StatsCalculator
import com.potato.focus.data.TaskItem
import com.potato.focus.data.isScheduledOn
import com.potato.focus.habits.HabitEvents
import com.potato.focus.timer.StopResult
import com.potato.focus.timer.TimerController
import com.potato.focus.timer.TimerMath
import com.potato.focus.timer.TimerMode
import com.potato.focus.timer.TimerPhase
import com.potato.focus.timer.TimerSnapshot
import com.potato.focus.timer.TimerStatus
import com.potato.focus.timer.TimerStore
import com.potato.focus.ui.components.ModeSwitch
import com.potato.focus.ui.components.PotatoCard
import com.potato.focus.ui.components.PotatoMark
import com.potato.focus.ui.components.TagPill
import com.potato.focus.ui.components.TimerRing
import com.potato.focus.ui.habits.HabitIconVisual
import com.potato.focus.ui.habits.habitColor
import com.potato.focus.ui.theme.Potato
import com.potato.focus.util.TimeFormat
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(
    snapshot: TimerSnapshot,
    now: Long,
    onChanged: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as PotatoApp
    val palette = Potato.palette
    val compactLayout = LocalConfiguration.current.screenHeightDp < 820
    val ringSize = if (compactLayout) 248.dp else 292.dp
    val ringAreaHeight = if (compactLayout) 266.dp else 310.dp
    val haptics = LocalHapticFeedback.current
    val store = remember { TimerStore(context) }
    val controller = remember { TimerController(context) }
    val settings = remember { app.settings }

    var presets by remember { mutableStateOf<List<Preset>>(emptyList()) }
    var tasks by remember { mutableStateOf<List<TaskItem>>(emptyList()) }
    var habits by remember { mutableStateOf<List<Habit>>(emptyList()) }
    var habitLogs by remember { mutableStateOf<Map<Long, HabitLog>>(emptyMap()) }
    var todaySessions by remember { mutableStateOf<List<FocusSession>>(emptyList()) }
    var selectedPresetId by remember { mutableLongStateOf(settings.selectedPresetId) }
    var selectedTaskId by remember { mutableLongStateOf(settings.selectedTaskId) }
    var stopwatchMode by remember {
        mutableStateOf(if (snapshot.isActive) snapshot.mode == TimerMode.STOPWATCH
        else settings.defaultMode == TimerMode.STOPWATCH.name)
    }
    var showTaskSheet by remember { mutableStateOf(false) }
    var showPresetSheet by remember { mutableStateOf(false) }
    var focusOverride by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmStop by remember { mutableStateOf(false) }
    var stopResult by remember { mutableStateOf<StopResult?>(null) }
    var showCompletionBanner by remember { mutableStateOf(false) }
    var previousPhase by remember { mutableStateOf(snapshot.phase) }

    suspend fun reloadData() {
        presets = app.presets.all()
        tasks = app.tasks.activeTasks()
        todaySessions = app.sessions.sessionsSince(TimeFormat.startOfToday())
        val today = TimeFormat.localDate(System.currentTimeMillis())
        habits = app.habits.activeHabits()
            .filter { it.isScheduledOn(today) }
            .sortedWith(compareBy({ it.reminderMinutes }, { it.sortOrder }))
        habitLogs = app.habitLogs.logsForDate(today)
    }

    LaunchedEffect(Unit) {
        reloadData()
        if (settings.selectedPresetId <= 0L) {
            val default = app.presets.default()
            if (default != null) {
                selectedPresetId = default.id
                settings.selectedPresetId = default.id
            }
        }
    }

    LaunchedEffect(Unit) {
        HabitEvents.version.collect { reloadData() }
    }

    LaunchedEffect(snapshot.phase, snapshot.status) {
        if (snapshot.isActive) {
            stopwatchMode = snapshot.mode == TimerMode.STOPWATCH
        }
        if (snapshot.phase != previousPhase) {
            if (previousPhase == TimerPhase.FOCUS && snapshot.isBreak) {
                showCompletionBanner = true
                delay(4_000)
                showCompletionBanner = false
            }
            previousPhase = snapshot.phase
        }
        todaySessions = app.sessions.sessionsSince(TimeFormat.startOfToday())
        tasks = app.tasks.activeTasks()
        val today = TimeFormat.localDate(System.currentTimeMillis())
        habits = app.habits.activeHabits()
            .filter { it.isScheduledOn(today) }
            .sortedWith(compareBy({ it.reminderMinutes }, { it.sortOrder }))
        habitLogs = app.habitLogs.logsForDate(today)
    }

    val selectedPreset = presets.firstOrNull { it.id == selectedPresetId }
        ?: presets.firstOrNull { it.isDefault }
        ?: presets.firstOrNull()
    val selectedTask = tasks.firstOrNull { it.id == selectedTaskId }
    val isActive = snapshot.isActive
    val isBreak = snapshot.isBreak
    val isHabit = isActive && snapshot.isHabit
    val isStopwatch = if (isActive) snapshot.isStopwatch else stopwatchMode
    val effectiveFocusMin = if (isActive) snapshot.focusMin else (focusOverride ?: selectedPreset?.focusMin ?: 25)
    val phaseColor = when {
        isBreak -> palette.breakColor
        isHabit -> palette.accent
        isStopwatch -> palette.accent
        else -> palette.focus
    }
    val phaseSoft = when {
        isBreak -> palette.breakSoft
        isHabit -> palette.accentSoft
        isStopwatch -> palette.accentSoft
        else -> palette.focusSoft
    }
    val todaySummary = StatsCalculator.summary(todaySessions)
    val todayMinutes = TimeFormat.roundedMinutes(todaySummary.focusSeconds)
    val goalMinutes = settings.dailyGoalMinutes
    val goalProgress = (todayMinutes.toFloat() / goalMinutes.coerceAtLeast(1)).coerceIn(0f, 1f)

    val displaySeconds = when {
        isActive -> TimerMath.displaySeconds(snapshot, now)
        stopwatchMode -> 0L
        else -> effectiveFocusMin * 60L
    }
    val timeText = TimeFormat.timerText(displaySeconds)
    val progress = when {
        isActive && isStopwatch -> ((displaySeconds % 60).toFloat() / 60f)
        isActive -> TimerMath.progress(snapshot, now)
        else -> 0f
    }
    val ringLabel = when {
        isActive && snapshot.status == TimerStatus.PAUSED && !isStopwatch ->
            "已暂停 · 剩余 ${TimeFormat.timerText((TimerMath.remainingMillis(snapshot, now) + 999) / 1000)}"
        isActive && snapshot.status == TimerStatus.PAUSED ->
            "已暂停 · 已用 ${TimeFormat.durationText(displaySeconds)}"
        isActive && isBreak ->
            "下一颗 ${snapshot.focusMin}:00 · ${TimeFormat.clockTime(snapshot.endsAt)} 开始"
        isActive && isStopwatch ->
            "自由专注 · 已用 ${TimeFormat.durationText(displaySeconds)}"
        isHabit ->
            "完成后自动打卡 · ${TimeFormat.clockTime(snapshot.endsAt)} 结束"
        isActive ->
            "预计 ${TimeFormat.clockTime(snapshot.endsAt)} 结束"
        stopwatchMode -> "自由专注 · 不设终点"
        else -> "$effectiveFocusMin 专注 · ${selectedPreset?.shortBreakMin ?: 5} 短休"
    }
    val taskTitle = if (isActive) snapshot.taskTitle else selectedTask?.title.orEmpty()
    val taskTag = if (isActive) snapshot.tag else selectedTask?.tag.orEmpty()
    val buttonText = when {
        !isActive && isStopwatch -> "开始计时"
        !isActive -> "开始专注"
        snapshot.status == TimerStatus.PAUSED -> "继续"
        else -> "暂停"
    }

    fun performStart() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        settings.notificationPrompted = true
        val task = selectedTask
        val started = if (stopwatchMode) {
            controller.startStopwatch(task)
        } else {
            selectedPreset?.let {
                controller.startPomodoro(it.copy(focusMin = effectiveFocusMin), task)
            } ?: false
        }
        if (started) onChanged()
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        performStart()
    }

    fun requestStart() {
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            performStart()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = TimeFormat.greeting(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = palette.ink
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = TimeFormat.dateTitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted
                )
            }
            PotatoMark(size = 38.dp)
        }

        Spacer(Modifier.height(18.dp))
        if (isHabit) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(palette.accentSoft)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "习惯计时 · ${snapshot.taskTitle}",
                    style = MaterialTheme.typography.labelLarge,
                    color = palette.accent
                )
            }
        } else {
            ModeSwitch(
                stopwatch = stopwatchMode,
                enabled = !isActive,
                onModeChange = {
                    stopwatchMode = it
                    settings.defaultMode = if (it) TimerMode.STOPWATCH.name else TimerMode.POMODORO.name
                }
            )
        }

        Spacer(Modifier.height(18.dp))
        PotatoCard(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isActive) { showTaskSheet = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            isActive -> "正在做"
                            taskTitle.isBlank() -> "今天想专注做什么？"
                            else -> "正在做"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = taskTitle.ifBlank { "添加一个任务，或直接开始" },
                        style = MaterialTheme.typography.titleLarge,
                        color = if (taskTitle.isBlank()) palette.muted else palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (taskTag.isNotBlank()) TagPill(taskTag)
                if (!isActive) {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "选择任务",
                        tint = palette.muted,
                        modifier = Modifier.padding(start = 8.dp).size(20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        AnimatedVisibility(
            visible = showCompletionBanner,
            enter = fadeIn() + slideInVertically { -it / 2 },
            exit = fadeOut()
        ) {
            Text(
                text = "这一颗，熟了。休息一下吧。",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.breakColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.breakSoft)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }

        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(ringAreaHeight),
            contentAlignment = Alignment.Center
        ) {
            TimerRing(
                timeText = timeText,
                label = ringLabel,
                progress = progress,
                color = phaseColor,
                running = isActive && snapshot.status == TimerStatus.RUNNING,
                modifier = Modifier.size(ringSize)
            )
        }

        if (isHabit) {
            Text(
                text = "本次 ${snapshot.plannedSeconds / 60} 分钟 · 完成后自动打卡",
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        } else if (!isStopwatch && !isBreak) {
            if (isActive) {
                Text(
                    text = "本次 ${snapshot.focusMin} 分钟 · 每 ${selectedPreset?.longBreakEvery ?: 4} 颗长休",
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DurationStepButton("−") {
                            val next = (effectiveFocusMin - 5).coerceAtLeast(1)
                            focusOverride = next.takeIf { it != selectedPreset?.focusMin }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$effectiveFocusMin 分钟",
                                style = MaterialTheme.typography.titleLarge,
                                color = palette.ink
                            )
                            Text(
                                text = "本次专注时长",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.muted
                            )
                        }
                        DurationStepButton("+") {
                            val next = (effectiveFocusMin + 5).coerceAtMost(180)
                            focusOverride = next.takeIf { it != selectedPreset?.focusMin }
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${selectedPreset?.name ?: "经典 25/5"} · 每 ${selectedPreset?.longBreakEvery ?: 4} 颗长休  ›",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { showPresetSheet = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
        } else {
            Spacer(Modifier.height(6.dp))
        }

        Spacer(Modifier.height(18.dp))
        Button(
            onClick = {
                if (!isActive) {
                    requestStart()
                    return@Button
                }
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (snapshot.status == TimerStatus.RUNNING) {
                    controller.pause()
                } else {
                    controller.resume()
                }
                onChanged()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = phaseColor,
                contentColor = palette.surface
            )
        ) {
            Icon(
                imageVector = when {
                    !isActive -> Icons.Rounded.PlayArrow
                    snapshot.status == TimerStatus.PAUSED -> Icons.Rounded.PlayArrow
                    else -> Icons.Rounded.Pause
                },
                contentDescription = null,
                modifier = Modifier.size(21.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(buttonText, style = MaterialTheme.typography.titleMedium)
        }

        if (isActive) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (isBreak) {
                    TextButton(onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        controller.skipBreak()
                        onChanged()
                    }) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("跳过休息")
                    }
                } else {
                    TextButton(onClick = { confirmStop = true }) {
                        Icon(Icons.Rounded.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            when {
                                isStopwatch -> "结束并记录"
                                isHabit -> "结束习惯"
                                else -> "结束本次"
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        PotatoCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .padding(horizontal = 2.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "今日",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.muted
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${TimeFormat.durationText(todaySummary.focusSeconds)} · ${todaySummary.tomatoCount} 颗",
                            style = MaterialTheme.typography.titleLarge,
                            color = palette.ink
                        )
                    }
                    Text(
                        text = "$todayMinutes / $goalMinutes 分钟",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted
                    )
                }
                Spacer(Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(CircleShape)
                        .background(phaseSoft)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(goalProgress)
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(phaseColor)
                    )
                }
                if (todaySummary.openCount > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "含 ${todaySummary.openCount} 次自由专注",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted
                    )
                }
            }
        }
    }

    if (showTaskSheet) {
        TaskSheet(
            tasks = tasks,
            habits = habits,
            habitLogs = habitLogs,
            selectedTaskId = selectedTaskId,
            onDismiss = { showTaskSheet = false },
            onSelect = { task ->
                selectedTaskId = task?.id ?: -1L
                settings.selectTask(task)
                showTaskSheet = false
            },
            onAdd = { title, estimate, tag ->
                app.tasks.add(title, estimate, tag)
                tasks = app.tasks.activeTasks()
            },
            onComplete = { task ->
                app.tasks.setCompleted(task.id, true)
                if (selectedTaskId == task.id) {
                    selectedTaskId = -1L
                    settings.selectTask(null)
                }
                tasks = app.tasks.activeTasks()
            },
            onDelete = { task ->
                app.tasks.delete(task.id)
                if (selectedTaskId == task.id) {
                    selectedTaskId = -1L
                    settings.selectTask(null)
                }
                tasks = app.tasks.activeTasks()
            },
            onStartHabit = { habit ->
                val log = habitLogs[habit.id]
                val remaining = HabitStats.remainingSeconds(habit, log)
                if (controller.startHabit(habit, remaining)) {
                    showTaskSheet = false
                    onChanged()
                }
            },
            onIncrementHabit = { habit ->
                val today = TimeFormat.localDate(System.currentTimeMillis())
                app.habitLogs.adjustCount(habit.id, today, 1, habit.targetCount)
                habitLogs = app.habitLogs.logsForDate(today)
                onChanged()
            },
            onCompleteHabit = { habit ->
                val today = TimeFormat.localDate(System.currentTimeMillis())
                app.habitLogs.markCompleted(habit.id, today)
                habitLogs = app.habitLogs.logsForDate(today)
                onChanged()
            }
        )
    }

    if (showPresetSheet) {
        PresetSheet(
            presets = presets,
            selectedId = selectedPresetId,
            onDismiss = { showPresetSheet = false },
            onSelect = { preset ->
                selectedPresetId = preset.id
                settings.selectedPresetId = preset.id
                focusOverride = null
                showPresetSheet = false
            },
            onManage = {
                showPresetSheet = false
                onOpenSettings()
            }
        )
    }

    if (confirmStop) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            containerColor = palette.surface,
            title = {
                Text(
                    when {
                        isBreak -> "跳过休息？"
                        isStopwatch -> "结束正计时？"
                        isHabit -> "结束习惯计时？"
                        else -> "结束本次？"
                    }
                )
            },
            text = {
                Text(
                    when {
                        isBreak -> "休息会被记录为部分完成。"
                        isStopwatch -> "已经专注的时间会保存为自由专注记录。"
                        isHabit -> "已计时的分钟会保留；未达到目标前，这个习惯仍未完成。"
                        else -> "已经专注的时间会保留；提前结束会记为部分完成。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    stopResult = controller.stopCurrent()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onChanged()
                }) {
                    Text("结束并记录", color = palette.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text("继续专注") }
            }
        )
    }

    stopResult?.let { result ->
        val discarded = result.status.name == "DISCARDED"
        AlertDialog(
            onDismissRequest = { stopResult = null },
            containerColor = palette.surface,
            title = { Text(if (discarded) "未计入记录" else "已记录") },
            text = {
                Text(
                    if (discarded) {
                        "不足 1 分钟，本次专注不写入统计。"
                    } else {
                        "${TimeFormat.durationText(result.focusedSeconds.toLong())}" +
                            if (result.status.name == "PARTIAL") " · 部分完成" else ""
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { stopResult = null }) { Text("好") }
            }
        )
    }
}

@Composable
private fun DurationStepButton(text: String, onClick: () -> Unit) {
    val palette = Potato.palette
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(palette.surfaceRaised)
            .border(1.dp, palette.line, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = palette.ink
        )
    }
}

@Composable
private fun TaskSheetTab(text: String, selected: Boolean, onClick: () -> Unit) {
    val palette = Potato.palette
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) palette.surface else palette.muted,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) palette.ink else palette.surfaceRaised)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    )
}

@Composable
private fun HabitPickerContent(
    habits: List<Habit>,
    habitLogs: Map<Long, HabitLog>,
    onStartHabit: (Habit) -> Unit,
    onIncrementHabit: (Habit) -> Unit,
    onCompleteHabit: (Habit) -> Unit
) {
    val palette = Potato.palette
    val sorted = habits.sortedWith(
        compareBy({ HabitStats.isCompleted(habitLogs[it.id]) }, { it.sortOrder })
    )
    if (sorted.isEmpty()) {
        Text(
            "今天没有安排习惯。可以在「习惯」页添加。",
            style = MaterialTheme.typography.bodyMedium,
            color = palette.muted,
            modifier = Modifier.padding(vertical = 20.dp)
        )
        return
    }
    Text(
        "今日习惯",
        style = MaterialTheme.typography.labelMedium,
        color = palette.muted
    )
    Spacer(Modifier.height(6.dp))
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
    ) {
        items(sorted, key = { it.id }) { habit ->
            val log = habitLogs[habit.id]
            val completed = HabitStats.isCompleted(log)
            val skipped = HabitStats.isSkipped(log)
            val accent = habitColor(habit.color)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HabitIconVisual(
                    iconType = habit.iconType,
                    icon = habit.icon,
                    imagePath = habit.iconImagePath,
                    contentDescription = habit.name,
                    size = 38.dp,
                    background = accent.copy(alpha = 0.14f),
                    tint = accent
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = when {
                            completed -> "已完成"
                            skipped -> "今天休息"
                            else -> HabitStats.progressText(habit, log)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (completed) palette.breakColor else palette.muted
                    )
                }
                when {
                    completed -> Icon(
                        Icons.Rounded.Check,
                        contentDescription = "已完成",
                        tint = palette.breakColor,
                        modifier = Modifier.size(19.dp)
                    )
                    skipped -> Text(
                        "已跳过",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted
                    )
                    else -> {
                        if (habit.type == HabitType.COUNT) {
                            TextButton(onClick = { onIncrementHabit(habit) }) {
                                Text("+1", color = accent)
                            }
                        } else {
                            Button(
                                onClick = { onStartHabit(habit) },
                                shape = CircleShape,
                                modifier = Modifier.height(34.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = palette.surface
                                )
                            ) {
                                Text("开始", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        IconButton(onClick = { onCompleteHabit(habit) }) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "标记完成",
                                tint = palette.muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskSheet(
    tasks: List<TaskItem>,
    habits: List<Habit>,
    habitLogs: Map<Long, HabitLog>,
    selectedTaskId: Long,
    onDismiss: () -> Unit,
    onSelect: (TaskItem?) -> Unit,
    onAdd: (String, Int, String) -> Unit,
    onComplete: (TaskItem) -> Unit,
    onDelete: (TaskItem) -> Unit,
    onStartHabit: (Habit) -> Unit,
    onIncrementHabit: (Habit) -> Unit,
    onCompleteHabit: (Habit) -> Unit
) {
    val palette = Potato.palette
    var newTitle by remember { mutableStateOf("") }
    var estimate by remember { mutableStateOf(1) }
    var tag by remember { mutableStateOf("工作") }
    var tab by remember { mutableStateOf(0) }
    var deletingTask by remember { mutableStateOf<TaskItem?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val addTask = {
        if (newTitle.isNotBlank()) {
            onAdd(newTitle.trim(), estimate, tag)
            newTitle = ""
            estimate = 1
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surface,
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
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
        ) {
            Text("今天想专注做什么？", style = MaterialTheme.typography.headlineMedium, color = palette.ink)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskSheetTab("任务", tab == 0) { tab = 0 }
                TaskSheetTab("习惯", tab == 1) { tab = 1 }
            }
            Spacer(Modifier.height(12.dp))
            if (tab == 0) {
            OutlinedTextField(
                value = newTitle,
                onValueChange = { newTitle = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("输入任务…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { addTask() }),
                trailingIcon = {
                    IconButton(onClick = addTask) {
                        Icon(Icons.Rounded.Add, contentDescription = "添加任务", tint = palette.focus)
                    }
                }
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("工作", "学习", "创作", "生活").forEach { item ->
                        FilterChip(
                            selected = tag == item,
                            onClick = { tag = item },
                            label = { Text(item, style = MaterialTheme.typography.labelMedium) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.focusSoft,
                                selectedLabelColor = palette.focus
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = tag == item,
                                borderColor = palette.line,
                                selectedBorderColor = Color.Transparent
                            )
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("预计颗数", style = MaterialTheme.typography.bodyMedium, color = palette.muted)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { estimate = (estimate - 1).coerceAtLeast(1) }) {
                        Text("−", style = MaterialTheme.typography.titleLarge)
                    }
                    Text("$estimate", style = MaterialTheme.typography.titleMedium, color = palette.ink)
                    TextButton(onClick = { estimate = (estimate + 1).coerceAtMost(20) }) {
                        Text("+", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { onSelect(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("不关联任务，直接开始", color = palette.muted)
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelect(task) }
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(21.dp)
                                .clip(CircleShape)
                                .border(
                                    1.5.dp,
                                    if (task.id == selectedTaskId) palette.focus else palette.line,
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (task.id == selectedTaskId) {
                                Box(
                                    Modifier
                                        .size(11.dp)
                                        .clip(CircleShape)
                                        .background(palette.focus)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = task.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "预计 ${task.estimatePomodoros} 颗",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.muted
                                )
                                if (task.tag.isNotBlank()) {
                                    Spacer(Modifier.width(8.dp))
                                    TagPill(task.tag)
                                }
                            }
                        }
                        IconButton(onClick = { onComplete(task) }) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = "完成任务",
                                tint = palette.muted,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        IconButton(onClick = { deletingTask = task }) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "删除任务",
                                tint = palette.muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            if (tasks.isEmpty()) {
                Text(
                    "还没有任务。先加一个，或者直接开始。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.muted,
                    modifier = Modifier.padding(vertical = 20.dp)
                )
            }
            } else {
                HabitPickerContent(
                    habits = habits,
                    habitLogs = habitLogs,
                    onStartHabit = onStartHabit,
                    onIncrementHabit = onIncrementHabit,
                    onCompleteHabit = onCompleteHabit
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    deletingTask?.let { task ->
        AlertDialog(
            onDismissRequest = { deletingTask = null },
            containerColor = palette.surface,
            title = { Text("删除这个任务？") },
            text = {
                Text("「${task.title}」会从任务列表中删除，已有的专注记录会保留。")
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(task)
                    deletingTask = null
                }) {
                    Text("删除", color = palette.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTask = null }) {
                    Text("取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetSheet(
    presets: List<Preset>,
    selectedId: Long,
    onDismiss: () -> Unit,
    onSelect: (Preset) -> Unit,
    onManage: () -> Unit
) {
    val palette = Potato.palette
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.surface,
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
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Text("我的节奏", style = MaterialTheme.typography.headlineMedium, color = palette.ink)
            Spacer(Modifier.height(12.dp))
            presets.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(preset) }
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(preset.name, style = MaterialTheme.typography.titleMedium, color = palette.ink)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "${preset.focusMin} 专注 · ${preset.shortBreakMin} 短休 · ${preset.longBreakMin} 长休",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.muted
                        )
                    }
                    if (preset.id == selectedId) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(palette.focus),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = palette.surface,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
            TextButton(onClick = onManage, modifier = Modifier.fillMaxWidth()) {
                Text("管理自定义节奏", color = palette.focus)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
