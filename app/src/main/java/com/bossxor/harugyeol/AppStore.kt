package com.bossxor.harugyeol

import com.bossxor.harugyeol.habit.Habit
import com.bossxor.harugyeol.habit.HabitTimer
import com.bossxor.harugyeol.task.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

const val SCHEMA_VERSION = 1

@Serializable
data class DailySummary(val enabled: Boolean = false, val time: String = "20:30")

@Serializable
data class Settings(val theme: String = "SYSTEM", val dailySummary: DailySummary = DailySummary())

// 습관·작업 등은 해당 단계에서 기본값과 함께 추가한다. 앱보다 높은 schemaVersion은 거부한다.
@Serializable
data class AppData(
    val schemaVersion: Int = SCHEMA_VERSION,
    val settings: Settings = Settings(),
    val habits: List<Habit> = emptyList(),
    val timer: HabitTimer? = null,
    val tasks: List<Task> = emptyList(),
)

class StoreException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** 앱 데이터 전체를 JSON 파일 하나로 저장한다. 모든 변경은 Mutex로 직렬화한다. */
class AppStore private constructor(private val file: File, initial: AppData) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<AppData> = _state

    suspend fun update(transform: (AppData) -> AppData) = mutex.withLock {
        val next = transform(_state.value)
        write(file, next) // 쓰기에 실패하면 상태를 바꾸지 않는다
        _state.value = next
    }

    private val snapshotFile get() = File(file.parentFile, "snapshot.json")

    /** 현재 데이터를 내부 스냅샷으로 남기고 새 데이터로 통째로 바꾼다 */
    suspend fun replaceAll(newData: AppData) = mutex.withLock {
        if (file.exists()) Files.copy(file.toPath(), snapshotFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        write(file, newData)
        _state.value = newData
    }

    /** 마지막 스냅샷 시각(없으면 null) */
    fun snapshotTime(): Long? = snapshotFile.takeIf { it.exists() }?.lastModified()

    /** 스냅샷으로 되돌린다. 되돌리기 전 데이터가 새 스냅샷이 된다. */
    suspend fun restoreSnapshot(): Boolean = mutex.withLock {
        if (!snapshotFile.exists()) return false
        val data = try { json.decodeFromString<AppData>(snapshotFile.readText()) } catch (e: Exception) { return false }
        Files.copy(file.toPath(), snapshotFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
        write(file, data)
        _state.value = data
        true
    }

    companion object {
        internal val json = Json { prettyPrint = true; encodeDefaults = true }

        /** 파일이 없으면 기본값. 읽을 수 없거나 더 새 버전이면 덮어쓰지 않고 예외를 던진다. */
        fun open(file: File): AppStore {
            if (!file.exists()) return AppStore(file, AppData())
            val data = try {
                json.decodeFromString<AppData>(file.readText())
            } catch (e: Exception) {
                throw StoreException("저장 파일을 읽을 수 없어요", e)
            }
            if (data.schemaVersion > SCHEMA_VERSION) throw StoreException("더 새 버전의 데이터예요")
            return AppStore(file, data)
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
