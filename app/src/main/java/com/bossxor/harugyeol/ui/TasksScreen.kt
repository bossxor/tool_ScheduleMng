package com.bossxor.harugyeol.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bossxor.harugyeol.habit.*
import androidx.compose.runtime.LaunchedEffect
import com.bossxor.harugyeol.AppData
import com.bossxor.harugyeol.task.inboxTasks
import com.bossxor.harugyeol.task.searchTasks
import com.bossxor.harugyeol.task.scheduledDates
import com.bossxor.harugyeol.task.Task
import com.bossxor.harugyeol.task.allOpenTasks
import com.bossxor.harugyeol.task.doneTasks
import com.bossxor.harugyeol.task.tasksOn
import com.bossxor.harugyeol.task.todayTasks
import com.bossxor.harugyeol.task.trashedTasks
import com.bossxor.harugyeol.task.upcomingTasks
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

fun dueLabel(date: LocalDate, today: LocalDate): String = when {
    date == today -> "오늘"
    date == today.plusDays(1) -> "내일"
    date.isBefore(today) -> "${ChronoUnit.DAYS.between(date, today)}일 지남"
    else -> "${date.monthValue}월 ${date.dayOfMonth}일"
}

private val priorityMark = listOf("", "!", "!!", "!!!")
private val priorityName = listOf("없음", "낮음", "중간", "높음")

@Composable
private fun TaskRow(t: Task, today: LocalDate, onToggle: (Boolean) -> Unit, onOpen: () -> Unit, canToggle: Boolean = true) {
    val c = LocalHaru.current
    val done = t.completedAt != null
    PopCard(Modifier.fillMaxWidth(), fill = if (done) c.stripeBg else c.surface, onClick = onOpen) {
        Row(
            Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(48.dp).clickable(enabled = canToggle, onClickLabel = if (done) "완료 취소" else "완료") { onToggle(!done) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(if (done) HabitPalettes[3].fill else c.surface, CircleShape)
                        .border(1.dp, c.outline, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (done) Text("✓", style = HaruType.caption, color = Color.White) }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    t.title, style = HaruType.body, maxLines = 2,
                    color = if (done) c.textSub else c.text,
                    textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                )
                val sub = buildList {
                    t.dueDate?.let { add(dueLabel(it, today) + (t.dueTime?.let { time -> " $time" } ?: "")) }
                    if (t.recurrence != null) add("반복")
                    if (t.checklist.isNotEmpty()) add("체크 ${t.checklist.count { it.isDone }}/${t.checklist.size}")
                    if (t.priority > 0) add("우선순위 ${priorityName[t.priority]}")
                }
                if (sub.isNotEmpty()) {
                    val overdue = t.dueDate?.isBefore(today) == true && !done
                    Text(sub.joinToString(" · "), style = HaruType.bodyS, color = if (overdue) c.errorText else c.textSub)
                }
            }
            if (t.priority > 0) Text(priorityMark[t.priority], style = HaruType.headline, color = HabitPalettes[0].fill)
        }
    }
}

