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
import com.bossxor.harugyeol.AppData
import com.bossxor.harugyeol.task.searchTasks
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
private fun TaskRow(t: Task, today: LocalDate, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    val c = LocalHaru.current
    val done = t.completedAt != null
    PopCard(Modifier.fillMaxWidth(), fill = if (done) c.stripeBg else c.surface, onClick = onOpen) {
        Row(
            Modifier.padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                Modifier.size(48.dp).clickable(onClickLabel = if (done) "완료 취소" else "완료") { onToggle(!done) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .background(if (done) HabitPalettes[3].fill else c.surface, CircleShape)
                        .border(2.dp, c.outline, CircleShape),
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
                    t.dueDate?.let { add(dueLabel(it, today)) }
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
    onAdd: () -> Unit, onOpen: (String) -> Unit, onToggle: (String, Boolean) -> Unit, onTrash: () -> Unit,
) {
    val c = LocalHaru.current
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var includeCompleted by rememberSaveable { mutableStateOf(false) }
    val names = listOf("오늘", "예정", "전체", "완료")
    val list = if (query.isNotBlank()) data.searchTasks(query, includeCompleted) else when (filter) {
        0 -> data.todayTasks(today)
        1 -> data.upcomingTasks(today)
        2 -> data.allOpenTasks()
        else -> data.doneTasks()
    }
    Box(Modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("할 일", style = HaruType.title, color = c.text, modifier = Modifier.weight(1f))
                Box(Modifier.height(48.dp).clickable(onClick = onTrash).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    Text("휴지통", style = HaruType.body, color = c.textSub)
                }
            }
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                names.forEachIndexed { i, n ->
                    PillButton(n, if (filter == i) HabitPalettes[1].fill else c.surface, if (filter == i) Color.White else c.text) { filter = i }
                }
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
                        if (query.isNotBlank()) "검색 결과가 없어요" else when (filter) { 0 -> "오늘 할 일이 없어요"; 1 -> "예정된 일이 없어요"; 2 -> "할 일이 없어요"; else -> "완료한 일이 없어요" },
                        style = HaruType.headline, color = c.textSub, modifier = Modifier.padding(top = 32.dp),
                    )
                }
                items(list, key = { it.id }) { t ->
                    TaskRow(t, today, { onToggle(t.id, it) }, { onOpen(t.id) })
                }
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(20.dp).size(56.dp)
                .background(HabitPalettes[1].fill, CircleShape).border(2.dp, c.outline, CircleShape)
                .clickable(onClickLabel = "할 일 추가", onClick = onAdd),
            contentAlignment = Alignment.Center,
        ) { Text("＋", style = HaruType.title, color = Color.White) }
    }
}

/** 빠른 추가와 편집을 같은 화면으로 쓴다 */
@Composable
fun TaskFormScreen(
    initial: Task?, defaultDate: LocalDate?, today: LocalDate,
    onSave: (title: String, note: String, priority: Int, due: LocalDate?) -> String?,
    onTrash: () -> Unit, onClose: () -> Unit,
) {
    val c = LocalHaru.current
    val ctx = LocalContext.current
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var priority by remember { mutableIntStateOf(initial?.priority ?: 0) }
    var due by remember { mutableStateOf(initial?.dueDate ?: defaultDate) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(if (initial == null) "할 일 추가" else "할 일 편집", onClose) {
            PillButton("저장", HabitPalettes[1].fill, Color.White) { error = onSave(title, note, priority, due) }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("제목", style = HaruType.headline, color = c.text)
            HaruTextField(title, { title = it }, "예: 병원 예약하기", Modifier.fillMaxWidth())
            Text("날짜", style = HaruType.headline, color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("오늘", if (due == today) HabitPalettes[1].fill else c.surface, if (due == today) Color.White else c.text) { due = today }
                PillButton("내일", if (due == today.plusDays(1)) HabitPalettes[1].fill else c.surface, if (due == today.plusDays(1)) Color.White else c.text) { due = today.plusDays(1) }
                PillButton("없음", if (due == null) HabitPalettes[1].fill else c.surface, if (due == null) Color.White else c.text) { due = null }
            }
            PillButton(due?.let { "${it.year}년 ${it.monthValue}월 ${it.dayOfMonth}일" } ?: "날짜 선택", c.surface, c.text) {
                val d = due ?: today
                DatePickerDialog(ctx, { _, y, m, day -> due = LocalDate.of(y, m + 1, day) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
            }
            Text("우선순위", style = HaruType.headline, color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                priorityName.forEachIndexed { i, n ->
                    PillButton(n, if (priority == i) HabitPalettes[1].fill else c.surface, if (priority == i) Color.White else c.text) { priority = i }
                }
            }
            Text("메모", style = HaruType.headline, color = c.text)
            HaruTextField(note, { note = it }, "메모를 입력해요", Modifier.fillMaxWidth())
            error?.let { Text(it, style = HaruType.body, color = c.errorText) }
            if (initial != null) PillButton("휴지통으로 이동", c.errorBg, c.errorText, Modifier.fillMaxWidth(), onClick = onTrash)
        }
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
            items(list, key = { it.id }) { t ->
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
    onAddOn: (LocalDate) -> Unit, onOpen: (String) -> Unit, onToggle: (String, Boolean) -> Unit,
) {
    val c = LocalHaru.current
    var month by rememberSaveable(stateSaver = Saver<YearMonth, String>(save = { it.toString() }, restore = { YearMonth.parse(it) })) { mutableStateOf(YearMonth.from(today)) }
    var selected by rememberSaveable(stateSaver = Saver<LocalDate, String>(save = { it.toString() }, restore = { LocalDate.parse(it) })) { mutableStateOf(today) }
    val counts = data.tasks.filter { it.deletedAt == null && it.dueDate != null }.groupingBy { it.dueDate!! }.eachCount()
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
                                        .background(if (sel) HabitPalettes[1].tint else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                        .border(if (date == today) 2.dp else 0.dp, if (date == today) c.outline else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                        .clickable(onClickLabel = "${date.monthValue}월 ${date.dayOfMonth}일") { selected = date },
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(date.dayOfMonth.toString(), style = HaruType.number, color = if (sel) HabitPalettes[1].deep else c.text)
                                    if (n > 0) Text(if (n > 3) "●●●+" else "●".repeat(n), style = HaruType.caption, color = HabitPalettes[1].fill)
                                }
                            }
                        }
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
        day.forEach { t -> TaskRow(t, today, { onToggle(t.id, it) }, { onOpen(t.id) }) }
    }
}
