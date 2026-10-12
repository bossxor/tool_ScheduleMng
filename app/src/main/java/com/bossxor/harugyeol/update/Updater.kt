package com.bossxor.harugyeol.update

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class Release(val tag: String, val version: String, val notes: String, val apkUrl: String, val size: Long)
class UpdateException(message: String) : Exception(message)

object Updater {
    private const val API = "https://api.github.com/repos/bossxor/tool_ScheduleMng/releases/latest"
    private const val MAX_APK = 100L * 1024 * 1024
    const val ACTION_RESULT = "com.bossxor.harugyeol.INSTALL_RESULT"

    fun parseVersion(s: String): List<Int> = s.trim().removePrefix("v").removePrefix("V").let { version ->
        if (!Regex("""\d+(\.\d+)*""").matches(version)) emptyList()
        else version.split('.').map { it.toIntOrNull() ?: return emptyList() }
    }
    fun isNewer(remote: String, local: String): Boolean {
        val r = parseVersion(remote); val l = parseVersion(local)
        if (r.isEmpty() || l.isEmpty()) return false
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }; val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
    fun isReleaseAssetUrl(url: String, tag: String? = null): Boolean = runCatching {
        val uri = URI(url)
        val prefix = "/bossxor/tool_ScheduleMng/releases/download/"
        val parts = uri.path.removePrefix(prefix).split('/')
        uri.scheme == "https" && uri.host == "github.com" && uri.port == -1 &&
            uri.userInfo == null && uri.query == null && uri.fragment == null &&
            uri.path.startsWith(prefix) && parts.size == 2 && parseVersion(parts[0]).isNotEmpty() &&
            (tag == null || parts[0] == tag) && parts[1].endsWith(".apk") && parts[1] != ".apk"
    }.getOrDefault(false)

