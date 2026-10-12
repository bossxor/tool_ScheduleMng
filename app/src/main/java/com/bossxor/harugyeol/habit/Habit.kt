package com.bossxor.harugyeol.habit

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.LocalDate

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

enum class MeasureType { CHECK, QUANTITY, DURATION }
enum class GoalMode { DAILY, WEEKDAYS, WEEKLY_COUNT }

/** 저장 단위: CHECK 0/1, QUANTITY 소수 둘째 자리 정수(1잔=100), DURATION 초 */
@Serializable
data class HabitGoal(
    @Serializable(LocalDateSerializer::class) val effectiveFrom: LocalDate,
    val mode: GoalMode,
    val weekdays: List<Int>? = null, // ISO 요일 1(월)~7(일), WEEKDAYS에서만 사용
    val targetDays: Int? = null,     // 1~7, WEEKLY_COUNT에서만 사용
    val targetAmount: Long,
)

@Serializable
data class HabitLog(
    @Serializable(LocalDateSerializer::class) val date: LocalDate,
    val amount: Long = 0,
    val skipped: Boolean = false,
)

@Serializable
data class Habit(
    val id: String,
    val name: String,
    val colorIndex: Int = 0,
    val iconKey: String? = null,
    val note: String = "",
    val measureType: MeasureType,
    val unit: String? = null,
    @Serializable(LocalDateSerializer::class) val startDate: LocalDate,
    val sortOrder: Int = 0,
    val archivedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val goals: List<HabitGoal>,
    val logs: List<HabitLog> = emptyList(),
    val reminders: List<String> = emptyList(),
)

const val MAX_AMOUNT = 1_000_000_000L
