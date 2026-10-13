package com.bossxor.harugyeol.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.bossxor.harugyeol.AppData
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId

/** 알림을 알람으로 예약한다. 알람은 매번 '지금 이후 다음 한 번'만 잡고, 수신하면 다시 예약한다. */
object ReminderScheduler {
    private const val PREFS = "reminder_keys"
    private const val KEYS = "keys"

    private fun pending(ctx: Context, key: String, at: Long): PendingIntent = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, AlarmReceiver::class.java)
            .setData(Uri.Builder().scheme("haru").authority("alarm").appendPath(key).build())
            .putExtra("key", key).putExtra("at", at),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @Synchronized
    fun rescheduleAll(ctx: Context, data: AppData, now: LocalDateTime = LocalDateTime.now(), reset: Boolean = false) {
        Notifier.ensureChannels(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java)
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val keys = prefs.getStringSet(KEYS, emptySet()).orEmpty()
        val previous = keys.filter { prefs.contains("at:$it") }.associateWith { prefs.getLong("at:$it", -1) }
        val zone = ZoneId.systemDefault()
        val next = planAlarms(data, Notifier.summaryAvailable(ctx)).associate { p ->
            val old = previous[p.key]?.takeIf { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() == p.time }
            p.key to (old ?: nextTrigger(p.time, now).atZone(zone).toInstant().toEpochMilli())
        }.toMutableMap()
        taskAlarmPlans(data, now.atZone(zone).toInstant().toEpochMilli(), zone, scheduled = previous)
            .forEach { next[it.key] = it.at }

        keys.filter { reset || previous[it] != next[it] || it !in next }.forEach { key ->
            val previous = PendingIntent.getBroadcast(ctx, 0,
                Intent(ctx, AlarmReceiver::class.java).setData(Uri.Builder().scheme("haru").authority("alarm").appendPath(key).build()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
            previous?.let { am.cancel(it) }
        }

        for ((key, at) in next) {
            if (!reset && previous[key] == at) continue
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, key, at))
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, key, at)) // 정확한 알람이 막힌 경우 부정확하게라도 예약
            }
        }
        prefs.edit().apply {
            keys.forEach { remove("at:$it") }
            next.forEach { (key, at) -> putLong("at:$key", at) }
            putStringSet(KEYS, next.keys)
        }.apply()
        Notifier.cancelInvalidTasks(ctx, data)

        // 이미 목표를 채웠거나 보관된 습관의 게시 알림은 지운다
        val today = LocalDate.now()
        data.habits.filter { !shouldNotifyHabit(it, today) }.forEach { Notifier.cancel(ctx, it.id) }
    }

    /** 수신한 예약만 소모한다. 변경 전 예약이나 중복 수신은 다음 예약을 지우지 않는다. */
    @Synchronized
    fun consume(ctx: Context, key: String, at: Long): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains("at:$key") || prefs.getLong("at:$key", -1) != at) return false
        prefs.edit().remove("at:$key").putStringSet(KEYS, prefs.getStringSet(KEYS, emptySet()).orEmpty() - key).apply()
        return true
    }

    @Synchronized
    fun resetTimes(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().apply {
            prefs.getStringSet(KEYS, emptySet()).orEmpty().forEach { remove("at:$it") }
        }.apply()
    }
}
