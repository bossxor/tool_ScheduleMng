package com.bossxor.harugyeol.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bossxor.harugyeol.AppData
import kotlinx.coroutines.launch

private data class OrganizationEdit(val tag: Boolean, val id: String?, val name: String, val color: Int?)

@Composable
fun OrganizationScreen(
    data: AppData, onBack: () -> Unit,
    saveList: suspend (String?, String, Int?) -> String?, deleteList: suspend (String) -> String?,
    saveTag: suspend (String?, String, Int?) -> String?, deleteTag: suspend (String) -> String?,
) {
    val c = LocalHaru.current
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf<OrganizationEdit?>(null) }
    var deleting by remember { mutableStateOf<OrganizationEdit?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    fun open(value: OrganizationEdit) { error = null; edit = value }
    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("목록과 태그", onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingCard("목록") {
                data.lists.forEach { list ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(list.name, style = HaruType.body, color = c.text)
                        if (!list.isInbox) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("수정", c.surface, c.text) { open(OrganizationEdit(false, list.id, list.name, list.colorIndex)) }
                            PillButton("삭제", c.errorBg, c.errorText) { error = null; deleting = OrganizationEdit(false, list.id, list.name, list.colorIndex) }
                        }
                    }
                }
                PillButton("목록 추가", HabitPalettes[1].fill, HabitPalettes[1].onFill) { open(OrganizationEdit(false, null, "", null)) }
            }
            SettingCard("태그") {
                if (data.tags.isEmpty()) Text("태그가 없어요", style = HaruType.body, color = c.textSub)
                data.tags.forEach { tag ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(tag.name, style = HaruType.body, color = c.text)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton("수정", c.surface, c.text) { open(OrganizationEdit(true, tag.id, tag.name, tag.colorIndex)) }
                            PillButton("삭제", c.errorBg, c.errorText) { error = null; deleting = OrganizationEdit(true, tag.id, tag.name, tag.colorIndex) }
                        }
                    }
                }
                PillButton("태그 추가", HabitPalettes[2].fill, HabitPalettes[2].onFill) { open(OrganizationEdit(true, null, "", null)) }
            }
        }
    }
    edit?.let { item ->
        var name by remember(item) { mutableStateOf(item.name) }
        var color by remember(item) { mutableStateOf(item.color) }
        Dialog(onDismissRequest = { if (!busy) edit = null }) {
            PopCard {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text((if (item.tag) "태그" else "목록") + if (item.id == null) " 추가" else " 수정", style = HaruType.headline, color = c.text)
                    HaruTextField(name, { name = it }, "이름", Modifier.fillMaxWidth())
                    Text("색", style = HaruType.body, color = c.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(4) { index ->
                            val p = HabitPalettes[index]
                            PillButton(if (color == index) "✓" else "${index + 1}", p.fill, p.onFill, Modifier.weight(1f)) { color = index }
                        }
                    }
                    error?.let { Text(it, style = HaruType.bodyS, color = c.errorText) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("취소", c.surface, c.text, Modifier.weight(1f)) { if (!busy) edit = null }
                        PillButton(if (busy) "저장 중" else "저장", HabitPalettes[1].fill, HabitPalettes[1].onFill, Modifier.weight(1f)) {
                            if (!busy) scope.launch {
                                busy = true
                                try { error = if (item.tag) saveTag(item.id, name, color) else saveList(item.id, name, color); if (error == null) edit = null }
                                finally { busy = false }
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { item ->
        Dialog(onDismissRequest = { if (!busy) deleting = null }) {
            PopCard {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("‘${item.name}’을 삭제할까요?", style = HaruType.headline, color = c.text)
                    Text(if (item.tag) "할 일에서 이 태그가 제거돼요. 할 일은 삭제되지 않아요." else "휴지통을 포함한 이 목록의 할 일은 받은함으로 이동해요.", style = HaruType.body, color = c.textSub)
                    error?.let { Text(it, style = HaruType.bodyS, color = c.errorText) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PillButton("취소", c.surface, c.text, Modifier.weight(1f)) { if (!busy) deleting = null }
                        PillButton(if (busy) "삭제 중" else "삭제", c.errorBg, c.errorText, Modifier.weight(1f)) {
                            if (!busy) scope.launch {
                                busy = true
                                try { error = if (item.tag) deleteTag(item.id!!) else deleteList(item.id!!); if (error == null) deleting = null }
                                finally { busy = false }
                            }
                        }
                    }
                }
            }
        }
    }
}
