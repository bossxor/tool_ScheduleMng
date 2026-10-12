package com.bossxor.harugyeol.backup

import com.bossxor.harugyeol.AppData
import com.bossxor.harugyeol.AppStore
import com.bossxor.harugyeol.SCHEMA_VERSION
import com.bossxor.harugyeol.habit.*
import com.bossxor.harugyeol.task.validateTaskRelations

class BackupException(message: String) : Exception(message)

/** 백업 파일은 저장 파일과 같은 형식이다. 복원 전에 아래 검증을 모두 통과해야 한다. */
object Backup {
    const val MAX_BYTES = 20 * 1024 * 1024

    fun encode(data: AppData): String = AppStore.json.encodeToString(AppData.serializer(), data)

    fun decode(text: String): AppData {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw BackupException("파일이 너무 커요")
        val data = try {
            AppStore.json.decodeFromString<AppData>(text)
        } catch (e: Exception) {
            throw BackupException("파일을 읽을 수 없어요. 다른 백업 파일을 선택해 주세요.")
        }
        validate(data)
        return data.copy(schemaVersion = SCHEMA_VERSION)
    }

    fun read(input: java.io.InputStream): AppData {
        val bytes = input.readNBytes(MAX_BYTES + 1)
        if (bytes.size > MAX_BYTES) throw BackupException("파일이 너무 커요")
        return decode(bytes.toString(Charsets.UTF_8))
    }

    private val TIME = Regex("""([01]\d|2[0-3]):[0-5]\d""")