    fun fetchLatest(): Release? {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000; conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode == 404) return null
            if (conn.responseCode != 200) throw UpdateException("업데이트 정보를 받지 못했어요 (${conn.responseCode})")
            val bytes = conn.inputStream.use { input ->
                val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    if (out.size() + n > 1024 * 1024) throw UpdateException("업데이트 정보가 너무 커요")
                    out.write(buffer, 0, n)
                }
                out.toByteArray()
            }
            return parseRelease(Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject)
        } finally { conn.disconnect() }
    }
    fun parseRelease(root: JsonObject): Release? = runCatching {
        if (root["draft"]?.jsonPrimitive?.content == "true" || root["prerelease"]?.jsonPrimitive?.content == "true") return null
        val tag = root["tag_name"]?.jsonPrimitive?.content ?: return null
        if (parseVersion(tag).isEmpty()) return null
        val apks = root["assets"]?.jsonArray?.map { it.jsonObject }?.filter { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true } ?: return null
        if (apks.size != 1) return null
        val asset = apks.single()
        val url = asset["browser_download_url"]?.jsonPrimitive?.content ?: return null
        val size = asset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: return null
        if (size !in 1..MAX_APK || !isReleaseAssetUrl(url, tag)) return null
        if (asset["state"]?.jsonPrimitive?.content?.let { it != "uploaded" } == true) return null
        Release(tag, tag.removePrefix("v").removePrefix("V"), root["body"]?.jsonPrimitive?.content.orEmpty(), url, size)
    }.getOrNull()

    fun download(url: String, dest: File, expectedSize: Long = 0, onProgress: (Int) -> Unit) {
        if (!isReleaseAssetUrl(url)) throw UpdateException("공식 릴리스의 설치 파일이 아니에요")
        if (expectedSize < 0 || expectedSize > MAX_APK) throw UpdateException("설치 파일 크기가 잘못됐어요")
        val tmp = File(dest.parentFile, dest.name + ".part")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000; conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        try {
            if (conn.responseCode != 200) throw UpdateException("다운로드하지 못했어요 (${conn.responseCode})")
            if (conn.url.protocol != "https") throw UpdateException("안전하지 않은 다운로드 주소예요")
            val total = conn.contentLengthLong
            if (total > MAX_APK) throw UpdateException("파일이 너무 커요")
            if (expectedSize > 0 && total > 0 && total != expectedSize) throw UpdateException("설치 파일 크기가 릴리스 정보와 달라요")
            var read = 0L
            conn.inputStream.use { input -> tmp.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    read += n
                    if (read > MAX_APK || (expectedSize > 0 && read > expectedSize)) throw UpdateException("파일이 너무 커요")
                    out.write(buffer, 0, n)
                    if (total > 0) onProgress((read * 100 / total).toInt().coerceIn(0, 99))
                }
            } }
            if (read == 0L || (total >= 0 && read != total) || (expectedSize > 0 && read != expectedSize)) throw UpdateException("다운로드가 끝나지 않았어요")
            Files.move(tmp.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            onProgress(100)
        } finally { conn.disconnect(); tmp.delete() }
    }
    fun verify(ctx: Context, apk: File, expectedVersion: String? = null) {
        if (!apk.isFile || apk.length() !in 1..MAX_APK) throw UpdateException("설치 파일 크기가 잘못됐어요")
        val info = ctx.packageManager.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: throw UpdateException("설치 파일을 읽을 수 없어요")
        if (info.packageName != ctx.packageName) throw UpdateException("다른 앱의 설치 파일이에요")
        if (expectedVersion != null && parseVersion(info.versionName.orEmpty()) != parseVersion(expectedVersion)) throw UpdateException("설치 파일 버전이 릴리스 정보와 달라요")
        val current = ctx.packageManager.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).longVersionCode
        val signatures = info.signingInfo?.apkContentsSigners.orEmpty()
        if (signatures.isEmpty() || signatures.any { !ctx.packageManager.hasSigningCertificate(ctx.packageName, it.toByteArray(), PackageManager.CERT_INPUT_RAW_X509) }) throw UpdateException("설치 파일의 서명이 달라요")
        if (info.longVersionCode <= current) throw UpdateException("이미 설치된 버전이에요")
    }
    fun reusableApk(ctx: Context, release: Release, apk: File): Boolean = runCatching {
        if (apk.length() != release.size) return false
        verify(ctx, apk, release.version); true
    }.getOrDefault(false)
    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()
    fun install(ctx: Context, apk: File) {
        verify(ctx, apk)
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = installer.createSession(params)
        try { installer.openSession(id).use { session ->
            apk.inputStream().use { input -> session.openWrite("harugyeol", 0, apk.length()).use { out -> input.copyTo(out); session.fsync(out) } }
            val intent = Intent(ctx, InstallResultReceiver::class.java).setAction(ACTION_RESULT).setPackage(ctx.packageName)
            val pending = PendingIntent.getBroadcast(ctx, id, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(pending.intentSender)
        } } catch (e: Exception) { runCatching { installer.abandonSession(id) }; throw e }
    }
    private fun saveDownload(ctx: Context, name: String, mime: String, write: (java.io.OutputStream) -> Unit): android.net.Uri {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, mime); put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = ctx.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw UpdateException("다운로드 폴더에 저장하지 못했어요")
        try {
            val output = ctx.contentResolver.openOutputStream(uri) ?: throw UpdateException("저장 파일을 열 수 없어요")
            output.use(write)
            if (ctx.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null) != 1) throw UpdateException("파일 저장을 마치지 못했어요")
            return uri
        } catch (e: Exception) { runCatching { ctx.contentResolver.delete(uri, null, null) }; throw e }
    }
    fun installDirectly(ctx: Context, apk: File, name: String) {
        verify(ctx, apk)
        val mime = "application/vnd.android.package-archive"
        val uri = saveDownload(ctx, name, mime) { out -> apk.inputStream().use { it.copyTo(out) } }
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (_: Exception) { throw UpdateException("다운로드 폴더에 $name 파일을 저장했어요. 파일 앱에서 열어 설치해 주세요") }
    }
    fun saveBackupToDownloads(ctx: Context, name: String, text: String) {
        saveDownload(ctx, name, "application/json") { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
}
