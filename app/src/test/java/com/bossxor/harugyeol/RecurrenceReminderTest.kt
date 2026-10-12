package com.bossxor.harugyeol

import com.bossxor.harugyeol.backup.Backup
import com.bossxor.harugyeol.backup.BackupException
import com.bossxor.harugyeol.habit.HabitInputException
import com.bossxor.harugyeol.reminders.*
import com.bossxor.harugyeol.task.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class RecurrenceReminderTest {
    private fun date(value: String) = LocalDate.parse(value)
    private val zone = ZoneId.of("Asia/Seoul")
    private fun millis(value: String) = java.time.LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
    private fun task(day: String = "2026-10-10", rule: RepeatRule? = null, reminders: List<TaskReminder> = emptyList()) =
        Task("task", "검증", dueDate = date(day), createdAt = 1, updatedAt = 1, recurrence = rule, reminders = reminders)
    private fun rule(unit: RepeatUnit, anchor: String, interval: Int = 1, days: List<Int>? = null, until: String? = null) =
        RepeatRule(unit, interval, date(anchor), days, until?.let(::date))

    @Test fun `월말 보정 이후 원래 31일로 돌아온다`() {
        val r = rule(RepeatUnit.MONTH, "2027-01-31")
        assertEquals(date("2027-02-28"), r.nextOnOrAfter(date("2027-02-01")))
        assertEquals(date("2027-03-31"), r.nextOnOrAfter(date("2027-03-01")))
        assertEquals(date("2028-02-29"), r.nextOnOrAfter(date("2028-02-01")))
    }
    @Test fun `윤년 연 반복은 다시 2월29일로 돌아온다`() {
        val r = rule(RepeatUnit.YEAR, "2024-02-29")
        assertEquals(date("2025-02-28"), r.nextOnOrAfter(date("2025-01-01")))
        assertEquals(date("2028-02-29"), r.nextOnOrAfter(date("2028-01-01")))
    }
    @Test fun `주 간격과 여러 요일은 기준 주를 따른다`() {
        val r = rule(RepeatUnit.WEEK, "2026-10-14", 2, listOf(1, 3, 5))
        assertEquals(date("2026-10-14"), r.nextOnOrAfter(date("2026-10-12")))
        assertEquals(date("2026-10-16"), r.nextOnOrAfter(date("2026-10-15")))
        assertEquals(date("2026-10-26"), r.nextOnOrAfter(date("2026-10-17")))
    }
    @Test fun `종료일은 포함하며 이후 회차는 없다`() {
        val r = rule(RepeatUnit.DAY, "2026-10-10", 2, until = "2026-10-14")
        assertEquals(date("2026-10-14"), r.nextOnOrAfter(date("2026-10-13")))
        assertNull(r.nextOnOrAfter(date("2026-10-15")))
    }
    @Test fun `늦은 완료는 완료일 이후 첫 회차로 이동하고 건너뛴 회차를 기록하지 않는다`() {
        val t = task(rule = rule(RepeatUnit.DAY, "2026-10-10"))
        val data = AppData(tasks = listOf(t)).setTaskDone(t.id, true, 2, t.dueDate, date("2026-10-13"))
        assertEquals(date("2026-10-14"), data.tasks.single().dueDate)
        assertEquals(listOf(date("2026-10-10")), data.tasks.single().occurrences.map { it.scheduledDate })
        assertNull(data.tasks.single().completedAt)
    }
    @Test fun `중복 완료와 미래 회차 액션은 다음 회차를 완료하지 않는다`() {
        val t = task(rule = rule(RepeatUnit.DAY, "2026-10-10"))
        val original = AppData(tasks = listOf(t))
        val next = original.setTaskDone(t.id, true, 2, t.dueDate, date("2026-10-10"))
        assertEquals(next, next.setTaskDone(t.id, true, 3, t.dueDate, date("2026-10-10")))
        assertEquals(original, original.setTaskDone(t.id, true, 3, date("2026-10-11"), date("2026-10-10")))
    }
    @Test fun `마지막 회차는 완료로 닫히고 완료 보기는 회차를 보여준다`() {
        val t = task(rule = rule(RepeatUnit.DAY, "2026-10-10", until = "2026-10-10"))
        val next = AppData(tasks = listOf(t)).setTaskDone(t.id, true, 2, t.dueDate, date("2026-10-10"))
        assertEquals(2L, next.tasks.single().completedAt)
        assertEquals(1, next.doneTasks().size)
        assertTrue(next.allOpenTasks().isEmpty())
    }
    @Test fun `직후 완료 취소는 회차 일정과 미루기를 모두 복원한다`() {
        val t = task(rule = rule(RepeatUnit.DAY, "2026-10-10"), reminders = listOf(TaskReminder(0, 100)))
        val data = AppData(tasks = listOf(t)).setTaskDone(t.id, true, 2, t.dueDate, date("2026-10-10"))
        assertNull(data.tasks.single().reminders.single().snoozedUntil)
        assertEquals(AppData(tasks = listOf(t)), data.undoTaskCompletion(t, data.tasks.single()))
        val edited = data.editTask(t.id, "다른 제목", "", 0, data.tasks.single().dueDate, 3)
        try { edited.undoTaskCompletion(t, data.tasks.single()); fail() } catch (_: HabitInputException) {}
    }
    @Test fun `조회 월의 미래 회차는 계산하며 저장 상태를 바꾸지 않는다`() {
        val t = task("2027-01-31", rule(RepeatUnit.MONTH, "2027-01-31"))
        val data = AppData(tasks = listOf(t))
        assertEquals(listOf(date("2027-02-28")), t.scheduledDates(date("2027-02-01"), date("2027-02-28")))
        assertEquals(1, data.tasksOn(date("2027-02-28")).size)
        assertEquals(date("2027-01-31"), data.tasks.single().dueDate)
    }
    @Test fun `종일 시각과 다중 알림을 정확히 계산한다`() {
        val t = task("2026-10-12", reminders = listOf(TaskReminder(0), TaskReminder(30)))
        val data = AppData(settings = Settings(allDayReminderTime = "10:30"), tasks = listOf(t))
        val plans = taskAlarmPlans(data, millis("2026-10-12T09:00"), zone)
        assertEquals(listOf(millis("2026-10-12T10:30"), millis("2026-10-12T10:00")), plans.map { it.at })
        assertTrue(taskAlarmPlans(data, millis("2026-10-12T11:00"), zone).isEmpty())
    }
    @Test fun `시간대 변경 후 같은 현지 시각으로 재계산한다`() {
        val t = task("2026-10-12", reminders = listOf(TaskReminder(0))).copy(dueTime = "10:30")
        val data = AppData(tasks = listOf(t))
        val a = taskAlarmPlans(data, 0, zone).single().at
        val b = taskAlarmPlans(data, 0, ZoneId.of("UTC")).single().at
        assertEquals(9 * 3600_000L, b - a)
    }
    @Test fun `수정 완료 삭제된 작업과 이전 예정 시각은 수신 시 거부한다`() {
        val t = task("2026-10-12", reminders = listOf(TaskReminder(0)))
        val data = AppData(tasks = listOf(t))
        val plan = taskAlarmPlans(data, 0, zone).single()
        assertNotNull(matchesTaskAlarm(data, plan.key, plan.at, plan.at, zone))
        assertNull(matchesTaskAlarm(data.copy(tasks = listOf(t.copy(dueTime = "11:00"))), plan.key, plan.at, plan.at, zone))
        assertNull(matchesTaskAlarm(data.trashTask(t.id, 2), plan.key, plan.at, plan.at, zone))
        assertNull(matchesTaskAlarm(data.setTaskDone(t.id, true, 2), plan.key, plan.at, plan.at, zone))
    }
    @Test fun `미루기는 저장 후 복원해도 유지되고 다른 회차 액션은 무해하다`() {
        val t = task("2026-10-12", reminders = listOf(TaskReminder(0)))
        val data = AppData(tasks = listOf(t))
        val now = millis("2026-10-12T09:01")
        val snoozed = data.snoozeTask(t.id, t.dueDate!!, 0, now)
        val restored = Backup.decode(Backup.encode(snoozed))
        assertEquals(now + 600_000, taskAlarmPlans(restored, now, zone).single().at)
        assertEquals(data, data.snoozeTask(t.id, date("2026-10-11"), 0, now))
    }
    @Test fun `회차와 반복 설정을 백업 왕복 보존하고 잘못된 설정은 거부한다`() {
        val t = task(rule = rule(RepeatUnit.DAY, "2026-10-10"))
        val data = AppData(tasks = listOf(t)).setTaskDone(t.id, true, 2, t.dueDate, date("2026-10-10"))
        assertEquals(data, Backup.decode(Backup.encode(data)))
        listOf(t.copy(recurrence = t.recurrence!!.copy(interval = 0)), t.copy(dueTime = "25:00"), t.copy(reminders = listOf(TaskReminder(0), TaskReminder(0)))).forEach { invalid ->
            try { Backup.decode(Backup.encode(AppData(tasks = listOf(invalid)))); fail() } catch (_: BackupException) {}
        }
    }
    @Test fun `이전 파일의 작업을 새 필드 기본값으로 읽는다`() {
        val old = """{"schemaVersion":1,"tasks":[{"id":"old","title":"이전","createdAt":1,"updatedAt":1}]}"""
        val t = Backup.decode(old).tasks.single()
        assertNull(t.recurrence)
        assertTrue(t.reminders.isEmpty())
        assertTrue(t.occurrences.isEmpty())
    }
    @Test fun `반복 해제 후에도 과거 완료 회차를 표시한다`() {
        val t = task(rule = rule(RepeatUnit.DAY, "2026-10-10"))
        val completed = AppData(tasks = listOf(t)).setTaskDone(t.id, true, 2, t.dueDate, date("2026-10-10"))
        val saved = completed.saveTask(t.id, TaskDraft("검증", "", 0, date("2026-10-11")), 3)
        assertEquals(1, saved.doneTasks().size)
        assertEquals(date("2026-10-10"), saved.doneTasks().single().dueDate)
        assertEquals(1, saved.tasksOn(date("2026-10-10")).size)
        assertEquals(1, saved.allOpenTasks().size)
    }
}
