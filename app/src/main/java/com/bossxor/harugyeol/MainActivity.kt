package com.bossxor.harugyeol

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.bossxor.harugyeol.habit.GoalMode
import com.bossxor.harugyeol.habit.HabitInputException
import com.bossxor.harugyeol.habit.addAmount
import com.bossxor.harugyeol.habit.addHabit
import com.bossxor.harugyeol.habit.archiveHabit
import com.bossxor.harugyeol.habit.cancelPendingGoal
import com.bossxor.harugyeol.habit.checkpointTimer
import com.bossxor.harugyeol.habit.deleteHabit
import com.bossxor.harugyeol.habit.discardTimer
import com.bossxor.harugyeol.habit.editHabit
import com.bossxor.harugyeol.habit.moveHabit
import com.bossxor.harugyeol.habit.pauseTimer
import com.bossxor.harugyeol.habit.recoverTimer
import com.bossxor.harugyeol.habit.resumeTimer
import com.bossxor.harugyeol.habit.saveTimer
import com.bossxor.harugyeol.habit.setSkipped
import com.bossxor.harugyeol.habit.setTotal
import com.bossxor.harugyeol.habit.startTimer
import com.bossxor.harugyeol.habit.unarchiveHabit
import com.bossxor.harugyeol.reminders.ReminderScheduler
import com.bossxor.harugyeol.ui.ArchiveScreen
import com.bossxor.harugyeol.ui.DetailActions
import com.bossxor.harugyeol.ui.HabitActions
import com.bossxor.harugyeol.ui.HabitDetailScreen
import com.bossxor.harugyeol.ui.HabitFormScreen
import com.bossxor.harugyeol.ui.HabitsScreen
import com.bossxor.harugyeol.ui.HaruTheme
import com.bossxor.harugyeol.ui.LocalHaru
import com.bossxor.harugyeol.ui.SettingsScreen
import com.bossxor.harugyeol.ui.TimerActions
import com.bossxor.harugyeol.ui.TimerScreen
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface Screen {
    data object List : Screen
    data object Add : Screen
    data class Detail(val id: String) : Screen
    data class Edit(val id: String) : Screen
    data class Timer(val id: String) : Screen
    data object Archive : Screen
    data object Settings : Screen
}

