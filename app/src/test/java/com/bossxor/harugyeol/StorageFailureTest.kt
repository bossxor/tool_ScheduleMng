package com.bossxor.harugyeol
import com.bossxor.harugyeol.backup.Backup
import com.bossxor.harugyeol.backup.BackupException
import com.bossxor.harugyeol.task.Task
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
class StorageFailureTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun file() = File(temporary.root, "data.json")
    private fun oversized() = AppData(tasks = (0 until 360).map { Task("large-$it", "large-$it",
        note = "가".repeat(20_000), createdAt = 1, updatedAt = 1) })

    @Test fun oversizedWriteKeepsOriginalFileAndStateReadable() = runBlocking {
        val store = AppStore.open(file())
        store.update { it.copy(settings = Settings(theme = "DARK")) }
        val before = file().readBytes()
        val state = store.state.value
        try { store.update { oversized() }; fail("Oversized write must fail") } catch (_: BackupException) { }
        assertEquals(state, store.state.value)
        assertArrayEquals(before, file().readBytes())
        assertEquals(state, AppStore.open(file()).state.value)
        assertFalse(File(temporary.root, "data.json.tmp").exists())
    }

    @Test fun exportedBackupUsesTheSameReadSizeLimit() {
        try { Backup.encode(oversized()); fail("Oversized export must fail") } catch (_: BackupException) { }
        assertEquals(AppData(), Backup.decode(Backup.encode(AppData())))
    }
    @Test fun updateWriteFailureKeepsPublishedAndPersistedData() = runBlocking {
        val store = AppStore.open(file())
        store.update { it.copy(settings = Settings(theme = "DARK")) }
        val before = file().readBytes()
        val state = store.state.value
        assertTrue(File(temporary.root, "data.json.tmp").mkdir())
        try { store.update { it.copy(settings = Settings(theme = "LIGHT")) }; org.junit.Assert.fail("Write must fail") } catch (_: IOException) { }
        assertEquals(state, store.state.value)
        assertArrayEquals(before, file().readBytes())
        assertEquals(state, AppStore.open(file()).state.value)
    }
    @Test fun failedSnapshotRestoreRetainsCandidateOriginalAndCurrentData() = runBlocking {
        val store = AppStore.open(file())
        val original = AppData()
        val current = original.copy(settings = Settings(theme = "DARK"))
        store.replaceAll(current)
        val candidateBytes = File(temporary.root, "snapshot.json").readBytes()
        val currentBytes = file().readBytes()
        assertTrue(File(temporary.root, "data.json.tmp").mkdir())
        try { store.restoreSnapshot(); org.junit.Assert.fail("Write must fail") } catch (_: IOException) { }
        assertEquals(current, store.state.value)
        assertArrayEquals(currentBytes, file().readBytes())
        val preserved = temporary.root.listFiles()!!.filter { it.name.startsWith("snapshot-original-") }
        assertEquals(1, preserved.size)
        assertArrayEquals(candidateBytes, preserved.single().readBytes())
        assertEquals(original, Backup.read(preserved.single().inputStream()))
    }
}
