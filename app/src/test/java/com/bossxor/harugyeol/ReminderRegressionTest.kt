package com.bossxor.harugyeol

import com.bossxor.harugyeol.habit.*
import com.bossxor.harugyeol.reminders.*
import com.bossxor.harugyeol.task.Task
import com.bossxor.harugyeol.task.TaskReminder
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderRegressionTest {
    private val due = LocalDateTime.of(2026, 10, 13, 10, 0)
    private val zone = ZoneId.of("Asia/Seoul")
    private fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
    private fun data() = AppData(tasks = listOf(Task("alarm", "alarm", dueDate = due.toLocalDate(),
        dueTime = "10:00", reminders = listOf(TaskReminder(0)), createdAt = 1, updatedAt = 1)))

    @Test fun unchangedScheduledTaskSurvivesDelayedRescheduling() {
        val d = data()
        val plan = taskAlarmPlans(d, millis(due.minusMinutes(1)), zone).single()
        assertEquals(listOf(plan), taskAlarmPlans(d, millis(due.plusMinutes(1)), zone,
            scheduled = mapOf(plan.key to plan.at)))
    }

    @Test fun oldOrRemovedTaskScheduleIsNotRetained() {
        val d = data()
        val plan = taskAlarmPlans(d, millis(due.minusMinutes(1)), zone).single()
        val previous = mapOf(plan.key to plan.at)
        val changed = d.copy(tasks = d.tasks.map { it.copy(dueTime = "09:00") })
        assertTrue(taskAlarmPlans(changed, millis(due.plusMinutes(1)), zone, scheduled = previous).isEmpty())
        assertTrue(taskAlarmPlans(d.copy(tasks = emptyList()), millis(due.plusMinutes(1)), zone, scheduled = previous).isEmpty())
    }

    @Test fun unscheduledPastTaskIsNotNewlyAlerted() {
        assertTrue(taskAlarmPlans(data(), millis(due.plusMinutes(1)), zone).isEmpty())
    }

    private fun habits() = AppData().addHabit("check", 0, MeasureType.CHECK, null, GoalMode.DAILY,
        null, null, 1, due.toLocalDate(), due.toLocalDate(), 1).let { d ->
        d.copy(habits = d.habits.map { it.copy(reminders = listOf("20:30")) },
            settings = Settings(dailySummary = DailySummary(true, "20:30")))
    }

    @Test fun blockedSummaryKeepsIndividualHabitAlarm() {
        val d = habits()
        val plan = planAlarms(d, summaryAvailable = false).single()
        assertEquals(d.habits.single().id, plan.habitId)
        assertNotEquals(SUMMARY_KEY, plan.key)
    }

    @Test fun allowedSummaryStillCombinesSameTimeAlarms() {
        assertEquals(SUMMARY_KEY, planAlarms(habits(), summaryAvailable = true).single().key)
    }
}
