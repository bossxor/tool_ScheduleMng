package com.bossxor.harugyeol

import com.bossxor.harugyeol.habit.GoalMode
import com.bossxor.harugyeol.habit.HabitInputException
import com.bossxor.harugyeol.habit.MeasureType
import com.bossxor.harugyeol.habit.addHabit
import com.bossxor.harugyeol.habit.archiveHabit
import com.bossxor.harugyeol.habit.cancelPendingGoal
import com.bossxor.harugyeol.habit.checkpointTimer
import com.bossxor.harugyeol.habit.discardTimer
import com.bossxor.harugyeol.habit.editHabit
import com.bossxor.harugyeol.habit.elapsedMs
import com.bossxor.harugyeol.habit.goalFor
import com.bossxor.harugyeol.habit.logOf
import com.bossxor.harugyeol.habit.moveHabit
import com.bossxor.harugyeol.habit.pauseTimer
import com.bossxor.harugyeol.habit.pendingGoal
import com.bossxor.harugyeol.habit.recoverTimer
import com.bossxor.harugyeol.habit.resumeTimer
import com.bossxor.harugyeol.habit.saveTimer
import com.bossxor.harugyeol.habit.setTotal
import com.bossxor.harugyeol.habit.startTimer
import com.bossxor.harugyeol.habit.TimerState
import com.bossxor.harugyeol.reminders.SUMMARY_KEY
import com.bossxor.harugyeol.reminders.nextTrigger
import com.bossxor.harugyeol.reminders.planAlarms
import com.bossxor.harugyeol.reminders.shouldNotifyHabit
import com.bossxor.harugyeol.reminders.summaryNotice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ManageTimerReminderTest {
    private val mon = LocalDate.of(2026, 10, 12)
    private val today = mon.plusDays(2) // 수

    private val baseData =
        AppData().addHabit("독서", 1, MeasureType.DURATION, null, GoalMode.DAILY, null, null, 1800, mon, today, 0)

    private fun reading() = baseData

    private fun AppData.h() = habits.single()

    private inline fun rejects(block: () -> Unit) {
        try { block(); fail("거부되어야 한다") } catch (_: HabitInputException) {}
    }

    // 목표 편집
    @Test fun `기록 없는 습관은 목표 변경이 바로 적용된다`() {
        val d = reading().editHabit(reading().h().id, "독서", 1, GoalMode.DAILY, null, null, 3600, emptyList(), today, 1)
        assertEquals(3600, d.h().goalFor(today)!!.targetAmount)
        assertNull(d.h().pendingGoal(today))
    }

    @Test fun `기록 있는 습관은 내일부터 적용되고 재편집하면 예약이 교체된다`() {
        var d = reading()
        d = d.setTotal(d.h().id, today, 600, today, 0)
        d = d.editHabit(d.h().id, "독서", 1, GoalMode.DAILY, null, null, 3600, emptyList(), today, 1)
        assertEquals(1800, d.h().goalFor(today)!!.targetAmount)
        assertEquals(today.plusDays(1), d.h().pendingGoal(today)!!.effectiveFrom)
        d = d.editHabit(d.h().id, "독서", 1, GoalMode.DAILY, null, null, 2400, emptyList(), today, 2)
        assertEquals(1, d.h().goals.count { it.effectiveFrom.isAfter(today) })
        assertEquals(2400, d.h().pendingGoal(today)!!.targetAmount)
        d = d.cancelPendingGoal(d.h().id, today, 3)
        assertNull(d.h().pendingGoal(today))
    }

    @Test fun `주 N일이 포함된 변경은 다음 월요일부터 적용된다`() {
        var d = reading()
        d = d.setTotal(d.h().id, today, 600, today, 0)
        d = d.editHabit(d.h().id, "독서", 1, GoalMode.WEEKLY_COUNT, null, 3, 1800, emptyList(), today, 1)
        assertEquals(LocalDate.of(2026, 10, 19), d.h().pendingGoal(today)!!.effectiveFrom)
    }

    @Test fun `과거 달성 판정은 당시 목표를 유지한다`() {
        var d = reading()
        d = d.setTotal(d.h().id, mon, 1800, today, 0) // 월요일 달성
        d = d.editHabit(d.h().id, "독서", 1, GoalMode.DAILY, null, null, 3600, emptyList(), today, 1)
        assertEquals(1800, d.h().goalFor(mon)!!.targetAmount)
    }

    @Test fun `알림 시각은 중복 제거하고 6개 이상이나 잘못된 형식은 거부한다`() {
        val id = reading().h().id
        val d = reading().editHabit(id, "독서", 1, GoalMode.DAILY, null, null, 1800, listOf("09:00", "09:00", "21:30"), today, 1)
        assertEquals(listOf("09:00", "21:30"), d.h().reminders)
        rejects { reading().editHabit(id, "독서", 1, GoalMode.DAILY, null, null, 1800, listOf("1", "2", "3", "4", "5", "6"), today, 1) }
        rejects { reading().editHabit(id, "독서", 1, GoalMode.DAILY, null, null, 1800, listOf("25:00"), today, 1) }
    }

    @Test fun `순서 변경은 보관하지 않은 습관 안에서 한 칸씩 이동한다`() {
        var d = reading()
        d = d.addHabit("물", 0, MeasureType.CHECK, null, GoalMode.DAILY, null, null, 1, mon, today, 0)
        d = d.addHabit("달리기", 2, MeasureType.CHECK, null, GoalMode.DAILY, null, null, 1, mon, today, 0)
        val ids = d.habits.map { it.id }
        d = d.moveHabit(ids[2], up = true)
        assertEquals(listOf(ids[0], ids[2], ids[1]), d.habits.sortedBy { it.sortOrder }.map { it.id })
        assertEquals(d, d.moveHabit(ids[0], up = true)) // 맨 위는 그대로
    }

    @Test fun `보관하면 이후 날짜는 대상이 아니다`() {
        val d = reading().archiveHabit(reading().h().id, 0)
        assertTrue(d.h().archivedAt != null)
        assertFalse(shouldNotifyHabit(d.h(), today))
    }

    // 타이머
    @Test fun `경과시간은 같은 부팅에서 차이로 계산하고 일시정지·재개로 이어진다`() {
        var d = reading().startTimer(reading().h().id, today, 1_000, boot = 7)
        assertEquals(5_000, d.timer!!.elapsedMs(6_000, 7))
        d = d.pauseTimer(6_000, 7)
        assertEquals(TimerState.PAUSED, d.timer!!.state)
        assertEquals(5_000, d.timer!!.elapsedMs(99_000, 7)) // 일시정지 중에는 늘지 않는다
        d = d.resumeTimer(10_000, 7)
        assertEquals(8_000, d.timer!!.elapsedMs(13_000, 7))
    }

    @Test fun `저장은 초 단위로 내림해 더하고 중복 저장은 무시한다`() {
        var d = reading()
        d = d.setTotal(d.h().id, today, 600, today, 0)
        d = d.startTimer(d.h().id, today, 0, 1)
        val session = d.timer!!.sessionId
        d = d.saveTimer(session, 125_900, 1, 5) // 125.9초 → 125초
        assertEquals(725, d.h().logOf(today)!!.amount)
        assertNull(d.timer)
        assertEquals(d, d.saveTimer(session, 200_000, 1, 6)) // 이미 저장됨
    }

    @Test fun `1초 미만은 저장할 수 없고 다른 세션 요청은 무시한다`() {
        val d = reading().startTimer(reading().h().id, today, 0, 1)
        rejects { d.saveTimer(d.timer!!.sessionId, 900, 1, 0) }
        assertEquals(d, d.saveTimer("other", 50_000, 1, 0))
    }

    @Test fun `재부팅 후에는 마지막 보존 시점에서 일시정지로 복구된다`() {
        var d = reading().startTimer(reading().h().id, today, 1_000, 3)
        d = d.checkpointTimer(61_000, 3) // 60초 시점 보존
        assertEquals(60_000, d.timer!!.checkpointMs)
        d = d.recoverTimer(boot = 4)
        assertEquals(TimerState.PAUSED, d.timer!!.state)
        assertEquals(60_000, d.timer!!.elapsedMs(5, 4))
        assertTrue(d.timer!!.rebooted)
    }

    @Test fun `타이머는 하나만 시작할 수 있고 버리면 기록은 그대로다`() {
        var d = reading()
        d = d.setTotal(d.h().id, today, 300, today, 0)
        d = d.startTimer(d.h().id, today, 0, 1)
        rejects { d.startTimer(d.h().id, today, 0, 1) }
        d = d.discardTimer(d.timer!!.sessionId)
        assertNull(d.timer)
        assertEquals(300, d.h().logOf(today)!!.amount)
    }

    @Test fun `완료형 습관에는 타이머를 시작할 수 없다`() {
        val d = AppData().addHabit("물", 0, MeasureType.CHECK, null, GoalMode.DAILY, null, null, 1, mon, today, 0)
        rejects { d.startTimer(d.h().id, today, 0, 1) }
    }

    @Test fun `30초 전 화면 이탈 보존량은 재부팅 복구에 사용된다`() {
        val base = reading()
        val d = base.startTimer(base.h().id, today, 1_000, 3).checkpointTimer(26_000, 3).recoverTimer(4)
        assertEquals(TimerState.PAUSED, d.timer!!.state)
        assertEquals(25_000L, d.timer!!.accumulatedMs)
    }

    // 알림 계획
    @Test fun `다음 발송 시각은 지금 이후 가장 가까운 시각`() {
        val now = LocalDateTime.of(2026, 10, 12, 10, 0)
        assertEquals(LocalDateTime.of(2026, 10, 12, 21, 30), nextTrigger(LocalTime.of(21, 30), now))
        assertEquals(LocalDateTime.of(2026, 10, 13, 9, 0), nextTrigger(LocalTime.of(9, 0), now))
    }

    @Test fun `하루 요약과 같은 시각의 개별 알림은 예약하지 않는다`() {
        var d = reading()
        d = d.editHabit(d.h().id, "독서", 1, GoalMode.DAILY, null, null, 1800, listOf("09:00", "20:30"), today, 1)
        assertEquals(2, planAlarms(d).size)
        d = d.copy(settings = Settings(dailySummary = DailySummary(enabled = true, time = "20:30")))
        val plan = planAlarms(d)
        assertEquals(2, plan.size) // 09:00 개별 + 요약
        assertTrue(plan.any { it.key == SUMMARY_KEY })
        assertFalse(plan.any { it.habitId != null && it.time == LocalTime.of(20, 30) })
    }

    @Test fun `개별 알림은 대상이고 목표 미달일 때만 표시된다`() {
        var d = reading()
        assertTrue(shouldNotifyHabit(d.h(), today))
        d = d.setTotal(d.h().id, today, 600, today, 0)
        assertTrue(shouldNotifyHabit(d.h(), today)) // 부분 기록은 유지
        d = d.setTotal(d.h().id, today, 1800, today, 0)
        assertFalse(shouldNotifyHabit(d.h(), today)) // 달성하면 중단
    }

    @Test fun `하루 요약은 대상 없음이면 생략하고 전부 달성이면 달성 문구`() {
        assertNull(summaryNotice(AppData(), today))
        var d = reading()
        val partial = summaryNotice(d, today)!!
        assertEquals("오늘 0/1 달성", partial.title)
        d = d.setTotal(d.h().id, today, 1800, today, 0)
        assertEquals("오늘 목표를 모두 달성했어요", summaryNotice(d, today)!!.title)
        assertNotNull(d.h())
    }
}
