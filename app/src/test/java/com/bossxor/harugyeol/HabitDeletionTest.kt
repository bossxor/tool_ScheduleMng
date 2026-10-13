package com.bossxor.harugyeol
import com.bossxor.harugyeol.habit.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
class HabitDeletionTest {
    @get:Rule val tmp = TemporaryFolder()
    private val day = LocalDate.of(2026, 10, 14)
    private fun file() = File(tmp.root, "data.json")
    private fun data() = AppData(habits = listOf(
        Habit("remove", "삭제 대상", measureType = MeasureType.DURATION, startDate = day, createdAt = 1, updatedAt = 1,
            goals = listOf(HabitGoal(day, GoalMode.DAILY, targetAmount = 600)), logs = listOf(HabitLog(day, 20)), reminders = listOf("09:00")),
        Habit("keep", "유지", measureType = MeasureType.CHECK, startDate = day, createdAt = 1, updatedAt = 1,
            goals = listOf(HabitGoal(day, GoalMode.DAILY, targetAmount = 1)), logs = listOf(HabitLog(day, 1)))
    ))
    @Test fun directDeletionPersistsWithoutChangingOtherHabitRecords() = runBlocking {
        val store = AppStore.open(file())
        val before = data()
        store.update { before }
        store.update { it.deleteHabit("remove") }
        assertEquals(listOf(before.habits.last()), AppStore.open(file()).state.value.habits)
        assertEquals(store.state.value, AppStore.open(file()).state.value)
    }
    @Test fun timerOwnerDeletionFailsWithoutChangingFileOrState() = runBlocking {
        val store = AppStore.open(file())
        val before = data().copy(timer = HabitTimer("session", "remove", day, TimerState.PAUSED,
            accumulatedMs = 30_000, segmentStartedElapsedMs = null, bootCount = 0, checkpointMs = 30_000))
        store.update { before }
        val bytes = file().readBytes()
        try { store.update { it.deleteHabit("remove") }; org.junit.Assert.fail("Timer owner must not be deleted") }
        catch (_: HabitInputException) { }
        assertEquals(before, store.state.value)
        assertArrayEquals(bytes, file().readBytes())
    }
}
