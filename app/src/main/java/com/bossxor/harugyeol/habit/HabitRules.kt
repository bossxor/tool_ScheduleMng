package com.bossxor.harugyeol.habit

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

// 규칙의 기준: docs/technical-design.md 3.4

enum class DayState { REST, SKIPPED, ACHIEVED, WEEK_DONE, PARTIAL, EMPTY }

fun Habit.goalFor(date: LocalDate): HabitGoal? =
    goals.filter { !it.effectiveFrom.isAfter(date) }.maxByOrNull { it.effectiveFrom }

fun Habit.logOf(date: LocalDate): HabitLog? = logs.firstOrNull { it.date == date }

private fun Habit.archivedDate(): LocalDate? =
    archivedAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }

/** 그 날짜가 기록 가능한 대상 날짜인가 (시작일 이후, 보관 전, 목표 주기에 해당) */
fun Habit.isTarget(date: LocalDate): Boolean {
    if (date.isBefore(startDate)) return false
    archivedDate()?.let { if (date.isAfter(it)) return false }
    val g = goalFor(date) ?: return false
    return when (g.mode) {
        GoalMode.DAILY, GoalMode.WEEKLY_COUNT -> true
        GoalMode.WEEKDAYS -> date.dayOfWeek.value in (g.weekdays ?: emptyList())
    }
}

fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

fun Habit.isAchieved(date: LocalDate): Boolean {
    val g = goalFor(date) ?: return false
    val log = logOf(date) ?: return false
    return !log.skipped && log.amount >= g.targetAmount
}

/** 주 N일 목표: 그 주에서 시작일 이후이고 건너뛰지 않은 날짜 수와 targetDays 중 작은 값 */
fun Habit.weekTarget(date: LocalDate): Int {
    val g = goalFor(date) ?: return 0
    if (g.mode != GoalMode.WEEKLY_COUNT) return 0
    val start = weekStart(date)
    val usable = (0..6).count { i ->
        val d = start.plusDays(i.toLong())
        !d.isBefore(startDate) && logOf(d)?.skipped != true
    }
    return minOf(g.targetDays ?: 0, usable)
}

fun Habit.weekAchievedDays(date: LocalDate): Int {
    val start = weekStart(date)
    return (0..6).count { isAchieved(start.plusDays(it.toLong())) }
}

fun Habit.dayState(date: LocalDate): DayState {
    if (!isTarget(date)) return DayState.REST
    val log = logOf(date)
    if (log?.skipped == true) return DayState.SKIPPED
    if (isAchieved(date)) return DayState.ACHIEVED
    val g = goalFor(date)!!
    if (g.mode == GoalMode.WEEKLY_COUNT) {
        val t = weekTarget(date)
        if (t > 0 && weekAchievedDays(date) >= t) return DayState.WEEK_DONE
    }
    return if ((log?.amount ?: 0) > 0) DayState.PARTIAL else DayState.EMPTY
}

val DayState.isDone get() = this == DayState.ACHIEVED || this == DayState.WEEK_DONE
val DayState.counts get() = this != DayState.REST && this != DayState.SKIPPED