    fun validate(d: AppData) {
        fun bad(msg: String): Nothing = throw BackupException(msg)
        if (d.schemaVersion > SCHEMA_VERSION) bad("이 앱보다 새 버전의 백업이에요. 앱을 업데이트해 주세요.")
        if (d.schemaVersion < 1) bad("지원하지 않는 백업이에요.")
        if (d.habits.map { it.id }.toSet().size != d.habits.size) bad("습관 ID가 겹쳐요")
        if (d.tasks.map { it.id }.toSet().size != d.tasks.size) bad("할 일 ID가 겹쳐요")
        if (!TIME.matches(d.settings.dailySummary.time)) bad("하루 요약 시각이 올바르지 않아요")
        if (d.settings.theme !in listOf("SYSTEM", "LIGHT", "DARK")) bad("테마 설정이 올바르지 않아요")
        if (d.settings.lastCelebrationDate != null) try { java.time.LocalDate.parse(d.settings.lastCelebrationDate) } catch (_: Exception) { bad("달성 효과 날짜가 올바르지 않아요") }
        if (!TIME.matches(d.settings.allDayReminderTime)) bad("종일 알림 시각이 올바르지 않아요")
        for (h in d.habits) {
            if (h.id.isBlank() || h.note.length > 20_000) bad("습관 정보가 올바르지 않아요")
            when (h.measureType) {
                MeasureType.CHECK -> if (h.unit != null) bad("완료형 단위가 올바르지 않아요")
                MeasureType.QUANTITY -> if (h.unit.isNullOrBlank() || h.unit.length > 20) bad("수량 단위가 올바르지 않아요")
                MeasureType.DURATION -> if (h.unit != "분") bad("시간 단위가 올바르지 않아요")
            }
            if (h.name.isBlank() || h.name.length > 100) bad("습관 이름이 올바르지 않아요")
            if (h.colorIndex !in 0..3) bad("습관 색이 올바르지 않아요")
            if (h.goals.isEmpty()) bad("목표가 없는 습관이 있어요")
            if (h.goals.map { it.effectiveFrom }.toSet().size != h.goals.size) bad("목표 적용일이 겹쳐요")
            if (h.goals.minOf { it.effectiveFrom } != h.startDate) bad("시작일의 목표가 없어요")
            for (g in h.goals) {
                if (h.measureType == MeasureType.CHECK && g.targetAmount != 1L) bad("완료형 목표가 올바르지 않아요")
                when (g.mode) {
                    GoalMode.DAILY -> if (g.weekdays != null || g.targetDays != null) bad("매일 목표가 올바르지 않아요")
                    GoalMode.WEEKDAYS -> if (g.weekdays.isNullOrEmpty() || g.weekdays.distinct().size != g.weekdays.size || g.targetDays != null) bad("요일 목표가 올바르지 않아요")
                    GoalMode.WEEKLY_COUNT -> if (g.targetDays == null || g.weekdays != null) bad("주 목표가 올바르지 않아요")
                }
                if (g.targetAmount !in 1..MAX_AMOUNT) bad("목표량이 올바르지 않아요")
                if (g.weekdays?.any { it !in 1..7 } == true) bad("요일이 올바르지 않아요")
                if (g.targetDays != null && g.targetDays !in 1..7) bad("주 목표 일수가 올바르지 않아요")
            }
            if (h.logs.map { it.date }.toSet().size != h.logs.size) bad("같은 날짜의 기록이 겹쳐요")
            if (h.logs.any { it.amount !in 0..MAX_AMOUNT }) bad("기록량이 올바르지 않아요")
            if (h.logs.any { it.date.isBefore(h.startDate) || (it.skipped && it.amount != 0L) || (h.measureType == MeasureType.CHECK && it.amount > 1) }) bad("습관 기록이 올바르지 않아요")
            if (h.reminders.distinct().size != h.reminders.size) bad("알림 시각이 겹쳐요")
            if (h.reminders.size > 5 || h.reminders.any { !TIME.matches(it) }) bad("알림 시각이 올바르지 않아요")
        }
        d.timer?.let { t ->
            val habit = d.habits.firstOrNull { it.id == t.habitId } ?: bad("타이머의 습관을 찾을 수 없어요")
            if (habit.measureType != MeasureType.DURATION || t.date.isBefore(habit.startDate) || t.sessionId.isBlank() || t.accumulatedMs < 0 || t.checkpointMs < 0 || t.segmentStartedElapsedMs?.let { it < 0 } == true) bad("타이머 정보가 올바르지 않아요")
            if ((t.state == TimerState.RUNNING) != (t.segmentStartedElapsedMs != null)) bad("타이머 상태가 올바르지 않아요")
        }
        if (d.lists.count { it.isInbox } != 1 || d.lists.singleOrNull { it.isInbox }?.id != com.bossxor.harugyeol.task.INBOX_ID || d.lists.map { it.id }.distinct().size != d.lists.size || d.lists.any { it.id.isBlank() || it.name.trim().length !in 1..100 || (it.colorIndex != null && it.colorIndex !in 0..3) }) bad("목록 정보가 올바르지 않아요")
        if (d.tags.map { it.id }.distinct().size != d.tags.size || d.tags.map { com.bossxor.harugyeol.task.normalizedTagName(it.name) }.distinct().size != d.tags.size || d.tags.any { it.id.isBlank() || it.name.trim().length !in 1..50 || (it.colorIndex != null && it.colorIndex !in 0..3) }) bad("태그 정보가 올바르지 않아요")
        for (t in d.tasks) {
            try { d.validateTaskRelations(t.listId, t.tagIds, t.checklist) } catch (e: HabitInputException) { bad(e.message ?: "작업 관계가 올바르지 않아요") }
            try { com.bossxor.harugyeol.task.validateTaskSchedule(t.dueDate, t.dueTime, t.recurrence, t.reminders) }
            catch (e: HabitInputException) { bad(e.message ?: "작업 일정이 올바르지 않아요") }
            if (t.occurrences.map { it.scheduledDate }.distinct().size != t.occurrences.size || t.occurrences.any { it.scheduledTime != null && !TIME.matches(it.scheduledTime) }) bad("반복 회차가 올바르지 않아요")
            if (t.id.isBlank() || t.note.length > 20_000) bad("할 일 정보가 올바르지 않아요")
            if (t.title.isBlank() || t.title.length > 500) bad("할 일 제목이 올바르지 않아요")
            if (t.priority !in 0..3) bad("우선순위가 올바르지 않아요")
        }
    }
}
