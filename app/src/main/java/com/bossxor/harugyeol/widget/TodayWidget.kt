package com.bossxor.harugyeol.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.bossxor.harugyeol.HarugyeolApp
import com.bossxor.harugyeol.MainActivity
import com.bossxor.harugyeol.R
import com.bossxor.harugyeol.habit.counts
import com.bossxor.harugyeol.habit.dayState
import com.bossxor.harugyeol.habit.isDone
import com.bossxor.harugyeol.task.todayTasks
import java.time.LocalDate

/** 홈 화면과 지원되는 잠금화면 호스트에서 이름 없이 집계만 표시한다. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = refresh(context)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in listOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) refresh(context)
    }
    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java))
            if (ids.isEmpty()) return
            val views = RemoteViews(context.packageName, R.layout.widget_today)
            val today = LocalDate.now()
            views.setTextViewText(R.id.widget_date, "${today.monthValue}월 ${today.dayOfMonth}일 · 오늘")
            try {
                val data = (context.applicationContext as HarugyeolApp).store.state.value
                val states = data.habits.filter { it.archivedAt == null }.map { it.dayState(today) }.filter { it.counts }
                views.setTextViewText(R.id.widget_habits, "습관 ${states.count { it.isDone }}/${states.size} 달성")
                views.setTextViewText(R.id.widget_tasks, "오늘·지난 할 일 ${data.todayTasks(today).size}개")
            } catch (_: Exception) {
                views.setTextViewText(R.id.widget_habits, "데이터 확인이 필요해요")
                views.setTextViewText(R.id.widget_tasks, "앱을 열어 확인해 주세요")
            }
            val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_open, open)
            manager.updateAppWidget(ids, views)
        }
    }
}
