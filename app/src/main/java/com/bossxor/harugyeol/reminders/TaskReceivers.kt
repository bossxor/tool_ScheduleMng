package com.bossxor.harugyeol.reminders

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bossxor.harugyeol.HarugyeolApp
import com.bossxor.harugyeol.task.setTaskDone
import com.bossxor.harugyeol.task.snoozeTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class TaskActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        // 알림 액션은 시스템 인증을 요구하며 잠긴 상태에서는 기록을 변경하지 않는다.
        if (ctx.getSystemService(KeyguardManager::class.java).isDeviceLocked) return
        val id = intent.getStringExtra("taskId") ?: return
        val date = try { LocalDate.parse(intent.getStringExtra("date")) } catch (_: Exception) { return }
        if (intent.action !in listOf(Notifier.ACTION_TASK_DONE, Notifier.ACTION_TASK_SNOOZE)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = (ctx.applicationContext as HarugyeolApp).store
                var changed = false
                store.update { current ->
                    val task = current.tasks.firstOrNull { it.id == id }
                    val next = if (task == null || task.dueDate != date || task.deletedAt != null || task.completedAt != null) current
                    else if (intent.action == Notifier.ACTION_TASK_DONE) current.setTaskDone(id, true, System.currentTimeMillis(), date, LocalDate.now())
                    else current.snoozeTask(id, date, intent.getIntExtra("offset", -1), System.currentTimeMillis())
                    changed = next != current
                    next
                }
                if (changed) {
                    Notifier.cancelTask(ctx, id)
                    ReminderScheduler.rescheduleAll(ctx, store.state.value)
                }
            } catch (_: Exception) { /* 파일과 메모리 상태는 기존 값을 유지한다. */ }
            finally { pending.finish() }
        }
    }
}

fun receiveTaskAlarm(receiver: BroadcastReceiver, ctx: Context, intent: Intent) {
    val key = intent.getStringExtra("key") ?: return
    if (!ReminderScheduler.consume(ctx, key, intent.getLongExtra("at", -1))) return
    val pending = receiver.goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val store = (ctx.applicationContext as HarugyeolApp).store
            val now = System.currentTimeMillis()
            var plan: TaskAlarmPlan? = null
            store.update { current ->
                val matched = matchesTaskAlarm(current, key, intent.getLongExtra("at", -1), now)
                plan = matched
                if (matched == null) current else current.copy(tasks = current.tasks.map { task ->
                    if (task.id != matched.taskId) task else task.copy(reminders = task.reminders.map { reminder ->
                        if (reminder.minutesBefore == matched.minutesBefore) reminder.copy(snoozedUntil = null) else reminder
                    })
                })
            }
            plan?.let { p -> store.state.value.tasks.firstOrNull { it.id == p.taskId }?.let { Notifier.postTask(ctx, it, p) } }
            ReminderScheduler.rescheduleAll(ctx, store.state.value)
        } catch (_: Exception) { /* 권한 철회·저장 오류가 핵심 기능을 막지 않는다. */ }
        finally { pending.finish() }
    }
}
