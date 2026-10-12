package com.bossxor.harugyeol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** 저장 파일을 읽지 못했을 때. 파일은 덮어쓰지 않고 그대로 둔다. */
@Composable
fun ErrorScreen(message: String) {
    val c = LocalHaru.current
    Column(
        Modifier.fillMaxSize().background(c.bg).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("데이터를 불러오지 못했어요", style = HaruType.title, color = c.text)
        Text(message, style = HaruType.body, color = c.textSub)
        Text("저장된 파일은 그대로 두었어요. 백업 파일에서 복원하거나 앱을 업데이트해 주세요.", style = HaruType.body, color = c.textSub)
    }
}
