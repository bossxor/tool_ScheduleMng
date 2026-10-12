package com.bossxor.harugyeol.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bossxor.harugyeol.habit.DayState
import com.bossxor.harugyeol.habit.Habit
import com.bossxor.harugyeol.habit.MeasureType
import com.bossxor.harugyeol.habit.StreakUnit
import com.bossxor.harugyeol.habit.amountText
import com.bossxor.harugyeol.habit.dayState
import com.bossxor.harugyeol.habit.formatQuantity
import com.bossxor.harugyeol.habit.goalFor
import com.bossxor.harugyeol.habit.goalLabel
import com.bossxor.harugyeol.habit.isDone
import com.bossxor.harugyeol.habit.logOf
import com.bossxor.harugyeol.habit.monthRate
import com.bossxor.harugyeol.habit.parseMinutes
import com.bossxor.harugyeol.habit.parseQuantity
import com.bossxor.harugyeol.habit.skippedCount
import com.bossxor.harugyeol.habit.streak
import com.bossxor.harugyeol.habit.totalRate
import java.time.LocalDate
import java.time.YearMonth

class DetailActions(
    val setTotalAt: (id: String, date: LocalDate, amount: Long) -> Unit,
    val setSkippedAt: (id: String, date: LocalDate, skipped: Boolean) -> Unit,
)

@Composable
fun HabitDetailScreen(habit: Habit, today: LocalDate, actions: DetailActions, onBack: () -> Unit) {
    val c = LocalHaru.current
    val p = palette(habit.colorIndex)
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var showMonthRate by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<LocalDate?>(null) }

    val streak = habit.streak(today)
    val rate = if (showMonthRate) habit.monthRate(month, today) else habit.totalRate(today)
    val unit = if (streak.unit == StreakUnit.WEEK) "주" else "일"

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clickable(onClickLabel = "뒤로", onClick = onBack), contentAlignment = Alignment.Center) {
                Text("←", style = HaruType.headline, color = c.text)
            }
            Text(habit.name, style = HaruType.title, color = c.text, maxLines = 1, modifier = Modifier.weight(1f))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Sticker(habit.name, p, DayState.ACHIEVED, 1f, 72.dp)
                Column {
                    Text(habit.goalLabel(), style = HaruType.headline, color = c.text)
                    Text("${habit.startDate.monthValue}월 ${habit.startDate.dayOfMonth}일부터", style = HaruType.bodyS, color = c.textSub)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("현재 연속", "${streak.current}$unit", Modifier.weight(1f))
                StatCard("최장 연속", "${streak.longest}$unit", Modifier.weight(1f))
                StatCard(
                    if (showMonthRate) "이번 달 달성률" else "전체 달성률",
                    rate?.let { "${it.percent}%" } ?: "–",
                    Modifier.weight(1f),
                    onClick = { showMonthRate = !showMonthRate },
                )
            }
            if (rate == null) Text("집계할 기록이 아직 없어요", style = HaruType.bodyS, color = c.textSub)
            val skipped = if (showMonthRate) habit.skippedCount(month.atDay(1), month.atEndOfMonth())
            else habit.skippedCount(habit.startDate, today)
            if (skipped > 0) Text("이 기간 건너뛴 날 ${skipped}일", style = HaruType.bodyS, color = c.textSub)

            PopCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MonthHeader(month, habit, today) { month = it }
                    MonthGrid(habit, p, month, today) { editing = it }
                }
            }
            Text("날짜를 누르면 기록을 고치거나 건너뛸 수 있어요", style = HaruType.bodyS, color = c.textSub)
        }
    }

    editing?.let { date ->
        DayEditDialog(habit, date, onDismiss = { editing = null }, actions = actions)
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, onClick: (() -> Unit)? = null) {
    val c = LocalHaru.current
    PopCard(modifier, onClick = onClick) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, style = HaruType.title, color = c.text)
            Text(label, style = HaruType.caption, color = c.textSub)
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, habit: Habit, today: LocalDate, onChange: (YearMonth) -> Unit) {
    val c = LocalHaru.current
    val canPrev = month > YearMonth.from(habit.startDate)
    val canNext = month < YearMonth.from(today)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clickable(enabled = canPrev, onClickLabel = "이전 달") { onChange(month.minusMonths(1)) },
            contentAlignment = Alignment.Center,
        ) { Text("◀", style = HaruType.button, color = if (canPrev) c.text else c.stripe) }
        Text(
            "${month.year}년 ${month.monthValue}월", style = HaruType.headline, color = c.text,
            textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.size(48.dp).clickable(enabled = canNext, onClickLabel = "다음 달") { onChange(month.plusMonths(1)) },
            contentAlignment = Alignment.Center,
        ) { Text("▶", style = HaruType.button, color = if (canNext) c.text else c.stripe) }
    }
}

