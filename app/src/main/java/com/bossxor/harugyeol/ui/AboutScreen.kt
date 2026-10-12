package com.bossxor.harugyeol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable fun AboutScreen(version: String, onBack: () -> Unit) {
    val c = LocalHaru.current
    val ctx = LocalContext.current
    val licenses = remember { ctx.assets.list("licenses").orEmpty().map { name -> name to ctx.assets.open("licenses/$name").bufferedReader().use { it.readText() } } }
    var open by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("앱 정보·라이선스", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("하루결 $version", style = HaruType.title, color = c.text)
            Text("개인용 오프라인 습관·일정 관리. 데이터는 이 기기에 저장되며 네트워크는 업데이트에만 사용해요.", style = HaruType.body, color = c.textSub)
            Text("Android·Jetpack Compose·Kotlin·kotlinx.serialization/coroutines: Apache License 2.0. 글자체 Jua·Pretendard: SIL OFL 1.1. 그림 Tabler Icons: MIT, Paweł Kuna.", style = HaruType.bodyS, color = c.textSub)
            licenses.forEach { (name, text) ->
                PillButton(name, c.surface, c.text) { open = if (open == name) null else name }
                if (open == name) Text(text, style = HaruType.bodyS, color = c.text)
            }
        }
    }
}
