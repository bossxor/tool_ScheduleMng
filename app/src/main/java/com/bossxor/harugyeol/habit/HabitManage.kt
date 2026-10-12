package com.bossxor.harugyeol.habit

import com.bossxor.harugyeol.AppData
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

// 습관 편집·보관·순서 (docs/product-design.md 5절 '습관 설정·관리')

fun Habit.hasRecords() = logs.any { it.amount > 0 || it.skipped }

/** 아직 적용되지 않은 예약된 목표 변경 */
fun Habit.pendingGoal(today: LocalDate): HabitGoal? =
    goals.filter { it.effectiveFrom.isAfter(today) }.minByOrNull { it.effectiveFrom }

private val TIME = Regex("""([01]\d|2[0-3]):[0-5]\d""")

fun AppData.editHabit(
    id: String, name: String, colorIndex: Int,
    mode: GoalMode, weekdays: List<Int>?, targetDays: Int?, targetAmount: Long,
    reminders: List<String>, today: LocalDate, now: Long,
): AppData = mapHabit(id) { h ->
    val n = name.trim()
    if (n.isEmpty() || n.length > 100) fail("이름을 1~100자로 입력해 주세요")
    if (colorIndex !in 0..3) fail("색을 선택해 주세요")
    val amount = if (h.measureType == MeasureType.CHECK) 1L else targetAmount
    if (amount !in 1..MAX_AMOUNT) fail("목표량을 확인해 주세요")
    when (mode) {
        GoalMode.DAILY -> {}
        GoalMode.WEEKDAYS ->
            if (weekdays.isNullOrEmpty() || weekdays.any { it !in 1..7 }) fail("요일을 하나 이상 선택해 주세요")
        GoalMode.WEEKLY_COUNT -> if (targetDays == null || targetDays !in 1..7) fail("주 목표 일수는 1~7이에요")
    }
    val times = reminders.distinct()
    if (times.size > 5) fail("알림 시각은 5개까지 추가할 수 있어요")
    if (times.any { !TIME.matches(it) }) fail("알림 시각을 확인해 주세요")

    val goal = HabitGoal(
        effectiveFrom = h.startDate, mode = mode,
        weekdays = if (mode == GoalMode.WEEKDAYS) weekdays!!.distinct().sorted() else null,
        targetDays = if (mode == GoalMode.WEEKLY_COUNT) targetDays else null,
        targetAmount = amount,
    )
    val current = h.goalFor(today) ?: h.goals.first()
    val same = current.mode == goal.mode && current.weekdays == goal.weekdays &&
        current.targetDays == goal.targetDays && current.targetAmount == goal.targetAmount
    val applied = h.goals.filter { !it.effectiveFrom.isAfter(today) } // 예약된 변경은 새 편집으로 교체한다
    val goals = when {
        !h.hasRecords() -> listOf(goal) // 기록이 없으면 바로 적용
        same -> applied
        else -> {
            val weekly = current.mode == GoalMode.WEEKLY_COUNT || mode == GoalMode.WEEKLY_COUNT
            val from = if (weekly) today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)) else today.plusDays(1)
            applied + goal.copy(effectiveFrom = from)
        }
    }
    h.copy(name = n, colorIndex = colorIndex, goals = goals, reminders = times.sorted(), updatedAt = now)
}

fun AppData.cancelPendingGoal(id: String, today: LocalDate, now: Long): AppData = mapHabit(id) { h ->
    h.copy(goals = h.goals.filter { !it.effectiveFrom.isAfter(today) }, updatedAt = now)
}

fun AppData.archiveHabit(id: String, now: Long): AppData {
    if (timer?.habitId == id) fail("실행 중인 타이머를 먼저 저장하거나 버려 주세요")
    return mapHabit(id) { it.copy(archivedAt = now, updatedAt = now) }
}

fun AppData.unarchiveHabit(id: String, now: Long): AppData = mapHabit(id) { it.copy(archivedAt = null, updatedAt = now) }

fun AppData.deleteHabit(id: String): AppData {
    if (timer?.habitId == id) fail("실행 중인 타이머를 먼저 저장하거나 버려 주세요")
    return copy(habits = habits.filter { it.id != id })
}

/** 보관하지 않은 습관 목록 안에서 한 칸 이동한다 */
fun AppData.moveHabit(id: String, up: Boolean): AppData {
    val active = habits.filter { it.archivedAt == null }.sortedBy { it.sortOrder }
    val i = active.indexOfFirst { it.id == id }
    val j = if (up) i - 1 else i + 1
    if (i < 0 || j !in active.indices) return this
    val a = active[i]
    val b = active[j]
    return copy(habits = habits.map {
        when (it.id) {
            a.id -> it.copy(sortOrder = b.sortOrder)
            b.id -> it.copy(sortOrder = a.sortOrder)
            else -> it
        }
    })
}
