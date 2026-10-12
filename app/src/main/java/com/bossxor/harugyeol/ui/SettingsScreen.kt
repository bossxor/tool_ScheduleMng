package com.bossxor.harugyeol.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bossxor.harugyeol.AppData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActions(
    val setTheme: (String) -> Unit,
    val setHaptics: (Boolean) -> Unit,
    val about: () -> Unit,
    val addWidget: () -> Unit,
    val setAllDayTime: (String) -> Unit,
    val setSummary: (enabled: Boolean, time: String) -> Unit,
    val requestNotifications: () -> Unit,
    val testNotification: () -> Unit,
    val backup: () -> Unit,
    val restore: () -> Unit,
    val restoreSnapshot: () -> Unit,
    val checkUpdate: () -> Unit,
)

@Composable
fun SettingsScreen(
    data: AppData, notificationsAllowed: Boolean, snapshotTime: Long?, versionName: String, updateStatus: String,
    actions: SettingsActions,
) {
    val c = LocalHaru.current
    val ctx = LocalContext.current
    val summary = data.settings.dailySummary
    val now = java.time.LocalDateTime.now()
    val pastCount = com.bossxor.harugyeol.reminders.taskAlarmPlans(data, System.currentTimeMillis(), futureOnly = false).count { it.at < System.currentTimeMillis() }
    val habitPast = data.habits.count { com.bossxor.harugyeol.reminders.shouldNotifyHabit(it, now.toLocalDate()) && it.reminders.any { t -> java.time.LocalTime.parse(t).isBefore(now.toLocalTime()) } }
    val ignoring = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("설정", style = HaruType.title, color = c.text)

        SettingCard("화면·달성 효과") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SYSTEM" to "시스템", "LIGHT" to "밝게", "DARK" to "어둡게").forEach { (theme, label) ->
                    PillButton(label, if (data.settings.theme == theme) HabitPalettes[1].fill else c.surface, if (data.settings.theme == theme) Color.White else c.text) { actions.setTheme(theme) }
                }
            }
            PillButton(if (data.settings.celebrationHaptics) "달성 진동 켜짐" else "달성 진동 꺼짐", c.surface, c.text) { actions.setHaptics(!data.settings.celebrationHaptics) }
        }

        SettingCard("알림") {
            if (pastCount + habitPast > 0) Text("알림 시각이 지난 미완료 항목이 있어요. 발송 여부는 확인할 수 없으니 오늘 화면에서 기록을 확인해 주세요.", style = HaruType.bodyS, color = c.textSub)
            val exact = ctx.getSystemService(android.app.AlarmManager::class.java).canScheduleExactAlarms()
            if (!exact) Text("정확한 알람이 제한돼 있어 알림이 지연될 수 있어요. Android 알람 권한을 확인해 주세요.", style = HaruType.bodyS, color = c.errorText)
            Text(if (notificationsAllowed) "알림이 허용돼 있어요" else "알림이 꺼져 있어요. 허용해야 알림을 받을 수 있어요.", style = HaruType.body, color = c.text)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!notificationsAllowed) PillButton("권한 요청", HabitPalettes[1].fill, Color.White, onClick = actions.requestNotifications)
                PillButton("테스트 알림 보내기", c.surface, c.text, onClick = actions.testNotification)
            }
        }

        SettingCard("종일 작업 알림") {
            PillButton("시각 ${data.settings.allDayReminderTime}", c.surface, c.text) {
                val value = java.time.LocalTime.parse(data.settings.allDayReminderTime)
                TimePickerDialog(ctx, { _, h, m -> actions.setAllDayTime(java.lang.String.format(java.util.Locale.ROOT, "%02d:%02d", h, m)) }, value.hour, value.minute, true).show()
            }
        }

        SettingCard("하루 요약") {
            Text(
                if (summary.enabled) "매일 ${summary.time}에 오늘 달성한 습관과 남은 습관을 알려 드려요."
                else "지금은 꺼져 있어요. 켜면 정한 시각에 한 번 알려 드려요.",
                style = HaruType.body, color = c.text,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(
                    if (summary.enabled) "끄기" else "켜기",
                    if (summary.enabled) c.surface else HabitPalettes[3].fill,
                    if (summary.enabled) c.text else Color.White,
                ) {
                    if (!summary.enabled && !notificationsAllowed) actions.requestNotifications()
                    actions.setSummary(!summary.enabled, summary.time)
                }
                PillButton("시각 ${summary.time} 바꾸기", c.surface, c.text) {
                    val (h, m) = summary.time.split(":").map { it.toInt() }
                    // 시각을 고르면 요약을 함께 켠다
                    TimePickerDialog(ctx, { _, hh, mm ->
                        if (!notificationsAllowed) actions.requestNotifications()
                        actions.setSummary(true, "%02d:%02d".format(hh, mm))
                    }, h, m, true).show()
                }
            }
        }

        SettingCard("위젯·잠금화면") {
            Text("위젯에는 오늘 요약의 개수만 표시해요. 잠금화면 위젯 추가는 기기의 지원 여부에 따라 달라요. 알림의 완료·미루기는 잠금 해제 후 실행돼요.", style = HaruType.bodyS, color = c.textSub)
            PillButton("홈 화면 위젯 추가", c.surface, c.text, onClick = actions.addWidget)
        }

        SettingCard("데이터") {
            Text("백업 파일은 암호화되지 않아요. 개인 기록이 그대로 들어 있으니 보관 위치에 주의해 주세요.", style = HaruType.bodyS, color = c.textSub)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("백업 저장", HabitPalettes[1].fill, Color.White, onClick = actions.backup)
                PillButton("백업에서 복원", c.surface, c.text, onClick = actions.restore)
            }
            val snap = snapshotTime?.let { SimpleDateFormat("M월 d일 HH:mm", Locale.KOREA).format(Date(it)) }
            if (snap != null) {
                Text("복원이나 업데이트 전의 데이터가 $snap 에 보관돼 있어요.", style = HaruType.bodyS, color = c.textSub)
                PillButton("스냅샷으로 되돌리기", c.errorBg, c.errorText, onClick = actions.restoreSnapshot)
            }
        }

        SettingCard("앱 업데이트") {
            Text("현재 버전 $versionName", style = HaruType.body, color = c.text)
            if (updateStatus.isNotEmpty()) Text(updateStatus, style = HaruType.bodyS, color = c.textSub)
            PillButton("지금 확인", c.surface, c.text, onClick = actions.checkUpdate)
        }

        SettingCard("알림이 늦거나 오지 않을 때") {
            Text(
                if (ignoring) "배터리 사용 제한에서 제외되어 있어요."
                else "배터리 절전 때문에 알림이 늦을 수 있어요. 삼성 폰은 ‘배터리 > 백그라운드 사용 제한’에서 하루결을 제외해 주세요.",
                style = HaruType.bodyS, color = c.textSub,
            )
            if (!ignoring) PillButton("제한 해제 요청", HabitPalettes[2].fill, HabitPalettes[2].onFill) {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
            }
        }

        SettingCard("앱 정보·라이선스") {
            PillButton("앱 정보·허가문 보기", c.surface, c.text, onClick = actions.about)
            Text(
                "글자체와 그림의 출처·허가문을 앱 안에서 확인할 수 있어요.",
                style = HaruType.bodyS, color = c.textSub,
            )
        }
    }
}
