package com.bossxor.harugyeol

import com.bossxor.harugyeol.habit.Habit
import com.bossxor.harugyeol.habit.HabitTimer
import com.bossxor.harugyeol.habit.TimerState
import com.bossxor.harugyeol.backup.Backup
import com.bossxor.harugyeol.task.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.util.UUID
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

const val SCHEMA_VERSION = 2

@Serializable
data class DailySummary(val enabled: Boolean = false, val time: String = "20:30")

@Serializable
data class Settings(val theme: String = "SYSTEM", val dailySummary: DailySummary = DailySummary(), val allDayReminderTime: String = "09:00", val celebrationHaptics: Boolean = true, val lastCelebrationDate: String? = null)

// 습관·작업 등은 해당 단계에서 기본값과 함께 추가한다. 앱보다 높은 schemaVersion은 거부한다.
@Serializable
data class AppData(
    val schemaVersion: Int = SCHEMA_VERSION,
    val settings: Settings = Settings(),
    val habits: List<Habit> = emptyList(),
    val timer: HabitTimer? = null,
    val tasks: List<Task> = emptyList(),
    val lists: List<com.bossxor.harugyeol.task.TaskList> = com.bossxor.harugyeol.task.defaultTaskLists(),
    val tags: List<com.bossxor.harugyeol.task.TaskTag> = emptyList(),
)

class StoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 앱 데이터 전체를 JSON 파일 하나로 저장한다. 모든 변경은 Mutex로 직렬화한다. */
class AppStore private constructor(private val file: File, initial: AppData, private var migrationPending: Boolean = false) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<AppData> = _state

    suspend fun update(transform: (AppData) -> AppData) = mutex.withLock {
        val next = transform(_state.value)
        Backup.validate(next)
        preserveMigration()
        write(file, next) // 쓰기에 실패하면 상태를 바꾸지 않는다
        _state.value = next
    }

    private val snapshotFile get() = File(file.parentFile, "snapshot.json")

    /** 현재 데이터를 내부 스냅샷으로 남기고 새 데이터로 통째로 바꾼다 */
    suspend fun replaceAll(newData: AppData) = mutex.withLock {
        ensureNoTimer()
        Backup.validate(newData)
        val restored = normalizeRestored(newData)
        Backup.validate(restored)
        preserveMigration()
        preserveSnapshotOriginal()
        write(snapshotFile, _state.value)
        write(file, restored)
        _state.value = restored
    }

    /** 업데이트 전 상태를 잠금 안에서 보관한다. 현재 데이터를 과거 값으로 교체하지 않는다. */
    suspend fun createSnapshot() = mutex.withLock {
        write(snapshotFile, _state.value)
    }

    /** 마지막 스냅샷 시각(없으면 null) */
    fun snapshotTime(): Long? = snapshotFile.takeIf { it.exists() }?.lastModified()

    /** 스냅샷으로 되돌린다. 되돌리기 전 데이터가 새 스냅샷이 된다. */
    suspend fun restoreSnapshot(): Boolean = mutex.withLock {
        ensureNoTimer()
        if (!snapshotFile.exists()) return false
        val data = try { normalizeRestored(readSnapshot(file)) } catch (e: Exception) { return false }
        Backup.validate(data)
        preserveMigration()
        preserveSnapshotOriginal()
        write(snapshotFile, _state.value)
        write(file, data)
        _state.value = data
        true
    }

    /** 교체 도중 저장에 실패해도 이전 복원 후보의 원문은 남긴다. */
    private fun preserveSnapshotOriginal() {
        if (!snapshotFile.exists()) return
        preserveOriginal(snapshotFile, "snapshot-original-")
    }

    private fun ensureNoTimer() {
        if (_state.value.timer != null) throw StoreException("타이머를 먼저 저장하거나 버려 주세요")
    }

    /** schema 1 원문을 최초 변경 전에 한 번 보존한다. 보존 실패 시 변경도 하지 않는다. */
    private fun preserveMigration() {
        if (!migrationPending) return
        val original = File(file.parentFile, "migration-v1.json")
        if (!original.exists()) Files.copy(file.toPath(), original.toPath())
        migrationPending = false
    }
    companion object {
        internal val json = Json { prettyPrint = true; encodeDefaults = true }

        /** 파일이 없으면 기본값. 읽을 수 없거나 더 새 버전이면 덮어쓰지 않고 예외를 던진다. */
        fun open(file: File): AppStore {
            if (!file.exists()) return AppStore(file, AppData())
            val data = try {
                file.inputStream().use { com.bossxor.harugyeol.backup.Backup.read(it) }
            } catch (e: Exception) {
                throw StoreException("저장 파일을 읽을 수 없어요", e)
            }
            if (data.schemaVersion > SCHEMA_VERSION) throw StoreException("더 새 버전의 데이터예요")
            val legacy = try {
                json.parseToJsonElement(file.readText()).jsonObject["schemaVersion"]?.jsonPrimitive?.intOrNull == 1
            } catch (_: Exception) { false }
            return AppStore(file, data, legacy)
        }

        /** 다른 실행 환경에서 가져온 실행 중 타이머는 마지막 보존량에서 멈춘다. */
        fun normalizeRestored(data: AppData): AppData {
            val timer = data.timer?.let { t ->
                if (t.state != TimerState.RUNNING) t else {
                    val elapsed = maxOf(t.accumulatedMs, t.checkpointMs)
                    t.copy(state = TimerState.PAUSED, accumulatedMs = elapsed,
                        segmentStartedElapsedMs = null, checkpointMs = elapsed, rebooted = true)
                }
            }
            return data.copy(schemaVersion = SCHEMA_VERSION, timer = timer)
        }

        fun readSnapshot(file: File): AppData =
            File(file.parentFile, "snapshot.json").inputStream().use { Backup.read(it) }

        /** 후보를 확인한 뒤 호출한다. 기존 원본은 별도 파일로 반드시 남긴다. */
        fun recover(file: File, data: AppData): AppStore {
            Backup.validate(data)
            val restored = normalizeRestored(data)
            Backup.validate(restored)
            if (file.exists()) {
                preserveOriginal(file, "data-original-")
            }
            write(file, restored)
            return AppStore(file, restored)
        }

        /** 동일한 원문은 재사용한다. 기존 원본은 삭제하거나 덮어쓰지 않는다. */
        private fun preserveOriginal(source: File, prefix: String) {
            val bytes = source.readBytes()
            val exists = source.parentFile?.listFiles().orEmpty().any { candidate ->
                candidate.isFile && candidate.name.startsWith(prefix) && candidate.name.endsWith(".json") &&
                    candidate.length() == bytes.size.toLong() && candidate.readBytes().contentEquals(bytes)
            }
            if (!exists) Files.copy(source.toPath(), File(source.parentFile, "$prefix${UUID.randomUUID()}.json").toPath())
        }
        private fun write(file: File, data: AppData) {
            val tmp = File(file.parentFile, file.name + ".tmp")
            FileOutputStream(tmp).use {
                it.write(json.encodeToString(AppData.serializer(), data).toByteArray())
                it.fd.sync()
            }
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
