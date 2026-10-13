package com.bossxor.harugyeol
import com.bossxor.harugyeol.backup.Backup
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
