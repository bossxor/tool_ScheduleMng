package com.bossxor.harugyeol.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bossxor.harugyeol.habit.DayState
import com.bossxor.harugyeol.habit.GoalMode
import com.bossxor.harugyeol.habit.Habit
import com.bossxor.harugyeol.habit.MeasureType
import com.bossxor.harugyeol.habit.formatQuantity
import com.bossxor.harugyeol.habit.goalFor
import com.bossxor.harugyeol.habit.parseMinutes
import com.bossxor.harugyeol.habit.parseQuantity
import com.bossxor.harugyeol.habit.pendingGoal
import java.time.LocalDate

class NewHabit(
    val name: String, val colorIndex: Int, val type: MeasureType, val unit: String?,
    val mode: GoalMode, val weekdays: List<Int>?, val targetDays: Int?, val targetAmount: Long,
    val reminders: List<String>,
)

private val presets = listOf(
    Triple("물 마시기", MeasureType.QUANTITY, "잔" to "8"),
    Triple("독서", MeasureType.DURATION, "" to "30"),
    Triple("스트레칭", MeasureType.CHECK, "" to ""),
)

/** 습관 추가(initial == null)와 편집을 같은 화면으로 쓴다. 편집에서는 유형·단위를 바꿀 수 없다. */
@Composable
fun HabitFormScreen(
    initial: Habit?,
    today: LocalDate,
    onSave: (NewHabit) -> String?,
    onCancelPending: () -> Unit,
    onClose: () -> Unit,
) {
    val c = LocalHaru.current
    val ctx = LocalContext.current
    val editing = initial != null
    val g = initial?.goalFor(today)
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var color by remember { mutableIntStateOf(initial?.colorIndex ?: 1) }
    var type by remember { mutableStateOf(initial?.measureType ?: MeasureType.CHECK) }
    var unit by remember { mutableStateOf(initial?.unit ?: "잔") }
    var target by remember {
        mutableStateOf(
            when (initial?.measureType) {
                MeasureType.QUANTITY -> formatQuantity(g?.targetAmount ?: 0)
                MeasureType.DURATION -> ((g?.targetAmount ?: 0) / 60).toString()
                else -> ""
            }
        )
    }
    var mode by remember { mutableStateOf(g?.mode ?: GoalMode.DAILY) }
    var weekdays by remember { mutableStateOf(g?.weekdays?.toSet() ?: setOf(1, 2, 3, 4, 5)) }
    var targetDays by remember { mutableIntStateOf(g?.targetDays ?: 3) }
    var reminders by remember { mutableStateOf(initial?.reminders ?: emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    val p = palette(color)
    val pending = initial?.pendingGoal(today)

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clickable(onClickLabel = "닫기", onClick = onClose), contentAlignment = Alignment.Center) {
                Text("✕", style = HaruType.headline, color = c.text)
            }
            Text(if (editing) "습관 편집" else "습관 추가", style = HaruType.title, color = c.text, modifier = Modifier.weight(1f))
            PillButton("저장", HabitPalettes[1].fill, Color.White) {
                val amount: Long? = when (type) {
                    MeasureType.CHECK -> 1L
                    MeasureType.QUANTITY -> parseQuantity(target)
                    MeasureType.DURATION -> parseMinutes(target)
                }
                error = if (amount == null || amount <= 0) "목표량을 입력해 주세요"
                else onSave(
                    NewHabit(
                        name, color, type, if (type == MeasureType.QUANTITY) unit else null,
                        mode, if (mode == GoalMode.WEEKDAYS) weekdays.toList() else null,
                        if (mode == GoalMode.WEEKLY_COUNT) targetDays else null, amount, reminders,
                    )
                )
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Sticker(name.ifBlank { "습" }, p, DayState.ACHIEVED, 1f, 72.dp)
            }
            if (!editing) {
                Label("추천 습관")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (n, t, ut) ->
                        Chip(n, selected = false) {
                            name = n; type = t; unit = ut.first.ifEmpty { unit }; target = ut.second
                        }
                    }
                }
            }
            Label("이름")
            HaruTextField(name, { name = it }, "예: 물 마시기", Modifier.fillMaxWidth())
            Label("색")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HabitPalettes.forEachIndexed { i, hp ->
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(hp.fill, CircleShape)
                            .border(if (i == color) 4.dp else 2.dp, c.outline, CircleShape)
                            .clickable(onClickLabel = "색 선택") { color = i },
                        contentAlignment = Alignment.Center,
                    ) { if (i == color) Text("✓", style = HaruType.headline, color = hp.onFill) }
                }
            }
            Label("목표 유형")
            if (editing) {
                Text(
                    "유형과 단위는 바꿀 수 없어요. 다른 유형은 새 습관으로 만들어 주세요.",
                    style = HaruType.bodyS, color = c.textSub,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(MeasureType.CHECK to "완료", MeasureType.QUANTITY to "수량", MeasureType.DURATION to "시간").forEach { (t, label) ->
                    if (!editing || t == type) Chip(label, type == t) { if (!editing) type = t }
                }
            }
            if (type != MeasureType.CHECK) {
                Label(if (type == MeasureType.QUANTITY) "목표량과 단위" else "목표 시간(분)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaruTextField(target, { target = it }, if (type == MeasureType.QUANTITY) "8" else "30", Modifier.weight(1f), numeric = true)
                    if (type == MeasureType.QUANTITY) {
                        if (editing) Box(Modifier.weight(1f).heightIn(min = 56.dp), contentAlignment = Alignment.CenterStart) {
                            Text(unit, style = HaruType.headline, color = c.text)
                        } else HaruTextField(unit, { unit = it }, "단위", Modifier.weight(1f))
                    }
                }
            }
            Label("주기")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("매일", mode == GoalMode.DAILY) { mode = GoalMode.DAILY }
                Chip("요일 선택", mode == GoalMode.WEEKDAYS) { mode = GoalMode.WEEKDAYS }
                Chip("주 N일", mode == GoalMode.WEEKLY_COUNT) { mode = GoalMode.WEEKLY_COUNT }
            }
            if (mode == GoalMode.WEEKDAYS) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    "월화수목금토일".forEachIndexed { i, ch ->
                        val d = i + 1
                        val on = d in weekdays
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(if (on) p.fill else c.surface, CircleShape)
                                .border(2.dp, c.outline, CircleShape)
                                .clickable { weekdays = if (on) weekdays - d else weekdays + d },
                            contentAlignment = Alignment.Center,
                        ) { Text(ch.toString(), style = HaruType.button, color = if (on) p.onFill else c.text) }
                    }
                }
            }
            if (mode == GoalMode.WEEKLY_COUNT) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { n ->
                        val on = n == targetDays
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(if (on) p.fill else c.surface, CircleShape)
                                .border(2.dp, c.outline, CircleShape)
                                .clickable { targetDays = n },
                            contentAlignment = Alignment.Center,
                        ) { Text(n.toString(), style = HaruType.button, color = if (on) p.onFill else c.text) }
                    }
                }
                Text("주 ${targetDays}일 목표", style = HaruType.bodyS, color = c.textSub)
            }

            Label("알림 시각")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reminders.forEach { t ->
                    PillButton("$t ✕", c.surface, c.text) { reminders = reminders - t }
                }
                if (reminders.size < 5) PillButton("＋ 시각", p.fill, p.onFill) {
                    TimePickerDialog(ctx, { _, h, m ->
                        val t = "%02d:%02d".format(h, m)
                        if (t !in reminders) reminders = (reminders + t).sorted()
                    }, 9, 0, true).show()
                }
            }
            if (reminders.isEmpty()) Text("정한 시각에 알려 드려요. 최대 5개까지 추가할 수 있어요.", style = HaruType.bodyS, color = c.textSub)

            if (pending != null) {
                PopCard(Modifier.fillMaxWidth(), fill = c.stripeBg) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "${pending.effectiveFrom.monthValue}월 ${pending.effectiveFrom.dayOfMonth}일부터 목표가 바뀌어요",
                            style = HaruType.body, color = c.text,
                        )
                        PillButton("예약 취소", c.surface, c.text, onClick = onCancelPending)
                    }
                }
            }
            if (editing && initial?.hasRecordsNote() == true) {
                Text("기록이 있어서 목표 변경은 내일(주 목표는 다음 월요일)부터 적용돼요. 지난 기록은 그대로예요.", style = HaruType.bodyS, color = c.textSub)
            }
            error?.let { Text(it, style = HaruType.body, color = c.errorText) }
        }
    }
}

private fun Habit.hasRecordsNote() = logs.any { it.amount > 0 || it.skipped }

@Composable
private fun Label(text: String) {
    Text(text, style = HaruType.headline, color = LocalHaru.current.text)
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalHaru.current
    PillButton(text, if (selected) HabitPalettes[1].fill else c.surface, if (selected) Color.White else c.text, onClick = onClick)
}
