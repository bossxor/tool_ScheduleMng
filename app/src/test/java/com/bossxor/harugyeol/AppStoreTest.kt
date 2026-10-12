package com.bossxor.harugyeol

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file() = File(tmp.root, "data.json")

    @Test fun `저장한 값이 다시 열어도 유지된다`() = runBlocking {
        AppStore.open(file()).update { it.copy(launchCount = 3) }
        assertEquals(3, AppStore.open(file()).state.value.launchCount)
        assertTrue(!File(tmp.root, "data.json.tmp").exists())
    }

    @Test fun `손상된 파일은 덮어쓰지 않고 거부한다`() {
        file().writeText("{ broken")
        try { AppStore.open(file()); fail() } catch (_: StoreException) {}
        assertEquals("{ broken", file().readText())
    }

    @Test fun `더 새 버전의 파일은 거부한다`() {
        file().writeText("""{"schemaVersion": 99}""")
        try { AppStore.open(file()); fail() } catch (_: StoreException) {}
    }

    @Test fun `동시 변경도 모두 반영된다`() = runBlocking {
        val store = AppStore.open(file())
        repeat(50) { store.update { it.copy(launchCount = it.launchCount + 1) } }
        assertEquals(50, AppStore.open(file()).state.value.launchCount)
    }
}
