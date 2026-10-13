package com.bossxor.harugyeol.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bossxor.harugyeol.HarugyeolApp
import com.bossxor.harugyeol.StoreException
import com.bossxor.harugyeol.habit.setTotal
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/** 예약된 시각에 울린다. 그날의 상태를 다시 확인해 표시 여부를 정하고 다음 날 알람을 예약한다. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        if (key.startsWith("task:")) { receiveTaskAlarm(this, ctx, intent); return }
        val at = intent.getLongExtra("at", -1)
        if (!ReminderScheduler.consume(ctx, key, at)) return
        val store = try { (ctx.applicationContext as HarugyeolApp).store } catch (_: StoreException) { return }
        val data = store.state.value
        val today = LocalDate.now()
        if (java.time.Instant.ofEpochMilli(at).atZone(java.time.ZoneId.systemDefault()).toLocalDate() != today) {
            ReminderScheduler.rescheduleAll(ctx, data)
            return
        }
        if (key == SUMMARY_KEY) {
            if (data.settings.dailySummary.enabled) summaryNotice(data, today)?.let { Notifier.postSummary(ctx, it) }
        } else {
            val parts = key.split(":", limit = 3)
            val habit = data.habits.firstOrNull { it.id == parts.getOrNull(1) }
            if (habit != null && parts.getOrNull(2) in habit.reminders && shouldNotifyHabit(habit, today)) {
                Notifier.postHabit(ctx, habit, today)
            }
        }
        ReminderScheduler.rescheduleAll(ctx, data)
    }
}

/** 알림의 ‘완료’ 버튼. 알림에 담긴 날짜로 기록하며 오늘로 바꾸지 않는다. */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Notifier.ACTION_DONE) return
        val id = intent.getStringExtra(Notifier.EXTRA_HABIT) ?: return
        val date = intent.getStringExtra(Notifier.EXTRA_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val store = try { (ctx.applicationContext as HarugyeolApp).store } catch (_: StoreException) { return }
        try {
            runBlocking { store.update { it.setTotal(id, date, 1, LocalDate.now(), System.currentTimeMillis()) } }
        } catch (_: Exception) {
            return
        }
        Notifier.cancel(ctx, id)
        ReminderScheduler.rescheduleAll(ctx, store.state.value)
    }
}

/** 재부팅·시간 변경·앱 업데이트 후 알람을 다시 예약한다 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val store = try { (ctx.applicationContext as HarugyeolApp).store } catch (_: StoreException) { return }
        val reset = intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)
        if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) ReminderScheduler.resetTimes(ctx)
        ReminderScheduler.rescheduleAll(ctx, store.state.value, reset = reset)
    }
}
