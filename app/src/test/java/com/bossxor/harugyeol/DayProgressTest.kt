package com.bossxor.harugyeol
import com.bossxor.harugyeol.habit.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
class DayProgressTest {
    private val day = LocalDate.of(2026, 10, 13)
    private fun habit(id: String, amount: Long) = Habit(id, id, measureType = MeasureType.QUANTITY, unit = "잔", startDate = day,
        createdAt = 1, updatedAt = 1, goals = listOf(HabitGoal(day, GoalMode.DAILY, targetAmount = 100)), logs = listOf(HabitLog(day, amount)))
    @Test fun partialOnlyIsVisible() { assertEquals(0.5f, dayProgress(listOf(habit("half", 50)), day), 0.0001f) }
    @Test fun mixedGoalsAreEquallyWeightedAndCapped() { assertEquals(0.5f, dayProgress(listOf(habit("done", 200), habit("half", 50), habit("empty", 0)), day), 0.0001f) }
    @Test fun skippedAndRestAreExcluded() {
        val skip = habit("skip", 0).copy(logs = listOf(HabitLog(day, skipped = true)))
        val rest = habit("rest", 0).copy(startDate = day.plusDays(1))
        assertEquals(0.5f, dayProgress(listOf(habit("half", 50), skip, rest), day), 0.0001f)
        assertEquals(0f, dayProgress(listOf(skip, rest), day), 0f)
    }
    @Test fun weeklyTargetMetIsComplete() {
        val h = habit("week", 0).copy(startDate = day.minusDays(1), goals = listOf(HabitGoal(day.minusDays(1), GoalMode.WEEKLY_COUNT, targetDays = 1, targetAmount = 100)), logs = listOf(HabitLog(day.minusDays(1), 100)))
        assertEquals(1f, dayProgress(listOf(h), day), 0f)
    }
}
