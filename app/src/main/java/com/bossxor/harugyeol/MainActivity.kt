package com.bossxor.harugyeol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import com.bossxor.harugyeol.backup.Backup
import com.bossxor.harugyeol.backup.BackupException
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
import com.bossxor.harugyeol.reminders.Notifier
import com.bossxor.harugyeol.reminders.ReminderScheduler
import com.bossxor.harugyeol.reminders.SummaryNotice
import com.bossxor.harugyeol.task.Task
import com.bossxor.harugyeol.task.saveTask
import com.bossxor.harugyeol.task.undoTaskCompletion
import com.bossxor.harugyeol.task.addTask
import com.bossxor.harugyeol.task.deleteTask
import com.bossxor.harugyeol.task.editTask
import com.bossxor.harugyeol.task.emptyTrash
import com.bossxor.harugyeol.task.restoreTask
import com.bossxor.harugyeol.task.setTaskDone
import com.bossxor.harugyeol.task.trashTask
import com.bossxor.harugyeol.ui.ArchiveScreen
import com.bossxor.harugyeol.ui.CalendarScreen
import com.bossxor.harugyeol.ui.DetailActions
import com.bossxor.harugyeol.ui.HabitActions
import com.bossxor.harugyeol.ui.HabitDetailScreen
import com.bossxor.harugyeol.ui.HabitFormScreen
import com.bossxor.harugyeol.ui.HabitPalettes
import com.bossxor.harugyeol.ui.HabitsScreen
import com.bossxor.harugyeol.ui.HaruTheme
import com.bossxor.harugyeol.ui.HaruType
import com.bossxor.harugyeol.ui.LocalHaru
import com.bossxor.harugyeol.ui.PillButton
import com.bossxor.harugyeol.ui.PopCard
import com.bossxor.harugyeol.ui.SettingsActions
import com.bossxor.harugyeol.ui.SettingsScreen
import com.bossxor.harugyeol.ui.TaskFormScreen
import com.bossxor.harugyeol.ui.TasksScreen
import com.bossxor.harugyeol.ui.TimerActions
import com.bossxor.harugyeol.ui.TimerScreen
import com.bossxor.harugyeol.ui.TrashScreen
import com.bossxor.harugyeol.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

sealed interface Screen {
    data object Habits : Screen
    data object Tasks : Screen
    data object Calendar : Screen
    data object Settings : Screen
    data object AddHabit : Screen
    data class Detail(val id: String) : Screen
    data class EditHabit(val id: String) : Screen
    data class Timer(val id: String) : Screen
    data object Archive : Screen
    data class TaskForm(val id: String?, val date: LocalDate?) : Screen
    data object Trash : Screen
}

private val tabs = listOf("습관" to 0, "할 일" to 1, "캘린더" to 2, "설정" to 3)

