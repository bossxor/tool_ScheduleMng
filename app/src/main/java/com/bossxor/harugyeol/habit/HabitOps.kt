package com.bossxor.harugyeol.habit

import com.bossxor.harugyeol.AppData
import java.time.LocalDate
import java.util.UUID

// 기록 연산. 모든 쓰기는 AppStore의 Mutex 안에서 실행된다(＋1은 증가분이므로 연타가 모두 반영된다).

class HabitInputException(message: String) : IllegalArgumentException(message)

internal fun fail(msg: String): Nothing = throw HabitInputException(msg)

internal fun AppData.mapHabit(id: String, f: (Habit) -> Habit): AppData {
    if (habits.none { it.id == id }) fail("습관을 찾을 수 없어요")
    return copy(habits = habits.map { if (it.id == id) f(it) else it })
}

fun AppData.addHabit(
    name: String, colorIndex: Int, measureType: MeasureType, unit: String?,
    mode: GoalMode, weekdays: List<Int>?, targetDays: Int?, targetAmount: Long,
    startDate: LocalDate, today: LocalDate, now: Long,
): AppData {
    val n = name.trim()
    if (n.isEmpty() || n.length > 100) fail("이름을 1~100자로 입력해 주세요")
    if (colorIndex !in 0..3) fail("색을 선택해 주세요")
    if (startDate.isAfter(today)) fail("시작일은 오늘이나 과거만 고를 수 있어요")
    val u = when (measureType) {
        MeasureType.CHECK -> null
        MeasureType.DURATION -> "분"
        MeasureType.QUANTITY -> unit?.trim()?.takeIf { it.length in 1..20 } ?: fail("단위를 1~20자로 입력해 주세요")
    }
    val amount = if (measureType == MeasureType.CHECK) 1L else targetAmount
    if (amount !in 1..MAX_AMOUNT) fail("목표량을 확인해 주세요")
    when (mode) {
        GoalMode.DAILY -> {}
        GoalMode.WEEKDAYS ->
            if (weekdays.isNullOrEmpty() || weekdays.any { it !in 1..7 }) fail("요일을 하나 이상 선택해 주세요")
        GoalMode.WEEKLY_COUNT -> if (targetDays == null || targetDays !in 1..7) fail("주 목표 일수는 1~7이에요")
    }
    val goal = HabitGoal(
        effectiveFrom = startDate, mode = mode,
        weekdays = if (mode == GoalMode.WEEKDAYS) weekdays!!.distinct().sorted() else null,
        targetDays = if (mode == GoalMode.WEEKLY_COUNT) targetDays else null,
        targetAmount = amount,
    )
    val habit = Habit(
        id = UUID.randomUUID().toString(), name = n, colorIndex = colorIndex, measureType = measureType,
        unit = u, startDate = startDate, sortOrder = (habits.maxOfOrNull { it.sortOrder } ?: -1) + 1,
        createdAt = now, updatedAt = now, goals = listOf(goal),
    )
    return copy(habits = habits + habit)
}

private fun Habit.requireRecordable(date: LocalDate, today: LocalDate) {
    if (date.isAfter(today)) fail("미래 날짜는 기록할 수 없어요")
    if (!isTarget(date)) fail("기록할 수 없는 날이에요")
}

private fun Habit.withLog(date: LocalDate, amount: Long, skipped: Boolean, now: Long): Habit {
    val rest = logs.filter { it.date != date }
    val next = if (amount == 0L && !skipped) rest else (rest + HabitLog(date, amount, skipped)).sortedBy { it.date }
    return copy(logs = next, updatedAt = now)
}

/** 총량을 지정한다(직접 입력·시간 기록·초기화·체크). 건너뛰기는 해제된다. */
fun AppData.setTotal(habitId: String, date: LocalDate, amount: Long, today: LocalDate, now: Long): AppData =
    mapHabit(habitId) { h ->
        h.requireRecordable(date, today)
        val max = if (h.measureType == MeasureType.CHECK) 1L else MAX_AMOUNT
        if (amount !in 0..max) fail("입력한 값을 확인해 주세요")
        h.withLog(date, amount, skipped = false, now)
    }

fun AppData.addAmount(habitId: String, date: LocalDate, delta: Long, today: LocalDate, now: Long): AppData =
    mapHabit(habitId) { h ->
        h.requireRecordable(date, today)
        if (h.measureType == MeasureType.CHECK) fail("완료형은 체크로만 기록해요")
        val next = (h.logOf(date)?.amount ?: 0) + delta
        if (next !in 0..MAX_AMOUNT) fail("입력한 값을 확인해 주세요")
        h.withLog(date, next, skipped = false, now)
    }

fun AppData.setSkipped(habitId: String, date: LocalDate, skipped: Boolean, today: LocalDate, now: Long): AppData =
    mapHabit(habitId) { h ->
        h.requireRecordable(date, today)
        val amount = h.logOf(date)?.amount ?: 0
        if (skipped && amount > 0) fail("기록이 있는 날은 건너뛸 수 없어요")
        h.withLog(date, amount, skipped, now)
    }
