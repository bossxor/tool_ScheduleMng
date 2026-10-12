package com.bossxor.harugyeol.reminders

import com.bossxor.harugyeol.AppData
import com.bossxor.harugyeol.task.Task
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

data class TaskAlarmPlan(val key: String, val taskId: String, val date: LocalDate, val minutesBefore: Int, val at: Long)
fun taskAlarmKey(id: String, date: LocalDate, offset: Int) = "task:$id:$date:$offset"

/** 현재 회차만 예약한다. 시간대 변경은 현지 시각을 새 시간대로 다시 계산한다. */
fun taskAlarmPlans(data: AppData, now: Long, zone: ZoneId = ZoneId.systemDefault(), futureOnly: Boolean = true): List<TaskAlarmPlan> =
    data.tasks.filter { it.deletedAt == null && it.completedAt == null }.flatMap { task ->
        val date = task.dueDate ?: return@flatMap emptyList()
        val due = date.atTime(LocalTime.parse(task.dueTime ?: data.settings.allDayReminderTime)).atZone(zone).toInstant().toEpochMilli()
        task.reminders.mapNotNull { reminder ->
            val at = reminder.snoozedUntil ?: (due - reminder.minutesBefore * 60_000L)
            if (futureOnly && at <= now) null else TaskAlarmPlan(taskAlarmKey(task.id, date, reminder.minutesBefore), task.id, date, reminder.minutesBefore, at)
        }
    }

fun matchesTaskAlarm(data: AppData, key: String, at: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): TaskAlarmPlan? =
    taskAlarmPlans(data, now, zone, futureOnly = false).firstOrNull { it.key == key && it.at == at && at <= now }

fun Task.notificationFingerprint(): String = "$dueDate|$dueTime|$title|$note|$completedAt|$deletedAt|$reminders"