class MainActivity : ComponentActivity() {
    private val today = mutableStateOf(LocalDate.now())
    private val notificationsAllowed = mutableStateOf(true)
    private val pendingRestore = mutableStateOf<AppData?>(null)
    private val taskToOpen = mutableStateOf<String?>(null)
    private val updateStatus = mutableStateOf("")
    private val banner = mutableStateOf("")
    private lateinit var store: AppStore

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsAllowed.value = it }

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) writeBackup(uri)
    }

    private val openBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) readBackup(uri)
    }

    private fun refreshPermission() {
        notificationsAllowed.value =
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestNotifications() = notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)

    private fun bootCount() = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun writeBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                // 실행 중인 타이머는 현재 측정량의 일시정지 상태로 담는다
                val data = store.state.value.pauseTimer(SystemClock.elapsedRealtime(), bootCount())
                val text = Backup.encode(data)
                withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) } }
                toast("백업을 저장했어요")
            } catch (e: Exception) {
                toast("백업을 저장하지 못했어요")
            }
        }
    }

    private fun readBackup(uri: Uri) {
        lifecycleScope.launch {
            try {
                pendingRestore.value = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)!!.use { Backup.read(it) }
                }
            } catch (e: BackupException) {
                toast(e.message ?: "복원할 수 없어요")
            } catch (e: Exception) {
                toast("파일을 읽을 수 없어요")
            }
        }
    }

    /** 새 릴리스를 확인하고, 있으면 받아서 설치한다. 실패는 조용히 넘기되 직접 확인하면 이유를 보여준다. */
    private fun checkForUpdate(manual: Boolean) {
        lifecycleScope.launch {
            try {
                if (manual) updateStatus.value = "확인하는 중이에요…"
                val release = withContext(Dispatchers.IO) { Updater.fetchLatest() }
                if (release == null || !Updater.isNewer(release.tag, BuildConfig.VERSION_NAME)) {
                    updateStatus.value = if (manual) "최신 버전이에요" else ""
                    return@launch
                }
                val apk = File(cacheDir, "update.apk")
                banner.value = "새 버전 ${release.version}을 받는 중이에요 (0%)"
                updateStatus.value = "새 버전 ${release.version}을 받는 중이에요"
                withContext(Dispatchers.IO) {
                    Updater.download(release.apkUrl, apk) { p -> banner.value = "새 버전 ${release.version}을 받는 중이에요 ($p%)" }
                    Updater.verify(this@MainActivity, apk)
                }
                // 설치 전에 현재 데이터를 내부 스냅샷으로 남긴다
                store.createSnapshot()
                banner.value = "새 버전 ${release.version}을 설치하는 중이에요"
                if (!Updater.canInstall(this@MainActivity)) {
                    banner.value = ""
                    updateStatus.value = "설치를 허용해야 업데이트할 수 있어요"
                    startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                    return@launch
                }
                try {
                    withContext(Dispatchers.IO) { Updater.install(this@MainActivity, apk) }
                } catch (e: Exception) {
                    // 앱 내부 설치가 안 되면 다운로드 폴더 + 시스템 설치 화면으로 넘어간다
                    val backup = Backup.encode(store.state.value)
                    withContext(Dispatchers.IO) {
                        Updater.saveBackupToDownloads(this@MainActivity, "harugyeol-backup.json", backup)
                        Updater.installDirectly(this@MainActivity, apk, "Harugyeol-${release.version}.apk")
                    }
                }
            } catch (e: Exception) {
                banner.value = ""
                updateStatus.value = if (manual) (e.message ?: "업데이트를 확인하지 못했어요") else ""
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskToOpen.value = intent.getStringExtra("taskId")
        enableEdgeToEdge()
        store = try {
            (application as HarugyeolApp).store
        } catch (e: StoreException) {
            setContent { HaruTheme { com.bossxor.harugyeol.ui.ErrorScreen(e.message ?: "") } }
            return
        }
        refreshPermission()
        if (savedInstanceState == null) checkForUpdate(manual = false)

        setContent {
            HaruTheme {
                val c = LocalHaru.current
                val data by store.state.collectAsState()
                val scope = rememberCoroutineScope()
                var screen by remember { mutableStateOf<Screen>(Screen.Habits) }
                var lastCompletion by remember { mutableStateOf<Pair<Task, Task>?>(null) }
                LaunchedEffect(taskToOpen.value) {
                    taskToOpen.value?.let { screen = Screen.TaskForm(it, null); taskToOpen.value = null }
                }
                fun toggleTask(id: String, done: Boolean) {
                    val expectedDate = data.tasks.firstOrNull { it.id == id }?.dueDate
                    scope.launch {
                        try {
                            var undo: Pair<Task, Task>? = null
                            store.update { current ->
                                val before = current.tasks.firstOrNull { it.id == id }
                                val next = current.setTaskDone(id, done, System.currentTimeMillis(), expectedDate, today.value)
                                val after = next.tasks.firstOrNull { it.id == id }
                                if (done && before != null && after != null && before != after) undo = before to after
                                next
                            }
                            lastCompletion = undo
                        } catch (e: Exception) { toast(e.message ?: "저장하지 못했어요") }
                    }
                }

                // 저장소 변경은 입력 검증 실패를 안내하고 상태는 바꾸지 않는다.
                fun change(f: (AppData) -> AppData) {
                    scope.launch {
                        try {
                            store.update(f)
                        } catch (e: HabitInputException) {
                            toast(e.message ?: "입력을 확인해 주세요")
                        } catch (e: Exception) {
                            toast("저장하지 못했어요. 다시 시도해 주세요")
                        }
                    }
                }
                fun nowMs() = System.currentTimeMillis()

                LaunchedEffect(Unit) { change { it.recoverTimer(bootCount()) } }


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
                val settingsActions = SettingsActions(
                    addWidget = {
                        val manager = android.appwidget.AppWidgetManager.getInstance(this@MainActivity)
                        if (manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(android.content.ComponentName(this@MainActivity, com.bossxor.harugyeol.widget.TodayWidget::class.java), null, null)
                        else toast("홈 화면을 길게 눌러 하루결 위젯을 추가해 주세요")
                    },
                    setAllDayTime = { time -> change { it.copy(settings = it.settings.copy(allDayReminderTime = time)) } },
                    setSummary = { enabled, time ->
                        change { it.copy(settings = it.settings.copy(dailySummary = DailySummary(enabled, time))) }
                    },
                    requestNotifications = { requestNotifications() },
                    testNotification = {
                        if (!notificationsAllowed.value) requestNotifications()
                        Notifier.ensureChannels(this@MainActivity)
                        Notifier.postSummary(this@MainActivity, SummaryNotice("테스트 알림", "알림이 잘 오고 있어요"))
                    },
                    backup = {
                        createBackup.launch("harugyeol-backup-${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}.json")
                    },
                    restore = { openBackup.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    restoreSnapshot = {
                        scope.launch { toast(if (store.restoreSnapshot()) "스냅샷으로 되돌렸어요" else "스냅샷이 없어요") }
                    },
                    checkUpdate = { checkForUpdate(manual = true) },
                )

                val tabStates = rememberSaveableStateHolder()
                val isTab = screen is Screen.Habits || screen is Screen.Tasks || screen is Screen.Calendar || screen is Screen.Settings
                BackHandler(enabled = screen != Screen.Habits) {
                    screen = when (val s = screen) {
                        is Screen.EditHabit -> Screen.Detail(s.id)
                        is Screen.Trash -> Screen.Tasks
                        is Screen.TaskForm -> Screen.Tasks
                        else -> Screen.Habits
                    }
                }

                Column(
                    Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    lastCompletion?.let { (before, after) ->
                        PillButton("완료 되돌리기", c.surface, c.text, Modifier.padding(8.dp)) {
                            change { it.undoTaskCompletion(before, after) }
                            lastCompletion = null
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        when (val s = screen) {
                            Screen.Habits -> HabitsScreen(
                                data, today.value, actions,
                                onAdd = { screen = Screen.AddHabit },
                                onOpen = { screen = Screen.Detail(it) },
                                onTimer = { screen = Screen.Timer(it) },
                                onArchive = { screen = Screen.Archive },
                                banner = banner.value,
                            )

                            Screen.Tasks -> tabStates.SaveableStateProvider("tasks") { TasksScreen(
                                data, today.value,
                                onAdd = { screen = Screen.TaskForm(null, null) },
                                onOpen = { screen = Screen.TaskForm(it, null) },
                                onToggle = { id, done -> toggleTask(id, done) },
                                onTrash = { screen = Screen.Trash },
                            ) }

                            Screen.Calendar -> tabStates.SaveableStateProvider("calendar") { CalendarScreen(
                                data, today.value,
                                onAddOn = { screen = Screen.TaskForm(null, it) },
                                onOpen = { screen = Screen.TaskForm(it, null) },
                                onToggle = { id, done -> toggleTask(id, done) },
                            ) }

                            Screen.Settings -> SettingsScreen(
                                data, notificationsAllowed.value, store.snapshotTime(),
                                BuildConfig.VERSION_NAME, updateStatus.value, settingsActions,
                            )

                            Screen.AddHabit -> HabitFormScreen(
                                initial = null, today = today.value,
                                onClose = { screen = Screen.Habits },
                                onCancelPending = {},
                                onSave = { n ->
                                    val t = today.value
                                    val now = nowMs()
                                    try {
                                        data.addHabit(n.name, n.colorIndex, n.type, n.unit, n.mode, n.weekdays, n.targetDays, n.targetAmount, t, t, now)
                                        change {
                                            it.addHabit(n.name, n.colorIndex, n.type, n.unit, n.mode, n.weekdays, n.targetDays, n.targetAmount, t, t, now)
                                                .let { d ->
                                                    val last = d.habits.last()
                                                    d.editHabit(last.id, last.name, last.colorIndex, n.mode, n.weekdays, n.targetDays, n.targetAmount, n.reminders, t, now)
                                                }
                                        }
                                        if (n.reminders.isNotEmpty() && !notificationsAllowed.value) requestNotifications()
                                        screen = Screen.Habits
                                        null
                                    } catch (e: HabitInputException) {
                                        e.message
                                    }
                                },
                            )

                            is Screen.Detail -> {
                                val h = data.habits.firstOrNull { it.id == s.id }
                                if (h == null) screen = Screen.Habits else HabitDetailScreen(
                                    h, today.value, detailActions,
                                    onBack = { screen = Screen.Habits },
                                    onEdit = { screen = Screen.EditHabit(h.id) },
                                    onArchive = { change { it.archiveHabit(h.id, nowMs()) }; screen = Screen.Habits },
                                    onCancelPending = { change { it.cancelPendingGoal(h.id, today.value, nowMs()) } },
                                )
                            }

                            is Screen.EditHabit -> {
                                val h = data.habits.firstOrNull { it.id == s.id }
                                if (h == null) screen = Screen.Habits else HabitFormScreen(
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
                                if (h == null) screen = Screen.Habits else TimerScreen(
                                    h, data, today.value, bootCount(), timerActions(h.id),
                                    onBack = { screen = Screen.Habits },
                                )
                            }

                            Screen.Archive -> ArchiveScreen(
                                habits = data.habits.filter { it.archivedAt != null }.sortedBy { it.sortOrder },
                                onBack = { screen = Screen.Habits },
                                onUnarchive = { id -> change { it.unarchiveHabit(id, nowMs()) } },
                                onDelete = { id -> change { it.deleteHabit(id) } },
                                onOpen = { screen = Screen.Detail(it) },
                            )

                            is Screen.TaskForm -> {
                                val t = s.id?.let { id -> data.tasks.firstOrNull { it.id == id } }
                                TaskFormScreen(
                                    initial = t, defaultDate = s.date, today = today.value,
                                    onClose = { screen = Screen.Tasks },
                                    onTrash = { t?.let { x -> change { it.trashTask(x.id, nowMs()) } }; screen = Screen.Tasks },
                                    onSave = { draft ->
                                        try {
                                            val now = nowMs()
                                            data.saveTask(t?.id, draft, now)
                                            scope.launch {
                                                try {
                                                    store.update { it.saveTask(t?.id, draft, now) }
                                                    if (draft.reminders.isNotEmpty() && !notificationsAllowed.value) requestNotifications()
                                                    screen = Screen.Tasks
                                                } catch (e: Exception) { toast(e.message ?: "저장하지 못했어요") }
                                            }
                                            null
                                        } catch (e: HabitInputException) { e.message }
                                    },
                                )
                            }

                            Screen.Trash -> TrashScreen(
                                data, onBack = { screen = Screen.Tasks },
                                onRestore = { id -> change { it.restoreTask(id, nowMs()) } },
                                onDelete = { id -> change { it.deleteTask(id) } },
                                onEmpty = { change { it.emptyTrash() } },
                            )
                        }
                    }
                    if (isTab) BottomBar(screen) { screen = it }
                }

                pendingRestore.value?.let { incoming ->
                    Dialog(onDismissRequest = { pendingRestore.value = null }) {
                        PopCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("백업으로 바꿀까요?", style = HaruType.headline, color = c.text)
                                Text(
                                    "지금: 습관 ${data.habits.size}개 · 할 일 ${data.tasks.size}개\n" +
                                        "백업: 습관 ${incoming.habits.size}개 · 할 일 ${incoming.tasks.size}개\n\n" +
                                        "현재 데이터가 모두 백업 내용으로 바뀌어요. 바꾸기 전의 데이터는 스냅샷으로 남겨요." +
                                        if (data.timer != null) "\n실행 중인 타이머는 사라져요." else "",
                                    style = HaruType.body, color = c.textSub,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    PillButton("취소", c.surface, c.text, Modifier.weight(1f)) { pendingRestore.value = null }
                                    PillButton("교체해서 복원", c.errorBg, c.errorText, Modifier.weight(1f)) {
                                        pendingRestore.value = null
                                        scope.launch {
                                            try {
                                                store.replaceAll(incoming)
                                                toast("백업으로 복원했어요")
                                            } catch (e: Exception) {
                                                toast("복원하지 못했어요. 기존 데이터는 그대로예요")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        taskToOpen.value = intent.getStringExtra("taskId")
    }

    override fun onResume() {
        super.onResume()
        today.value = LocalDate.now()
        refreshPermission()
    }
}

@androidx.compose.runtime.Composable
private fun BottomBar(current: Screen, onSelect: (Screen) -> Unit) {
    val c = LocalHaru.current
    val screens = listOf(Screen.Habits, Screen.Tasks, Screen.Calendar, Screen.Settings)
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .border(2.dp, c.outline, RoundedCornerShape(0.dp))
            .height(64.dp),
    ) {
        tabs.forEachIndexed { i, (label, _) ->
            val selected = current == screens[i]
            val p = HabitPalettes[i]
            Box(
                Modifier.weight(1f).fillMaxSize().clickable(onClickLabel = label) { onSelect(screens[i]) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .background(if (selected) p.fill else Color.Transparent, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label, style = HaruType.button,
                        color = if (selected) p.onFill else c.textSub,
                    )
                }
            }
        }
    }
}
