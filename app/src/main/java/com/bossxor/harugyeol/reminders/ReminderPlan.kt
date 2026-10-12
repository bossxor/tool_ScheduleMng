package com.bossxor.harugyeol.reminders

import com.bossxor.harugyeol.AppData
import com.bossxor.harugyeol.habit.DayState
import com.bossxor.harugyeol.habit.Habit
import com.bossxor.harugyeol.habit.amountText
import com.bossxor.harugyeol.habit.counts
import com.bossxor.harugyeol.habit.dayState
import com.bossxor.harugyeol.habit.goalFor
import com.bossxor.harugyeol.habit.isDone
import com.bossxor.harugyeol.habit.logOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

// 알림 계획과 내용 (Android 의존 없음). docs/technical-design.md 5절

const val SUMMARY_KEY = "summary"

data class AlarmPlan(val key: String, val habitId: String?, val time: LocalTime)

fun habitAlarmKey(habitId: String, time: String) = "habit:$habitId:$time"

/** 지금 이후 가장 가까운 발송 시각 */
fun nextTrigger(time: LocalTime, now: LocalDateTime): LocalDateTime {
    val today = now.toLocalDate().atTime(time)
    return if (today.isAfter(now)) today else today.plusDays(1)
}

/**
 * 예약할 알람 전체. 하루 요약이 켜져 있으면 같은 시각의 개별 알림은 예약하지 않는다.
 * 매일 같은 시각에 울리고, 수신할 때 그날의 상태를 다시 검사해 표시 여부를 정한다.
 */
fun planAlarms(data: AppData): List<AlarmPlan> {
    val summary = data.settings.dailySummary
    val plans = mutableListOf<AlarmPlan>()
    for (h in data.habits) {
        if (h.archivedAt != null) continue
        for (t in h.reminders) {
            if (summary.enabled && t == summary.time) continue
            plans += AlarmPlan(habitAlarmKey(h.id, t), h.id, LocalTime.parse(t))
        }
    }
    if (summary.enabled) plans += AlarmPlan(SUMMARY_KEY, null, LocalTime.parse(summary.time))
    return plans
}

/** 개별 알림을 표시할지: 오늘 대상이고 아직 목표를 채우지 않은 경우만 */
fun shouldNotifyHabit(h: Habit, today: LocalDate): Boolean =
    h.archivedAt == null && h.dayState(today).let { it == DayState.EMPTY || it == DayState.PARTIAL }

class HabitNotice(val title: String, val body: String)

fun habitNotice(h: Habit, today: LocalDate): HabitNotice {
    val g = h.goalFor(today)
    val amount = h.logOf(today)?.amount ?: 0
    val body = if (g == null) "오늘 아직 기록이 없어요"
    else if (amount == 0L) "오늘 아직 기록이 없어요"
    else h.amountText(amount, g.targetAmount)
    return HabitNotice(h.name, body)
}

class SummaryNotice(val title: String, val body: String)

/** 오늘 대상이 없으면 null(발송 생략) */
fun summaryNotice(data: AppData, today: LocalDate): SummaryNotice? {
    val habits = data.habits.filter { it.archivedAt == null }
    val states = habits.associateWith { it.dayState(today) }
    val counted = states.filter { it.value.counts }
    if (counted.isEmpty()) return null
    val done = counted.count { it.value.isDone }
    if (done == counted.size) return SummaryNotice("오늘 목표를 모두 달성했어요", "${counted.size}개 습관을 모두 해냈어요")
    val rest = counted.filter { !it.value.isDone }.keys.joinToString(", ") { h ->
        val g = h.goalFor(today)
        val a = h.logOf(today)?.amount ?: 0
        if (g != null && a > 0) "${h.name} ${h.amountText(a, g.targetAmount)}" else h.name
    }
    return SummaryNotice("오늘 $done/${counted.size} 달성", "남은 습관: $rest")
}
