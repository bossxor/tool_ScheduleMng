package com.bossxor.harugyeol.update

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

// docs/technical-design.md 7절: GitHub 릴리스 확인 → 다운로드 → 검사 → 앱 내부 설치, 실패하면 직접 설치

class Release(val tag: String, val version: String, val notes: String, val apkUrl: String, val size: Long)

class UpdateException(message: String) : Exception(message)

object Updater {
    private const val API = "https://api.github.com/repos/bossxor/tool_ScheduleMng/releases/latest"
    private const val MAX_APK = 100L * 1024 * 1024
    const val ACTION_RESULT = "com.bossxor.harugyeol.INSTALL_RESULT"

    /** "v1.2.3" → [1,2,3] */
    fun parseVersion(s: String): List<Int> =
        s.trim().removePrefix("v").removePrefix("V").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }

    fun isNewer(remote: String, local: String): Boolean {
        val r = parseVersion(remote)
        val l = parseVersion(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    /** 최신 릴리스. 릴리스가 없으면 null. 네트워크는 호출자가 IO 스레드에서 부른다. */
    fun fetchLatest(): Release? {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode == 404) return null
            if (conn.responseCode != 200) throw UpdateException("업데이트 정보를 받지 못했어요 (${conn.responseCode})")
            val root = Json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
            return parseRelease(root)
        } finally {
            conn.disconnect()
        }
    }

    fun parseRelease(root: JsonObject): Release? {
        val tag = root["tag_name"]?.jsonPrimitive?.content ?: return null
        val assets: JsonArray = root["assets"]?.jsonArray ?: return null
        val apks = assets.map { it.jsonObject }.filter { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
        if (apks.size != 1) return null // asset이 하나가 아니면 안전하게 건너뛴다
        val a = apks.single()
        return Release(
            tag, tag.removePrefix("v"), root["body"]?.jsonPrimitive?.content.orEmpty(),
            a["browser_download_url"]!!.jsonPrimitive.content,
            a["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0,
        )
    }

    fun download(url: String, dest: File, onProgress: (Int) -> Unit) {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode != 200) throw UpdateException("다운로드하지 못했어요 (${conn.responseCode})")
            val total = conn.contentLengthLong
            if (total > MAX_APK) throw UpdateException("파일이 너무 커요")
            val tmp = File(dest.parentFile, dest.name + ".part")
            var read = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (read > MAX_APK) throw UpdateException("파일이 너무 커요")
                        if (total > 0) onProgress((read * 100 / total).toInt())
                    }
                }
            }
            if (!tmp.renameTo(dest)) throw UpdateException("파일을 저장하지 못했어요")
        } finally {
            conn.disconnect()
        }
    }

    /** 패키지명과 버전 코드를 확인한다. 서명 일치는 설치 과정에서 시스템이 강제한다. */
    fun verify(ctx: Context, apk: File) {
        val info = ctx.packageManager.getPackageArchiveInfo(apk.path, 0)
            ?: throw UpdateException("설치 파일을 읽을 수 없어요")
        if (info.packageName != ctx.packageName) throw UpdateException("다른 앱의 설치 파일이에요")
        val current = ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode
        if (info.longVersionCode <= current) throw UpdateException("이미 설치된 버전이에요")
    }

    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()

    /** 1단계: 앱 내부 설치 */
    fun install(ctx: Context, apk: File) {
        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = pi.createSession(params)
        pi.openSession(id).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("harugyeol", 0, apk.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val intent = Intent(ctx, InstallResultReceiver::class.java).setAction(ACTION_RESULT).setPackage(ctx.packageName)
            val pending = PendingIntent.getBroadcast(ctx, id, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(pending.intentSender)
        }
    }

    /** 2단계: 다운로드 폴더에 설치 파일을 저장하고 시스템 설치 화면을 연다 */
    fun installDirectly(ctx: Context, apk: File, name: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/vnd.android.package-archive")
        }
        val uri: Uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw UpdateException("다운로드 폴더에 저장하지 못했어요")
        ctx.contentResolver.openOutputStream(uri)!!.use { out -> apk.inputStream().use { it.copyTo(out) } }
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** 백업 JSON을 다운로드 폴더에 남긴다(직접 설치 전 안전장치) */
    fun saveBackupToDownloads(ctx: Context, name: String, text: String) {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, "application/json")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
        ctx.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
    }
}
