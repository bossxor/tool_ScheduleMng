package com.bossxor.harugyeol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bossxor.harugyeol.AppData

/** 원본은 그대로 보존하고 검증된 후보를 사용자가 확인한 뒤 복구한다. */
@Composable
fun ErrorScreen(
    message: String,
    onRestore: () -> Unit = {},
    onSnapshot: () -> Unit = {},
    candidate: AppData? = null,
    onConfirm: () -> Unit = {},
    onCancel: () -> Unit = {},
    busy: Boolean = false,
) {
    val c = LocalHaru.current
    Column(
        Modifier.fillMaxSize().background(c.bg).verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("데이터를 불러오지 못했어요", style = HaruType.title, color = c.text)
        Text(message, style = HaruType.body, color = c.textSub)
        Text("저장된 원본은 그대로 남겨요. 백업 파일이나 마지막 스냅샷을 선택하고 내용을 확인해 주세요.", style = HaruType.body, color = c.textSub)
        if (busy) {
            Text("복구 데이터를 처리하고 있어요…", style = HaruType.body, color = c.textSub)
        } else if (candidate == null) {
            PillButton("백업 파일 선택", HabitPalettes[1].fill, HabitPalettes[1].onFill, Modifier.fillMaxWidth(), onClick = onRestore)
            PillButton("마지막 스냅샷 확인", c.surface, c.text, Modifier.fillMaxWidth(), onClick = onSnapshot)
        } else {
            PopCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("이 데이터로 복구할까요?", style = HaruType.headline, color = c.text)
                    Text("습관 ${candidate.habits.size}개 · 할 일 ${candidate.tasks.size}개", style = HaruType.body, color = c.text)
                    Text("습관 기록 ${candidate.habits.sumOf { it.logs.size }}개 · 목록 ${candidate.lists.size}개 · 태그 ${candidate.tags.size}개", style = HaruType.bodyS, color = c.textSub)
                    if (candidate.timer != null) Text("가져온 타이머는 보존된 시간에서 일시정지돼요", style = HaruType.bodyS, color = c.textSub)
                    Text("현재 원본 파일은 별도로 보존한 뒤 복구해요.", style = HaruType.bodyS, color = c.textSub)
                    PillButton("확인하고 복구", HabitPalettes[1].fill, HabitPalettes[1].onFill, Modifier.fillMaxWidth(), onClick = onConfirm)
                    PillButton("취소", c.surface, c.text, Modifier.fillMaxWidth(), onClick = onCancel)
                }
            }
        }
    }
}