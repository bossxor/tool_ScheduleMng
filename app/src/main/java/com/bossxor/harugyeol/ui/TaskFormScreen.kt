package com.bossxor.harugyeol.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bossxor.harugyeol.habit.HabitInputException
import com.bossxor.harugyeol.task.*
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun TaskFormScreen(
    initial: Task?, defaultDate: LocalDate?, today: LocalDate, data: com.bossxor.harugyeol.AppData,
    onSave: suspend (TaskDraft) -> String?, onTrash: () -> Unit, onClose: () -> Unit,
) {
    val c = LocalHaru.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    var priority by remember { mutableIntStateOf(initial?.priority ?: 0) }
    var due by remember { mutableStateOf(initial?.dueDate ?: defaultDate) }
    var time by remember { mutableStateOf(initial?.dueTime) }
    var unit by remember { mutableStateOf(initial?.recurrence?.unit) }
    var interval by remember { mutableStateOf((initial?.recurrence?.interval ?: 1).toString()) }
    var weekdays by remember { mutableStateOf(initial?.recurrence?.weekdays ?: listOf((due ?: today).dayOfWeek.value)) }
    var until by remember { mutableStateOf(initial?.recurrence?.untilDate) }
    var reminders by remember { mutableStateOf(initial?.reminders?.map { it.minutesBefore }.orEmpty()) }
    var listId by remember { mutableStateOf(initial?.listId ?: INBOX_ID) }
    var tagIds by remember { mutableStateOf(initial?.tagIds.orEmpty()) }
    var checklist by remember { mutableStateOf(initial?.checklist.orEmpty()) }
    var checklistText by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val units = listOf(null to "없음", RepeatUnit.DAY to "일", RepeatUnit.WEEK to "주", RepeatUnit.MONTH to "월", RepeatUnit.YEAR to "년")
    fun datePicker(value: LocalDate, set: (LocalDate) -> Unit) = DatePickerDialog(ctx, { _, y, m, d -> set(LocalDate.of(y, m + 1, d)) }, value.year, value.monthValue - 1, value.dayOfMonth).show()
    fun save() {
        if (saving) return
        try {
            val anchor = if (due == initial?.dueDate) initial?.recurrence?.anchorDate ?: due else due
            val rule = unit?.let { RepeatRule(it, interval.toIntOrNull() ?: throw HabitInputException("반복 간격은 정수로 입력해 주세요"), anchor ?: throw HabitInputException("반복 시작 날짜를 골라 주세요"), if (it == RepeatUnit.WEEK) weekdays else null, until) }
            rule?.validate()
            val date = if (rule != null && due != null) rule.nextOnOrAfter(due!!) ?: throw HabitInputException("종료일 안에 반복 날짜가 없어요") else due
            val draft = TaskDraft(title, note, priority, date, time, rule, reminders.map { TaskReminder(it) }, listId, tagIds, checklist)
            saving = true
            error = null
            scope.launch {
                try { error = onSave(draft) }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "저장하지 못했어요. 다시 시도해 주세요" }
                finally { saving = false }
            }
        } catch (e: HabitInputException) { error = e.message }
    }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar(if (initial == null) "할 일 추가" else "할 일 편집", { if (!saving) onClose() }) {
            PillButton(if (saving) "저장 중…" else "저장", HabitPalettes[1].fill, Color.White) { save() }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("제목", style = HaruType.headline, color = c.text)
            HaruTextField(title, { title = it }, "할 일 제목", Modifier.fillMaxWidth())
            Text("목록", style = HaruType.headline, color = c.text)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                data.lists.forEach { list -> PillButton(list.name, if (listId == list.id) HabitPalettes[1].fill else c.surface, if (listId == list.id) Color.White else c.text) { listId = list.id } }
            }
            if (data.tags.isNotEmpty()) {
                Text("태그", style = HaruType.headline, color = c.text)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.tags.forEach { tag -> PillButton(tag.name, if (tag.id in tagIds) HabitPalettes[2].fill else c.surface, if (tag.id in tagIds) Color.White else c.text) { tagIds = if (tag.id in tagIds) tagIds - tag.id else tagIds + tag.id } }
                }
            }
            Text("날짜", style = HaruType.headline, color = c.text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("오늘", c.surface, c.text) { due = today }
                PillButton("내일", c.surface, c.text) { due = today.plusDays(1) }
                PillButton("없음", c.surface, c.text) { due = null; time = null; unit = null; reminders = emptyList(); until = null }
            }
            PillButton(due?.toString() ?: "날짜 선택", c.surface, c.text) { datePicker(due ?: today) { due = it } }
            if (due != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(time ?: "시각 선택", c.surface, c.text) {
                        val value = time?.let(LocalTime::parse) ?: LocalTime.of(9, 0)
                        TimePickerDialog(ctx, { _, h, m -> time = String.format(Locale.ROOT, "%02d:%02d", h, m) }, value.hour, value.minute, true).show()
                    }
                    if (time != null) PillButton("종일", c.surface, c.text) { time = null }
                }
                Text("반복", style = HaruType.headline, color = c.text)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    units.forEach { (u, label) -> PillButton(label, if (unit == u) HabitPalettes[1].fill else c.surface, if (unit == u) Color.White else c.text) { unit = u } }
                }
                if (unit != null) {
                    HaruTextField(interval, { interval = it }, "반복 간격 (1~999)", Modifier.fillMaxWidth(), numeric = true)
                    if (unit == RepeatUnit.WEEK) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            "월화수목금토일".forEachIndexed { i, day ->
                                val selected = i + 1 in weekdays
                                PillButton(day.toString(), if (selected) HabitPalettes[1].fill else c.surface, if (selected) Color.White else c.text) {
                                    weekdays = if (selected) weekdays - (i + 1) else (weekdays + (i + 1)).sorted()
                                }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton(until?.let { "종료 $it" } ?: "종료일 없음", c.surface, c.text) { datePicker(until ?: due!!) { until = it } }
                        if (until != null) PillButton("해제", c.surface, c.text) { until = null }
                    }
                    Text("늦게 완료하면 완료일 이후 첫 회차로 이동해요. 과거 완료 기록은 유지돼요.", style = HaruType.bodyS, color = c.textSub)
                }
                Text("알림 (최대 5개)", style = HaruType.headline, color = c.text)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "정시", 10 to "10분 전", 30 to "30분 전", 60 to "1시간 전", 1440 to "1일 전").forEach { (offset, label) ->
                        val selected = offset in reminders
                        PillButton(label, if (selected) HabitPalettes[1].fill else c.surface, if (selected) Color.White else c.text) { reminders = if (selected) reminders - offset else reminders + offset }
                    }
                }
                if (time == null) Text("종일 알림은 설정의 종일 알림 시각을 기준으로 보내요.", style = HaruType.bodyS, color = c.textSub)
            }
            Text("우선순위", style = HaruType.headline, color = c.text)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("없음", "낮음", "중간", "높음").forEachIndexed { i, label -> PillButton(label, if (priority == i) HabitPalettes[1].fill else c.surface, if (priority == i) Color.White else c.text) { priority = i } }
            }
            Text("메모", style = HaruType.headline, color = c.text)
            HaruTextField(note, { note = it }, "메모", Modifier.fillMaxWidth(), multiline = true)
            Text("체크리스트", style = HaruType.headline, color = c.text)
            checklist.forEachIndexed { index, item ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(item.isDone, { done -> checklist = checklist.map { if (it.id == item.id) it.copy(isDone = done) else it } })
                        HaruTextField(item.text, { text -> checklist = checklist.map { if (it.id == item.id) it.copy(text = text) else it } }, "항목", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (index > 0) PillButton("위로", c.surface, c.text) { checklist = checklist.toMutableList().apply { java.util.Collections.swap(this, index, index - 1) } }
                        if (index < checklist.lastIndex) PillButton("아래로", c.surface, c.text) { checklist = checklist.toMutableList().apply { java.util.Collections.swap(this, index, index + 1) } }
                        PillButton("항목 삭제", c.errorBg, c.errorText) { checklist = checklist.filter { it.id != item.id } }
                    }
                }
            }
            HaruTextField(checklistText, { checklistText = it }, "새 체크리스트 항목", Modifier.fillMaxWidth())
            PillButton("항목 추가", c.surface, c.text) {
                if (checklistText.isNotBlank()) { checklist = checklist + ChecklistItem(java.util.UUID.randomUUID().toString(), checklistText.trim()); checklistText = "" }
            }
            Text("체크리스트를 모두 체크해도 작업은 자동 완료되지 않아요.", style = HaruType.bodyS, color = c.textSub)
            initial?.occurrences?.sortedByDescending { it.completedAt }?.take(10)?.forEach { Text("${it.scheduledDate} 회차 완료", style = HaruType.bodyS, color = c.textSub) }
            error?.let { Text(it, style = HaruType.body, color = c.errorText) }
            if (initial != null) PillButton("휴지통으로 이동", c.errorBg, c.errorText, Modifier.fillMaxWidth(), onClick = { if (!saving) onTrash() })
        }
    }
}
