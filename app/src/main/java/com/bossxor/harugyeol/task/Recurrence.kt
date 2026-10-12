package com.bossxor.harugyeol.task

import com.bossxor.harugyeol.habit.HabitInputException
import com.bossxor.harugyeol.habit.LocalDateSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

enum class RepeatUnit { DAY, WEEK, MONTH, YEAR }

@Serializable
data class RepeatRule(
    val unit: RepeatUnit,
    val interval: Int = 1,
    @Serializable(LocalDateSerializer::class) val anchorDate: LocalDate,
    val weekdays: List<Int>? = null,
    @Serializable(LocalDateSerializer::class) val untilDate: LocalDate? = null,
)

@Serializable
data class TaskOccurrence(
    @Serializable(LocalDateSerializer::class) val scheduledDate: LocalDate,
    val scheduledTime: String? = null,
    val completedAt: Long,
)

@Serializable
data class TaskReminder(val minutesBefore: Int = 0, val snoozedUntil: Long? = null)

fun RepeatRule.validate() {
    if (interval !in 1..999) throw HabitInputException("반복 간격은 1~999예요")
    if (untilDate != null && untilDate.isBefore(anchorDate)) throw HabitInputException("종료일은 시작일 이후로 골라 주세요")
    if (unit == RepeatUnit.WEEK) {
        if (weekdays.isNullOrEmpty() || weekdays.any { it !in 1..7 } || weekdays.distinct().size != weekdays.size)
            throw HabitInputException("반복 요일을 선택해 주세요")
    } else if (weekdays != null) throw HabitInputException("요일은 주 반복에서만 고를 수 있어요")
}

/** 원래 날짜를 보존하므로 1/31 → 2/28 → 3/31, 윤년 2/29 복귀가 가능하다. */
fun RepeatRule.nextOnOrAfter(from: LocalDate): LocalDate? {
    validate()
    val start = maxOf(from, anchorDate)
    val candidate = when (unit) {
        RepeatUnit.DAY -> {
            val days = ChronoUnit.DAYS.between(anchorDate, start)
            anchorDate.plusDays(((days + interval - 1) / interval) * interval)
        }
        RepeatUnit.MONTH -> {
            val base = YearMonth.from(anchorDate)
            val months = ChronoUnit.MONTHS.between(base, YearMonth.from(start))
            var offset = (months / interval) * interval
            fun at(n: Long): LocalDate = base.plusMonths(n).let { it.atDay(minOf(anchorDate.dayOfMonth, it.lengthOfMonth())) }
            if (at(offset).isBefore(start)) offset += interval
            at(offset)
        }
        RepeatUnit.YEAR -> {
            var offset = ((start.year - anchorDate.year) / interval).toLong() * interval
            fun at(n: Long) = anchorDate.plusYears(n)
            if (at(offset).isBefore(start)) offset += interval
            at(offset)
        }
        RepeatUnit.WEEK -> {
            val base = anchorDate.minusDays((anchorDate.dayOfWeek.value - 1).toLong())
            val weeks = ChronoUnit.DAYS.between(base, start) / 7
            var week = (weeks / interval) * interval
            fun inWeek(n: Long) = weekdays!!.sorted().map { base.plusWeeks(n).plusDays((it - 1).toLong()) }
                .firstOrNull { !it.isBefore(start) }
            inWeek(week) ?: run { week += interval; inWeek(week)!! }
        }
    }
    return candidate.takeIf { untilDate == null || !it.isAfter(untilDate) }
}

/** 조회 기간의 예정 회차만 생성한다. 저장 파일에 미래 회차를 만들지 않는다. */
fun Task.scheduledDates(from: LocalDate, through: LocalDate): List<LocalDate> {
    val due = dueDate ?: return emptyList()
    if (deletedAt != null || completedAt != null || from.isAfter(through)) return emptyList()
    val rule = recurrence ?: return listOfNotNull(due.takeIf { it in from..through })
    val result = mutableListOf<LocalDate>()
    var date = rule.nextOnOrAfter(maxOf(from, due))
    while (date != null && !date.isAfter(through)) {
        result += date
        if (date == LocalDate.MAX) break
        date = rule.nextOnOrAfter(date.plusDays(1))
    }
    return result
}
