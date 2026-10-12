package com.bossxor.harugyeol

import com.bossxor.harugyeol.backup.Backup
import com.bossxor.harugyeol.backup.BackupException
import com.bossxor.harugyeol.habit.*
import com.bossxor.harugyeol.task.*
import com.bossxor.harugyeol.update.Updater
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.InputStream
import java.time.LocalDate

class TaskBackupUpdateTest {
    @get:Rule val tmp = TemporaryFolder()
    private val today = LocalDate.of(2026, 10, 12)
    private fun task(id: String, due: LocalDate? = null, priority: Int = 0, done: Long? = null, trash: Long? = null, created: Long = 1) =
        Task(id, id, note = "메모 $id", priority = priority, dueDate = due, completedAt = done, deletedAt = trash, createdAt = created, updatedAt = created)
    private fun habit() = AppData().addHabit("독서", 0, MeasureType.DURATION, "분", GoalMode.DAILY, null, null, 1800, today, today, 1).habits.single()
    private fun rejected(data: AppData) {
        try { Backup.decode(Backup.encode(data)); org.junit.Assert.fail("invalid backup accepted") } catch (_: BackupException) {}
    }

    @Test fun `보기에서 완료 휴지통 날짜 없는 작업을 구분한다`() {
        val data = AppData(tasks = listOf(task("지난", today.minusDays(1)), task("오늘", today), task("내일", today.plusDays(1)), task("없음"), task("완료", today, done = 2), task("삭제", today, trash = 2)))
        assertEquals(listOf("지난", "오늘"), data.todayTasks(today).map { it.id })
        assertEquals(listOf("내일"), data.upcomingTasks(today).map { it.id })
        assertEquals(4, data.allOpenTasks().size)
        assertEquals(listOf("완료"), data.doneTasks().map { it.id })
        assertEquals(listOf("삭제"), data.trashedTasks().map { it.id })
        assertEquals(listOf("오늘", "완료"), data.tasksOn(today).map { it.id })
    }

    @Test fun `우선순위 기한 생성순으로 정렬한다`() {
        val data = AppData(tasks = listOf(task("없음"), task("나중", today.plusDays(2), 3), task("두번째", today, 3, created = 2), task("첫번째", today, 3), task("과거", today.minusDays(2), 1)))
        assertEquals(listOf("첫번째", "두번째", "나중", "과거", "없음"), data.allOpenTasks().map { it.id })
    }

    @Test fun `중복 완료와 휴지통의 늦은 완료 요청은 무해하다`() {
        val data = AppData(tasks = listOf(task("a"))).setTaskDone("a", true, 2)
        assertEquals(data, data.setTaskDone("a", true, 3))
        val trashed = data.trashTask("a", 4)
        assertEquals(trashed, trashed.setTaskDone("a", false, 5))
        assertEquals(2L, trashed.restoreTask("a", 6).tasks.single().completedAt)
    }

    @Test fun `검색은 메모 대소문자 완료 포함과 휴지통 제외를 적용한다`() {
        val data = AppData(tasks = listOf(task("Alpha"), task("done", done = 2), task("trash", trash = 2)))
        assertEquals(listOf("Alpha"), data.searchTasks("alpha").map { it.id })
        assertEquals(1, data.searchTasks("메모").size)
        assertEquals(2, data.searchTasks("메모", true).size)
    }

    @Test fun `습관 초 단위 기록 건너뜀 타이머 휴지통을 왕복 보존한다`() {
        val h = habit().copy(logs = listOf(HabitLog(today, 1841)))
        val data = AppData(habits = listOf(h), tasks = listOf(task("a", trash = 5)))
            .startTimer(h.id, today, 100, 1).pauseTimer(1100, 1)
        assertEquals(data, Backup.decode(Backup.encode(data)))
        assertEquals(AppData(), Backup.decode("""{"schemaVersion":1}"""))
    }

    @Test fun `잘못된 목표 유형 중복 날짜 알림 참조를 거부한다`() {
        val h = habit()
        rejected(AppData(habits = listOf(h.copy(goals = listOf(h.goals.single().copy(mode = GoalMode.WEEKDAYS))))))
        rejected(AppData(habits = listOf(h.copy(goals = listOf(h.goals.single().copy(effectiveFrom = today.minusDays(1)))))))
        rejected(AppData(habits = listOf(h.copy(logs = listOf(HabitLog(today, 1, true))))))
        rejected(AppData(habits = listOf(h.copy(reminders = listOf("09:00", "09:00")))))
        rejected(AppData(habits = listOf(h, h)))
        rejected(AppData(timer = HabitTimer("session", "missing", today, TimerState.PAUSED, bootCount = 1)))
        rejected(AppData(tasks = listOf(task("a"), task("a"))))
        rejected(AppData(schemaVersion = 99))
    }

    @Test fun `과대 입력은 제한까지만 읽고 거부한다`() {
        var read = 0
        val stream = object : InputStream() { override fun read(): Int { read++; return 32 } }
        try { Backup.read(stream); org.junit.Assert.fail() } catch (_: BackupException) {}
        assertEquals(Backup.MAX_BYTES + 1, read)
    }

    @Test fun `잘못된 복원과 스냅샷은 현재 파일을 변경하지 않는다`() = runBlocking {
        val file = File(tmp.root, "data.json")
        val store = AppStore.open(file)
        store.update { it.copy(tasks = listOf(task("기존"))) }
        val original = file.readText()
        try { store.replaceAll(AppData(schemaVersion = 99)); org.junit.Assert.fail() } catch (_: BackupException) {}
        assertEquals(original, file.readText())
        File(tmp.root, "snapshot.json").writeText(Backup.encode(AppData(schemaVersion = 99)))
        assertFalse(store.restoreSnapshot())
        assertEquals(original, file.readText())
    }

    @Test fun `복원 전 스냅샷과 되돌리기가 왕복한다`() = runBlocking {
        val file = File(tmp.root, "data.json")
        val store = AppStore.open(file)
        val original = AppData(tasks = listOf(task("기존")))
        val incoming = AppData(tasks = listOf(task("복원")))
        store.update { original }
        store.replaceAll(incoming)
        assertTrue(store.restoreSnapshot())
        assertEquals(original, store.state.value)
        assertTrue(store.restoreSnapshot())
        assertEquals(incoming, AppStore.open(file).state.value)
    }

    @Test fun `버전 비교는 숫자와 누락 자리만 허용한다`() {
        assertTrue(Updater.isNewer("v0.10.0", "0.9.9"))
        assertFalse(Updater.isNewer("1.0", "1.0.0"))
        assertFalse(Updater.isNewer("v1.1.0-beta", "1.0.0"))
        assertFalse(Updater.isNewer("2.oops", "1.0.0"))
        assertFalse(Updater.isNewer("999999999999999", "1"))
    }

    @Test fun `사전 릴리스와 여러 APK는 선택하지 않는다`() {
        fun release(extra: String, assets: String) = Json.parseToJsonElement("""{"tag_name":"v1.0.0",$extra"assets":[$assets]}""").jsonObject
        val asset = """{"name":"Harugyeol.apk","browser_download_url":"https://github.com/example.apk","size":10}"""
        assertNotNull(Updater.parseRelease(release("", asset)))
        assertNull(Updater.parseRelease(release("\"prerelease\":true,", asset)))
        assertNull(Updater.parseRelease(release("", "$asset,$asset")))
    }
}
