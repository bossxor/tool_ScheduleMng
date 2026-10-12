package com.bossxor.harugyeol.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bossxor.harugyeol.AppData
import com.bossxor.harugyeol.habit.DayState
import com.bossxor.harugyeol.habit.Habit
import com.bossxor.harugyeol.habit.TimerState
import com.bossxor.harugyeol.habit.elapsedMs
import com.bossxor.harugyeol.habit.logOf
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun TopBar(title: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    val c = LocalHaru.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(48.dp).clickable(onClickLabel = "뒤로", onClick = onBack), contentAlignment = Alignment.Center) {
            Text("←", style = HaruType.headline, color = c.text)
        }
        Text(title, style = HaruType.title, color = c.text, maxLines = 1, modifier = Modifier.weight(1f))
        trailing()
    }
}

private fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return buildString {
        if (h > 0) append("${h}시간 ")
        if (h > 0 || m > 0) append("${m}분 ")
        append("${sec}초")
    }
}

private fun formatClock(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = s % 3600 / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
}

class TimerActions(
    val start: () -> Unit,
    val pause: () -> Unit,
    val resume: () -> Unit,
    val save: (sessionId: String) -> Unit,
    val discard: (sessionId: String) -> Unit,
    val checkpoint: () -> Unit,
)

@Composable
fun TimerScreen(habit: Habit, data: AppData, today: LocalDate, boot: Int, actions: TimerActions, onBack: () -> Unit) {
    val c = LocalHaru.current
    val p = palette(habit.colorIndex)
    val t = data.timer?.takeIf { it.habitId == habit.id }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var confirmSave by remember { mutableStateOf(false) }

    // 화면이 열려 있는 동안 1초마다 표시를 갱신하고 30초마다 보존한다
    LaunchedEffect(t?.sessionId, t?.state) {
        var n = 0
        while (t?.state == TimerState.RUNNING) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
            if (++n % 30 == 0) actions.checkpoint()
        }
        now = SystemClock.elapsedRealtime()
    }

    val elapsed = t?.elapsedMs(now, boot) ?: 0
    val saved = habit.logOf(today)?.amount ?: 0

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(habit.name, onBack)
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Sticker(habit.name, p, DayState.PARTIAL, 0f, 72.dp)
            Text(formatClock(elapsed), style = HaruType.display.copy(fontSize = 56.sp, lineHeight = 64.sp), color = c.text)
            Text(
                "오늘 저장된 기록 ${saved / 60}분" + if (t != null) " · 저장하면 더해져요" else "",
                style = HaruType.body, color = c.textSub, textAlign = TextAlign.Center,
            )
            if (t?.rebooted == true) {
                Text(
                    "휴대폰이 다시 켜져서 마지막으로 기록된 시점에서 멈췄어요. 빠진 시간은 습관 화면에서 직접 더할 수 있어요.",
                    style = HaruType.bodyS, color = c.errorText, textAlign = TextAlign.Center,
                )
            }
            when (t?.state) {
                null -> PillButton("시작", p.fill, p.onFill, Modifier.fillMaxWidth()) { actions.start() }
                TimerState.RUNNING -> PillButton("일시정지", p.fill, p.onFill, Modifier.fillMaxWidth()) { actions.pause() }
                TimerState.PAUSED -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("이어서 하기", p.fill, p.onFill, Modifier.fillMaxWidth()) { actions.resume() }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PillButton("버리기", c.surface, c.text, Modifier.weight(1f)) { actions.discard(t.sessionId); onBack() }
                        PillButton("저장", HabitPalettes[3].fill, Color.White, Modifier.weight(1f)) { confirmSave = true }
                    }
                }
            }
            if (t?.state == TimerState.RUNNING) {
                PillButton("멈추고 저장", c.surface, c.text, Modifier.fillMaxWidth()) {
                    actions.pause(); confirmSave = true
                }
            }
        }
    }

    if (confirmSave && t != null) {
        val secs = (t.elapsedMs(now, boot) / 1000)
        Dialog(onDismissRequest = { confirmSave = false }) {
            PopCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("기록에 더할까요?", style = HaruType.headline, color = c.text)
                    Text(
                        "${t.date.monthValue}월 ${t.date.dayOfMonth}일에 ${formatDuration(secs * 1000)}를 더해요. " +
                            "오늘 기록은 ${formatDuration((saved + secs) * 1000)}가 돼요.",
                        style = HaruType.body, color = c.textSub,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PillButton("취소", c.surface, c.text, Modifier.weight(1f)) { confirmSave = false }
                        PillButton("저장", HabitPalettes[1].fill, Color.White, Modifier.weight(1f)) {
                            confirmSave = false
                            actions.save(t.sessionId)
                            onBack()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveScreen(
    habits: List<Habit>, onBack: () -> Unit,
    onUnarchive: (String) -> Unit, onDelete: (String) -> Unit, onOpen: (String) -> Unit,
) {
    val c = LocalHaru.current
    var confirm by remember { mutableStateOf<Habit?>(null) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("보관한 습관", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (habits.isEmpty()) Text("보관한 습관이 없어요", style = HaruType.headline, color = c.textSub)
            habits.forEach { h ->
                val p = palette(h.colorIndex)
                PopCard(Modifier.fillMaxWidth(), onClick = { onOpen(h.id) }) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Sticker(h.name, p, DayState.REST, 0f, 48.dp)
                            Text(h.name, style = HaruType.headline, color = c.text, modifier = Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            PillButton("보관 해제", p.fill, p.onFill, Modifier.weight(1f)) { onUnarchive(h.id) }
                            PillButton("삭제", c.errorBg, c.errorText, Modifier.weight(1f)) { confirm = h }
                        }
                    }
                }
            }
        }
    }
    confirm?.let { h ->
        Dialog(onDismissRequest = { confirm = null }) {
            PopCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("‘${h.name}’을 삭제할까요?", style = HaruType.headline, color = c.text)
                    Text("이 습관과 모든 기록이 삭제돼요. 백업 파일이 없으면 되돌릴 수 없어요.", style = HaruType.body, color = c.textSub)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PillButton("취소", c.surface, c.text, Modifier.weight(1f)) { confirm = null }
                        PillButton("삭제", c.errorBg, c.errorText, Modifier.weight(1f)) { onDelete(h.id); confirm = null }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    data: AppData, notificationsAllowed: Boolean, onBack: () -> Unit,
    onSummary: (enabled: Boolean, time: String) -> Unit, onRequestNotifications: () -> Unit,
) {
    val c = LocalHaru.current
    val ctx = LocalContext.current
    val summary = data.settings.dailySummary
    val ignoring = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("설정", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingCard("알림 권한") {
                Text(if (notificationsAllowed) "허용됨" else "꺼져 있어요. 알림을 받으려면 허용해 주세요.", style = HaruType.body, color = c.text)
                if (!notificationsAllowed) PillButton("권한 요청", HabitPalettes[1].fill, Color.White, onClick = onRequestNotifications)
            }
            SettingCard("하루 요약") {
                Text("정한 시각에 오늘 달성한 습관과 남은 습관을 한 번 알려 드려요.", style = HaruType.bodyS, color = c.textSub)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    PillButton(
                        if (summary.enabled) "켜짐" else "꺼짐",
                        if (summary.enabled) HabitPalettes[3].fill else c.surface,
                        if (summary.enabled) Color.White else c.text,
                    ) {
                        if (!summary.enabled && !notificationsAllowed) onRequestNotifications()
                        onSummary(!summary.enabled, summary.time)
                    }
                    PillButton("시각 ${summary.time}", c.surface, c.text) {
                        val (h, m) = summary.time.split(":").map { it.toInt() }
                        TimePickerDialog(ctx, { _, hh, mm -> onSummary(summary.enabled, "%02d:%02d".format(hh, mm)) }, h, m, true).show()
                    }
                }
            }
            SettingCard("알림이 늦거나 오지 않을 때") {
                Text(
                    if (ignoring) "배터리 사용 제한에서 제외되어 있어요."
                    else "배터리 절전 때문에 알림이 늦을 수 있어요. 삼성 폰은 ‘배터리 > 백그라운드 사용 제한’에서 하루결을 제외해 주세요.",
                    style = HaruType.bodyS, color = c.textSub,
                )
                if (!ignoring) PillButton("제한 해제 요청", HabitPalettes[2].fill, HabitPalettes[2].onFill) {
                    ctx.startActivity(
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    val c = LocalHaru.current
    PopCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = HaruType.headline, color = c.text)
            content()
        }
    }
}
