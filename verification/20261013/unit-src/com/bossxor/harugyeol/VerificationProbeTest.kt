package com.bossxor.harugyeol

import com.bossxor.harugyeol.backup.Backup
import com.bossxor.harugyeol.reminders.taskAlarmPlans
import com.bossxor.harugyeol.task.Task
import com.bossxor.harugyeol.task.TaskReminder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId

/** Diagnostic assertions confirm the current defect, not the desired fixed behavior. */
class VerificationProbeTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun delayedUnchangedAlarmDisappearsFromPlan() {
        val due = LocalDateTime.of(2026, 10, 13, 10, 0)
        val zone = ZoneId.of("Asia/Seoul")
        val data = AppData(tasks = listOf(Task("probe", "probe", dueDate = due.toLocalDate(),
            dueTime = "10:00", reminders = listOf(TaskReminder(0)), createdAt = 1, updatedAt = 1)))
        fun millis(t: LocalDateTime) = t.atZone(zone).toInstant().toEpochMilli()
        assertEquals(1, taskAlarmPlans(data, millis(due.minusMinutes(1)), zone).size)
        assertEquals(0, taskAlarmPlans(data, millis(due.plusMinutes(1)), zone).size)
        assertEquals(1, taskAlarmPlans(data, millis(due.plusMinutes(1)), zone, futureOnly = false).size)
        println("PROBE delay: unchanged pending alarm plan count 1 -> 0")
    }

    @Test fun smallSavedDataCanBeReopened() = runBlocking {
        val file = File(tmp.root, "small.json")
        val store = AppStore.open(file)
        val data = AppData(tasks = listOf(Task("small", "small", createdAt = 1, updatedAt = 1)))
        store.update { data }
        assertEquals(data, AppStore.open(file).state.value)
    }

    @Test fun validLargeDataWritesSuccessfullyButCannotBeReopened() = runBlocking {
        val file = File(tmp.root, "large.json")
        val store = AppStore.open(file)
        val note = "가".repeat(20_000)
        val data = AppData(tasks = (0 until 360).map { Task("probe-$it", "probe-$it", note = note,
            createdAt = 1, updatedAt = 1) })
        Backup.validate(data)
        store.update { data }
        assertEquals(360, store.state.value.tasks.size)
        assertTrue(file.length() > Backup.MAX_BYTES)
        val bytes = file.readBytes()
        try {
            AppStore.open(file)
            fail("Expected the existing oversized-file read rejection")
        } catch (e: StoreException) {
            assertEquals("파일이 너무 커요", e.cause?.message)
        }
        assertArrayEquals(bytes, file.readBytes())
        println("PROBE size: valid tasks=360; savedBytes=${file.length()}; limit=${Backup.MAX_BYTES}; reopen=rejected; originalPreserved=true")
    }
}
