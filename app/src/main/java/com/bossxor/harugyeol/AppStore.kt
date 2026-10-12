package com.bossxor.harugyeol

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
data class Settings(val theme: String = "SYSTEM")

// 습관·작업 등은 해당 단계에서 기본값과 함께 추가한다. 앱보다 높은 schemaVersion은 거부한다.
@Serializable
data class AppData(
    val schemaVersion: Int = SCHEMA_VERSION,
    val settings: Settings = Settings(),
    val launchCount: Int = 0, // ponytail: M1-A 저장 검증용, 습관 화면 구현 때 삭제
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

    companion object {
        private val json = Json { prettyPrint = true; encodeDefaults = true }

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
