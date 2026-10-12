package com.bossxor.harugyeol.habit

import java.time.LocalDate
import java.time.YearMonth

// 통계는 매번 원본 목표·기록에서 계산한다 (docs/technical-design.md 3.4)

enum class StreakUnit { DAY, WEEK }

data class Streak(val unit: StreakUnit, val current: Int, val longest: Int)

data class Rate(val achieved: Int, val total: Int) {
    val percent get() = if (total == 0) 0 else achieved * 100 / total
}

private fun Habit.isWeekly(date: LocalDate) = goalFor(date)?.mode == GoalMode.WEEKLY_COUNT

/** 오늘 기준 목표 주기의 연속 달성. 아직 끝나지 않은 오늘·이번 주는 끊지 않는다. */
fun Habit.streak(today: LocalDate): Streak {
    val weekly = isWeekly(today)
    return if (weekly) weeklyStreak(today) else dailyStreak(today)
}

private fun Habit.dailyStreak(today: LocalDate): Streak {
    var current = 0
    var longest = 0
    var run = 0
    var currentDone = false
    var d = startDate
    while (!d.isAfter(today)) {
        if (isWeekly(d)) { run = 0 } // 다른 단위의 구간은 이어 세지 않는다
        else if (isTarget(d) && logOf(d)?.skipped != true) {
            if (isAchieved(d)) run++
            else if (d != today) run = 0
        }
        longest = maxOf(longest, run)
        d = d.plusDays(1)
    }
    current = run
    return Streak(StreakUnit.DAY, current, longest)
}

private fun Habit.weeklyStreak(today: LocalDate): Streak {
    var run = 0
    var longest = 0
    var w = weekStart(startDate)
    val thisWeek = weekStart(today)
    while (!w.isAfter(thisWeek)) {
        val g = goalFor(w.plusDays(6)) ?: goalFor(w)
        if (g?.mode != GoalMode.WEEKLY_COUNT) { run = 0 }
        else {
            val t = weekTarget(w)
            if (t > 0) {
                if (weekAchievedDays(w) >= t) run++
                else if (w != thisWeek) run = 0
            }
        }
        longest = maxOf(longest, run)
        w = w.plusWeeks(1)
    }
    return Streak(StreakUnit.WEEK, run, longest)
}

/**
 * 달성률. 오늘과 이번 주는 포함하지 않는다(어제까지 끝난 날·주만).
 * 일 목표: 달성한 대상 날짜 / 대상 날짜(건너뜀 제외). 주 목표: 각 주 min(달성 일수, 주 목표) 합 / 주 목표 합.
 * 기간 경계에 걸친 주는 제외하되 시작일로 잘린 첫 주는 포함한다.
 */
fun Habit.rate(from: LocalDate, to: LocalDate, today: LocalDate): Rate? {
    val start = maxOf(from, startDate)
    val end = minOf(to, today.minusDays(1))
    if (end.isBefore(start)) return null
    var achieved = 0
    var total = 0
    // 일 목표 구간
    var d = start
    while (!d.isAfter(end)) {
        if (!isWeekly(d) && isTarget(d) && logOf(d)?.skipped != true) {
            total++
            if (isAchieved(d)) achieved++
        }
        d = d.plusDays(1)
    }
    // 주 목표 구간
    var w = weekStart(start)
    while (!w.isAfter(end)) {
        val sunday = w.plusDays(6)
        val inside = sunday <= end && (w >= start || start == startDate)
        if (inside && isWeekly(sunday)) {
            val t = weekTarget(w)
            if (t > 0) {
                total += t
                achieved += minOf(weekAchievedDays(w), t)
            }
        }
        w = w.plusWeeks(1)
    }
    return if (total == 0) null else Rate(achieved, total)
}

fun Habit.monthRate(month: YearMonth, today: LocalDate) = rate(month.atDay(1), month.atEndOfMonth(), today)

fun Habit.totalRate(today: LocalDate) = rate(startDate, today, today)

fun Habit.skippedCount(from: LocalDate, to: LocalDate) =
    logs.count { it.skipped && !it.date.isBefore(from) && !it.date.isAfter(to) }

/** 대상 날짜의 목표 진행 평균. 휴식/건너뜀 제외, 달성은 100%. */
fun dayProgress(habits: List<Habit>, date: LocalDate): Float {
    val progress = habits.mapNotNull { h ->
        val state = h.dayState(date)
        if (!state.counts) null else if (state.isDone) 1f
        else {
            val target = h.goalFor(date)?.targetAmount ?: 0
            if (target <= 0) 0f else ((h.logOf(date)?.amount ?: 0).toDouble() / target).coerceIn(0.0, 1.0).toFloat()
        }
    }
    return if (progress.isEmpty()) 0f else progress.average().toFloat()
}
