package com.bossxor.harugyeol

import com.bossxor.harugyeol.habit.HabitInputException
import com.bossxor.harugyeol.task.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TaskOrganizationTest {
    private fun task(id: String, listId: String = INBOX_ID, deleted: Long? = null) = Task(id, id, createdAt = 1, updatedAt = 1, listId = listId, deletedAt = deleted)
    private fun rejects(block: () -> Unit) {
        try { block(); fail("Expected invalid input to be rejected") } catch (_: HabitInputException) { }
    }
    @Test fun deletingListMovesOpenAndTrashedTasksWithoutChangingTheirState() {
        val data = AppData(lists = defaultTaskLists() + TaskList("work", "업무"), tasks = listOf(task("open", "work"), task("trash", "work", 9)))
        val next = data.deleteTaskList("work")
        assertEquals(listOf(INBOX_ID, INBOX_ID), next.tasks.map { it.listId })
        assertEquals(9L, next.tasks.last().deletedAt)
        assertEquals(data.tasks.map { it.updatedAt }, next.tasks.map { it.updatedAt })
        assertEquals(defaultTaskLists(), next.lists)
    }
    @Test fun inboxCannotBeDeletedOrRenamed() {
        rejects { AppData().deleteTaskList(INBOX_ID) }
        rejects { AppData().saveTaskList(INBOX_ID, "변경", 1) }
    }
    @Test fun tagDuplicatesIgnoreCaseAndSurroundingWhitespace() {
        val data = AppData().saveTaskTag(null, "  Work ", 0)
        assertEquals("Work", data.tags.single().name)
        rejects { data.saveTaskTag(null, " wORK ", 1) }
        assertEquals("WORK", data.saveTaskTag(data.tags.single().id, "WORK", 2).tags.single().name)
    }
    @Test fun deletingTagRemovesReferencesAlsoFromTrash() {
        val data = AppData(tags = listOf(TaskTag("a", "A"), TaskTag("b", "B")), tasks = listOf(task("open").copy(tagIds = listOf("a", "b")), task("trash", deleted = 9).copy(tagIds = listOf("a"))))
        val next = data.deleteTaskTag("a")
        assertEquals(listOf("b"), next.tasks.first().tagIds)
        assertTrue(next.tasks.last().tagIds.isEmpty())
        assertEquals(listOf("b"), next.tags.map { it.id })
    }
    @Test fun relationsRejectUnknownAndDuplicateIdsAndInvalidChecklist() {
        val data = AppData(tags = listOf(TaskTag("a", "A")))
        rejects { data.validateTaskRelations("missing", emptyList(), emptyList()) }
        rejects { data.validateTaskRelations(INBOX_ID, listOf("missing"), emptyList()) }
        rejects { data.validateTaskRelations(INBOX_ID, listOf("a", "a"), emptyList()) }
        rejects { data.validateTaskRelations(INBOX_ID, emptyList(), listOf(ChecklistItem("x", "one"), ChecklistItem("x", "two"))) }
        rejects { data.validateTaskRelations(INBOX_ID, emptyList(), listOf(ChecklistItem("", "one"))) }
        rejects { data.validateTaskRelations(INBOX_ID, emptyList(), listOf(ChecklistItem("x", "   "))) }
        data.validateTaskRelations(INBOX_ID, listOf("a"), listOf(ChecklistItem("x", "one")))
    }
    @Test fun repeatingCompletionResetsChecklistAndUndoRestoresEntireTask() {
        val date = LocalDate.of(2026, 10, 12)
        val before = task("repeat").copy(dueDate = date, recurrence = RepeatRule(RepeatUnit.DAY, anchorDate = date), checklist = listOf(ChecklistItem("x", "first", true), ChecklistItem("y", "second", false)))
        val data = AppData(tasks = listOf(before))
        val afterData = data.setTaskDone(before.id, true, 10, date, date)
        val after = afterData.tasks.single()
        assertEquals(date.plusDays(1), after.dueDate)
        assertTrue(after.checklist.none { it.isDone })
        assertEquals(before, afterData.undoTaskCompletion(before, after).tasks.single())
    }
    @Test fun finalRepeatingOccurrencePreservesChecklist() {
        val date = LocalDate.of(2026, 10, 12)
        val before = task("final").copy(dueDate = date, recurrence = RepeatRule(RepeatUnit.DAY, anchorDate = date, untilDate = date), checklist = listOf(ChecklistItem("x", "first", true)))
        val after = AppData(tasks = listOf(before)).setTaskDone(before.id, true, 10, date, date).tasks.single()
        assertEquals(before.checklist, after.checklist)
        assertEquals(10L, after.completedAt)
    }
}
