package com.bossxor.harugyeol

import com.bossxor.harugyeol.habit.GoalMode
import com.bossxor.harugyeol.habit.MeasureType
import com.bossxor.harugyeol.habit.Rate
import com.bossxor.harugyeol.habit.StreakUnit
import com.bossxor.harugyeol.habit.addHabit
import com.bossxor.harugyeol.habit.rate
import com.bossxor.harugyeol.habit.setSkipped
import com.bossxor.harugyeol.habit.setTotal
import com.bossxor.harugyeol.habit.streak
import com.bossxor.harugyeol.habit.totalRate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HabitStatsTest {
    private val mon = LocalDate.of(2026, 10, 12) // 월요일

    private fun habit(mode: GoalMode = GoalMode.DAILY, weekdays: List<Int>? = null, days: Int? = null,
                      start: LocalDate = mon, today: LocalDate): AppData =
        AppData().addHabit("운동", 0, MeasureType.CHECK, null, mode, weekdays, days, 1, start, today, 0)

    private fun AppData.done(vararg dates: LocalDate, today: LocalDate): AppData =
        dates.fold(this) { d, date -> d.setTotal(d.habits.single().id, date, 1, today, 0) }

    private fun d(offset: Int) = mon.plusDays(offset.toLong())

    @Test fun `오늘이 비어 있어도 연속은 끊기지 않는다`() {
        val today = d(3) // 목
        val data = habit(today = today).done(d(0), d(1), d(2), today = today)
        val s = data.habits.single().streak(today)
        assertEquals(StreakUnit.DAY, s.unit)
        assertEquals(3, s.current)
        assertEquals(3, s.longest)
    }

    @Test fun `기록 없는 지난 날은 연속을 끊고 최장은 남는다`() {
        val today = d(4) // 금
        val data = habit(today = today).done(d(0), d(1), d(3), today = today)
        val s = data.habits.single().streak(today)
        assertEquals(1, s.current) // 수요일이 비어 끊김, 목요일만
        assertEquals(2, s.longest)
    }

    @Test fun `비대상 요일과 건너뛴 날은 연속을 끊지 않는다`() {
        val today = d(4) // 금
        var data = habit(GoalMode.WEEKDAYS, listOf(1, 3, 5), today = today).done(d(0), d(2), today = today)
        assertEquals(2, data.habits.single().streak(today).current) // 화요일은 끊김이 아님
        val t2 = d(3) // 목: 수요일까지 달성, 오늘은 진행 중
        val daily = habit(today = t2).done(d(0), d(2), today = t2)
        data = daily.setSkipped(daily.habits.single().id, d(1), true, t2, 0)
        assertEquals(2, data.habits.single().streak(t2).current)
    }

    @Test fun `달성률은 어제까지 대상 날짜만 센다`() {
        val today = d(2) // 수
        val data = habit(today = today).done(d(0), today = today)
        val h = data.habits.single()
        assertEquals(Rate(1, 2), h.totalRate(today)) // 월 달성, 화 미달성, 오늘은 제외
        assertEquals(50, h.totalRate(today)!!.percent)
    }

    @Test fun `집계할 날이 없으면 null`() {
        val data = habit(today = mon)
        assertNull(data.habits.single().totalRate(mon))
    }

    @Test fun `건너뛴 날은 달성률 분모에서 빠진다`() {
        val today = d(3)
        var data = habit(today = today).done(d(0), today = today)
        data = data.setSkipped(data.habits.single().id, d(1), true, today, 0)
        assertEquals(Rate(1, 2), data.habits.single().totalRate(today)) // 월 달성, 화 건너뜀, 수 미달성
    }

    @Test fun `주 N일 목표는 주 단위로 연속과 달성률을 센다`() {
        val start = mon.minusDays(7) // 지난주 월요일
        val today = mon              // 이번 주 월요일
        val data = habit(GoalMode.WEEKLY_COUNT, days = 2, start = start, today = today)
            .done(start, start.plusDays(1), today = today)
        val h = data.habits.single()
        val s = h.streak(today)
        assertEquals(StreakUnit.WEEK, s.unit)
        assertEquals(1, s.current) // 이번 주는 진행 중이라 끊지 않는다
        assertEquals(1, s.longest)
        assertEquals(Rate(2, 2), h.rate(start, today, today))
    }
}