@Composable
private fun MonthGrid(habit: Habit, p: HabitPalette, month: YearMonth, today: LocalDate, onPick: (LocalDate) -> Unit) {
    val c = LocalHaru.current
    Row(Modifier.fillMaxWidth()) {
        "월화수목금토일".forEach {
            Text(it.toString(), style = HaruType.caption, color = c.textSub, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        }
    }
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1
    val cells = lead + month.lengthOfMonth()
    val rows = (cells + 6) / 7
    for (r in 0 until rows) {
        Row(Modifier.fillMaxWidth()) {
            for (col in 0..6) {
                val idx = r * 7 + col - lead
                if (idx < 0 || idx >= month.lengthOfMonth()) {
                    Spacer(Modifier.weight(1f).height(60.dp))
                } else {
                    val date = month.atDay(idx + 1)
                    val tappable = !date.isAfter(today) && !date.isBefore(habit.startDate)
                    DayCell(habit, p, date, today, tappable, Modifier.weight(1f)) { onPick(date) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(habit: Habit, p: HabitPalette, date: LocalDate, today: LocalDate, tappable: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalHaru.current
    val inRange = !date.isBefore(habit.startDate) && !date.isAfter(today)
    val state = if (inRange) habit.dayState(date) else DayState.REST
    val goal = habit.goalFor(date)
    val amount = habit.logOf(date)?.amount ?: 0
    val progress = if (goal != null && goal.targetAmount > 0) (amount.toFloat() / goal.targetAmount).coerceIn(0f, 1f) else 0f
    Column(
        modifier
            .height(60.dp)
            .clickable(enabled = tappable, onClickLabel = "${date.monthValue}월 ${date.dayOfMonth}일 기록") { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            date.dayOfMonth.toString(), style = HaruType.caption,
            color = if (date == today) c.text else c.textSub,
        )
        if (inRange && state != DayState.REST) {
            Sticker(if (state == DayState.SKIPPED) "" else habit.name, p, state, progress, 34.dp)
        } else {
            Box(Modifier.size(34.dp))
        }
    }
}

@Composable
private fun DayEditDialog(habit: Habit, date: LocalDate, onDismiss: () -> Unit, actions: DetailActions) {
    val c = LocalHaru.current
    val p = palette(habit.colorIndex)
    val log = habit.logOf(date)
    val amount = log?.amount ?: 0
    val goal = habit.goalFor(date)
    val skipped = log?.skipped == true
    val initial = when (habit.measureType) {
        MeasureType.QUANTITY -> if (amount == 0L) "" else formatQuantity(amount)
        MeasureType.DURATION -> if (amount == 0L) "" else (amount / 60).toString()
        MeasureType.CHECK -> ""
    }
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val unit = if (habit.measureType == MeasureType.DURATION) "분" else habit.unit.orEmpty()

    Dialog(onDismissRequest = onDismiss) {
        PopCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${date.monthValue}월 ${date.dayOfMonth}일", style = HaruType.headline, color = c.text)
                if (goal != null) {
                    Text(
                        "당시 목표 " + habit.amountText(goal.targetAmount, goal.targetAmount).substringAfter('/').ifEmpty { "완료" },
                        style = HaruType.bodyS, color = c.textSub,
                    )
                }
                if (skipped) Text("이 날은 건너뛰었어요", style = HaruType.body, color = c.textSub)
                if (habit.measureType == MeasureType.CHECK) {
                    val done = amount >= 1
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PillButton(
                            if (done) "완료 취소" else "완료로 기록",
                            if (done) c.surface else HabitPalettes[3].fill,
                            if (done) c.text else Color.White,
                            Modifier.weight(1f),
                        ) { actions.setTotalAt(habit.id, date, if (done) 0 else 1); onDismiss() }
                    }
                } else {
                    HaruTextField(text, { text = it; error = null }, "0 $unit", Modifier.fillMaxWidth(), numeric = true)
                    error?.let { Text(it, style = HaruType.bodyS, color = c.errorText) }
                    PillButton("저장", p.fill, p.onFill, Modifier.fillMaxWidth()) {
                        val v = if (habit.measureType == MeasureType.DURATION) parseMinutes(text) else parseQuantity(text)
                        if (v == null) error = "0 이상 소수 둘째 자리까지 입력해 주세요"
                        else { actions.setTotalAt(habit.id, date, v); onDismiss() }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (skipped || amount == 0L) PillButton(
                        if (skipped) "건너뛰기 취소" else "건너뛰기", c.surface, c.text, Modifier.weight(1f),
                    ) { actions.setSkippedAt(habit.id, date, !skipped); onDismiss() }
                    PillButton("닫기", c.surface, c.text, Modifier.weight(1f), onClick = onDismiss)
                }
            }
        }
    }
}
