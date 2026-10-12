package com.bossxor.harugyeol

import com.bossxor.harugyeol.backup.Backup
import com.bossxor.harugyeol.backup.BackupException
import com.bossxor.harugyeol.habit.*
import com.bossxor.harugyeol.task.INBOX_ID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

class RecoveryMigrationTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun file() = File(temporary.root, "data.json")
    private fun runningData(): AppData {
        val day = LocalDate.of(2026, 10, 12)
        val habit = Habit("duration", "독서", measureType = MeasureType.DURATION, unit = "분", startDate = day,
            createdAt = 1, updatedAt = 1, goals = listOf(HabitGoal(day, GoalMode.DAILY, targetAmount = 600)))
        val timer = HabitTimer("session", habit.id, day, TimerState.RUNNING, accumulatedMs = 1_000,
            segmentStartedElapsedMs = 9_000, bootCount = 7, checkpointMs = 6_000)
        return AppData(habits = listOf(habit), timer = timer)
    }
    @Test fun schemaOneIsReadWithoutMutationAndOriginalBytesArePreservedOnce() = runBlocking {
        val original = """{ "schemaVersion":1, "tasks":[{"id":"old","title":"기존 작업","createdAt":7,"updatedAt":8}] }""".toByteArray()
        file().writeBytes(original)
        val store = AppStore.open(file())
        assertEquals(2, store.state.value.schemaVersion)
        assertEquals(INBOX_ID, store.state.value.tasks.single().listId)
        assertArrayEquals(original, file().readBytes())
        assertFalse(File(temporary.root, "migration-v1.json").exists())
        store.update { it.copy(settings = it.settings.copy(theme = "DARK")) }
        val preserved = File(temporary.root, "migration-v1.json")
        assertArrayEquals(original, preserved.readBytes())
        assertEquals("기존 작업", AppStore.open(file()).state.value.tasks.single().title)
        store.update { it.copy(settings = it.settings.copy(theme = "LIGHT")) }
        assertArrayEquals(original, preserved.readBytes())
    }
    @Test fun invalidRestoreDoesNotMutateStateDataOrExistingSnapshot() = runBlocking {
        val store = AppStore.open(file())
        store.update { it.copy(settings = it.settings.copy(theme = "DARK")) }
        store.createSnapshot()
        val before = store.state.value
        val bytes = file().readBytes()
        val snapshot = File(temporary.root, "snapshot.json").readBytes()
        try { store.replaceAll(AppData(lists = emptyList())); org.junit.Assert.fail("Invalid restore should fail") } catch (_: BackupException) { }
        assertEquals(before, store.state.value)
        assertArrayEquals(bytes, file().readBytes())
        assertArrayEquals(snapshot, File(temporary.root, "snapshot.json").readBytes())
    }
    @Test fun recoveryPreservesExactCorruptOriginalAndNormalizesRunningTimer() {
        val original = byteArrayOf(0, 1, 2, 13, 10, 127, -1)
        file().writeBytes(original)
        val store = AppStore.recover(file(), runningData())
        val preserved = temporary.root.listFiles()!!.single { it.name.startsWith("data-original-") }
        assertArrayEquals(original, preserved.readBytes())
        val timer = store.state.value.timer!!
        assertEquals(TimerState.PAUSED, timer.state)
        assertEquals(6_000L, timer.accumulatedMs)
        assertEquals(6_000L, timer.checkpointMs)
        assertNull(timer.segmentStartedElapsedMs)
        assertTrue(timer.rebooted)
        assertEquals(store.state.value, Backup.decode(file().readText()))
    }
    @Test fun normalRestorePausesIncomingTimerAtCheckpointAndSnapshotsPreviousData() = runBlocking {
        val store = AppStore.open(file())
        store.update { it.copy(settings = it.settings.copy(theme = "DARK")) }
        val before = store.state.value
        store.replaceAll(runningData())
        assertEquals(TimerState.PAUSED, store.state.value.timer!!.state)
        assertEquals(6_000L, store.state.value.timer!!.accumulatedMs)
        assertEquals(before, AppStore.readSnapshot(file()))
    }
    @Test fun anyCurrentTimerBlocksRestoreAndSnapshotUndoWithoutMutation() = runBlocking {
        for (state in listOf(TimerState.RUNNING, TimerState.PAUSED)) {
            val current = runningData().let { data -> if (state == TimerState.RUNNING) data else AppStore.normalizeRestored(data) }
            file().writeText(Backup.encode(current))
            val store = AppStore.open(file())
            store.createSnapshot()
            val dataBytes = file().readBytes()
            val snapshotBytes = File(temporary.root, "snapshot.json").readBytes()
            try { store.replaceAll(AppData()); org.junit.Assert.fail("Current timer must block restore") } catch (_: StoreException) { }
            try { store.restoreSnapshot(); org.junit.Assert.fail("Current timer must block undo") } catch (_: StoreException) { }
            assertEquals(current, store.state.value)
            assertArrayEquals(dataBytes, file().readBytes())
            assertArrayEquals(snapshotBytes, File(temporary.root, "snapshot.json").readBytes())
        }
    }
    @Test fun invalidRecoveryLeavesOriginalAndDoesNotCreatePreservationFile() {
        val original = "broken original".toByteArray()
        file().writeBytes(original)
        try { AppStore.recover(file(), AppData(lists = emptyList())); org.junit.Assert.fail("Invalid candidate should fail") } catch (_: BackupException) { }
        assertArrayEquals(original, file().readBytes())
        assertEquals(listOf("data.json"), temporary.root.listFiles()!!.map { it.name })
    }
}
