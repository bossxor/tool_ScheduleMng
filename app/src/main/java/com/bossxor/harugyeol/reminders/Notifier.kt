package com.bossxor.harugyeol.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import com.bossxor.harugyeol.MainActivity
import com.bossxor.harugyeol.R
import com.bossxor.harugyeol.habit.Habit
import com.bossxor.harugyeol.habit.MeasureType
import java.time.LocalDate

object Notifier {
    const val CH_HABIT = "habit"
    const val CH_SUMMARY = "summary"
    const val EXTRA_HABIT = "habitId"
    const val EXTRA_DATE = "date"
    const val ACTION_DONE = "com.bossxor.harugyeol.DONE"

    fun id(habitId: String) = habitId.hashCode()
    private const val SUMMARY_ID = 1

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_HABIT, "습관 알림", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SUMMARY, "하루 요약", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE }
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun postHabit(ctx: Context, h: Habit, today: LocalDate) {
        val notice = habitNotice(h, today)
        val b = Notification.Builder(ctx, CH_HABIT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notice.title)
            .setContentText(notice.body)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
        if (h.measureType == MeasureType.CHECK) {
            // 날짜를 명시해 지난 알림의 액션이 다른 날에 적용되지 않게 한다
            val pi = PendingIntent.getBroadcast(
                ctx, id(h.id),
                Intent(ctx, ActionReceiver::class.java).setAction(ACTION_DONE)
                    .putExtra(EXTRA_HABIT, h.id).putExtra(EXTRA_DATE, today.toString()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_notification), "완료", pi).build())
        }
        ctx.getSystemService(NotificationManager::class.java).notify(id(h.id), b.build())
    }

    fun postSummary(ctx: Context, n: SummaryNotice) {
        val b = Notification.Builder(ctx, CH_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(Notification.BigTextStyle().bigText(n.body))
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
        ctx.getSystemService(NotificationManager::class.java).notify(SUMMARY_ID, b.build())
    }

    fun cancel(ctx: Context, habitId: String) =
        ctx.getSystemService(NotificationManager::class.java).cancel(id(habitId))
}
