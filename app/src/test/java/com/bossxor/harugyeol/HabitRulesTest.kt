package com.bossxor.harugyeol

import com.bossxor.harugyeol.habit.DayState
import com.bossxor.harugyeol.habit.GoalMode
import com.bossxor.harugyeol.habit.HabitInputException
import com.bossxor.harugyeol.habit.MeasureType
import com.bossxor.harugyeol.habit.addAmount
import com.bossxor.harugyeol.habit.addHabit
import com.bossxor.harugyeol.habit.dayState
import com.bossxor.harugyeol.habit.logOf
import com.bossxor.harugyeol.habit.setSkipped
import com.bossxor.harugyeol.habit.setTotal
import com.bossxor.harugyeol.habit.weekTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

class HabitRulesTest {
    // 2026-10-12는 월요일
    private val mon = LocalDate.of(2026, 10, 12)
    private val today = mon.plusDays(2) // 수요일

    private fun daily(type: MeasureType = MeasureType.QUANTITY, target: Long = 800, start: LocalDate = mon) =
        AppData().addHabit("물", 1, type, "잔", GoalMode.DAILY, null, null, target, start, today, 0)

    private fun AppData.h() = habits.single()

    private inline fun rejects(block: () -> Unit) {
        try { block(); fail("거부되어야 한다") } catch (_: HabitInputException) {}
    }

    @Test fun `더하기는 연타해도 모두 반영되고 목표 이상이면 달성`() {
        var d = daily()
        repeat(8) { d = d.addAmount(d.h().id, today, 100, today, 0) }
        assertEquals(800, d.h().logOf(today)!!.amount)
        assertEquals(DayState.ACHIEVED, d.h().dayState(today))
        d = d.addAmount(d.h().id, today, 100, today, 0) // 초과량 보존
        assertEquals(900, d.h().logOf(today)!!.amount)
    }

    @Test fun `부분 기록은 달성이 아니고 0으로 되돌리면 기록이 사라진다`() {
        var d = daily()
        d = d.addAmount(d.h().id, today, 300, today, 0)
        assertEquals(DayState.PARTIAL, d.h().dayState(today))
        d = d.setTotal(d.h().id, today, 0, today, 0)
        assertNull(d.h().logOf(today))
        assertEquals(DayState.EMPTY, d.h().dayState(today))
    }

    @Test fun `완료형은 중복 체크해도 하나다`() {
        var d = daily(MeasureType.CHECK)
        repeat(3) { d = d.setTotal(d.h().id, today, 1, today, 0) }
        assertEquals(1, d.h().logs.size)
        assertEquals(DayState.ACHIEVED, d.h().dayState(today))
    }

    @Test fun `미래·시작일 이전·음수·범위 초과는 거부한다`() {
        val d = daily()
        val id = d.h().id
        rejects { d.setTotal(id, today.plusDays(1), 100, today, 0) }
        rejects { d.setTotal(id, mon.minusDays(1), 100, today, 0) }
        rejects { d.addAmount(id, today, -100, today, 0) }
        rejects { d.setTotal(id, today, 1_000_000_001, today, 0) }
    }

    @Test fun `선택 요일 습관은 비대상 날짜를 기록할 수 없다`() {
        val d = AppData().addHabit("독서", 0, MeasureType.DURATION, null, GoalMode.WEEKDAYS, listOf(1, 3, 5), null, 1800, mon, today, 0)
        assertEquals(DayState.REST, d.h().dayState(mon.plusDays(1))) // 화요일
        rejects { d.setTotal(d.h().id, mon.plusDays(1), 600, today, 0) }
    }

    @Test fun `건너뛰기는 기록이 없을 때만 되고 기록하면 해제된다`() {
        var d = daily()
        val id = d.h().id
        d = d.setSkipped(id, today, true, today, 0)
        assertEquals(DayState.SKIPPED, d.h().dayState(today))
        d = d.addAmount(id, today, 100, today, 0)
        assertEquals(DayState.PARTIAL, d.h().dayState(today))
        rejects { d.setSkipped(id, today, true, today, 0) }
    }

    @Test fun `주 N일 목표는 건너뛴 날만큼 줄고 채우면 주 완료`() {
        var d = AppData().addHabit("운동", 3, MeasureType.CHECK, null, GoalMode.WEEKLY_COUNT, null, 3, 1, mon, mon.plusDays(6), 0)
        val id = d.h().id
        val sun = mon.plusDays(6)
        assertEquals(3, d.h().weekTarget(sun))
        (0..4).forEach { d = d.setSkipped(id, mon.plusDays(it.toLong()), true, sun, 0) } // 월~금 건너뜀
        assertEquals(2, d.h().weekTarget(sun))
        d = d.setTotal(id, mon.plusDays(5), 1, sun, 0)
        d = d.setTotal(id, sun, 1, sun, 0)
        assertEquals(DayState.ACHIEVED, d.h().dayState(sun))
    }

    @Test fun `시작일이 주 중간이면 첫 주 목표는 남은 날짜 수로 제한된다`() {
        val sat = mon.plusDays(5)
        val d = AppData().addHabit("운동", 3, MeasureType.CHECK, null, GoalMode.WEEKLY_COUNT, null, 3, 1, sat, sat, 0)
        assertEquals(2, d.h().weekTarget(sat))
    }
}
