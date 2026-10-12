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
    val ignoring = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)

    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("설정", style = HaruType.title, color = c.text)

        SettingCard("알림") {
            Text(if (notificationsAllowed) "알림이 허용돼 있어요" else "알림이 꺼져 있어요. 허용해야 알림을 받을 수 있어요.", style = HaruType.body, color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!notificationsAllowed) PillButton("권한 요청", HabitPalettes[1].fill, Color.White, onClick = actions.requestNotifications)
                PillButton("테스트 알림 보내기", c.surface, c.text, onClick = actions.testNotification)
            }
        }

        SettingCard("하루 요약") {
            Text(
                if (summary.enabled) "매일 ${summary.time}에 오늘 달성한 습관과 남은 습관을 알려 드려요."
                else "지금은 꺼져 있어요. 켜면 정한 시각에 한 번 알려 드려요.",
                style = HaruType.body, color = c.text,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        SettingCard("데이터") {
            Text("백업 파일은 암호화되지 않아요. 개인 기록이 그대로 들어 있으니 보관 위치에 주의해 주세요.", style = HaruType.bodyS, color = c.textSub)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        SettingCard("오픈소스 라이선스") {
            Text(
                "글자체: 주아(Jua, SIL OFL 1.1), Pretendard(SIL OFL 1.1). 자세한 내용은 저장소의 assets/fonts 폴더에 있어요.",
                style = HaruType.bodyS, color = c.textSub,
            )
        }
    }
}
