package com.potato.focus.ui.settings

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.potato.focus.PotatoApp
import com.potato.focus.data.HabitAlertLevel
import com.potato.focus.data.HabitReminderSlot
import com.potato.focus.data.Preset
import com.potato.focus.habits.HabitNotificationChannels
import com.potato.focus.habits.HabitReminderScheduler
import com.potato.focus.habits.HabitSounds
import com.potato.focus.timer.TimerStore
import com.potato.focus.ui.components.PotatoCard
import com.potato.focus.ui.components.SectionLabel
import com.potato.focus.ui.components.TagPill
import com.potato.focus.ui.habits.HabitSoundPickerDialog
import com.potato.focus.ui.theme.Potato
import com.potato.focus.util.BackupPreview
import com.potato.focus.util.CustomSoundStorage
import com.potato.focus.util.DataBackup
import com.potato.focus.util.TimeFormat
import java.util.Locale
import kotlinx.coroutines.launch

private enum class SoundTarget { HABIT, TIMER_FOCUS, TIMER_BREAK }

@Composable
fun SettingsScreen(onDataChanged: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PotatoApp
    val palette = Potato.palette
    val scope = rememberCoroutineScope()
    val settings = app.settings

    var goal by remember { mutableIntStateOf(settings.dailyGoalMinutes) }
    var keepScreenOn by remember { mutableStateOf(settings.keepScreenOn) }
    var presets by remember { mutableStateOf<List<Preset>>(emptyList()) }
    var editingPreset by remember { mutableStateOf<Preset?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showBackgroundHelp by remember { mutableStateOf(false) }
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    var habitMorningMin by remember { mutableIntStateOf(settings.habitSlotMinutes(HabitReminderSlot.MORNING)) }
    var habitAfternoonMin by remember { mutableIntStateOf(settings.habitSlotMinutes(HabitReminderSlot.AFTERNOON)) }
    var habitEveningMin by remember { mutableIntStateOf(settings.habitSlotMinutes(HabitReminderSlot.EVENING)) }
    var habitMorningEnabled by remember { mutableStateOf(settings.habitSlotEnabled(HabitReminderSlot.MORNING)) }
    var habitAfternoonEnabled by remember { mutableStateOf(settings.habitSlotEnabled(HabitReminderSlot.AFTERNOON)) }
    var habitEveningEnabled by remember { mutableStateOf(settings.habitSlotEnabled(HabitReminderSlot.EVENING)) }
    var habitSoundKey by remember { mutableStateOf(settings.habitSoundKey) }
    var habitAlertLevel by remember {
        mutableStateOf(
            runCatching { HabitAlertLevel.valueOf(settings.habitAlertLevel) }
                .getOrDefault(HabitAlertLevel.STANDARD)
        )
    }
    var showHabitSoundPicker by remember { mutableStateOf(false) }
    var channelStatusVersion by remember { mutableIntStateOf(0) }
    var soundImportMessage by remember { mutableStateOf("") }
    var useAppSound by remember { mutableStateOf(settings.habitUseAppSound) }
    var timerFocusSoundKey by remember { mutableStateOf(settings.timerFocusSoundKey) }
    var timerBreakSoundKey by remember { mutableStateOf(settings.timerBreakSoundKey) }
    var showTimerFocusPicker by remember { mutableStateOf(false) }
    var showTimerBreakPicker by remember { mutableStateOf(false) }
    var audioTarget by remember { mutableStateOf(SoundTarget.HABIT) }
    var backupMessage by remember { mutableStateOf("") }
    var importPreview by remember { mutableStateOf<BackupPreview?>(null) }
    var backupBusy by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let {
                IntentCompat.getParcelableExtra(
                    it,
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java
                )
            }
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val key = "system:$uri"
                habitSoundKey = key
                settings.habitSoundKey = key
                HabitSounds.preview(context, key)
            }
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val key = CustomSoundStorage.import(context, uri)
                if (key != null) {
                    when (audioTarget) {
                        SoundTarget.HABIT -> {
                            habitSoundKey = key
                            settings.habitSoundKey = key
                        }
                        SoundTarget.TIMER_FOCUS -> {
                            timerFocusSoundKey = key
                            settings.timerFocusSoundKey = key
                        }
                        SoundTarget.TIMER_BREAK -> {
                            timerBreakSoundKey = key
                            settings.timerBreakSoundKey = key
                        }
                    }
                    soundImportMessage = ""
                    HabitSounds.preview(context, key)
                } else {
                    soundImportMessage = "导入失败：请选择小于 5MB 的音频文件"
                }
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        audioPickerLauncher.launch(arrayOf("audio/*"))
    }

    val backupImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            if (TimerStore(context).read().isActive) {
                backupMessage = "计时进行中，结束后再导入备份"
            } else {
                scope.launch {
                    backupBusy = true
                    backupMessage = ""
                    val preview = DataBackup.inspect(context, uri)
                    backupBusy = false
                    if (preview == null) {
                        backupMessage = "无法读取这个备份文件"
                    } else {
                        importPreview = preview
                    }
                }
            }
        }
    }

    fun reloadPresets() {
        presets = app.presets.all()
    }

    LaunchedEffect(Unit) { reloadPresets() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        channelStatusVersion++
    }

    fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun enableNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
        }
    }

    fun openAppDetails() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    val exactAlarmOk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    } else true
    val habitChannelStatus = remember(
        notificationsEnabled,
        habitSoundKey,
        habitAlertLevel,
        channelStatusVersion
    ) {
        HabitNotificationChannels.status(context)
    }

    fun pickHabitTime(
        slot: HabitReminderSlot,
        currentMinutes: Int,
        onPicked: (Int) -> Unit
    ) {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                val value = hour * 60 + minute
                onPicked(value)
                settings.setHabitSlotMinutes(slot, value)
                HabitReminderScheduler.scheduleAll(context)
            },
            currentMinutes / 60,
            currentMinutes % 60,
            true
        ).show()
    }

    fun toggleHabitSlot(slot: HabitReminderSlot, enabled: Boolean, update: (Boolean) -> Unit) {
        update(enabled)
        settings.setHabitSlotEnabled(slot, enabled)
        HabitReminderScheduler.scheduleAll(context)
    }

    fun launchSystemRingtonePicker() {
        val previewUri = if (habitSoundKey.startsWith("system:")) {
            runCatching { Uri.parse(habitSoundKey.removePrefix("system:")) }.getOrNull()
        } else {
            null
        }
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, previewUri)
        }
        runCatching { ringtoneLauncher.launch(intent) }
    }

    fun openAudioImporter(target: SoundTarget) {
        audioTarget = target
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
            audioPickerLauncher.launch(arrayOf("audio/*"))
        } else {
            audioPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun openHabitChannelSettings(channelId: String) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(12.dp))
            Text("设置", style = MaterialTheme.typography.headlineLarge, color = palette.ink)
            Spacer(Modifier.height(2.dp))
            Text(
                "让 Potato 更适合你的节奏。",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.muted
            )
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
                            Text("每日目标", style = MaterialTheme.typography.titleMedium, color = palette.ink)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "用分钟记录，比颗数更诚实",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.muted
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StepperButton("−") {
                                goal = (goal - 15).coerceAtLeast(15)
                                settings.dailyGoalMinutes = goal
                                onDataChanged()
                            }
                            Text(
                                "$goal",
                                style = MaterialTheme.typography.headlineMedium,
                                color = palette.ink,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                            StepperButton("+") {
                                goal = (goal + 15).coerceAtMost(720)
                                settings.dailyGoalMinutes = goal
                                onDataChanged()
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("分钟 / 天", style = MaterialTheme.typography.labelMedium, color = palette.muted)
                }
            }
        }

        item {
            SectionLabel(
                text = "我的节奏",
                trailing = "添加",
                onTrailing = {
                    editingPreset = Preset(
                        id = 0L,
                        name = "新节奏",
                        focusMin = 25,
                        shortBreakMin = 5,
                        longBreakMin = 15,
                        longBreakEvery = 4,
                        autoStartBreak = true,
                        autoStartFocus = false,
                        sound = true,
                        isDefault = presets.isEmpty(),
                        sortOrder = presets.size
                    )
                }
            )
        }

        item {
            PotatoCard(Modifier.fillMaxWidth()) {
                Column {
                    presets.forEachIndexed { index, preset ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { editingPreset = preset }
                                .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .border(
                                        1.5.dp,
                                        if (preset.isDefault) palette.focus else palette.line,
                                        CircleShape
                                    )
                                    .clickable {
                                        app.presets.setDefault(preset.id)
                                        settings.selectedPresetId = preset.id
                                        reloadPresets()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (preset.isDefault) {
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        preset.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = palette.ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (preset.isDefault) {
                                        Spacer(Modifier.width(8.dp))
                                        TagPill("默认")
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    "${preset.focusMin} 专注 · ${preset.shortBreakMin} 短休 · ${preset.longBreakMin} 长休",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = palette.muted
                                )
                            }
                            Icon(
                                Icons.Rounded.ChevronRight,
                                contentDescription = "编辑",
                                tint = palette.muted,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        if (index != presets.lastIndex) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 50.dp)
                                    .height(1.dp)
                                    .background(palette.line)
                            )
                        }
                    }
                    if (presets.isEmpty()) {
                        Text(
                            "还没有自定义节奏。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }

        item { SectionLabel("提醒与后台") }

        item {
            PotatoCard(Modifier.fillMaxWidth()) {
                Column {
                    SettingRow(
                        icon = Icons.Rounded.Notifications,
                        title = "通知权限",
                        subtitle = if (notificationsEnabled) "已开启，计时结束会提醒你" else "未开启，结束提醒可能看不到",
                        trailing = if (notificationsEnabled) "已开启" else "去开启",
                        onClick = { if (notificationsEnabled) openNotificationSettings() else enableNotifications() }
                    )
                    DividerLine()
                    SettingRow(
                        icon = Icons.Rounded.Info,
                        title = "精确提醒",
                        subtitle = if (exactAlarmOk) "系统允许精确闹钟" else "后台提醒可能延迟，建议允许精确闹钟",
                        trailing = if (exactAlarmOk) "已允许" else "设置",
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }
                            }
                        }
                    )
                    DividerLine()
                    SettingRow(
                        icon = Icons.Rounded.Info,
                        title = "后台运行帮助",
                        subtitle = "国产手机省电策略可能中断计时",
                        trailing = "查看",
                        onClick = { showBackgroundHelp = true }
                    )
                }
            }
        }

        item { SectionLabel("习惯提醒") }

        item {
            PotatoCard(Modifier.fillMaxWidth()) {
                Column {
                    HabitSlotSettingRow(
                        label = "早上",
                        time = formatMinutes(habitMorningMin),
                        enabled = habitMorningEnabled,
                        onTimeClick = {
                            pickHabitTime(HabitReminderSlot.MORNING, habitMorningMin) {
                                habitMorningMin = it
                            }
                        },
                        onToggle = {
                            toggleHabitSlot(HabitReminderSlot.MORNING, it) { value ->
                                habitMorningEnabled = value
                            }
                        }
                    )
                    DividerLine()
                    HabitSlotSettingRow(
                        label = "下午",
                        time = formatMinutes(habitAfternoonMin),
                        enabled = habitAfternoonEnabled,
                        onTimeClick = {
                            pickHabitTime(HabitReminderSlot.AFTERNOON, habitAfternoonMin) {
                                habitAfternoonMin = it
                            }
                        },
                        onToggle = {
                            toggleHabitSlot(HabitReminderSlot.AFTERNOON, it) { value ->
                                habitAfternoonEnabled = value
                            }
                        }
                    )
                    DividerLine()
                    HabitSlotSettingRow(
                        label = "晚上",
                        time = formatMinutes(habitEveningMin),
                        enabled = habitEveningEnabled,
                        onTimeClick = {
                            pickHabitTime(HabitReminderSlot.EVENING, habitEveningMin) {
                                habitEveningMin = it
                            }
                        },
                        onToggle = {
                            toggleHabitSlot(HabitReminderSlot.EVENING, it) { value ->
                                habitEveningEnabled = value
                            }
                        }
                    )
                }
            }
        }

        item {
            PotatoCard(Modifier.fillMaxWidth()) {
                Column {
                    SettingRow(
                        title = "默认习惯提示音",
                        subtitle = "汇总提醒和未单独设置的独立提醒使用",
                        trailing = HabitSounds.label(habitSoundKey),
                        onClick = { showHabitSoundPicker = true }
                    )
                    DividerLine()
                    SettingRow(
                        title = "系统铃声",
                        subtitle = "从系统提示音中选择一个声音",
                        trailing = "选择",
                        onClick = { launchSystemRingtonePicker() }
                    )
                    DividerLine()
                    SettingRow(
                        title = "导入本地提示音",
                        subtitle = "选择小于 5MB 的音频文件；会请求媒体读取权限",
                        trailing = "导入",
                        onClick = { openAudioImporter(SoundTarget.HABIT) }
                    )
                    DividerLine()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "使用 App 直接播放习惯提醒音",
                                style = MaterialTheme.typography.titleMedium,
                                color = palette.ink
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                "不依赖系统通知渠道，避免厂商拦截声音；会尊重静音、媒体音量和免打扰。",
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.muted
                            )
                        }
                        Switch(
                            checked = useAppSound,
                            onCheckedChange = {
                                useAppSound = it
                                settings.habitUseAppSound = it
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = palette.focus,
                                uncheckedThumbColor = palette.muted,
                                uncheckedTrackColor = palette.line
                            )
                        )
                    }
                    DividerLine()
                    Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        Text(
                            "提醒强度",
                            style = MaterialTheme.typography.titleMedium,
                            color = palette.ink
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "轻柔不发声；醒目会尝试 heads-up 提醒",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.muted
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AlertLevelChip(
                                "轻柔",
                                habitAlertLevel == HabitAlertLevel.GENTLE
                            ) {
                                habitAlertLevel = HabitAlertLevel.GENTLE
                                settings.habitAlertLevel = HabitAlertLevel.GENTLE.name
                            }
                            AlertLevelChip(
                                "标准",
                                habitAlertLevel == HabitAlertLevel.STANDARD
                            ) {
                                habitAlertLevel = HabitAlertLevel.STANDARD
                                settings.habitAlertLevel = HabitAlertLevel.STANDARD.name
                            }
                            AlertLevelChip(
                                "醒目",
                                habitAlertLevel == HabitAlertLevel.STRONG
                            ) {
                                habitAlertLevel = HabitAlertLevel.STRONG
                                settings.habitAlertLevel = HabitAlertLevel.STRONG.name
                            }
                        }
                    }
                    DividerLine()
                    SettingRow(
                        title = "提醒状态",
                        subtitle = when {
                            !habitChannelStatus.appNotificationsEnabled ->
                                "通知权限未开启，习惯提醒不会出现"
                            !habitChannelStatus.channelEnabled ->
                                "当前通知渠道被系统关闭"
                            habitChannelStatus.appSoundMode ->
                                "App 直接播放已开启，不依赖系统通知声音"
                            !habitChannelStatus.soundEnabled ->
                                "通知渠道声音被系统关闭，可尝试修复"
                            else -> "当前设置可以正常提醒"
                        },
                        trailing = when {
                            !habitChannelStatus.appNotificationsEnabled -> "去开启"
                            !habitChannelStatus.channelEnabled -> "去设置"
                            !habitChannelStatus.appSoundMode &&
                                !habitChannelStatus.soundEnabled -> "修复声音"
                            else -> "已就绪"
                        },
                        onClick = {
                            when {
                                !habitChannelStatus.appNotificationsEnabled ->
                                    enableNotifications()
                                !habitChannelStatus.channelEnabled ->
                                    openHabitChannelSettings(habitChannelStatus.channelId)
                                !habitChannelStatus.appSoundMode &&
                                    !habitChannelStatus.soundEnabled -> {
                                    HabitNotificationChannels.repairChannel(context)
                                    channelStatusVersion++
                                }
                                else -> Unit
                            }
                        }
                    )
                }
            }
        }

        if (soundImportMessage.isNotBlank()) {
            item {
                Text(
                    soundImportMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.danger,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        item {
            Text(
                "每个时段每天最多提醒一次，只列出仍未完成的习惯。",
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        item { SectionLabel("计时提示音") }

        item {
            PotatoCard(Modifier.fillMaxWidth()) {
                Column {
                    SettingRow(
                        title = "专注完成提示音",
                        subtitle = "土豆钟专注阶段自然完成时播放 · ${HabitSounds.label(timerFocusSoundKey)}",
                        trailing = "选择",
                        onClick = { showTimerFocusPicker = true }
                    )
                    DividerLine()
                    SettingRow(
                        title = "休息结束提示音",
                        subtitle = "短休或长休结束时播放 · ${HabitSounds.label(timerBreakSoundKey)}",
                        trailing = "选择",
                        onClick = { showTimerBreakPicker = true }
                    )
                    DividerLine()
                    Text(
                        "计时提示音由 App 直接播放，不依赖系统通知渠道。",
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.muted,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)
                    )
                }
            }
        }

        item { SectionLabel("专注体验") }

        item {
            PotatoCard(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("专注时保持屏幕常亮", style = MaterialTheme.typography.titleMedium, color = palette.ink)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "适合把手机放在桌上看着倒计时",
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.muted
                        )
                    }
                    Switch(
                        checked = keepScreenOn,
                        onCheckedChange = {
                            keepScreenOn = it
                            settings.keepScreenOn = it
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = palette.focus,
                            uncheckedThumbColor = palette.muted,
                            uncheckedTrackColor = palette.line
                        )
                    )
                }
            }
        }

        item { SectionLabel("数据") }

        item {
            PotatoCard(Modifier.fillMaxWidth()) {
                Column {
                    SettingRow(
                        title = "导出完整备份",
                        subtitle = "包含记录、任务、习惯、设置、自定义图标与提示音",
                        trailing = if (backupBusy) "处理中" else "导出",
                        onClick = {
                            if (backupBusy) return@SettingRow
                            backupBusy = true
                            backupMessage = ""
                            scope.launch {
                                val intent = DataBackup.export(context)
                                backupBusy = false
                                if (intent == null) {
                                    backupMessage = "导出失败，请稍后重试"
                                } else {
                                    runCatching { context.startActivity(intent) }
                                        .onFailure { backupMessage = "没有可用的分享应用" }
                                }
                            }
                        }
                    )
                    DividerLine()
                    SettingRow(
                        title = "导入备份",
                        subtitle = "从 Potato 备份文件恢复；会覆盖当前全部数据",
                        trailing = if (backupBusy) "处理中" else "导入",
                        onClick = {
                            if (backupBusy) return@SettingRow
                            backupMessage = ""
                            runCatching {
                                backupImportLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream"
                                    )
                                )
                            }.onFailure { backupMessage = "无法打开文件选择器" }
                        }
                    )
                    DividerLine()
                    SettingRow(
                        title = "清空记录",
                        subtitle = "专注、正计时与习惯打卡记录",
                        trailing = "清空",
                        danger = true,
                        onClick = { showClearDialog = true }
                    )
                }
            }
        }

        if (backupMessage.isNotBlank()) {
            item {
                Text(
                    backupMessage,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.muted,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }

        item {
            PotatoCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Info,
                            contentDescription = null,
                            tint = palette.muted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("关于 Potato", style = MaterialTheme.typography.titleMedium, color = palette.ink)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Potato 1.0 · 本地优先，无需账号。\n专注记录只保存在这台设备上，随时可以导出。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.muted
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    editingPreset?.let { preset ->
        PresetEditorDialog(
            initial = preset,
            canDelete = presets.size > 1 && preset.id > 0L,
            onDismiss = { editingPreset = null },
            onSave = { updated ->
                if (updated.id == 0L) {
                    val newId = app.presets.insert(updated.copy(sortOrder = presets.size))
                    if (presets.isEmpty()) app.presets.setDefault(newId)
                    if (presets.isEmpty()) settings.selectedPresetId = newId
                } else {
                    app.presets.update(updated)
                    if (updated.isDefault) app.presets.setDefault(updated.id)
                }
                editingPreset = null
                reloadPresets()
                onDataChanged()
            },
            onDelete = { target ->
                app.presets.delete(target.id)
                val remaining = app.presets.all()
                if (target.isDefault && remaining.isNotEmpty()) {
                    app.presets.setDefault(remaining.first().id)
                    settings.selectedPresetId = remaining.first().id
                }
                if (settings.selectedPresetId == target.id) {
                    settings.selectedPresetId = remaining.firstOrNull()?.id ?: -1L
                }
                editingPreset = null
                reloadPresets()
                onDataChanged()
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = palette.surface,
            title = { Text("清空所有记录？") },
            text = { Text("所有土豆、正计时、休息和习惯打卡记录都会被删除；任务、习惯和节奏保留。这个操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    app.sessions.clearAll()
                    app.habitLogs.clearAll()
                    showClearDialog = false
                    onDataChanged()
                }) { Text("清空", color = palette.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!backupBusy) importPreview = null },
            containerColor = palette.surface,
            title = { Text("导入这份备份？") },
            text = {
                Text(
                    "备份时间：${TimeFormat.shortDate(preview.exportedAt)} " +
                        TimeFormat.clockTime(preview.exportedAt) + "\n\n" +
                        "专注记录 ${preview.sessions} 条\n" +
                        "任务 ${preview.tasks} 个 · 节奏 ${preview.presets} 个\n" +
                        "习惯 ${preview.habits} 个 · 打卡 ${preview.habitLogs} 条\n" +
                        "自定义图标 ${preview.icons} 个 · 提示音 ${preview.sounds} 个\n\n" +
                        "导入会覆盖当前所有数据和设置，且无法撤销。"
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !backupBusy,
                    onClick = {
                        backupBusy = true
                        backupMessage = ""
                        scope.launch {
                            val restored = DataBackup.restore(context, preview)
                            backupBusy = false
                            importPreview = null
                            if (restored) {
                                Toast.makeText(context, "备份已恢复", Toast.LENGTH_SHORT).show()
                                onDataChanged()
                                (context as? Activity)?.recreate()
                            } else {
                                backupMessage = "恢复失败：备份文件不完整或当前正在计时"
                            }
                        }
                    }
                ) { Text(if (backupBusy) "恢复中…" else "覆盖并恢复", color = palette.danger) }
            },
            dismissButton = {
                TextButton(
                    enabled = !backupBusy,
                    onClick = { importPreview = null }
                ) { Text("取消") }
            }
        )
    }

    if (showBackgroundHelp) {
        AlertDialog(
            onDismissRequest = { showBackgroundHelp = false },
            containerColor = palette.surface,
            title = { Text("保持后台计时") },
            text = {
                Text(
                    "Potato 使用前台服务和精确闹钟保持计时。\n\n" +
                        "如果手机在锁屏后停止计时或收不到提醒，请在系统设置中允许 Potato 自启动、关闭省电限制，并把电池策略设为「不限制」。\n\n" +
                        "小米、华为、OPPO、vivo 等系统的名称可能不同，通常在「应用管理 → 电池 → 后台耗电管理」中。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showBackgroundHelp = false
                    openAppDetails()
                }) { Text("打开应用设置") }
            },
            dismissButton = {
                TextButton(onClick = { showBackgroundHelp = false }) { Text("知道了") }
            }
        )
    }

    if (showHabitSoundPicker) {
        HabitSoundPickerDialog(
            currentKey = habitSoundKey,
            allowFollowGlobal = false,
            onDismiss = {
                showHabitSoundPicker = false
                HabitSounds.stopPreview()
            },
            onSelect = { key ->
                habitSoundKey = key
                settings.habitSoundKey = key
            }
        )
    }

    if (showTimerFocusPicker) {
        HabitSoundPickerDialog(
            currentKey = timerFocusSoundKey,
            allowFollowGlobal = false,
            onDismiss = {
                showTimerFocusPicker = false
                HabitSounds.stopPreview()
            },
            onSelect = { key ->
                timerFocusSoundKey = key
                settings.timerFocusSoundKey = key
            },
            onImportAudio = {
                showTimerFocusPicker = false
                openAudioImporter(SoundTarget.TIMER_FOCUS)
            }
        )
    }

    if (showTimerBreakPicker) {
        HabitSoundPickerDialog(
            currentKey = timerBreakSoundKey,
            allowFollowGlobal = false,
            onDismiss = {
                showTimerBreakPicker = false
                HabitSounds.stopPreview()
            },
            onSelect = { key ->
                timerBreakSoundKey = key
                settings.timerBreakSoundKey = key
            },
            onImportAudio = {
                showTimerBreakPicker = false
                openAudioImporter(SoundTarget.TIMER_BREAK)
            }
        )
    }
}


@Composable
private fun AlertLevelChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val palette = Potato.palette
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) palette.focus else palette.muted,
        modifier = Modifier
            .clip(CircleShape)
            .background(if (selected) palette.focusSoft else palette.surfaceRaised)
            .border(
                1.dp,
                if (selected) palette.focus else palette.line,
                CircleShape
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    trailing: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    danger: Boolean = false
) {
    val palette = Potato.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = palette.muted,
                modifier = Modifier.size(19.dp)
            )
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (danger) palette.danger else palette.ink
            )
            Spacer(Modifier.height(3.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            trailing,
            style = MaterialTheme.typography.labelLarge,
            color = if (danger) palette.danger else palette.focus
        )
    }
}

