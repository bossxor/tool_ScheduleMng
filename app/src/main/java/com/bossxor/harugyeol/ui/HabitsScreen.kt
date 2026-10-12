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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bossxor.harugyeol.AppData
import com.bossxor.harugyeol.habit.DayState
import com.bossxor.harugyeol.habit.GoalMode
import com.bossxor.harugyeol.habit.Habit
import com.bossxor.harugyeol.habit.MeasureType
import com.bossxor.harugyeol.habit.amountText
import com.bossxor.harugyeol.habit.counts
import com.bossxor.harugyeol.habit.dayState
import com.bossxor.harugyeol.habit.formatQuantity
import com.bossxor.harugyeol.habit.goalFor
import com.bossxor.harugyeol.habit.goalLabel
import com.bossxor.harugyeol.habit.isDone
import com.bossxor.harugyeol.habit.logOf
import com.bossxor.harugyeol.habit.parseMinutes
import com.bossxor.harugyeol.habit.parseQuantity
import com.bossxor.harugyeol.habit.weekAchievedDays
import com.bossxor.harugyeol.habit.weekStart
import com.bossxor.harugyeol.habit.weekTarget
import java.time.LocalDate

class HabitActions(
    val addAmount: (id: String, delta: Long) -> Unit,
    val setTotal: (id: String, amount: Long) -> Unit,
    val setSkipped: (id: String, skipped: Boolean) -> Unit,
)

