package com.bossxor.harugyeol.verification

import android.app.Activity
import android.app.Instrumentation
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.os.ParcelFileDescriptor
import com.bossxor.harugyeol.AppData
import com.bossxor.harugyeol.DailySummary
import com.bossxor.harugyeol.Settings
import com.bossxor.harugyeol.habit.GoalMode
import com.bossxor.harugyeol.habit.MeasureType
import com.bossxor.harugyeol.habit.addHabit
import com.bossxor.harugyeol.reminders.*
import com.bossxor.harugyeol.task.Task
import com.bossxor.harugyeol.task.TaskReminder
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Runs only in the separate verification package. Never opens the personal app's store. */
class VerificationInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            verifyDelayedAlarm(result)
            verifyBlockedSummary(result)
            result.putString("status", "ALL_CURRENT_DEFECT_ASSERTIONS_PASSED")
            finish(Activity.RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString("status", "FAILED: ${e.javaClass.simpleName}: ${e.message}")
            finish(Activity.RESULT_CANCELED, result)
        } finally {
            ReminderScheduler.rescheduleAll(targetContext, AppData())
        }
    }

    private fun alarmCount(): Int {
        val fd = uiAutomation.executeShellCommand("dumpsys alarm")
        val dump = ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
        return dump.lineSequence().count { it.contains("Alarm{") && it.contains("${targetContext.packageName}") }
    }

    private fun verifyDelayedAlarm(result: Bundle) {
        val due = LocalDateTime.now().withSecond(0).withNano(0).plusMinutes(2)
        val data = AppData(tasks = listOf(Task("verification-delay", "test only", dueDate = due.toLocalDate(),
            dueTime = due.format(DateTimeFormatter.ofPattern("HH:mm")), reminders = listOf(TaskReminder(0)),
            createdAt = 1, updatedAt = 1)))
        ReminderScheduler.rescheduleAll(targetContext, data, due.minusMinutes(1))
        val before = alarmCount()
        check(before == 1) { "Expected 1 real OS alarm, got $before" }
        // Inject time through the existing API. Never change the device's system clock.
        ReminderScheduler.rescheduleAll(targetContext, data, due.plusMinutes(1))
        val after = alarmCount()
        check(after == 0) { "Expected cancellation, got $after alarms" }
        result.putString("delayedAlarm", "OS alarms $before -> $after with unchanged data; simulated now only")
    }

    private fun verifyBlockedSummary(result: Bundle) {
        val ctx = targetContext
        val nm = ctx.getSystemService(NotificationManager::class.java)
        check(nm.areNotificationsEnabled()) { "Verification package notification permission missing" }
        nm.createNotificationChannel(NotificationChannel(Notifier.CH_SUMMARY, "test blocked summary", NotificationManager.IMPORTANCE_NONE))
        Notifier.ensureChannels(ctx)
        check(nm.getNotificationChannel(Notifier.CH_SUMMARY).importance == NotificationManager.IMPORTANCE_NONE)
        check(nm.getNotificationChannel(Notifier.CH_HABIT).importance > NotificationManager.IMPORTANCE_NONE)
        val today = LocalDate.now()
        val base = AppData().addHabit("test only", 0, MeasureType.CHECK, null, GoalMode.DAILY,
            null, null, 1, today, today, 1)
        val data = base.copy(habits = base.habits.map { it.copy(reminders = listOf("20:30")) },
            settings = Settings(dailySummary = DailySummary(true, "20:30")))
        val plans = planAlarms(data)
        check(plans.single().key == SUMMARY_KEY) { "Expected only summary plan" }
        val summary = summaryNotice(data, today) ?: error("Missing summary")
        Notifier.postSummary(ctx, summary)
        check(nm.activeNotifications.isEmpty()) { "Blocked summary unexpectedly posted" }
        result.putString("blockedSummary", "habit channel allowed; summary channel blocked; plans=summary only; posted=0")
        val control = data.copy(settings = Settings(dailySummary = DailySummary(false, "20:30")))
        check(planAlarms(control).single().habitId != null)
        Notifier.postHabit(ctx, control.habits.single(), today)
        check(nm.activeNotifications.size == 1) { "Control habit notification failed" }
        nm.cancelAll()
        result.putString("habitControl", "summary off; habit plan present; posted=1")
    }
}
