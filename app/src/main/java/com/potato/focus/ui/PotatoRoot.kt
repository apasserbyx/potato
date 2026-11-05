package com.potato.focus.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.potato.focus.PotatoApp
import com.potato.focus.timer.TimerController
import com.potato.focus.timer.TimerMath
import com.potato.focus.timer.TimerService
import com.potato.focus.timer.TimerSnapshot
import com.potato.focus.timer.TimerStatus
import com.potato.focus.timer.TimerStore
import com.potato.focus.ui.components.BottomNavBar
import com.potato.focus.ui.components.SideNavBar
import com.potato.focus.ui.focus.FocusScreen
import com.potato.focus.ui.habits.HabitsScreen
import com.potato.focus.ui.settings.SettingsScreen
import com.potato.focus.ui.stats.StatsScreen
import com.potato.focus.ui.theme.Potato
import kotlinx.coroutines.delay

@Composable
fun PotatoRoot(
    requestedTab: Int? = null,
    onTabRequestHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as PotatoApp
    val palette = Potato.palette
    val isWide = LocalConfiguration.current.screenWidthDp >= 600
    val view = LocalView.current
    val store = remember { TimerStore(context) }
    var snapshot by remember { mutableStateOf<TimerSnapshot>(store.read()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(requestedTab) {
        if (requestedTab != null) {
            tab = requestedTab
            refreshKey++
            onTabRequestHandled()
        }
    }

    LaunchedEffect(Unit) {
        val initial = store.read()
        if (initial.isActive) {
            if (!initial.isStopwatch &&
                TimerMath.isDue(initial, System.currentTimeMillis())
            ) {
                TimerController(context).completeDue()
                snapshot = store.read()
            } else {
                TimerService.start(context)
            }
        }
        while (true) {
            snapshot = store.read()
            delay(500)
        }
    }

    LaunchedEffect(snapshot.status) {
        if (snapshot.status == TimerStatus.RUNNING) {
            while (true) {
                now = System.currentTimeMillis()
                delay(250)
            }
        } else {
            now = System.currentTimeMillis()
        }
    }

    DisposableEffect(snapshot.isActive, app.settings.keepScreenOn) {
        view.keepScreenOn = snapshot.isActive && app.settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    fun selectTab(index: Int) {
        if (tab != index) {
            tab = index
            refreshKey++
        }
    }

    Scaffold(
        containerColor = palette.background,
        contentWindowInsets = if (isWide) {
            WindowInsets.safeDrawing
        } else {
            WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal
            )
        },
        bottomBar = {
            if (!isWide) {
                BottomNavBar(selected = tab, onSelect = ::selectTab)
            }
        }
    ) { padding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isWide) {
                SideNavBar(selected = tab, onSelect = ::selectTab)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                when (tab) {
                    0 -> FocusScreen(
                        snapshot = snapshot,
                        now = now,
                        onChanged = {
                            snapshot = store.read()
                            now = System.currentTimeMillis()
                            refreshKey++
                        },
                        onOpenSettings = {
                            tab = 3
                            refreshKey++
                        }
                    )
                    1 -> HabitsScreen(
                        snapshot = snapshot,
                        now = now,
                        refreshKey = refreshKey,
                        onStartFocus = {
                            tab = 0
                            refreshKey++
                        },
                        onChanged = {
                            snapshot = store.read()
                            refreshKey++
                        }
                    )
                    2 -> StatsScreen(refreshKey = refreshKey)
                    else -> SettingsScreen(
                        onDataChanged = {
                            refreshKey++
                            snapshot = store.read()
                        }
                    )
                }
            }
        }
    }
}
