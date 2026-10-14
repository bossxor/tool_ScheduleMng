package com.bossxor.harugyeol.verification

import android.app.Activity
import android.app.Instrumentation
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import com.bossxor.harugyeol.*
import com.bossxor.harugyeol.habit.*
import com.bossxor.harugyeol.reminders.*
import com.bossxor.harugyeol.task.Task
import com.bossxor.harugyeol.task.TaskReminder
import com.bossxor.harugyeol.ui.HaruTheme
import com.bossxor.harugyeol.ui.TimerActions
import com.bossxor.harugyeol.ui.TimerScreen
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Regression assertions run in the isolated package using the production Application. */
class FixedInstrumentation : Instrumentation() {
    private val app get() = targetContext.applicationContext as HarugyeolApp
    private fun waitFor(description: String, test: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + 5_000
        while (!test()) {
            check(SystemClock.elapsedRealtime() < end) { "Timed out: $description" }
            Thread.sleep(50)
        }
    }

    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        var activity: MainActivity? = null
        try {
            Thread.sleep(300)
            verifyAlarms(result)
            verifyDailyRearm(result)
            verifyChannels(result)
            runBlocking { app.store.update { AppData() } }
            activity = startActivitySync(Intent(targetContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            verifyDate(activity, result)
            verifyTimer(activity, result)
            result.putString("status", "ALL_FIXED_REGRESSIONS_PASSED")
        } catch (e: Throwable) {
            result.putString("status", "FAILED: ${e.javaClass.simpleName}: ${e.message}")
        } finally {
            activity?.let { a -> runOnMainSync { a.finish() } }
            runBlocking { app.store.update { AppData() } }
            ReminderScheduler.rescheduleAll(targetContext, AppData())
            targetContext.getSystemService(NotificationManager::class.java).cancelAll()
        }
        finish(if (result.getString("status") == "ALL_FIXED_REGRESSIONS_PASSED") Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }

    private fun alarmCount(): Int {
        val fd = uiAutomation.executeShellCommand("dumpsys alarm")
        val dump = ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
        return dump.lineSequence().count { it.contains("Alarm{") && it.contains(targetContext.packageName) }
    }

    private fun verifyAlarms(result: Bundle) {
        val due = LocalDateTime.now().withSecond(0).withNano(0).plusMinutes(10)
        val data = AppData(tasks = listOf(Task("fixed-delay", "test only", dueDate = due.toLocalDate(),
            dueTime = due.format(DateTimeFormatter.ofPattern("HH:mm")), reminders = listOf(TaskReminder(0)), createdAt = 1, updatedAt = 1)))
        runBlocking { app.store.update { data } }
        waitFor("initial OS alarm") { alarmCount() == 1 }
        ReminderScheduler.rescheduleAll(targetContext, data, due.minusMinutes(1))
        check(alarmCount() == 1)
        ReminderScheduler.rescheduleAll(targetContext, data, due.plusMinutes(1))
        check(alarmCount() == 1) { "Unchanged delayed alarm was canceled" }
        val plan = taskAlarmPlans(data, 0).single()
        check(!ReminderScheduler.consume(targetContext, plan.key, plan.at + 1))
        check(alarmCount() == 1) { "Stale callback altered current alarm" }
        ReminderScheduler.rescheduleAll(targetContext, data, due.minusMinutes(1), reset = true)
        check(alarmCount() == 1) { "Startup reset failed" }
        runBlocking { app.store.update { AppData() } }
        waitFor("removed task cancellation") { alarmCount() == 0 }
        result.putString("delayedAlarm", "OS count 1 -> 1; stale callback rejected; startup reset=1; deleted=0")
    }

    private fun verifyDailyRearm(result: Bundle) {
        val ctx = targetContext
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val time = now.plusMinutes(5).format(DateTimeFormatter.ofPattern("HH:mm"))
        val base = AppData().addHabit("daily test", 0, MeasureType.CHECK, null, GoalMode.DAILY,
            null, null, 1, now.toLocalDate(), now.toLocalDate(), 1)
        val data = base.copy(habits = base.habits.map { it.copy(reminders = listOf(time)) })
        runBlocking { app.store.update { data } }
        val key = habitAlarmKey(data.habits.single().id, time)
        val prefs = ctx.getSharedPreferences("reminder_keys", 0)
        waitFor("daily scheduled") { prefs.contains("at:$key") }
        val first = prefs.getLong("at:$key", -1)
        check(ReminderScheduler.consume(ctx, key, first))
        ReminderScheduler.rescheduleAll(ctx, data,
            java.time.Instant.ofEpochMilli(first + 1_000).atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
        val next = prefs.getLong("at:$key", -1)
        check(next > first && alarmCount() == 1) { "Daily alarm did not advance" }
        check(!ReminderScheduler.consume(ctx, key, first))
        check(prefs.getLong("at:$key", -1) == next) { "Duplicate callback consumed next day" }
        runBlocking { app.store.update { AppData() } }
        waitFor("daily cleanup") { alarmCount() == 0 }
        result.putString("dailyRearm", "consumed alarm advanced; duplicate callback retained next-day alarm")
    }

    private fun verifyChannels(result: Bundle) {
        val nm = targetContext.getSystemService(NotificationManager::class.java)
        check(nm.areNotificationsEnabled())
        nm.createNotificationChannel(NotificationChannel(Notifier.CH_SUMMARY, "blocked test summary", NotificationManager.IMPORTANCE_NONE))
        check(!Notifier.summaryAvailable(targetContext))
        val day = LocalDate.now()
        val base = AppData().addHabit("test only", 0, MeasureType.CHECK, null, GoalMode.DAILY, null, null, 1, day, day, 1)
        val data = base.copy(habits = base.habits.map { it.copy(reminders = listOf("20:30")) },
            settings = Settings(dailySummary = DailySummary(true, "20:30")))
        runBlocking { app.store.update { data } }
        val key = habitAlarmKey(data.habits.single().id, "20:30")
        waitFor("individual alarm with blocked summary") {
            targetContext.getSharedPreferences("reminder_keys", 0).getStringSet("keys", emptySet()) == setOf(key)
        }
        check(alarmCount() == 1)
        val habit = data.habits.single()
        Notifier.postHabit(targetContext, habit, day)
        waitFor("habit notice posted") {
            nm.activeNotifications.any { it.id == Notifier.id(habit.id) && it.notification.channelId == Notifier.CH_HABIT }
        }
        check(nm.activeNotifications.size == 1) { "Unexpected notification count after habit post" }
        nm.cancelAll()
        result.putString("blockedSummary", "summary blocked; individual OS alarm=1; habit notice=1")
    }

    @Suppress("UNCHECKED_CAST")
    private fun verifyDate(activity: MainActivity, result: Bundle) {
        val field = MainActivity::class.java.getDeclaredField("today").apply { isAccessible = true }
        val day = field.get(activity) as MutableState<LocalDate>
        val receiver = MainActivity::class.java.getDeclaredField("dateReceiver").apply { isAccessible = true }.get(activity) as BroadcastReceiver
        runOnMainSync {
            day.value = LocalDate.now().minusDays(1)
            receiver.onReceive(targetContext, Intent(Intent.ACTION_DATE_CHANGED))
            check(day.value == LocalDate.now()) { "Date broadcast did not update UI state" }
            day.value = LocalDate.now().minusDays(1)
            val refresh = MainActivity::class.java.getDeclaredMethod("refreshToday").apply { isAccessible = true }
            check(refresh.invoke(activity) == LocalDate.now())
            check(day.value == LocalDate.now()) { "Record-time refresh retained yesterday" }
        }
        result.putString("dateBoundary", "stale yesterday refreshed by production date receiver and record-time guard; clock unchanged")
    }

    private fun verifyTimer(activity: MainActivity, result: Bundle) {
        val day = LocalDate.now()
        val boot = AndroidSettings.Global.getInt(targetContext.contentResolver, AndroidSettings.Global.BOOT_COUNT, 0)
        val base = AppData().addHabit("timer test", 0, MeasureType.DURATION, "분", GoalMode.DAILY, null, null, 1_800, day, day, 1)
        runBlocking { app.store.update { base.startTimer(base.habits.single().id, day, SystemClock.elapsedRealtime() - 25_000, boot) } }
        val show = mutableStateOf(true)
        runOnMainSync {
            activity.setContent {
                val data = app.store.state.collectAsState().value
                HaruTheme {
                    if (show.value) TimerScreen(data.habits.single(), data, day, boot,
                        TimerActions({}, {}, {}, {}, {}, { app.preserveTimerCheckpoint() }), {})
                    else Text("verification complete")
                }
            }
        }
        waitForIdleSync()
        Thread.sleep(300)
        check(app.store.state.value.timer!!.checkpointMs == 0L)
        runOnMainSync { show.value = false }
        waitFor("timer screen exit checkpoint") { (app.store.state.value.timer?.checkpointMs ?: 0) >= 25_000 }
        val exitMs = app.store.state.value.timer!!.checkpointMs
        val recovered = app.store.state.value.recoverTimer(boot + 1).timer!!
        check(recovered.accumulatedMs == exitMs && recovered.state == TimerState.PAUSED)
        runBlocking {
            app.store.update { it.copy(timer = null).startTimer(base.habits.single().id, day, SystemClock.elapsedRealtime() - 20_000, boot) }
        }
        runOnMainSync { activity.moveTaskToBack(true) }
        waitFor("background checkpoint") { (app.store.state.value.timer?.checkpointMs ?: 0) >= 20_000 }
        result.putString("timerExit", "production TimerScreen disposal checkpoint=${exitMs}ms; reboot recovery matches; background checkpoint>=20000ms")
    }
}
