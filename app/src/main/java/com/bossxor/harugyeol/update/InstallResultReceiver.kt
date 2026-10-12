package com.bossxor.harugyeol.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast
import com.bossxor.harugyeol.HarugyeolApp
import java.io.File

/** 앱 내부 설치 결과. 확인이 필요하면 시스템 화면을 열고, 실패하면 직접 설치로 넘어간다. */
class InstallResultReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val apk = File(ctx.cacheDir, "update.apk")
                val app = ctx.applicationContext as HarugyeolApp
                try {
                    if (apk.exists()) {
                        Updater.saveBackupToDownloads(ctx, "harugyeol-backup.json", com.bossxor.harugyeol.backup.Backup.encode(app.store.state.value))
                        Updater.installDirectly(ctx, apk, "Harugyeol-update.apk")
                        Toast.makeText(ctx, "다운로드 폴더의 설치 파일로 업데이트해 주세요", Toast.LENGTH_LONG).show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(ctx, "업데이트를 설치하지 못했어요", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
