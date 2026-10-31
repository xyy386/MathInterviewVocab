package com.xingyiyao.mathinterviewvocab.updates

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal fun interface ApkInspector {
    fun inspect(file: File): ApkIdentity
}

internal data class VerifiedUpdate(val release: AndroidRelease, val file: File, val sha256: String)

/** 提交系统安装前，验证真正写入 Session 的字节，防止检查后文件被改变。 */
internal suspend fun copyVerifiedArchive(input: InputStream, output: OutputStream, bytes: Long, sha256: String) {
    val buffer = ByteArray(64 * 1024)
    var count = 0L
    val hash = MessageDigest.getInstance("SHA-256")
    while (true) {
        currentCoroutineContext().ensureActive()
        val read = input.read(buffer)
        if (read < 0) break
        count += read
        if (count > bytes) throw UpdateFailure("安装包在复制过程中发生变化。")
        output.write(buffer, 0, read)
        hash.update(buffer, 0, read)
    }
    if (count != bytes || hash.digest().toHex() != sha256) {
        throw UpdateFailure("复制到系统安装会话的内容校验失败，已取消安装。")
    }
}

/** 保持单实例状态；网络与磁盘工作均在 IO 调度器中执行。 */
internal class UpdateEngine(
    val currentVersion: String,
    private val directory: File,
    private val installed: () -> ApkIdentity,
    private val inspector: ApkInspector,
    private val transport: UpdateTransport = GitHubUpdateTransport(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val lock = Any()
    private var generation = 0L
    private var operation: Job? = null
    private var response: UpdateResponse? = null
    private var release: AndroidRelease? = null
    private var verified: VerifiedUpdate? = null
    private var pageUrl: String? = null
    private val mutableState = MutableStateFlow(UpdateUiState(currentVersion))
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()

    fun check(): Job? = start("checking", "正在检查 GitHub 正式版本…") { id ->
        discardVerified()
        synchronized(lock) { release = null; pageUrl = null }
        val metadata = withTimeout(45_000) { fetch(id, UpdatePolicy.LATEST_URL, UpdatePolicy.MAX_METADATA) }
        when (val result = UpdatePolicy.parseRelease(metadata, currentVersion)) {
            ReleaseCheck.Current -> publish(id) { UpdateUiState(currentVersion, "current", "当前已是最新正式版本。") }
            is ReleaseCheck.Unavailable -> {
                synchronized(lock) { if (generation == id) pageUrl = result.pageUrl }
                publish(id) { UpdateUiState(currentVersion, "unavailable", "新版本尚无对应的 Android 安装包，请稍后再试。", result.version.toString(), result.notes) }
            }
            is ReleaseCheck.Available -> {
                synchronized(lock) { if (generation == id) { release = result.release; pageUrl = result.release.pageUrl } }
                publish(id) { UpdateUiState(currentVersion, "available", "发现新的 Android 正式版本，可手动下载。", result.release.version.toString(), result.release.notes, totalBytes = result.release.apk.bytes, canDownload = true) }
            }
        }
    }

    fun download(): Job? {
        val selected = synchronized(lock) { release } ?: return null
        return start("downloading", "正在下载并校验 Android 安装包…") { id ->
            withTimeout(15 * 60_000L) { downloadAndVerify(id, selected) }
        }
    }

    fun cancel() {
        val handles = synchronized(lock) {
            if (mutableState.value.status !in setOf("checking", "downloading", "verifying")) return
            generation++
            val handles = operation to response
            operation = null
            response = null
            val selected = release
            mutableState.value = mutableState.value.copy(
                status = if (selected == null) "idle" else "available", message = "操作已取消，可重新尝试。",
                downloadedBytes = 0, canDownload = selected != null, canInstall = false,
            )
            handles
        }
        handles.first?.cancel()
        runCatching { handles.second?.cancel() }
    }

    fun releasePage(): String? = synchronized(lock) { pageUrl }
    fun verifiedUpdate(): VerifiedUpdate? = synchronized(lock) { verified }

    suspend fun verifyForInstall(): VerifiedUpdate {
        val candidate = verifiedUpdate() ?: throw UpdateFailure("请先下载并验证 Android 安装包。")
        if (!candidate.file.isFile || candidate.file.length() != candidate.release.apk.bytes) {
            throw UpdateFailure("已下载的安装包不存在或大小发生变化，请重新下载。")
        }
        val hash = candidate.file.inputStream().use { digest(it) }
        if (hash != candidate.sha256) throw UpdateFailure("已下载的安装包校验失败，请重新下载。")
        UpdatePolicy.validateIdentity(inspector.inspect(candidate.file), installed(), candidate.release)
        return candidate
    }

    fun updateInstallState(transform: (UpdateUiState) -> UpdateUiState) {
        synchronized(lock) { mutableState.value = transform(mutableState.value) }
    }

    private fun start(status: String, message: String, work: suspend (Long) -> Unit): Job? = synchronized(lock) {
        if (mutableState.value.status in setOf("checking", "downloading", "verifying", "installing")) return null
        generation++
        val id = generation
        mutableState.value = mutableState.value.copy(
            status = status, message = message, downloadedBytes = 0,
            canDownload = false, canInstall = false, needsInstallPermission = false, needsUserConfirmation = false,
        )
        scope.launch(start = CoroutineStart.LAZY) {
            try {
                work(id)
            } catch (cancelled: CancellationException) {
                // 显式取消由 cancel() 先更新状态；超时则保留可重试入口。
                publishIfCurrent(id) { it.copy(status = "error", message = "更新操作已超时或中断，请重试。", canDownload = release != null) }
                throw cancelled
            } catch (failure: Exception) {
                val messageText = (failure as? UpdateFailure)?.message ?: "更新失败，请检查网络或存储空间后重试。"
                publishIfCurrent(id) { it.copy(status = "error", message = messageText, canDownload = release != null, canInstall = false) }
            } finally {
                synchronized(lock) { if (generation == id) { operation = null; response = null } }
            }
        }.also { operation = it; it.start() }
    }

    private suspend fun downloadAndVerify(id: Long, selected: AndroidRelease) {
        discardVerified()
        val checksumBytes = fetch(id, selected.checksums.url, UpdatePolicy.MAX_CHECKSUMS, selected.checksums.bytes)
        if (selected.checksums.digest != null && UpdatePolicy.sha256(checksumBytes) != selected.checksums.digest) {
            throw UpdateFailure("校验文件与 GitHub 附件摘要不一致。")
        }
        val expectedHash = UpdatePolicy.checksum(checksumBytes, selected.apk.name)
        if (selected.apk.digest != null && selected.apk.digest != expectedHash) {
            throw UpdateFailure("安装包的两份 SHA-256 校验信息不一致。")
        }
        if ((!directory.isDirectory && !directory.mkdirs()) || !directory.isDirectory) throw UpdateFailure("无法创建应用私有更新目录。")
        val ownedDirectory = File(directory, UUID.randomUUID().toString())
        if (!ownedDirectory.mkdir()) throw UpdateFailure("无法创建临时下载目录。")
        val partial = File(ownedDirectory, "download.partial")
        val final = File(ownedDirectory, selected.apk.name)
        var keep = false
        try {
            val hash = MessageDigest.getInstance("SHA-256")
            var count = 0L
            var lastProgress = 0L
            withResponse(id, selected.apk.url) { remote ->
                if (remote.contentLength != null && remote.contentLength != selected.apk.bytes) throw UpdateFailure("安装包的下载大小与正式版本不一致。")
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = remote.input.read(buffer)
                        if (read < 0) break
                        count += read
                        if (count > UpdatePolicy.MAX_APK || count > selected.apk.bytes) throw UpdateFailure("安装包下载大小超出声明值。")
                        output.write(buffer, 0, read)
                        hash.update(buffer, 0, read)
                        val now = System.nanoTime()
                        if (now - lastProgress >= 100_000_000) {
                            publish(id) { it.copy(downloadedBytes = count, totalBytes = selected.apk.bytes) }
                            lastProgress = now
                        }
                    }
                    output.fd.sync()
                }
            }
            if (count != selected.apk.bytes || hash.digest().toHex() != expectedHash) throw UpdateFailure("安装包大小或 SHA-256 校验失败，已拒绝安装。")
            publish(id) { it.copy(status = "verifying", message = "正在验证包名、版本与签名证书…", downloadedBytes = count) }
            currentCoroutineContext().ensureActive()
            UpdatePolicy.validateIdentity(inspector.inspect(partial), installed(), selected)
            currentCoroutineContext().ensureActive()
            if (!partial.renameTo(final)) throw UpdateFailure("无法保存已验证的安装包。")
            synchronized(lock) {
                if (generation != id) throw CancellationException("obsolete download")
                verified = VerifiedUpdate(selected, final, expectedHash)
                mutableState.value = mutableState.value.copy(
                    status = "ready", message = "校验通过。点击安装更新后，由系统请求安装授权与确认。",
                    downloadedBytes = count, canInstall = true, canDownload = false,
                )
                keep = true
            }
        } finally {
            // 只清理本次唯一目录内的文件，不触及学习记录及其他下载。
            partial.delete()
            if (!keep) { final.delete(); ownedDirectory.delete() }
        }
    }

    private suspend fun fetch(id: Long, url: String, limit: Long, expectedBytes: Long? = null): ByteArray =
        withResponse(id, url) { remote ->
            if (remote.contentLength != null && (remote.contentLength!! > limit ||
                    expectedBytes != null && remote.contentLength != expectedBytes)) throw UpdateFailure("更新附件大小无效。")
            val output = ByteArrayOutputStream()
            var count = 0L
            val buffer = ByteArray(16 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val read = remote.input.read(buffer)
                if (read < 0) break
                count += read
                if (count > limit || expectedBytes != null && count > expectedBytes) throw UpdateFailure("更新附件超过允许大小。")
                output.write(buffer, 0, read)
            }
            if (expectedBytes != null && count != expectedBytes) throw UpdateFailure("更新附件未完整下载。")
            if (remote.contentLength != null && count != remote.contentLength) throw UpdateFailure("更新内容未完整下载。")
            output.toByteArray()
        }

    private suspend fun <T> withResponse(id: Long, url: String, block: suspend (UpdateResponse) -> T): T {
        val remote = transport.open(url)
        try {
            synchronized(lock) {
                if (generation != id) { remote.cancel(); throw CancellationException("obsolete request") }
                response = remote
            }
            return block(remote)
        } finally {
            remote.close()
            synchronized(lock) { if (response === remote) response = null }
        }
    }

    private suspend fun digest(input: InputStream): String {
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            hash.update(buffer, 0, count)
        }
        return hash.digest().toHex()
    }

    private fun discardVerified() {
        val old = synchronized(lock) { verified.also { verified = null } }
        old?.file?.let { file -> file.delete(); file.parentFile?.delete() }
    }

    private fun publish(id: Long, transform: (UpdateUiState) -> UpdateUiState) {
        synchronized(lock) {
            if (generation != id) throw CancellationException("obsolete update")
            mutableState.value = transform(mutableState.value)
        }
    }

    private fun publishIfCurrent(id: Long, transform: (UpdateUiState) -> UpdateUiState) {
        synchronized(lock) { if (generation == id) mutableState.value = transform(mutableState.value) }
    }
}
