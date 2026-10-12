package com.bossxor.harugyeol.task

import com.bossxor.harugyeol.AppData
import com.bossxor.harugyeol.habit.HabitInputException
import kotlinx.serialization.Serializable
import java.util.Locale
import java.util.UUID

const val INBOX_ID = "inbox"
@Serializable data class TaskList(val id: String, val name: String, val colorIndex: Int? = null, val isInbox: Boolean = false)
@Serializable data class TaskTag(val id: String, val name: String, val colorIndex: Int? = null)
@Serializable data class ChecklistItem(val id: String, val text: String, val isDone: Boolean = false)
fun defaultTaskLists() = listOf(TaskList(INBOX_ID, "받은함", isInbox = true))
fun normalizedTagName(name: String) = name.trim().lowercase(Locale.ROOT)
private fun requireTaxonomy(condition: Boolean, message: String) { if (!condition) throw HabitInputException(message) }

fun AppData.saveTaskList(id: String?, name: String, color: Int?): AppData {
    val n = name.trim()
    requireTaxonomy(n.length in 1..100, "목록 이름을 1~100자로 입력해 주세요")
    requireTaxonomy(color == null || color in 0..3, "목록 색을 확인해 주세요")
    if (id == null) return copy(lists = lists + TaskList(UUID.randomUUID().toString(), n, color))
    val current = lists.firstOrNull { it.id == id } ?: throw HabitInputException("목록을 찾을 수 없어요")
    requireTaxonomy(!current.isInbox, "받은함은 이름과 색을 바꿀 수 없어요")
    return copy(lists = lists.map { if (it.id == id) it.copy(name = n, colorIndex = color) else it })
}
fun AppData.deleteTaskList(id: String): AppData {
    val list = lists.firstOrNull { it.id == id } ?: throw HabitInputException("목록을 찾을 수 없어요")
    requireTaxonomy(!list.isInbox, "받은함은 삭제할 수 없어요")
    val inbox = lists.single { it.isInbox }.id
    return copy(lists = lists.filter { it.id != id }, tasks = tasks.map { if (it.listId == id) it.copy(listId = inbox) else it })
}
fun AppData.saveTaskTag(id: String?, name: String, color: Int?): AppData {
    val n = name.trim()
    requireTaxonomy(n.length in 1..50, "태그를 1~50자로 입력해 주세요")
    requireTaxonomy(color == null || color in 0..3, "태그 색을 확인해 주세요")
    requireTaxonomy(tags.none { it.id != id && normalizedTagName(it.name) == normalizedTagName(n) }, "같은 이름의 태그가 있어요")
    if (id == null) return copy(tags = tags + TaskTag(UUID.randomUUID().toString(), n, color))
    requireTaxonomy(tags.any { it.id == id }, "태그를 찾을 수 없어요")
    return copy(tags = tags.map { if (it.id == id) it.copy(name = n, colorIndex = color) else it })
}
fun AppData.deleteTaskTag(id: String): AppData = copy(tags = tags.filter { it.id != id }, tasks = tasks.map { it.copy(tagIds = it.tagIds - id) })
fun AppData.validateTaskRelations(listId: String, tagIds: List<String>, checklist: List<ChecklistItem>) {
    requireTaxonomy(lists.any { it.id == listId }, "목록을 찾을 수 없어요")
    requireTaxonomy(tagIds.distinct().size == tagIds.size && tagIds.all { id -> tags.any { it.id == id } }, "태그를 확인해 주세요")
    requireTaxonomy(checklist.map { it.id }.distinct().size == checklist.size && checklist.all { it.id.isNotBlank() && it.text.trim().length in 1..500 }, "체크리스트를 확인해 주세요")
}
fun AppData.inboxTasks() = allOpenTasks().filter { it.listId == lists.single { list -> list.isInbox }.id }
