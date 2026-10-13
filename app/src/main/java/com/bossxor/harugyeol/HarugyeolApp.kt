package com.bossxor.harugyeol

import android.app.Application
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import com.bossxor.harugyeol.habit.TimerState
import com.bossxor.harugyeol.habit.checkpointTimer
import com.bossxor.harugyeol.reminders.ReminderScheduler
import com.bossxor.harugyeol.widget.TodayWidget
import kotlinx.coroutines.*
import java.io.File

class HarugyeolApp : Application() {
    val store: AppStore by lazy { AppStore.open(File(filesDir, "data.json")) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observer: Job? = null
    @Synchronized fun observeStore() {
        if (observer?.isActive == true) return
        observer = scope.launch {
            try {
                var first = true
                store.state.collect { data ->
                    try { ReminderScheduler.rescheduleAll(this@HarugyeolApp, data, reset = first); first = false } catch (_: Exception) {}
                    try { TodayWidget.refresh(this@HarugyeolApp) } catch (_: Exception) {}
                }
            } catch (_: StoreException) {}
        }
    }
    fun preserveTimerCheckpoint() {
        val session = store.state.value.timer?.takeIf { it.state == TimerState.RUNNING }?.sessionId ?: return
        scope.launch {
            try {
                store.update { current ->
                    if (current.timer?.sessionId != session) current else current.checkpointTimer(
                        SystemClock.elapsedRealtime(), Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0))
                }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { Toast.makeText(this@HarugyeolApp, "타이머 측정량을 보존하지 못했어요. 다시 저장해 주세요", Toast.LENGTH_LONG).show() }
            }
        }
    }
    override fun onCreate() {
        super.onCreate()
        observeStore()
    }
}