@Composable
fun TasksScreen(
    data: AppData, today: LocalDate,
    onAdd: () -> Unit, onOpen: (String) -> Unit, onToggle: (String, Boolean) -> Unit, onTrash: () -> Unit, onManage: () -> Unit,
) {
    val c = LocalHaru.current
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var includeCompleted by rememberSaveable { mutableStateOf(false) }
    val names = listOf("오늘", "예정", "받은함", "전체", "완료")
    var selectedList by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedTag by rememberSaveable { mutableStateOf<String?>(null) }
    val baseList = if (query.isNotBlank()) data.searchTasks(query, includeCompleted) else when (filter) {
        0 -> data.todayTasks(today)
        1 -> data.upcomingTasks(today)
        2 -> data.inboxTasks()
        3 -> data.allOpenTasks()
        else -> data.doneTasks()
    }
    val list = baseList.filter { (selectedList == null || it.listId == selectedList) && (selectedTag == null || selectedTag in it.tagIds) }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("할 일", style = HaruType.title, color = c.text, modifier = Modifier.weight(1f))
                PillButton("분류", c.surface, c.text, onClick = onManage)
                Box(Modifier.height(48.dp).clickable(onClick = onTrash).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text("휴지통", style = HaruType.body, color = c.textSub)
                }
            }
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEachIndexed { i, n ->
                    PillButton(n, if (filter == i) HabitPalettes[1].fill else c.surface, if (filter == i) Color.White else c.text) { filter = i }
                }
            }
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PillButton("모든 목록", c.surface, c.text) { selectedList = null }
                data.lists.forEach { category -> PillButton(category.name, if (selectedList == category.id) HabitPalettes[1].fill else c.surface, if (selectedList == category.id) Color.White else c.text) { selectedList = category.id } }
                data.tags.forEach { tag -> PillButton("#${tag.name}", if (selectedTag == tag.id) HabitPalettes[2].fill else c.surface, if (selectedTag == tag.id) HabitPalettes[2].onFill else c.text) { selectedTag = if (selectedTag == tag.id) null else tag.id } }
            }
            HaruTextField(query, { query = it }, "제목·메모 검색", Modifier.fillMaxWidth().padding(16.dp, 8.dp))
            if (query.isNotBlank()) {
                PillButton(if (includeCompleted) "완료 포함 ✓" else "완료 포함", c.surface, c.text, Modifier.padding(horizontal = 16.dp)) { includeCompleted = !includeCompleted }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (list.isEmpty()) item {
                    Text(
                        if (query.isNotBlank()) "검색 결과가 없어요" else when (filter) { 0 -> "오늘 할 일이 없어요"; 1 -> "예정된 일이 없어요"; 2 -> "받은함이 비어 있어요"; 3 -> "할 일이 없어요"; else -> "완료한 일이 없어요" },
                        style = HaruType.headline, color = c.textSub, modifier = Modifier.padding(top = 32.dp),
                    )
                }
                if (filter == 0 && query.isBlank()) {
                    val overdue = list.filter { it.dueDate?.isBefore(today) == true }
                    val current = list.filter { it.dueDate == today }
                    if (overdue.isNotEmpty()) item { Text("지난 할 일", style = HaruType.headline, color = c.errorText) }
                    items(overdue, key = { "overdue:${it.id}" }) { t -> TaskRow(t, today, { onToggle(t.id, it) }, { onOpen(t.id) }) }
                    if (current.isNotEmpty()) item { Text("오늘 할 일", style = HaruType.headline, color = c.text) }
                    items(current, key = { "today:${it.id}" }) { t -> TaskRow(t, today, { onToggle(t.id, it) }, { onOpen(t.id) }) }
                } else items(list, key = { "${it.id}:${it.dueDate}:${it.completedAt}" }) { t ->
                    TaskRow(t, today, { onToggle(t.id, it) }, { onOpen(t.id) }, canToggle = t.recurrence == null || (t.completedAt == null && data.tasks.firstOrNull { it.id == t.id }?.dueDate == t.dueDate))
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(20.dp).size(56.dp)
                .background(HabitPalettes[1].fill, CircleShape).border(1.dp, c.outline, CircleShape)
                .clickable(onClickLabel = "할 일 추가", onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Text("＋", style = HaruType.title, color = Color.White) }
    }
}

