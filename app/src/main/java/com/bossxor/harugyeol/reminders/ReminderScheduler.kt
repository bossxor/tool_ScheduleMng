package com.bossxor.harugyeol.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bossxor.harugyeol.AppData
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 알림을 알람으로 예약한다. 알람은 매번 '지금 이후 다음 한 번'만 잡고, 수신하면 다시 예약한다. */
object ReminderScheduler {
    private const val PREFS = "reminder_keys"
    private const val KEYS = "keys"

    private fun pending(ctx: Context, key: String, at: Long = 0): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, AlarmReceiver::class.java)
            .setData(Uri.Builder().scheme("haru").authority("alarm").appendPath(key).build())
            .putExtra("key", key).putExtra("at", at),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @Synchronized
    fun rescheduleAll(ctx: Context, data: AppData, now: LocalDateTime = LocalDateTime.now()) {
        Notifier.ensureChannels(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getStringSet(KEYS, emptySet()).orEmpty().forEach { key ->
            val previous = PendingIntent.getBroadcast(ctx, 0,
                Intent(ctx, AlarmReceiver::class.java).setData(Uri.Builder().scheme("haru").authority("alarm").appendPath(key).build()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            previous?.let { am.cancel(it) }
        }

        val plans = planAlarms(data)
        val zone = ZoneId.systemDefault()
        for (p in plans) {
            val at = nextTrigger(p.time, now).atZone(zone).toInstant().toEpochMilli()
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, p.key))
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, p.key)) // 정확한 알람이 막힌 경우 부정확하게라도 예약
            }
        }
        val taskPlans = taskAlarmPlans(data, now.atZone(zone).toInstant().toEpochMilli(), zone)
        for (p in taskPlans) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.at, pending(ctx, p.key, p.at))
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, p.at, pending(ctx, p.key, p.at))
            }
        }
        prefs.edit().putStringSet(KEYS, (plans.map { it.key } + taskPlans.map { it.key }).toSet()).apply()
        Notifier.cancelInvalidTasks(ctx, data)

        // 이미 목표를 채웠거나 보관된 습관의 게시 알림은 지운다
        val today = LocalDate.now()
        data.habits.filter { !shouldNotifyHabit(it, today) }.forEach { Notifier.cancel(ctx, it.id) }
    }
}