@Composable
fun HabitsScreen(data: AppData, today: LocalDate, actions: HabitActions, onAdd: () -> Unit, onOpen: (String) -> Unit) {
    val c = LocalHaru.current
    val habits = data.habits.filter { it.archivedAt == null }.sortedBy { it.sortOrder }
    val states = habits.associate { it.id to it.dayState(today) }
    fun state(h: Habit) = states.getValue(h.id)

    val daily = habits.filter { state(it) in setOf(DayState.EMPTY, DayState.PARTIAL) && it.goalFor(today)?.mode != GoalMode.WEEKLY_COUNT }
    val weekly = habits.filter { state(it) in setOf(DayState.EMPTY, DayState.PARTIAL) && it.goalFor(today)?.mode == GoalMode.WEEKLY_COUNT }
    val finished = habits.filter { state(it).isDone || state(it) == DayState.SKIPPED }
    val rest = habits.filter { state(it) == DayState.REST }
    var restOpen by remember { mutableStateOf(false) }
    var dialogHabit by remember { mutableStateOf<Habit?>(null) }

    Box(Modifier.fillMaxSize().background(c.bg)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("하루결", style = HaruType.title, color = c.text)
            }
            item { SummaryCard(habits, states, today) }
            if (habits.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("첫 습관을 만들어 볼까요?", style = HaruType.headline, color = c.text)
                        PillButton("습관 추가", HabitPalettes[1].fill, Color.White, onClick = onAdd)
                    }
                }
            }
            section("오늘 할 습관", daily) { HabitCard(it, today, state(it), actions, { onOpen(it.id) }) { dialogHabit = it } }
            section("이번 주 목표", weekly) { HabitCard(it, today, state(it), actions, { onOpen(it.id) }) { dialogHabit = it } }
            section("끝낸 습관", finished) { HabitCard(it, today, state(it), actions, { onOpen(it.id) }) { dialogHabit = it } }
            if (rest.isNotEmpty()) {
                item {
                    Text(
                        (if (restOpen) "▾ " else "▸ ") + "오늘 쉬는 습관 (${rest.size})",
                        style = HaruType.headline, color = c.textSub,
                        modifier = Modifier.clickable { restOpen = !restOpen }.padding(vertical = 12.dp),
                    )
                }
                if (restOpen) items(rest, key = { it.id }) { HabitCard(it, today, DayState.REST, actions, { onOpen(it.id) }) {} }
            }
        }
        if (habits.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .size(56.dp)
                    .background(HabitPalettes[0].fill, CircleShape)
                    .border(2.dp, c.outline, CircleShape)
                    .clickable(onClickLabel = "습관 추가", onClick = onAdd),
                contentAlignment = Alignment.Center,
            ) { Text("＋", style = HaruType.title, color = Color.White) }
        }
    }

    dialogHabit?.let { h ->
        AmountDialog(h, today, onDismiss = { dialogHabit = null }) { amount ->
            actions.setTotal(h.id, amount)
            dialogHabit = null
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String, list: List<Habit>, card: @Composable (Habit) -> Unit,
) {
    if (list.isEmpty()) return
    item(key = "h-$title") { SectionTitle(title) }
    items(list, key = { it.id }) { card(it) }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = HaruType.headline, color = LocalHaru.current.text, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun SummaryCard(habits: List<Habit>, states: Map<String, DayState>, today: LocalDate) {
    val c = LocalHaru.current
    val counted = habits.filter { states.getValue(it.id).counts }
    val done = counted.count { states.getValue(it.id).isDone }
    PopCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("$done/${counted.size}", style = HaruType.display, color = c.text)
                Text(
                    if (counted.isEmpty()) "오늘은 쉬는 날이에요" else "오늘 달성",
                    style = HaruType.body, color = c.textSub, modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            WeekStrip(habits, today)
        }
    }
}

@Composable
private fun WeekStrip(habits: List<Habit>, today: LocalDate) {
    val c = LocalHaru.current
    val start = weekStart(today)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (i in 0..6) {
            val d = start.plusDays(i.toLong())
            val sts = habits.map { it.dayState(d) }.filter { it.counts }
            val allDone = sts.isNotEmpty() && sts.all { it.isDone }
            val someDone = sts.any { it.isDone || it == DayState.PARTIAL }
            val future = d.isAfter(today)
            val bg = when {
                allDone -> HabitPalettes[3].fill
                else -> c.surface
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("월화수목금토일"[i].toString(), style = HaruType.caption, color = c.textSub)
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .size(36.dp)
                        .background(bg, CircleShape)
                        .border(
                            if (d == today) 3.dp else if (sts.isEmpty() || future) 1.dp else 2.dp,
                            if (someDone || allDone || d == today) c.outline else c.dashed,
                            CircleShape,
                        )
                        .semantics {
                            contentDescription = "${d.monthValue}월 ${d.dayOfMonth}일, " +
                                if (allDone) "모두 달성" else if (sts.isEmpty()) "대상 없음" else "${sts.count { it.isDone }}/${sts.size} 달성"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        d.dayOfMonth.toString(), style = HaruType.number,
                        color = if (allDone) Color.White else if (future) c.textSub else c.text,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun HabitCard(h: Habit, today: LocalDate, state: DayState, actions: HabitActions, onOpen: () -> Unit, onInput: () -> Unit) {
    val c = LocalHaru.current
    val p = palette(h.colorIndex)
    val goal = h.goalFor(today)
    val log = h.logOf(today)
    val amount = log?.amount ?: 0
    val target = goal?.targetAmount ?: 1
    val progress = if (target > 0) (amount.toFloat() / target).coerceIn(0f, 1f) else 0f
    val fill = when {
        state.isDone -> c.achievedBg
        state == DayState.SKIPPED -> c.stripeBg
        else -> c.surface
    }
    val shadow = if (c.dark) p.darkTint else c.outline
    var menu by remember { mutableStateOf(false) }

    PopCard(Modifier.fillMaxWidth(), fill = fill, shadow = shadow, onClick = onOpen) {
        Row(
            Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Sticker(h.name, p, state, progress)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(h.name, style = HaruType.headline, color = c.text, maxLines = 2)
                if (state != DayState.REST && state != DayState.SKIPPED) {
                    ProgressBar(progress, p)
                    Text(
                        if (state.isDone && h.measureType != MeasureType.CHECK) h.amountText(amount, target) + " · 달성"
                        else if (state.isDone) "달성"
                        else h.amountText(amount, target),
                        style = HaruType.number, color = if (state.isDone) c.achievedText else p.accent(c),
                    )
                } else {
                    Text(
                        if (state == DayState.SKIPPED) "오늘은 쉬어요" else h.goalLabel() + " · 오늘은 대상이 아니에요",
                        style = HaruType.bodyS, color = c.textSub,
                    )
                }
            }
            if (state != DayState.REST) {
                Box(contentAlignment = Alignment.Center) {
                    RecordButton(h, state, p, actions, onInput)
                }
                Box {
                    Box(
                        Modifier.size(48.dp).clickable(onClickLabel = "더보기") { menu = true },
                        contentAlignment = Alignment.Center,
                    ) { Text("⋮", style = HaruType.headline, color = c.textSub) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        val canSkip = amount == 0L
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (state == DayState.SKIPPED) "건너뛰기 취소"
                                    else if (canSkip) "오늘 건너뛰기" else "기록이 있어 건너뛸 수 없어요",
                                    style = HaruType.body,
                                )
                            },
                            enabled = state == DayState.SKIPPED || canSkip,
                            onClick = { menu = false; actions.setSkipped(h.id, state != DayState.SKIPPED) },
                        )
                        DropdownMenuItem(
                            text = { Text("기록 초기화", style = HaruType.body) },
                            enabled = amount > 0,
                            onClick = { menu = false; actions.setTotal(h.id, 0) },
                        )
                    }
                }
            } else {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun RecordButton(h: Habit, state: DayState, p: HabitPalette, actions: HabitActions, onInput: () -> Unit) {
    val c = LocalHaru.current
    val done = state.isDone
    when (h.measureType) {
        MeasureType.CHECK -> Box(
            Modifier
                .size(48.dp)
                .background(if (done) HabitPalettes[3].fill else c.surface, CircleShape)
                .border(2.dp, c.outline, CircleShape)
                .clickable(onClickLabel = if (done) "${h.name} 취소" else "${h.name} 완료") {
                    actions.setTotal(h.id, if (done) 0 else 1)
                },
            contentAlignment = Alignment.Center,
        ) { Text(if (done) "✓" else "", style = HaruType.headline, color = Color.White) }

        MeasureType.QUANTITY -> PillButton(
            "＋1", p.fill, p.onFill,
            onLongClick = onInput,
            onClick = { actions.addAmount(h.id, 100) },
        )

        MeasureType.DURATION -> PillButton("기록", p.fill, p.onFill, onClick = onInput)
    }
}

@Composable
private fun ProgressBar(progress: Float, p: HabitPalette) {
    val c = LocalHaru.current
    val shape = RoundedCornerShape(6.dp)
    Box(Modifier.fillMaxWidth().height(10.dp).background(c.surface, shape).border(2.dp, c.outline, shape)) {
        if (progress > 0f) Box(Modifier.fillMaxWidth(progress).height(10.dp).background(p.fill, shape))
    }
}

@Composable
private fun AmountDialog(h: Habit, today: LocalDate, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val c = LocalHaru.current
    val current = h.logOf(today)?.amount ?: 0
    val initial = when (h.measureType) {
        MeasureType.QUANTITY -> if (current == 0L) "" else formatQuantity(current)
        else -> if (current == 0L) "" else (current / 60).toString()
    }
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val unit = if (h.measureType == MeasureType.DURATION) "분" else h.unit.orEmpty()

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        PopCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(h.name, style = HaruType.headline, color = c.text)
                Text(
                    if (h.measureType == MeasureType.DURATION) "오늘 기록한 시간을 분 단위로 입력해요"
                    else "오늘 기록한 양을 입력해요",
                    style = HaruType.bodyS, color = c.textSub,
                )
                HaruTextField(text, { text = it; error = null }, "0 $unit", Modifier.fillMaxWidth(), numeric = true)
                error?.let { Text(it, style = HaruType.bodyS, color = c.errorText) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    PillButton("취소", c.surface, c.text, Modifier.weight(1f), onClick = onDismiss)
                    PillButton("저장", HabitPalettes[1].fill, Color.White, Modifier.weight(1f)) {
                        val v = if (h.measureType == MeasureType.DURATION) parseMinutes(text) else parseQuantity(text)
                        if (v == null) error = "0 이상 소수 둘째 자리까지 입력해 주세요" else onSave(v)
                    }
                }
            }
        }
    }
}

// 주 목표 표시용 (카드 문구에서 사용 예정)
@Suppress("unused")
private fun Habit.weekProgress(date: LocalDate) = "${weekAchievedDays(date)}/${weekTarget(date)}"
