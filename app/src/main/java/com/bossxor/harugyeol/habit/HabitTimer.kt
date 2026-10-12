package com.bossxor.harugyeol.habit

import com.bossxor.harugyeol.AppData
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

// 습관 스톱워치 (docs/technical-design.md 3.5). 경과 시간은 SystemClock.elapsedRealtime 차이로 계산한다.

enum class TimerState { RUNNING, PAUSED }

@Serializable
data class HabitTimer(
    val sessionId: String,
    val habitId: String,
    @Serializable(LocalDateSerializer::class) val date: LocalDate,
    val state: TimerState,
    val accumulatedMs: Long = 0,
    val segmentStartedElapsedMs: Long? = null,
    val bootCount: Int,
    val checkpointMs: Long = 0,
    val rebooted: Boolean = false,
)

/** 같은 부팅이면 경과 시간 차이를, 재부팅 후라면 마지막 보존 시점(checkpoint)을 쓴다 */
fun HabitTimer.elapsedMs(nowElapsed: Long, nowBoot: Int): Long {
    if (state == TimerState.PAUSED) return accumulatedMs
    val start = segmentStartedElapsedMs
    return if (nowBoot == bootCount && start != null && nowElapsed >= start) accumulatedMs + (nowElapsed - start)
    else checkpointMs
}

fun AppData.startTimer(habitId: String, today: LocalDate, nowElapsed: Long, boot: Int): AppData {
    if (timer != null) fail("이미 실행 중인 타이머가 있어요. 저장하거나 버려야 새로 시작할 수 있어요")
    val h = habits.firstOrNull { it.id == habitId } ?: fail("습관을 찾을 수 없어요")
    if (h.archivedAt != null) fail("보관한 습관은 기록할 수 없어요")
    if (h.measureType != MeasureType.DURATION) fail("시간 목표 습관만 타이머를 쓸 수 있어요")
    if (!h.isTarget(today)) fail("오늘은 대상이 아니에요")
    return copy(
        timer = HabitTimer(
            UUID.randomUUID().toString(), habitId, today, TimerState.RUNNING,
            segmentStartedElapsedMs = nowElapsed, bootCount = boot,
        )
    )
}

fun AppData.pauseTimer(nowElapsed: Long, boot: Int): AppData {
    val t = timer ?: return this
    if (t.state != TimerState.RUNNING) return this
    val e = t.elapsedMs(nowElapsed, boot)
    return copy(timer = t.copy(state = TimerState.PAUSED, accumulatedMs = e, segmentStartedElapsedMs = null, checkpointMs = e))
}

fun AppData.resumeTimer(nowElapsed: Long, boot: Int): AppData {
    val t = timer ?: return this
    if (t.state != TimerState.PAUSED) return this
    return copy(
        timer = t.copy(
            state = TimerState.RUNNING, segmentStartedElapsedMs = nowElapsed, bootCount = boot,
            checkpointMs = t.accumulatedMs, rebooted = false,
        )
    )
}

/** 화면이 열려 있는 동안 주기적으로 호출해 프로세스 종료·재부팅에 대비한다 */
fun AppData.checkpointTimer(nowElapsed: Long, boot: Int): AppData {
    val t = timer ?: return this
    if (t.state != TimerState.RUNNING) return this
    return copy(timer = t.copy(checkpointMs = t.elapsedMs(nowElapsed, boot)))
}

/** 앱 시작 시: 부팅이 바뀌었다면 마지막 보존 시점에서 일시정지로 바꾼다 */
fun AppData.recoverTimer(boot: Int): AppData {
    val t = timer ?: return this
    if (t.state != TimerState.RUNNING || t.bootCount == boot) return this
    return copy(
        timer = t.copy(
            state = TimerState.PAUSED, accumulatedMs = t.checkpointMs, segmentStartedElapsedMs = null, rebooted = true,
        )
    )
}

/** 저장: 초 단위로 내림해 시작 날짜 기록에 더하고 세션을 끝낸다. 이미 저장된 세션 요청은 무시한다. */
fun AppData.saveTimer(sessionId: String, nowElapsed: Long, boot: Int, now: Long): AppData {
    val t = timer ?: return this
    if (t.sessionId != sessionId) return this
    val seconds = t.elapsedMs(nowElapsed, boot) / 1000
    if (seconds < 1) fail("1초 미만은 저장할 수 없어요")
    val h = habits.firstOrNull { it.id == t.habitId } ?: fail("습관을 찾을 수 없어요")
    val next = (h.logOf(t.date)?.amount ?: 0) + seconds
    if (next > MAX_AMOUNT) fail("기록할 수 있는 양을 넘었어요")
    return copy(timer = null).mapHabit(h.id) { hh ->
        val rest = hh.logs.filter { it.date != t.date }
        hh.copy(logs = (rest + HabitLog(t.date, next, false)).sortedBy { it.date }, updatedAt = now)
    }
}

fun AppData.discardTimer(sessionId: String): AppData =
    if (timer?.sessionId == sessionId) copy(timer = null) else this
