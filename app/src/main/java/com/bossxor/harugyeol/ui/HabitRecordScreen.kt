package com.bossxor.harugyeol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bossxor.harugyeol.habit.*
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable fun HabitRecordScreen(habit: Habit, date: LocalDate, onBack: () -> Unit, onSave: suspend (Long) -> String?) {
    val c = LocalHaru.current
    val amount = habit.logOf(date)?.amount ?: 0
    var text by remember { mutableStateOf(if (habit.measureType == MeasureType.QUANTITY) formatQuantity(amount) else (amount / 60).toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("${date.monthValue}월 ${date.dayOfMonth}일 기록", onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(habit.name, style = HaruType.headline, color = c.text)
            Text(if (habit.measureType == MeasureType.DURATION) "시간 총량(분). 저장하면 기존 초 단위 타이머 기록을 이 총량으로 바꿔요." else "${habit.unit} 총량", style = HaruType.bodyS, color = c.textSub)
            HaruTextField(text, { text = it }, "기록량", Modifier.fillMaxWidth(), numeric = true)
            error?.let { Text(it, style = HaruType.body, color = c.errorText) }
            PillButton(if (saving) "저장 중" else "저장", c.surface, c.text) {
                if (!saving) try {
                    val value = (if (habit.measureType == MeasureType.QUANTITY) parseQuantity(text) else parseMinutes(text)) ?: throw HabitInputException("기록량을 입력해 주세요")
                    saving = true
                    scope.launch { error = onSave(value); saving = false; if (error == null) onBack() }
                } catch (e: HabitInputException) { error = e.message }
            }
        }
    }
}