@Composable
private fun HabitSlotSettingRow(
    label: String,
    time: String,
    enabled: Boolean,
    onTimeClick: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    val palette = Potato.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = palette.ink)
            Spacer(Modifier.height(3.dp))
            Text(
                "汇总该时段未完成的习惯",
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted
            )
        }
        TextButton(onClick = onTimeClick) {
            Text(
                time,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) palette.focus else palette.muted
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.focus,
                uncheckedThumbColor = palette.muted,
                uncheckedTrackColor = palette.line
            )
        )
    }
}

private fun formatMinutes(minutesOfDay: Int): String =
    String.format(Locale.US, "%02d:%02d", minutesOfDay / 60, minutesOfDay % 60)

@Composable
private fun DividerLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp)
            .height(1.dp)
            .background(Potato.palette.line)
    )
}

@Composable
private fun StepperButton(text: String, onClick: () -> Unit) {
    val palette = Potato.palette
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(palette.surfaceRaised)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = palette.ink)
    }
}

@Composable
private fun PresetEditorDialog(
    initial: Preset,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (Preset) -> Unit,
    onDelete: (Preset) -> Unit
) {
    val palette = Potato.palette
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var focus by remember(initial.id) { mutableIntStateOf(initial.focusMin) }
    var shortBreak by remember(initial.id) { mutableIntStateOf(initial.shortBreakMin) }
    var longBreak by remember(initial.id) { mutableIntStateOf(initial.longBreakMin) }
    var every by remember(initial.id) { mutableIntStateOf(initial.longBreakEvery) }
    var autoBreak by remember(initial.id) { mutableStateOf(initial.autoStartBreak) }
    var autoFocus by remember(initial.id) { mutableStateOf(initial.autoStartFocus) }
    var isDefault by remember(initial.id) { mutableStateOf(initial.isDefault) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = { Text(if (initial.id == 0L) "新建节奏" else "编辑节奏") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
                Spacer(Modifier.height(12.dp))
                NumberStepper("专注", focus, 1..180, "分钟") { focus = it }
                NumberStepper("短休息", shortBreak, 1..60, "分钟") { shortBreak = it }
                NumberStepper("长休息", longBreak, 5..120, "分钟") { longBreak = it }
                NumberStepper("每几颗长休", every, 2..8, "颗") { every = it }
                Spacer(Modifier.height(8.dp))
                SwitchRow("自动开始休息", autoBreak) { autoBreak = it }
                SwitchRow("自动开始下一颗", autoFocus) { autoFocus = it }
                SwitchRow("设为默认节奏", isDefault) { isDefault = it }
                if (canDelete) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { onDelete(initial) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = palette.danger, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("删除这个节奏", color = palette.danger)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim().ifBlank { "我的节奏" },
                            focusMin = focus,
                            shortBreakMin = shortBreak,
                            longBreakMin = longBreak,
                            longBreakEvery = every,
                            autoStartBreak = autoBreak,
                            autoStartFocus = autoFocus,
                            isDefault = isDefault
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = palette.focus)
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun NumberStepper(
    label: String,
    value: Int,
    range: IntRange,
    unit: String,
    onChange: (Int) -> Unit
) {
    val palette = Potato.palette
    var text by remember(value) { mutableStateOf(value.toString()) }
    LaunchedEffect(value) {
        if (text.toIntOrNull() != value) text = value.toString()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.ink, modifier = Modifier.weight(1f))
        StepperButton("−") { onChange((value - 1).coerceIn(range.first, range.last)) }
        Row(
            modifier = Modifier
                .width(104.dp)
                .padding(horizontal = 5.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, palette.line, RoundedCornerShape(10.dp))
                .padding(horizontal = 7.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = text,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(3)
                    text = digits
                    digits.toIntOrNull()?.let { parsed ->
                        val clamped = parsed.coerceIn(range.first, range.last)
                        onChange(clamped)
                        if (clamped != parsed) text = clamped.toString()
                    }
                },
                modifier = Modifier.width(44.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.labelLarge.copy(
                    color = palette.ink,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Text(unit, style = MaterialTheme.typography.labelMedium, color = palette.muted)
        }
        StepperButton("+") { onChange((value + 1).coerceIn(range.first, range.last)) }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val palette = Potato.palette
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = palette.ink, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = palette.focus,
                uncheckedThumbColor = palette.muted,
                uncheckedTrackColor = palette.line
            )
        )
    }
}
