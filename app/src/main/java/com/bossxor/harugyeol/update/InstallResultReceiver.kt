package com.bossxor.harugyeol.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.SystemClock
import android.provider.Settings
import android.widget.Toast
import com.bossxor.harugyeol.HarugyeolApp
import com.bossxor.harugyeol.backup.Backup
import com.bossxor.harugyeol.habit.pauseTimer
import kotlinx.coroutines.*
import java.io.File

/** Installation callbacks never perform disk I/O on the broadcast main thread. */
class InstallResultReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Updater.ACTION_RESULT) return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_SUCCESS) return
        if (status == PackageInstaller.STATUS_FAILURE_ABORTED) {
            Toast.makeText(ctx, "업데이트 설치를 취소했어요. 설정에서 다시 설치할 수 있어요", Toast.LENGTH_LONG).show()
            return
        }
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if (confirm != null && runCatching { ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return
        }
        val pending = goAsync()
        val context = ctx.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var message = "업데이트를 설치하지 못했어요. 설정에서 다시 시도해 주세요"
            try {
                val apk = File(context.cacheDir, "update.apk")
                if (apk.exists()) {
                    Updater.verify(context, apk)
                    val app = context as HarugyeolApp
                    val boot = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
                    app.store.update { it.pauseTimer(SystemClock.elapsedRealtime(), boot) }
                    app.store.createSnapshot()
                    Updater.saveBackupToDownloads(context, "harugyeol-backup.json", Backup.encode(app.store.state.value))
                    Updater.installDirectly(context, apk, "Harugyeol-update.apk")
                    message = "다운로드 폴더의 설치 파일로 업데이트해 주세요"
                }
            } catch (e: Exception) { message = e.message ?: message }
            finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    pending.finish()
                }
            }
        }
    }
}