class MainActivity : ComponentActivity() {
    private val today = mutableStateOf(LocalDate.now())
    private val notificationsAllowed = mutableStateOf(true)

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed.value = it }

    private fun refreshPermission() {
        notificationsAllowed.value =
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotifications() = notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

    private fun bootCount() = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = try {
            (application as HarugyeolApp).store
        } catch (e: StoreException) {
            setContent { HaruTheme { com.bossxor.harugyeol.ui.ErrorScreen(e.message ?: "") } }
            return
        }
        refreshPermission()
        setContent {
            HaruTheme {
                val data by store.state.collectAsState()
                val scope = rememberCoroutineScope()
                var screen by remember { mutableStateOf<Screen>(Screen.List) }

                // 저장소 변경은 입력 검증 실패를 안내하고 상태는 바꾸지 않는다.
                fun change(f: (AppData) -> AppData) {
                    scope.launch {
                        try {
                            store.update(f)
                        } catch (e: HabitInputException) {
                            Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "저장하지 못했어요. 다시 시도해 주세요", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                fun nowMs() = System.currentTimeMillis()

                // 시작 시 타이머 복구, 데이터가 바뀔 때마다 알람을 다시 예약한다
                LaunchedEffect(Unit) { change { it.recoverTimer(bootCount()) } }
                LaunchedEffect(data) { ReminderScheduler.rescheduleAll(this@MainActivity, data) }

                val actions = HabitActions(
                    addAmount = { id, d -> change { it.addAmount(id, today.value, d, today.value, nowMs()) } },
                    setTotal = { id, a -> change { it.setTotal(id, today.value, a, today.value, nowMs()) } },
                    setSkipped = { id, s -> change { it.setSkipped(id, today.value, s, today.value, nowMs()) } },
                    move = { id, up -> change { it.moveHabit(id, up) } },
                )
                val detailActions = DetailActions(
                    setTotalAt = { id, date, a -> change { it.setTotal(id, date, a, today.value, nowMs()) } },
                    setSkippedAt = { id, date, s -> change { it.setSkipped(id, date, s, today.value, nowMs()) } },
                )
                fun timerActions(id: String) = TimerActions(
                    start = { change { it.startTimer(id, today.value, SystemClock.elapsedRealtime(), bootCount()) } },
                    pause = { change { it.pauseTimer(SystemClock.elapsedRealtime(), bootCount()) } },
                    resume = { change { it.resumeTimer(SystemClock.elapsedRealtime(), bootCount()) } },
                    save = { sid -> change { it.saveTimer(sid, SystemClock.elapsedRealtime(), bootCount(), nowMs()) } },
                    discard = { sid -> change { it.discardTimer(sid) } },
                    checkpoint = { change { it.checkpointTimer(SystemClock.elapsedRealtime(), bootCount()) } },
                )

                BackHandler(enabled = screen != Screen.List) {
                    screen = when (val s = screen) {
                        is Screen.Edit -> Screen.Detail(s.id)
                        is Screen.Timer -> Screen.List
                        else -> Screen.List
                    }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(LocalHaru.current.bg)
                        .windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    when (val s = screen) {
                        Screen.List -> HabitsScreen(
                            data, today.value, actions,
                            onAdd = { screen = Screen.Add },
                            onOpen = { screen = Screen.Detail(it) },
                            onTimer = { screen = Screen.Timer(it) },
                            onArchive = { screen = Screen.Archive },
                            onSettings = { screen = Screen.Settings },
                        )

                        Screen.Add -> HabitFormScreen(
                            initial = null, today = today.value,
                            onClose = { screen = Screen.List },
                            onCancelPending = {},
                            onSave = { n ->
                                val t = today.value
                                val now = nowMs()
                                try {
                                    // 검증은 같은 연산을 미리 돌려 확인하고 실제 저장은 저장소 안에서 한다
                                    data.addHabit(n.name, n.colorIndex, n.type, n.unit, n.mode, n.weekdays, n.targetDays, n.targetAmount, t, t, now)
                                    change {
                                        it.addHabit(n.name, n.colorIndex, n.type, n.unit, n.mode, n.weekdays, n.targetDays, n.targetAmount, t, t, now)
                                            .let { d ->
                                                // 알림 시각은 방금 만든 습관(마지막)에 붙인다
                                                val last = d.habits.last()
                                                d.editHabit(last.id, last.name, last.colorIndex, n.mode, n.weekdays, n.targetDays, n.targetAmount, n.reminders, t, now)
                                            }
                                    }
                                    if (n.reminders.isNotEmpty() && !notificationsAllowed.value) requestNotifications()
                                    screen = Screen.List
                                    null
                                } catch (e: HabitInputException) {
                                    e.message
                                }
                            },
                        )

                        is Screen.Detail -> {
                            val h = data.habits.firstOrNull { it.id == s.id }
                            if (h == null) screen = Screen.List else HabitDetailScreen(
                                h, today.value, detailActions,
                                onBack = { screen = Screen.List },
                                onEdit = { screen = Screen.Edit(h.id) },
                                onArchive = { change { it.archiveHabit(h.id, nowMs()) }; screen = Screen.List },
                                onCancelPending = { change { it.cancelPendingGoal(h.id, today.value, nowMs()) } },
                            )
                        }

                        is Screen.Edit -> {
                            val h = data.habits.firstOrNull { it.id == s.id }
                            if (h == null) screen = Screen.List else HabitFormScreen(
                                initial = h, today = today.value,
                                onClose = { screen = Screen.Detail(h.id) },
                                onCancelPending = { change { it.cancelPendingGoal(h.id, today.value, nowMs()) } },
                                onSave = { n ->
                                    val t = today.value
                                    val now = nowMs()
                                    try {
                                        data.editHabit(h.id, n.name, n.colorIndex, n.mode, n.weekdays, n.targetDays, n.targetAmount, n.reminders, t, now)
                                        change { it.editHabit(h.id, n.name, n.colorIndex, n.mode, n.weekdays, n.targetDays, n.targetAmount, n.reminders, t, now) }
                                        if (n.reminders.isNotEmpty() && !notificationsAllowed.value) requestNotifications()
                                        screen = Screen.Detail(h.id)
                                        null
                                    } catch (e: HabitInputException) {
                                        e.message
                                    }
                                },
                            )
                        }

                        is Screen.Timer -> {
                            val h = data.habits.firstOrNull { it.id == s.id }
                            if (h == null) screen = Screen.List else TimerScreen(
                                h, data, today.value, bootCount(), timerActions(h.id),
                                onBack = { screen = Screen.List },
                            )
                        }

                        Screen.Archive -> ArchiveScreen(
                            habits = data.habits.filter { it.archivedAt != null }.sortedBy { it.sortOrder },
                            onBack = { screen = Screen.List },
                            onUnarchive = { id -> change { it.unarchiveHabit(id, nowMs()) } },
                            onDelete = { id -> change { it.deleteHabit(id) } },
                            onOpen = { screen = Screen.Detail(it) },
                        )

                        Screen.Settings -> SettingsScreen(
                            data, notificationsAllowed.value,
                            onBack = { screen = Screen.List },
                            onSummary = { enabled, time ->
                                change { it.copy(settings = it.settings.copy(dailySummary = DailySummary(enabled, time))) }
                            },
                            onRequestNotifications = { requestNotifications() },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        today.value = LocalDate.now()
        refreshPermission()
    }
}
