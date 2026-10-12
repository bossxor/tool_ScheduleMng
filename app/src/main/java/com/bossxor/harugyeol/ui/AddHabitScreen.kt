package com.bossxor.harugyeol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.bossxor.harugyeol.habit.DayState
import com.bossxor.harugyeol.habit.GoalMode
import com.bossxor.harugyeol.habit.MeasureType
import com.bossxor.harugyeol.habit.parseMinutes
import com.bossxor.harugyeol.habit.parseQuantity

class NewHabit(
    val name: String, val colorIndex: Int, val type: MeasureType, val unit: String?,
    val mode: GoalMode, val weekdays: List<Int>?, val targetDays: Int?, val targetAmount: Long,
)

private val presets = listOf(
    Triple("물 마시기", MeasureType.QUANTITY, "잔" to "8"),
    Triple("독서", MeasureType.DURATION, "" to "30"),
    Triple("스트레칭", MeasureType.CHECK, "" to ""),
)

@Composable
fun AddHabitScreen(onSave: (NewHabit) -> String?, onClose: () -> Unit) {
    val c = LocalHaru.current
    var name by remember { mutableStateOf("") }
    var color by remember { mutableIntStateOf(1) }
    var type by remember { mutableStateOf(MeasureType.CHECK) }
    var unit by remember { mutableStateOf("잔") }
    var target by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(GoalMode.DAILY) }
    var weekdays by remember { mutableStateOf(setOf(1, 2, 3, 4, 5)) }
    var targetDays by remember { mutableIntStateOf(3) }
    var error by remember { mutableStateOf<String?>(null) }
    val p = palette(color)

    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp).clickable(onClickLabel = "닫기", onClick = onClose), contentAlignment = Alignment.Center) {
                Text("✕", style = HaruType.headline, color = c.text)
            }
            Text("습관 추가", style = HaruType.title, color = c.text, modifier = Modifier.weight(1f))
            PillButton("저장", HabitPalettes[1].fill, Color.White) {
                val amount: Long? = when (type) {
                    MeasureType.CHECK -> 1L
                    MeasureType.QUANTITY -> parseQuantity(target)
                    MeasureType.DURATION -> parseMinutes(target)
                }
                error = if (amount == null || amount <= 0) "목표량을 입력해 주세요"
                else onSave(
                    NewHabit(
                        name, color, type, if (type == MeasureType.QUANTITY) unit else null,
                        mode, if (mode == GoalMode.WEEKDAYS) weekdays.toList() else null,
                        if (mode == GoalMode.WEEKLY_COUNT) targetDays else null, amount,
                    )
                )
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Sticker(name.ifBlank { "습" }, p, DayState.ACHIEVED, 1f, 72.dp)
            }
            Label("추천 습관")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { (n, t, ut) ->
                    Chip(n, selected = false) {
                        name = n; type = t; unit = ut.first.ifEmpty { unit }; target = ut.second
                    }
                }
            }
            Label("이름")
            HaruTextField(name, { name = it }, "예: 물 마시기", Modifier.fillMaxWidth())
            Label("색")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HabitPalettes.forEachIndexed { i, hp ->
                    Box(
                        Modifier
                            .size(48.dp)
                            .background(hp.fill, CircleShape)
                            .border(if (i == color) 4.dp else 2.dp, c.outline, CircleShape)
                            .clickable(onClickLabel = "색 선택") { color = i },
                        contentAlignment = Alignment.Center,
                    ) { if (i == color) Text("✓", style = HaruType.headline, color = hp.onFill) }
                }
            }
            Label("목표 유형")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("완료", type == MeasureType.CHECK) { type = MeasureType.CHECK }
                Chip("수량", type == MeasureType.QUANTITY) { type = MeasureType.QUANTITY }
                Chip("시간", type == MeasureType.DURATION) { type = MeasureType.DURATION }
            }
            if (type != MeasureType.CHECK) {
                Label(if (type == MeasureType.QUANTITY) "목표량과 단위" else "목표 시간(분)")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaruTextField(target, { target = it }, if (type == MeasureType.QUANTITY) "8" else "30", Modifier.weight(1f), numeric = true)
                    if (type == MeasureType.QUANTITY) HaruTextField(unit, { unit = it }, "단위", Modifier.weight(1f))
                }
            }
            Label("주기")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("매일", mode == GoalMode.DAILY) { mode = GoalMode.DAILY }
                Chip("요일 선택", mode == GoalMode.WEEKDAYS) { mode = GoalMode.WEEKDAYS }
                Chip("주 N일", mode == GoalMode.WEEKLY_COUNT) { mode = GoalMode.WEEKLY_COUNT }
            }
            if (mode == GoalMode.WEEKDAYS) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    "월화수목금토일".forEachIndexed { i, ch ->
                        val d = i + 1
                        val on = d in weekdays
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(if (on) p.fill else c.surface, CircleShape)
                                .border(2.dp, c.outline, CircleShape)
                                .clickable { weekdays = if (on) weekdays - d else weekdays + d },
                            contentAlignment = Alignment.Center,
                        ) { Text(ch.toString(), style = HaruType.button, color = if (on) p.onFill else c.text) }
                    }
                }
            }
            if (mode == GoalMode.WEEKLY_COUNT) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { n ->
                        val on = n == targetDays
                        Box(
                            Modifier
                                .size(44.dp)
                                .background(if (on) p.fill else c.surface, CircleShape)
                                .border(2.dp, c.outline, CircleShape)
                                .clickable { targetDays = n },
                            contentAlignment = Alignment.Center,
                        ) { Text(n.toString(), style = HaruType.button, color = if (on) p.onFill else c.text) }
                    }
                }
                Text("주 ${targetDays}일 목표", style = HaruType.bodyS, color = c.textSub)
            }
            error?.let { Text(it, style = HaruType.body, color = c.errorText) }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = HaruType.headline, color = LocalHaru.current.text)
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalHaru.current
    PillButton(text, if (selected) HabitPalettes[1].fill else c.surface, if (selected) Color.White else c.text, onClick = onClick)
}
