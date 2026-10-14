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
    const val CH_TASK = "task_v1"
    const val ACTION_TASK_DONE = "com.bossxor.harugyeol.TASK_DONE"
    const val ACTION_TASK_SNOOZE = "com.bossxor.harugyeol.TASK_SNOOZE"
    const val CH_HABIT = "habit_v2"
    const val CH_SUMMARY = "summary_v2"
    const val EXTRA_HABIT = "habitId"
    const val EXTRA_DATE = "date"
    const val ACTION_DONE = "com.bossxor.harugyeol.DONE"

    fun id(habitId: String) = habitId.hashCode()
    private const val SUMMARY_ID = 1

    fun summaryAvailable(ctx: Context): Boolean {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        return nm.areNotificationsEnabled() && nm.getNotificationChannel(CH_SUMMARY)?.importance?.let { it > NotificationManager.IMPORTANCE_NONE } == true
    }

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_TASK, "할 일 알림", NotificationManager.IMPORTANCE_HIGH).apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE })
        // 중요도는 채널을 만든 뒤 바꿀 수 없어 새 채널을 쓰고 이전 채널은 지운다
        nm.deleteNotificationChannel("habit")
        nm.deleteNotificationChannel("summary")
        nm.createNotificationChannel(
            NotificationChannel(CH_HABIT, "습관 알림", NotificationManager.IMPORTANCE_HIGH)
                .apply { lockscreenVisibility = Notification.VISIBILITY_PRIVATE }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_SUMMARY, "하루 요약", NotificationManager.IMPORTANCE_HIGH)
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
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
        if (h.measureType == MeasureType.CHECK) {
            // 날짜를 명시해 지난 알림의 액션이 다른 날에 적용되지 않게 한다
            val pi = PendingIntent.getBroadcast(
                ctx, id(h.id),
                Intent(ctx, ActionReceiver::class.java).setAction(ACTION_DONE)
                    .setData(android.net.Uri.parse("haru://habit-done/${h.id}/$today"))
                    .putExtra(EXTRA_HABIT, h.id).putExtra(EXTRA_DATE, today.toString()),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_notification), "완료", pi).setAuthenticationRequired(true).build())
        }
        if (h.measureType != MeasureType.CHECK) {
            val record = PendingIntent.getActivity(ctx, 0,
                Intent(ctx, MainActivity::class.java).setData(android.net.Uri.parse("haru://habit-record/${h.id}/$today"))
                    .putExtra(EXTRA_HABIT, h.id).putExtra(EXTRA_DATE, today.toString()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            b.addAction(Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_notification), "기록하기", record).setAuthenticationRequired(true).build())
        }
        ctx.getSystemService(NotificationManager::class.java).notify(id(h.id), b.build())
    }

    fun postSummary(ctx: Context, n: SummaryNotice) {
        val b = Notification.Builder(ctx, CH_SUMMARY)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(n.title)
            .setContentText(n.body)
            .setStyle(Notification.BigTextStyle().bigText(n.body))
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(ctx))
            .setAutoCancel(true)
        ctx.getSystemService(NotificationManager::class.java).notify(SUMMARY_ID, b.build())
    }

    fun postTask(ctx: Context, task: com.bossxor.harugyeol.task.Task, plan: TaskAlarmPlan) {
        ensureChannels(ctx)
        val launch = PendingIntent.getActivity(ctx, 0,
            Intent(ctx, MainActivity::class.java).setData(android.net.Uri.parse("haru://task/${task.id}"))
                .putExtra("taskId", task.id).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        fun action(name: String): PendingIntent = PendingIntent.getBroadcast(ctx, 0,
            Intent(ctx, TaskActionReceiver::class.java).setAction(name)
                .setData(android.net.Uri.parse("haru://task-action/${task.id}/${plan.date}/${plan.minutesBefore}/$name"))
                .putExtra("taskId", task.id).putExtra("date", plan.date.toString()).putExtra("offset", plan.minutesBefore),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val public = Notification.Builder(ctx, CH_TASK).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("하루결 할 일 알림").setContentText("잠금을 해제해 확인해 주세요").build()
        val notification = Notification.Builder(ctx, CH_TASK).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title).setContentText("${plan.date} ${task.dueTime ?: "종일"}")
            .setVisibility(Notification.VISIBILITY_PRIVATE).setPublicVersion(public).setContentIntent(launch).setAutoCancel(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_notification), "완료", action(ACTION_TASK_DONE)).setAuthenticationRequired(true).build())
            .addAction(Notification.Action.Builder(Icon.createWithResource(ctx, R.drawable.ic_notification), "10분 미루기", action(ACTION_TASK_SNOOZE)).setAuthenticationRequired(true).build())
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify("task:${task.id}", 0, notification)
        ctx.getSharedPreferences("posted_tasks", Context.MODE_PRIVATE).edit().putString(task.id, task.notificationFingerprint()).apply()
    }

    fun cancelTask(ctx: Context, id: String) {
        ctx.getSystemService(NotificationManager::class.java).cancel("task:$id", 0)
        ctx.getSharedPreferences("posted_tasks", Context.MODE_PRIVATE).edit().remove(id).apply()
    }

    fun cancelInvalidTasks(ctx: Context, data: com.bossxor.harugyeol.AppData) {
        val prefs = ctx.getSharedPreferences("posted_tasks", Context.MODE_PRIVATE)
        prefs.all.forEach { (id, fingerprint) ->
            val task = data.tasks.firstOrNull { it.id == id }
            if (task == null || task.deletedAt != null || task.completedAt != null || task.notificationFingerprint() != fingerprint) cancelTask(ctx, id)
        }
    }

    fun cancel(ctx: Context, habitId: String) =
        ctx.getSystemService(NotificationManager::class.java).cancel(id(habitId))
}
