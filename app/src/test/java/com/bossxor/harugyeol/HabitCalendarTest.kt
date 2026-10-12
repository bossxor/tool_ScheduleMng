package com.bossxor.harugyeol
import com.bossxor.harugyeol.habit.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
class HabitCalendarTest {
    private val today = LocalDate.of(2026, 10, 13)
    private val yesterday = today.minusDays(1)
    private fun habit() = Habit("old", "독서", measureType = MeasureType.CHECK, startDate = yesterday, createdAt = 1, updatedAt = 1,
        goals = listOf(HabitGoal(yesterday, GoalMode.DAILY, targetAmount = 1)), logs = listOf(HabitLog(yesterday, 1)))
    @Test fun yesterdayRetainsArchivedHistory() {
        val h = habit().copy(archivedAt = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
        assertEquals(listOf(h), habitsOnDate(listOf(h), yesterday, today))
        assertEquals(1L, habitsOnDate(listOf(h), yesterday, today).single().logOf(yesterday)!!.amount)
        assertTrue(habitsOnDate(listOf(h), today.plusDays(1), today.plusDays(1)).isEmpty())
    }
    @Test fun emptyTargetDaysRemainVisibleButBeforeStartAndFutureDoNot() {
        val h = habit().copy(logs = emptyList())
        assertEquals(listOf(h), habitsOnDate(listOf(h), today, today))
        assertTrue(habitsOnDate(listOf(h), yesterday.minusDays(1), today).isEmpty())
        assertTrue(habitsOnDate(listOf(h), today.plusDays(1), today).isEmpty())
    }
    @Test fun restDaysAreExcludedAndSkippedTargetDatesRetained() {
        val h = habit().copy(goals = listOf(HabitGoal(yesterday, GoalMode.WEEKDAYS, weekdays = listOf(1), targetAmount = 1)), logs = listOf(HabitLog(yesterday, skipped = true)))
        assertTrue(habitsOnDate(listOf(h), today, today).isEmpty())
        assertEquals(listOf(h), habitsOnDate(listOf(h), yesterday, today))
    }
}
