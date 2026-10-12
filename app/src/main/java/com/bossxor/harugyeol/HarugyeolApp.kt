package com.bossxor.harugyeol

import android.app.Application
import com.bossxor.harugyeol.reminders.ReminderScheduler
import com.bossxor.harugyeol.widget.TodayWidget
import kotlinx.coroutines.*
import java.io.File

class HarugyeolApp : Application() {
    val store: AppStore by lazy { AppStore.open(File(filesDir, "data.json")) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        scope.launch {
            try {
                store.state.collect { data ->
                    try { ReminderScheduler.rescheduleAll(this@HarugyeolApp, data) } catch (_: Exception) {}
                    try { TodayWidget.refresh(this@HarugyeolApp) } catch (_: Exception) {}
                }
            } catch (_: StoreException) { /* 손상 파일은 앱의 복구 화면에서 안내한다. */ }
        }
    }
}
