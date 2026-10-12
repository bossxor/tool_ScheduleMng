package com.bossxor.harugyeol.habit

import java.math.BigDecimal

/** 소수 둘째 자리 정수(1.5 → 150)를 보기 좋은 문자열로 */
fun formatQuantity(scaled: Long): String =
    BigDecimal.valueOf(scaled, 2).stripTrailingZeros().toPlainString()

/** 입력 문자열을 저장 단위로 바꾼다. 음수·소수 3자리 이상·범위 초과는 null */
fun parseQuantity(text: String): Long? {
    val d = text.trim().replace(',', '.').toBigDecimalOrNull() ?: return null
    if (d.signum() < 0 || d.stripTrailingZeros().scale() > 2) return null
    val scaled = d.movePointRight(2)
    return if (scaled <= BigDecimal(MAX_AMOUNT)) scaled.toLong() else null
}

fun parseMinutes(text: String): Long? {
    val m = text.trim().toLongOrNull() ?: return null
    return if (m in 0..(MAX_AMOUNT / 60)) m * 60 else null
}

fun Habit.amountText(amount: Long, target: Long): String = when (measureType) {
    MeasureType.CHECK -> if (amount >= 1) "완료" else "아직 기록이 없어요"
    MeasureType.QUANTITY -> "${formatQuantity(amount)}/${formatQuantity(target)}$unit"
    MeasureType.DURATION -> "${amount / 60}/${target / 60}분"
}

fun Habit.goalLabel(): String {
    val g = goals.last()
    val amount = when (measureType) {
        MeasureType.CHECK -> ""
        MeasureType.QUANTITY -> " ${formatQuantity(g.targetAmount)}$unit"
        MeasureType.DURATION -> " ${g.targetAmount / 60}분"
    }
    val days = "월화수목금토일"
    return when (g.mode) {
        GoalMode.DAILY -> "매일$amount"
        GoalMode.WEEKDAYS -> (g.weekdays ?: emptyList()).joinToString("·") { days[it - 1].toString() } + amount
        GoalMode.WEEKLY_COUNT -> "주 ${g.targetDays}일$amount"
    }
}
