package com.bossxor.harugyeol.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bossxor.harugyeol.AppData

@Composable fun ResolveTimerButtons(data: AppData, onSave: () -> Unit, onDiscard: () -> Unit) {
    val c = LocalHaru.current
    if (data.timer == null) return
    Text("타이머가 남아 있어요. 먼저 저장하거나 버려 주세요.", style = HaruType.body, color = c.text)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("타이머 저장", c.surface, c.text, onClick = onSave)
        PillButton("타이머 버리기", c.errorBg, c.errorText, onClick = onDiscard)
    }
}