@Composable
fun TrashScreen(
    data: AppData, onBack: () -> Unit, onRestore: (String) -> Unit, onDelete: (String) -> Unit, onEmpty: () -> Unit,
) {
    val c = LocalHaru.current
    val list = data.trashedTasks()
    var deleteId by remember { mutableStateOf<String?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    if (deleteId != null || confirmEmpty) {
        AlertDialog(
            onDismissRequest = { deleteId = null; confirmEmpty = false },
            title = { Text(if (confirmEmpty) "휴지통을 비울까요?" else "영구 삭제할까요?") },
            text = { Text("삭제한 할 일은 복구할 수 없어요.") },
            confirmButton = {
                TextButton(onClick = {
                    if (confirmEmpty) onEmpty() else deleteId?.let(onDelete)
                    deleteId = null; confirmEmpty = false
                }) { Text("영구 삭제") }
            },
            dismissButton = { TextButton(onClick = { deleteId = null; confirmEmpty = false }) { Text("취소") } },
        )
    }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("휴지통", onBack) {
            if (list.isNotEmpty()) PillButton("비우기", c.errorBg, c.errorText, onClick = { confirmEmpty = true })
        }
        LazyColumn(
            Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (list.isEmpty()) item { Text("휴지통이 비어 있어요", style = HaruType.headline, color = c.textSub) }
            items(list, key = { "${it.id}:${it.dueDate}:${it.completedAt}" }) { t ->
                PopCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(t.title, style = HaruType.body, color = c.text, maxLines = 2)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            PillButton("복구", HabitPalettes[3].fill, Color.White, Modifier.weight(1f)) { onRestore(t.id) }
                            PillButton("영구 삭제", c.errorBg, c.errorText, Modifier.weight(1f)) { deleteId = t.id }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarScreen(
    data: AppData, today: LocalDate,
    initialDate: LocalDate? = null, onDateConsumed: () -> Unit = {}, onHabit: (String, LocalDate) -> Unit = { _, _ -> },
    onAddOn: (LocalDate) -> Unit, onOpen: (String) -> Unit, onToggle: (String, Boolean) -> Unit,
) {
    val c = LocalHaru.current
    var month by rememberSaveable(stateSaver = Saver<YearMonth, String>(save = { it.toString() }, restore = { YearMonth.parse(it) })) { mutableStateOf(YearMonth.from(today)) }
    var selected by rememberSaveable(stateSaver = Saver<LocalDate, String>(save = { it.toString() }, restore = { LocalDate.parse(it) })) { mutableStateOf(today) }
    LaunchedEffect(initialDate) { initialDate?.let { selected = it; month = YearMonth.from(it); onDateConsumed() } }
    val habitDay = habitsOnDate(data.habits, selected, today)
    val counts = data.tasks.filter { it.deletedAt == null }.flatMap { task ->
        if (task.recurrence == null) listOfNotNull(task.dueDate)
        else task.scheduledDates(month.atDay(1), month.atEndOfMonth()) + task.occurrences.map { it.scheduledDate }
    }.groupingBy { it }.eachCount()
    val day = data.tasksOn(selected)

    Column(Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("캘린더", style = HaruType.title, color = c.text)
        PopCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(48.dp).clickable(onClickLabel = "이전 달") { month = month.minusMonths(1) }, contentAlignment = Alignment.Center) {
                        Text("◀", style = HaruType.button, color = c.text)
                    }
                    Text("${month.year}년 ${month.monthValue}월", style = HaruType.headline, color = c.text, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    Box(Modifier.size(48.dp).clickable(onClickLabel = "다음 달") { month = month.plusMonths(1) }, contentAlignment = Alignment.Center) {
                        Text("▶", style = HaruType.button, color = c.text)
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    "월화수목금토일".forEach {
                        Text(it.toString(), style = HaruType.caption, color = c.textSub, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
                val lead = month.atDay(1).dayOfWeek.value - 1
                val rows = (lead + month.lengthOfMonth() + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (col in 0..6) {
                            val idx = r * 7 + col - lead
                            if (idx < 0 || idx >= month.lengthOfMonth()) {
                                Box(Modifier.weight(1f).height(52.dp))
                            } else {
                                val date = month.atDay(idx + 1)
                                val n = counts[date] ?: 0
                                val sel = date == selected
                                Column(
                                    Modifier.weight(1f).height(52.dp)
                                        .background(if (sel) HabitPalettes[1].tintBg(c) else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                        .border(if (date == today) 2.dp else 0.dp, if (date == today) c.outline else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                        .clickable(onClickLabel = "${date.monthValue}월 ${date.dayOfMonth}일") { selected = date },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(date.dayOfMonth.toString(), style = HaruType.number, color = if (sel) HabitPalettes[1].accent(c) else c.text)
                                    if (n > 0) Text(if (n > 3) "●●●+" else "●".repeat(n), style = HaruType.caption, color = HabitPalettes[1].fill)
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("어제", c.surface, c.text) { selected = today.minusDays(1); month = YearMonth.from(selected) }
            PillButton("오늘", c.surface, c.text) { selected = today; month = YearMonth.from(today) }
        }
        Text("${selected.monthValue}월 ${selected.dayOfMonth}일 습관 기록", style = HaruType.headline, color = c.text)
        if (selected.isAfter(today)) Text("미래 날짜에는 아직 습관 기록이 없어요", style = HaruType.body, color = c.textSub)
        else if (habitDay.isEmpty()) Text("이 날의 대상 습관이나 기록이 없어요", style = HaruType.body, color = c.textSub)
        habitDay.forEach { h ->
            val state = h.dayState(selected)
            val amount = h.logOf(selected)?.amount ?: 0
            val target = h.goalFor(selected)?.targetAmount ?: 1
            PopCard(Modifier.fillMaxWidth(), onClick = { onHabit(h.id, selected) }) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Sticker(h.name, palette(h.colorIndex), state, (amount.toFloat() / target).coerceIn(0f, 1f), iconKey = h.iconKey)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(h.name + if (h.archivedAt != null) " · 보관됨" else "", style = HaruType.headline, color = c.text)
                        Text(if (state == DayState.SKIPPED) "건너뛴 날" else h.amountText(amount, target) + if (state.isDone) " · 달성" else "", style = HaruType.body, color = c.text)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${selected.monthValue}월 ${selected.dayOfMonth}일 할 일", style = HaruType.headline, color = c.text,
                modifier = Modifier.weight(1f),
            )
            PillButton("추가", HabitPalettes[1].fill, Color.White) { onAddOn(selected) }
        }
        if (day.isEmpty()) Text("이 날의 할 일이 없어요", style = HaruType.body, color = c.textSub)
        day.forEach { t -> TaskRow(t, today, { onToggle(t.id, it) }, { onOpen(t.id) }, canToggle = t.recurrence == null || (t.completedAt == null && data.tasks.firstOrNull { it.id == t.id }?.dueDate == t.dueDate)) }
    }
}
