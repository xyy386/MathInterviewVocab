package com.xingyiyao.mathinterviewvocab.updates

import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull

data class UpdateUiState(
    val currentVersion: String,
    val status: String = "idle",
    val message: String = "仅在点击检查更新后联网，学习和发音始终离线。",
    val availableVersion: String? = null,
    val notes: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val canDownload: Boolean = false,
    val canInstall: Boolean = false,
    val needsInstallPermission: Boolean = false,
    val needsUserConfirmation: Boolean = false,
)

internal class UpdateFailure(message: String) : IOException(message)

internal data class StableVersion(val major: Long, val minor: Long, val patch: Long) : Comparable<StableVersion> {
    override fun compareTo(other: StableVersion): Int =
        compareValuesBy(this, other, StableVersion::major, StableVersion::minor, StableVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        fun parse(text: String): StableVersion {
            if (!text.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))) {
                throw UpdateFailure("版本号不是有效的正式版本。")
            }
            val numbers = text.split('.').map { it.toLongOrNull() ?: throw UpdateFailure("版本号超出范围。") }
            return StableVersion(numbers[0], numbers[1], numbers[2])
        }
    }
}

internal data class ReleaseAsset(val name: String, val url: String, val bytes: Long, val digest: String?)
internal data class AndroidRelease(
    val version: StableVersion,
    val pageUrl: String,
    val notes: String,
    val apk: ReleaseAsset,
    val checksums: ReleaseAsset,
)

internal sealed interface ReleaseCheck {
    data object Current : ReleaseCheck
    data class Unavailable(val version: StableVersion, val pageUrl: String, val notes: String) : ReleaseCheck
    data class Available(val release: AndroidRelease) : ReleaseCheck
}

internal data class ApkIdentity(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val certificates: Set<String>,
)

/** 只信任官方版本、专用 Android 附件以及当前应用的真实签名。 */
internal object UpdatePolicy {
    const val REPOSITORY = "https://github.com/xyy386/MathInterviewVocab"
    const val LATEST_URL = "https://api.github.com/repos/xyy386/MathInterviewVocab/releases/latest"
    const val CHECKSUM_NAME = "SHA256SUMS-Android.txt"
    const val MAX_METADATA = 1024L * 1024
    const val MAX_CHECKSUMS = 128L * 1024
    const val MAX_APK = 500L * 1024 * 1024
    const val MAX_REDIRECTS = 5
    private val hosts = setOf("github.com", "api.github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")
    private val shaPattern = Regex("[0-9a-fA-F]{64}")

    fun checkedUrl(value: String): URI {
        val uri = try { URI(value) } catch (_: Exception) { throw UpdateFailure("更新地址无效。") }
        if (uri.scheme != "https" || uri.host !in hosts || uri.rawUserInfo != null ||
            uri.port !in setOf(-1, 443) || uri.rawFragment != null || uri.isOpaque) {
            throw UpdateFailure("更新地址不属于受信任的 HTTPS 服务。")
        }
        return uri
    }

    fun redirectUrl(current: String, location: String, redirectCount: Int): String {
        if (redirectCount >= MAX_REDIRECTS) throw UpdateFailure("更新下载重定向次数过多。")
        val resolved = try { checkedUrl(current).resolve(location).toString() }
        catch (_: Exception) { throw UpdateFailure("更新重定向地址无效。") }
        checkedUrl(resolved)
        return resolved
    }

