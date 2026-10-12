package com.bossxor.harugyeol.task

import com.bossxor.harugyeol.AppData
import com.bossxor.harugyeol.habit.HabitInputException
import com.bossxor.harugyeol.habit.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

/** 기존 파일을 기본값으로 읽으며 반복 회차와 알림을 작업 안에 보존한다. */
@Serializable
data class Task(
    val id: String,
    val title: String,
    val note: String = "",
    val priority: Int = 0, // 0 없음, 1 낮음, 2 중간, 3 높음
    @Serializable(LocalDateSerializer::class) val dueDate: LocalDate? = null,
    val completedAt: Long? = null,
    val deletedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val dueTime: String? = null,
    val recurrence: RepeatRule? = null,
    val reminders: List<TaskReminder> = emptyList(),
    val occurrences: List<TaskOccurrence> = emptyList(),
)

private fun fail(msg: String): Nothing = throw HabitInputException(msg)

private fun check(title: String, note: String, priority: Int) {
    if (title.isBlank() || title.trim().length > 500) fail("제목을 1~500자로 입력해 주세요")
    if (note.length > 20_000) fail("메모가 너무 길어요")
    if (priority !in 0..3) fail("우선순위를 확인해 주세요")
}

private fun AppData.mapTask(id: String, f: (Task) -> Task): AppData {
    if (tasks.none { it.id == id }) fail("할 일을 찾을 수 없어요")
    return copy(tasks = tasks.map { if (it.id == id) f(it) else it })
}

fun AppData.addTask(title: String, note: String, priority: Int, dueDate: LocalDate?, now: Long): AppData {
    check(title, note, priority)
    val t = Task(UUID.randomUUID().toString(), title.trim(), note, priority, dueDate, createdAt = now, updatedAt = now)
    return copy(tasks = tasks + t)
}

fun AppData.editTask(id: String, title: String, note: String, priority: Int, dueDate: LocalDate?, now: Long): AppData {
    check(title, note, priority)
    return mapTask(id) { if (it.deletedAt != null) fail("휴지통의 할 일은 편집할 수 없어요"); it.copy(title = title.trim(), note = note, priority = priority, dueDate = dueDate, updatedAt = now) }
}

fun AppData.setTaskDone(
    id: String, done: Boolean, now: Long, expectedDate: LocalDate? = null,
    today: LocalDate = LocalDate.now(),
): AppData = mapTask(id) { task ->
    if (task.deletedAt != null) task
    else if (task.recurrence == null) {
        if ((task.completedAt != null) == done) task
        else task.copy(completedAt = if (done) now else null, updatedAt = now, reminders = task.reminders.map { it.copy(snoozedUntil = null) })
    } else {
        val date = task.dueDate
        if (!done || task.completedAt != null || date == null || date != expectedDate || task.occurrences.any { it.scheduledDate == date }) task
        else {
            val next = task.recurrence.nextOnOrAfter(maxOf(date, today).plusDays(1))
            task.copy(
                dueDate = next ?: date, completedAt = if (next == null) now else null, updatedAt = now,
                occurrences = task.occurrences + TaskOccurrence(date, task.dueTime, now),
                reminders = task.reminders.map { it.copy(snoozedUntil = null) },
            )
        }
    }
}

/** 다른 변경이 없는 직전 완료만 취소한다. */
fun AppData.undoTaskCompletion(before: Task, after: Task): AppData = mapTask(before.id) {
    if (it != after) fail("이후 변경이 있어 완료를 되돌릴 수 없어요")
    before
}

fun validateTaskSchedule(due: LocalDate?, time: String?, rule: RepeatRule?, reminders: List<TaskReminder>) {
    if (due == null && (time != null || rule != null || reminders.isNotEmpty())) fail("시각·반복·알림에는 날짜가 필요해요")
    if (time != null && !Regex("""([01]\d|2[0-3]):[0-5]\d""").matches(time)) fail("시각을 확인해 주세요")
    rule?.validate()
    if (rule != null && due != null && (due.isBefore(rule.anchorDate) || rule.nextOnOrAfter(due) != due)) fail("반복 날짜를 확인해 주세요")
    if (reminders.size > 5 || reminders.map { it.minutesBefore }.distinct().size != reminders.size || reminders.any { it.minutesBefore !in 0..525600 || (it.snoozedUntil ?: 0) < 0 }) fail("알림 시각을 확인해 주세요")
}

fun AppData.saveTaskSchedule(id: String, time: String?, rule: RepeatRule?, reminders: List<TaskReminder>, now: Long): AppData = mapTask(id) {
    validateTaskSchedule(it.dueDate, time, rule, reminders)
    it.copy(dueTime = time, recurrence = rule, reminders = reminders, updatedAt = now)
}

fun AppData.snoozeTask(id: String, expectedDate: LocalDate, minutesBefore: Int, now: Long): AppData = mapTask(id) {
    if (it.deletedAt != null || it.completedAt != null || it.dueDate != expectedDate) it
    else it.copy(reminders = it.reminders.map { reminder ->
        if (reminder.minutesBefore == minutesBefore) reminder.copy(snoozedUntil = now + 600_000) else reminder
    })
}

fun AppData.trashTask(id: String, now: Long): AppData = mapTask(id) { it.copy(deletedAt = now, updatedAt = now) }

fun AppData.restoreTask(id: String, now: Long): AppData = mapTask(id) { it.copy(deletedAt = null, updatedAt = now) }

fun AppData.deleteTask(id: String): AppData = copy(tasks = tasks.filter { it.id != id })

fun AppData.emptyTrash(): AppData = copy(tasks = tasks.filter { it.deletedAt == null })

// 보기 (docs/product-design.md 5절 '할 일')
private val order = compareByDescending<Task> { it.priority }
    .thenBy { it.dueDate ?: LocalDate.MAX }
    .thenBy { it.createdAt }

private fun List<Task>.open() = filter { it.deletedAt == null && it.completedAt == null }

/** 기한이 오늘이거나 지난 미완료 */
fun AppData.todayTasks(today: LocalDate) =
    tasks.open().filter { it.dueDate != null && !it.dueDate.isAfter(today) }.sortedWith(order)

fun AppData.upcomingTasks(today: LocalDate) =
    tasks.open().filter { it.dueDate != null && it.dueDate.isAfter(today) }.sortedWith(order)

fun AppData.allOpenTasks() = tasks.open().sortedWith(order)

fun AppData.doneTasks(): List<Task> = tasks.filter { it.deletedAt == null }.flatMap { task ->
    val history = task.occurrences.map { task.copy(dueDate = it.scheduledDate, dueTime = it.scheduledTime, completedAt = it.completedAt) }
    history + listOfNotNull(task.takeIf { it.completedAt != null && task.occurrences.none { o -> o.scheduledDate == task.dueDate && o.completedAt == task.completedAt } })
}.sortedByDescending { it.completedAt }

fun AppData.trashedTasks() = tasks.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }

fun AppData.tasksOn(date: LocalDate): List<Task> = tasks.filter { it.deletedAt == null }.flatMap { task ->
    val history = task.occurrences.filter { it.scheduledDate == date }.map { task.copy(dueDate = date, dueTime = it.scheduledTime, completedAt = it.completedAt) }
    val planned = if (task.recurrence == null) listOfNotNull(task.takeIf { it.dueDate == date && (history.isEmpty() || it.completedAt == null) })
        else task.scheduledDates(date, date).map { task.copy(dueDate = it) }
    history + planned
}.sortedWith(compareBy<Task> { it.completedAt != null }.then(order))

/** 검색은 제목·메모를 대상으로 하며 완료 포함을 선택한다. */
fun AppData.searchTasks(query: String, includeCompleted: Boolean = false): List<Task> {
    val term = query.trim()
    return tasks.filter {
        it.deletedAt == null && (includeCompleted || it.completedAt == null) &&
            (it.title.contains(term, ignoreCase = true) || it.note.contains(term, ignoreCase = true))
    }.sortedWith(order)
}

data class TaskDraft(
    val title: String, val note: String, val priority: Int, val dueDate: LocalDate?,
    val dueTime: String? = null, val recurrence: RepeatRule? = null, val reminders: List<TaskReminder> = emptyList(),
)

fun AppData.saveTask(id: String?, draft: TaskDraft, now: Long): AppData {
    validateTaskSchedule(draft.dueDate, draft.dueTime, draft.recurrence, draft.reminders)
    val next = if (id == null) addTask(draft.title, draft.note, draft.priority, draft.dueDate, now)
        else editTask(id, draft.title, draft.note, draft.priority, draft.dueDate, now)
    val taskId = id ?: next.tasks.last().id
    if (draft.recurrence != null && next.tasks.first { it.id == taskId }.occurrences.any { it.scheduledDate == draft.dueDate }) fail("완료 기록이 있는 날짜를 현재 회차로 쓸 수 없어요")
    return next.saveTaskSchedule(taskId, draft.dueTime, draft.recurrence, draft.reminders, now)
}
