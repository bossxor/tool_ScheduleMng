package com.bossxor.harugyeol.task

import com.bossxor.harugyeol.AppData
import com.bossxor.harugyeol.habit.HabitInputException
import com.bossxor.harugyeol.habit.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

/** 할 일. 목록·태그·체크리스트·반복은 이후 단계에서 추가한다(기본값을 두어 파일 호환 유지). */
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

fun AppData.setTaskDone(id: String, done: Boolean, now: Long): AppData =
    mapTask(id) { if (it.deletedAt != null || (it.completedAt != null) == done) it else it.copy(completedAt = if (done) now else null, updatedAt = now) }

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

fun AppData.doneTasks() = tasks.filter { it.deletedAt == null && it.completedAt != null }.sortedByDescending { it.completedAt }

fun AppData.trashedTasks() = tasks.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }

fun AppData.tasksOn(date: LocalDate) =
    tasks.filter { it.deletedAt == null && it.dueDate == date }.sortedWith(compareBy<Task> { it.completedAt != null }.then(order))

/** 검색은 제목·메모를 대상으로 하며 완료 포함을 선택한다. */
fun AppData.searchTasks(query: String, includeCompleted: Boolean = false): List<Task> {
    val term = query.trim()
    return tasks.filter {
        it.deletedAt == null && (includeCompleted || it.completedAt == null) &&
            (it.title.contains(term, ignoreCase = true) || it.note.contains(term, ignoreCase = true))
    }.sortedWith(order)
}