    fun parseRelease(bytes: ByteArray, currentVersion: String): ReleaseCheck {
        if (bytes.size > MAX_METADATA) throw UpdateFailure("版本信息过大。")
        val root = try { Json.parseToJsonElement(strictUtf8(bytes)) as? JsonObject }
        catch (_: Exception) { null } ?: throw UpdateFailure("无法读取正式版本信息。")
        if (root.boolean("draft") || root.boolean("prerelease")) throw UpdateFailure("不安装草稿或预发布版本。")
        val tag = root.string("tag_name")
        if (!tag.startsWith('v')) throw UpdateFailure("正式版本标签无效。")
        val version = StableVersion.parse(tag.drop(1))
        val page = "$REPOSITORY/releases/tag/v$version"
        if (root.string("html_url") != page) throw UpdateFailure("版本页面不属于本项目。")
        val notes = (root["body"] as? JsonPrimitive)?.takeIf { it.isString }?.content?.take(8_000).orEmpty()
        // 旧正式版未发布 Android 安装包时，也应正确显示已是最新。
        if (version <= StableVersion.parse(currentVersion)) return ReleaseCheck.Current
        val assets = root["assets"] as? JsonArray ?: throw UpdateFailure("正式版本附件列表无效。")
        val apkName = "MathInterviewVocab-Android-universal-v$version.apk"
        fun asset(name: String, maximum: Long): ReleaseAsset? {
            val candidates = assets.filterIsInstance<JsonObject>().filter { it.stringOrNull("name") == name }
            if (candidates.size > 1) throw UpdateFailure("正式版本包含重复附件。")
            val entry = candidates.singleOrNull() ?: return null
            if (entry.string("state") != "uploaded") throw UpdateFailure("Android 附件尚未上传完成。")
            val size = (entry["size"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull
                ?: throw UpdateFailure("附件大小无效。")
            if (size !in 1..maximum) throw UpdateFailure("附件大小超出允许范围。")
            val url = entry.string("browser_download_url")
            if (url != "$REPOSITORY/releases/download/v$version/$name") {
                throw UpdateFailure("Android 附件地址与正式版本不一致。")
            }
            checkedUrl(url)
            val rawDigest = entry["digest"]
            val digest = if (rawDigest == null || rawDigest.toString() == "null") null else {
                val text = (rawDigest as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?: throw UpdateFailure("附件摘要无效。")
                if (!text.matches(Regex("sha256:[0-9a-f]{64}"))) throw UpdateFailure("附件摘要无效。")
                text.removePrefix("sha256:")
            }
            return ReleaseAsset(name, url, size, digest)
        }
        val apk = asset(apkName, MAX_APK)
        val checksums = asset(CHECKSUM_NAME, MAX_CHECKSUMS)
        if (apk == null || checksums == null) return ReleaseCheck.Unavailable(version, page, notes)
        return ReleaseCheck.Available(AndroidRelease(version, page, notes, apk, checksums))
    }

    fun checksum(bytes: ByteArray, name: String): String {
        if (bytes.size > MAX_CHECKSUMS) throw UpdateFailure("校验文件过大。")
        val entries = mutableMapOf<String, String>()
        for (line in strictUtf8(bytes).split('\n')) {
            val text = line.removeSuffix("\r")
            if (text.isBlank()) continue
            val match = Regex("([0-9a-fA-F]{64}) [ *]([A-Za-z0-9][A-Za-z0-9._-]*)").matchEntire(text)
                ?: throw UpdateFailure("校验文件格式无效，拒绝安装。")
            val fileName = match.groupValues[2]
            if (entries.put(fileName, match.groupValues[1].lowercase()) != null) {
                throw UpdateFailure("校验文件包含重复文件名。")
            }
        }
        return entries[name] ?: throw UpdateFailure("校验文件中没有对应的 Android 安装包。")
    }

    fun validateIdentity(candidate: ApkIdentity, installed: ApkIdentity, release: AndroidRelease) {
        if (candidate.packageName != installed.packageName) throw UpdateFailure("安装包的应用标识不一致。")
        if (candidate.versionName != release.version.toString()) throw UpdateFailure("安装包版本与发布版本不一致。")
        if (candidate.versionCode <= installed.versionCode) throw UpdateFailure("安装包版本编码不是更高版本，拒绝降级或重复安装。")
        if (installed.certificates.isEmpty() || candidate.certificates.isEmpty() ||
            installed.certificates.any { !shaPattern.matches(it) } || candidate.certificates != installed.certificates) {
            throw UpdateFailure("安装包签名证书与当前应用不一致。")
        }
    }

    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    fun strictUtf8(bytes: ByteArray): String = try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    } catch (_: Exception) { throw UpdateFailure("更新文本不是有效的 UTF-8。") }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    private fun JsonObject.string(key: String): String = stringOrNull(key)
        ?: throw UpdateFailure("正式版本信息缺少有效的 $key 字段。")
    private fun JsonObject.boolean(key: String): Boolean = (this[key] as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
        ?: throw UpdateFailure("正式版本信息缺少有效的 $key 字段。")
}

internal fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }
