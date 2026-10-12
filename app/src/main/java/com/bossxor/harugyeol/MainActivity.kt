package com.bossxor.harugyeol

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import com.bossxor.harugyeol.habit.GoalMode
import com.bossxor.harugyeol.habit.HabitInputException
import com.bossxor.harugyeol.habit.addAmount
import com.bossxor.harugyeol.habit.addHabit
import com.bossxor.harugyeol.habit.setSkipped
import com.bossxor.harugyeol.habit.setTotal
import com.bossxor.harugyeol.ui.AddHabitScreen
import com.bossxor.harugyeol.ui.DetailActions
import com.bossxor.harugyeol.ui.HabitDetailScreen
import com.bossxor.harugyeol.ui.HabitActions
import com.bossxor.harugyeol.ui.HabitsScreen
import com.bossxor.harugyeol.ui.HaruTheme
import com.bossxor.harugyeol.ui.LocalHaru
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    private val today = mutableStateOf(LocalDate.now())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = try {
            (application as HarugyeolApp).store
        } catch (e: StoreException) {
            setContent { HaruTheme { com.bossxor.harugyeol.ui.ErrorScreen(e.message ?: "") } }
            return
        }
        setContent {
            HaruTheme {
                val data by store.state.collectAsState()
                val scope = rememberCoroutineScope()
                var adding by remember { mutableStateOf(false) }
                var openId by remember { mutableStateOf<String?>(null) }

                // 저장소 변경은 입력 검증 실패를 안내하고 상태는 바꾸지 않는다.
                fun change(f: (AppData) -> AppData) {
                    scope.launch {
                        try {
                            store.update(f)
                        } catch (e: HabitInputException) {
                            Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "저장하지 못했어요. 다시 시도해 주세요", Toast.LENGTH_SHORT).show()
                        }
                    }
                }

                val actions = HabitActions(
                    addAmount = { id, d -> change { it.addAmount(id, today.value, d, today.value, System.currentTimeMillis()) } },
                    setTotal = { id, a -> change { it.setTotal(id, today.value, a, today.value, System.currentTimeMillis()) } },
                    setSkipped = { id, s -> change { it.setSkipped(id, today.value, s, today.value, System.currentTimeMillis()) } },
                )

                val detailActions = DetailActions(
                    setTotalAt = { id, date, a -> change { it.setTotal(id, date, a, today.value, System.currentTimeMillis()) } },
                    setSkippedAt = { id, date, s -> change { it.setSkipped(id, date, s, today.value, System.currentTimeMillis()) } },
                )

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(LocalHaru.current.bg)
                        .windowInsetsPadding(WindowInsets.systemBars),
                ) {
                    if (adding) {
                        BackHandler { adding = false }
                        AddHabitScreen(
                            onClose = { adding = false },
                            onSave = { n ->
                                try {
                                    val now = System.currentTimeMillis()
                                    // 저장소 직렬화를 위해 같은 경로를 쓰되 검증 실패는 즉시 화면에 알린다.
                                    val probe = data.addHabit(
                                        n.name, n.colorIndex, n.type, n.unit, n.mode, n.weekdays, n.targetDays,
                                        n.targetAmount, today.value, today.value, now,
                                    )
                                    check(probe.habits.size == data.habits.size + 1)
                                    change {
                                        it.addHabit(
                                            n.name, n.colorIndex, n.type, n.unit, n.mode, n.weekdays, n.targetDays,
                                            n.targetAmount, today.value, today.value, now,
                                        )
                                    }
                                    adding = false
                                    null
                                } catch (e: HabitInputException) {
                                    e.message
                                }
                            },
                        )
                    } else {
                        val open = data.habits.firstOrNull { it.id == openId }
                        if (open != null) {
                            BackHandler { openId = null }
                            HabitDetailScreen(open, today.value, detailActions, onBack = { openId = null })
                        } else {
                            HabitsScreen(data, today.value, actions, onAdd = { adding = true }, onOpen = { openId = it })
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        today.value = LocalDate.now()
    }
}
