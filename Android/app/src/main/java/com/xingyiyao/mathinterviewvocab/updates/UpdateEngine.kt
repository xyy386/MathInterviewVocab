package com.xingyiyao.mathinterviewvocab.updates

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

internal fun interface ApkInspector {
    fun inspect(file: File): ApkIdentity
}

internal data class VerifiedUpdate(val release: AndroidRelease, val file: File, val sha256: String)

private class SlowRouteException : IOException("当前线路下载速度过慢，正在切换加速镜像。")

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

/** 保持单实例状态；网络与磁盘工作均在 IO 调度器中执行，支持多线路择优与断点续传。 */
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
    private val activeResponses = mutableSetOf<UpdateResponse>()
    private var release: AndroidRelease? = null
    private var verified: VerifiedUpdate? = null
    private var pageUrl: String? = null
    private var routeMode: String = "auto"
    private var fastestRouteId: String? = null
    private val mutableState = MutableStateFlow(UpdateUiState(currentVersion))
    val state: StateFlow<UpdateUiState> = mutableState.asStateFlow()

    fun setRouteMode(mode: String) {
        val normalized = UpdatePolicy.normalizeRouteMode(mode)
        synchronized(lock) {
            if (routeMode == normalized) return
            routeMode = normalized
            fastestRouteId = null
            mutableState.value = mutableState.value.copy(routeMode = normalized)
        }
    }

    fun check(): Job? = start("checking", "正在多线探测最新正式版本…") { id ->
        discardVerified(id)
        val mode = synchronized(lock) {
            if (generation != id) throw CancellationException("obsolete check")
            release = null
            pageUrl = null
            fastestRouteId = null
            routeMode
        }
        val candidates = UpdatePolicy.metadataCandidates(mode)
        val hedgeDelayMs = if (mode == "mirror") 400L else HEDGE_DELAY_MS
        val (winningRoute, checkResult) = withTimeout(45_000) {
            raceCandidates(
                id = id,
                candidates = candidates,
                hedgeDelayMs = hedgeDelayMs,
                isImmediateWin = { it is ReleaseCheck.Available },
                isPreferredSource = { route ->
                    route.id !in setOf(UpdatePolicy.JSDELIVR_CF.id, UpdatePolicy.JSDELIVR_FASTLY.id)
                },
            ) { route, url ->
                val bytes = fetch(id, url, UpdatePolicy.MAX_METADATA)
                UpdatePolicy.parseRelease(bytes, currentVersion).also {
                    publish(id) { state ->
                        if (state.status == "checking") {
                            state.copy(activeRouteLabel = route.label)
                        } else state
                    }
                }
            }
        }
        synchronized(lock) {
            if (generation == id) fastestRouteId = winningRoute.id
        }
        when (checkResult) {
            ReleaseCheck.Current -> publish(id) {
                UpdateUiState(
                    currentVersion = currentVersion,
                    status = "current",
                    message = "当前已是最新正式版本。",
                    routeMode = mode,
                    activeRouteLabel = winningRoute.label,
                )
            }
            is ReleaseCheck.Unavailable -> {
                synchronized(lock) { if (generation == id) pageUrl = checkResult.pageUrl }
                publish(id) {
                    UpdateUiState(
                        currentVersion = currentVersion,
                        status = "unavailable",
                        message = "新版本尚无对应的 Android 安装包，请稍后再试。",
                        availableVersion = checkResult.version.toString(),
                        notes = checkResult.notes,
                        routeMode = mode,
                        activeRouteLabel = winningRoute.label,
                    )
                }
            }
            is ReleaseCheck.Available -> {
                synchronized(lock) {
                    if (generation == id) {
                        release = checkResult.release
                        pageUrl = checkResult.release.pageUrl
                    }
                }
                publish(id) {
                    UpdateUiState(
                        currentVersion = currentVersion,
                        status = "available",
                        message = "发现新的 Android 正式版本，可手动下载。",
                        availableVersion = checkResult.release.version.toString(),
                        notes = checkResult.release.notes,
                        totalBytes = checkResult.release.apk.bytes,
                        canDownload = true,
                        routeMode = mode,
                        activeRouteLabel = winningRoute.label,
                    )
                }
            }
        }
    }

    fun download(): Job? {
        val selected = synchronized(lock) { release } ?: return null
        return start("downloading", "正在选择最快线路下载并校验安装包…") { id ->
            withTimeout(15 * 60_000L) { downloadAndVerify(id, selected) }
        }
    }

    fun cancel() {
        val handles = synchronized(lock) {
            if (mutableState.value.status !in setOf("checking", "downloading", "verifying")) return
            generation++
            val currentOperation = operation
            val currentResponses = activeResponses.toList()
            operation = null
            activeResponses.clear()
            val selected = release
            mutableState.value = mutableState.value.copy(
                status = if (selected == null) "idle" else "available",
                message = "操作已取消，可重新尝试。",
                downloadedBytes = 0,
                speedBytesPerSecond = 0,
                canDownload = selected != null,
                canInstall = false,
            )
            currentOperation to currentResponses
        }
        handles.first?.cancel()
        handles.second.forEach { runCatching { it.cancel() } }
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
            status = status,
            message = message,
            downloadedBytes = 0,
            speedBytesPerSecond = 0,
            canDownload = false,
            canInstall = false,
            needsInstallPermission = false,
            needsUserConfirmation = false,
        )
        scope.launch(start = CoroutineStart.LAZY) {
            try {
                work(id)
            } catch (cancelled: CancellationException) {
                // 显式取消由 cancel() 先更新状态；超时则保留可重试入口。
                publishIfCurrent(id) {
                    it.copy(
                        status = "error",
                        message = "更新操作已超时或中断，请重试。",
                        speedBytesPerSecond = 0,
                        canDownload = release != null,
                    )
                }
                throw cancelled
            } catch (failure: Exception) {
                val messageText = (failure as? UpdateFailure)?.message ?: "更新失败，请检查网络或切换线路后重试。"
                publishIfCurrent(id) {
                    it.copy(
                        status = "error",
                        message = messageText,
                        speedBytesPerSecond = 0,
                        canDownload = release != null,
                        canInstall = false,
                    )
                }
            } finally {
                synchronized(lock) {
                    if (generation == id) {
                        operation = null
                        activeResponses.clear()
                    }
                }
            }
        }.also { operation = it; it.start() }
    }

    private suspend fun downloadAndVerify(id: Long, selected: AndroidRelease) {
        discardVerified(id)
        val (mode, knownFastRoute) = synchronized(lock) { routeMode to fastestRouteId }
        val initialPreferredRoute = when {
            mode == "direct" -> UpdatePolicy.DIRECT.id
            knownFastRoute in setOf(UpdatePolicy.JSDELIVR_CF.id, UpdatePolicy.JSDELIVR_FASTLY.id) -> UpdatePolicy.GH_PROXY.id
            else -> knownFastRoute
        }
        val checksumCandidates = UpdatePolicy.assetCandidates(selected.checksums.url, mode, initialPreferredRoute)
        val checksumHedgeDelayMs = if (mode == "mirror") 400L else HEDGE_DELAY_MS
        val (checksumRoute, checksumBytes) = raceCandidates(
            id = id,
            candidates = checksumCandidates,
            hedgeDelayMs = checksumHedgeDelayMs,
        ) { route, url ->
            val bytes = fetch(id, url, UpdatePolicy.MAX_CHECKSUMS, selected.checksums.bytes)
            // 镜像代理若返回伪 200 错误页，立即拒绝以便竞速切换到其他有效线路；官方直连直接返回由下方统一权威校验。
            if (route.id != UpdatePolicy.DIRECT.id) {
                if (selected.checksums.digest != null && UpdatePolicy.sha256(bytes) != selected.checksums.digest) {
                    throw UpdateFailure("校验文件与 GitHub 附件摘要不一致。")
                }
                UpdatePolicy.checksum(bytes, selected.apk.name)
            }
            bytes
        }
        if (selected.checksums.digest != null && UpdatePolicy.sha256(checksumBytes) != selected.checksums.digest) {
            throw UpdateFailure("校验文件与 GitHub 附件摘要不一致。")
        }
        val expectedHash = UpdatePolicy.checksum(checksumBytes, selected.apk.name)
        if (selected.apk.digest != null && selected.apk.digest != expectedHash) {
            throw UpdateFailure("安装包的两份 SHA-256 校验信息不一致。")
        }
        synchronized(lock) {
            if (generation == id) fastestRouteId = checksumRoute.id
        }
        publish(id) {
            it.copy(
                activeRouteLabel = checksumRoute.label,
                message = "正在通过 ${checksumRoute.label} 下载安装包…",
            )
        }

        if ((!directory.isDirectory && !directory.mkdirs()) || !directory.isDirectory) throw UpdateFailure("无法创建应用私有更新目录。")
        val ownedDirectory = File(directory, UUID.randomUUID().toString())
        if (!ownedDirectory.mkdir()) throw UpdateFailure("无法创建临时下载目录。")
        val partial = File(ownedDirectory, "download.partial")
        val final = File(ownedDirectory, selected.apk.name)
        var keep = false
        try {
            val apkCandidates = UpdatePolicy.assetCandidates(selected.apk.url, mode, checksumRoute.id)
            var lastFailure: Exception? = null
            var winningApkRoute = checksumRoute

            for ((routeIndex, candidate) in apkCandidates.withIndex()) {
                currentCoroutineContext().ensureActive()
                val (route, candidateUrl) = candidate
                val hasMoreRoutes = routeIndex < apkCandidates.lastIndex
                val resumeOffset = if (partial.isFile && partial.length() in 1 until selected.apk.bytes) {
                    partial.length()
                } else {
                    0L
                }
                publish(id) {
                    it.copy(
                        activeRouteLabel = route.label,
                        message = if (resumeOffset > 0L) "正在通过 ${route.label} 断点续传安装包…" else "正在通过 ${route.label} 下载安装包…",
                        downloadedBytes = resumeOffset,
                        totalBytes = selected.apk.bytes,
                    )
                }
                try {
                    streamApkCandidate(
                        id = id,
                        route = route,
                        url = candidateUrl,
                        partial = partial,
                        totalBytes = selected.apk.bytes,
                        rangeStart = resumeOffset,
                        allowSlowFailover = hasMoreRoutes,
                    )
                    if (partial.length() == selected.apk.bytes) {
                        winningApkRoute = route
                        break
                    }
                    lastFailure = UpdateFailure("安装包未完整下载。")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: SlowRouteException) {
                    lastFailure = failure
                    if (!hasMoreRoutes) throw UpdateFailure("当前网络下载过慢，请切换加速镜像或稍后重试。")
                } catch (failure: UpdateFailure) {
                    lastFailure = failure
                    if (!hasMoreRoutes || failure.message?.contains("超出声明值") == true) {
                        throw failure
                    }
                } catch (failure: Exception) {
                    if (lastFailure == null || lastFailure is SlowRouteException) {
                        lastFailure = failure
                    }
                    if (!hasMoreRoutes) throw lastFailure
                }
            }

            if (!partial.isFile || partial.length() != selected.apk.bytes) {
                throw lastFailure ?: UpdateFailure("安装包下载未完成，请重试。")
            }

            publish(id) {
                it.copy(
                    status = "verifying",
                    message = "正在验证安装包 SHA-256、包名与签名证书…",
                    downloadedBytes = selected.apk.bytes,
                    speedBytesPerSecond = 0,
                    activeRouteLabel = winningApkRoute.label,
                )
            }
            val actualHash = partial.inputStream().use { digest(it) }
            if (actualHash != expectedHash) {
                throw UpdateFailure("安装包大小或 SHA-256 校验失败，已拒绝安装。")
            }
            currentCoroutineContext().ensureActive()
            UpdatePolicy.validateIdentity(inspector.inspect(partial), installed(), selected)
            currentCoroutineContext().ensureActive()
            if (!partial.renameTo(final)) throw UpdateFailure("无法保存已验证的安装包。")
            synchronized(lock) {
                if (generation != id) throw CancellationException("obsolete download")
                fastestRouteId = winningApkRoute.id
                verified = VerifiedUpdate(selected, final, expectedHash)
                mutableState.value = mutableState.value.copy(
                    status = "ready",
                    message = "校验通过（${winningApkRoute.label}）。点击安装更新后，由系统请求安装授权与确认。",
                    downloadedBytes = selected.apk.bytes,
                    speedBytesPerSecond = 0,
                    activeRouteLabel = winningApkRoute.label,
                    canInstall = true,
                    canDownload = false,
                )
                keep = true
            }
        } finally {
            // 只清理本次唯一目录内的文件，不触及学习记录及其他下载。
            partial.delete()
            if (!keep) {
                final.delete()
                ownedDirectory.delete()
            }
        }
    }

    private suspend fun streamApkCandidate(
        id: Long,
        route: UpdateRoute,
        url: String,
        partial: File,
        totalBytes: Long,
        rangeStart: Long,
        allowSlowFailover: Boolean,
    ) {
        withResponse(id, url, rangeStart) { remote ->
            val append = rangeStart > 0L && remote.resumedFromOffset
            val expectedStreamLength = if (append) totalBytes - rangeStart else totalBytes
            if (remote.contentLength != null && remote.contentLength != expectedStreamLength) {
                throw UpdateFailure("安装包的下载大小与正式版本不一致。")
            }
            var count = if (append) rangeStart else 0L
            val routeStartNanos = System.nanoTime()
            var lastProgressNanos = routeStartNanos
            var windowStartNanos = routeStartNanos
            var windowBytes = 0L
            var lowSpeedStartNanos = 0L
            var currentSpeedBps = 0L

            FileOutputStream(partial, append).use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val read = remote.input.read(buffer)
                    if (read < 0) break
                    count += read
                    windowBytes += read
                    if (count > UpdatePolicy.MAX_APK || count > totalBytes) {
                        throw UpdateFailure("安装包下载大小超出声明值。")
                    }
                    output.write(buffer, 0, read)
                    val now = System.nanoTime()
                    val elapsedSinceWindow = now - windowStartNanos
                    if (elapsedSinceWindow >= SPEED_WINDOW_NANOS) {
                        currentSpeedBps = (windowBytes * 1_000_000_000L) / elapsedSinceWindow.coerceAtLeast(1L)
                        windowStartNanos = now
                        windowBytes = 0L
                        if (allowSlowFailover && now - routeStartNanos >= SLOW_GRACE_NANOS) {
                            if (currentSpeedBps < SLOW_SPEED_THRESHOLD_BPS) {
                                if (lowSpeedStartNanos == 0L) {
                                    lowSpeedStartNanos = now
                                } else if (now - lowSpeedStartNanos >= SLOW_FAILOVER_DURATION_NANOS) {
                                    throw SlowRouteException()
                                }
                            } else {
                                lowSpeedStartNanos = 0L
                            }
                        }
                    }
                    if (now - lastProgressNanos >= UI_PROGRESS_INTERVAL_NANOS) {
                        lastProgressNanos = now
                        val instantSpeed = if (currentSpeedBps > 0L) currentSpeedBps else {
                            val totalElapsed = (now - routeStartNanos).coerceAtLeast(1L)
                            ((count - (if (append) rangeStart else 0L)) * 1_000_000_000L) / totalElapsed
                        }
                        publish(id) {
                            it.copy(
                                downloadedBytes = count,
                                totalBytes = totalBytes,
                                speedBytesPerSecond = instantSpeed,
                                activeRouteLabel = route.label,
                            )
                        }
                    }
                }
                output.flush()
                output.fd.sync()
            }
            if (count != totalBytes) {
                throw UpdateFailure("安装包未完整下载。")
            }
        }
    }

    private suspend fun <T> raceCandidates(
        id: Long,
        candidates: List<Pair<UpdateRoute, String>>,
        hedgeDelayMs: Long,
        isImmediateWin: (T) -> Boolean = { true },
        isPreferredSource: (UpdateRoute) -> Boolean = { true },
        worker: suspend (UpdateRoute, String) -> T,
    ): Pair<UpdateRoute, T> {
        if (candidates.isEmpty()) throw UpdateFailure("没有可用的更新线路。")
        if (candidates.size == 1) {
            val (route, url) = candidates.first()
            return route to worker(route, url)
        }
        return coroutineScope {
            data class RaceOutcome(val index: Int, val result: Result<Pair<UpdateRoute, T>>)

            val outcomes = Channel<RaceOutcome>(Channel.UNLIMITED)
            val jobs = mutableListOf<Job>()
            fun launchCandidate(index: Int) {
                val (route, url) = candidates[index]
                jobs += launch {
                    val outcome = try {
                        Result.success(route to worker(route, url))
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Throwable) {
                        Result.failure(failure)
                    }
                    outcomes.trySend(RaceOutcome(index, outcome))
                }
            }

            try {
                launchCandidate(0)
                val firstOutcome = withTimeoutOrNull(hedgeDelayMs) { outcomes.receive() }
                var fallbackSuccess: Pair<UpdateRoute, T>? = null
                var lastError: Throwable? = null
                var pending = candidates.size
                val remainingPreferred = candidates.indices.filter { isPreferredSource(candidates[it].first) }.toMutableSet()

                if (firstOutcome != null) {
                    pending -= 1
                    remainingPreferred.remove(firstOutcome.index)
                    val firstRes = firstOutcome.result
                    if (firstRes.isSuccess) {
                        val value = firstRes.getOrThrow()
                        if (isImmediateWin(value.second) || isPreferredSource(value.first) || remainingPreferred.isEmpty()) {
                            return@coroutineScope value
                        }
                        fallbackSuccess = value
                    } else {
                        lastError = firstRes.exceptionOrNull()
                    }
                }

                for (i in 1 until candidates.size) {
                    currentCoroutineContext().ensureActive()
                    launchCandidate(i)
                }

                while (pending > 0) {
                    val outcome = if (fallbackSuccess != null) {
                        withTimeoutOrNull(CDN_FRESHNESS_GRACE_MS) { outcomes.receive() }
                            ?: return@coroutineScope fallbackSuccess
                    } else {
                        outcomes.receive()
                    }
                    pending -= 1
                    remainingPreferred.remove(outcome.index)
                    val res = outcome.result
                    if (res.isSuccess) {
                        val value = res.getOrThrow()
                        if (isImmediateWin(value.second) || isPreferredSource(value.first) || remainingPreferred.isEmpty()) {
                            return@coroutineScope value
                        }
                        if (fallbackSuccess == null) fallbackSuccess = value
                    } else {
                        if (lastError == null || res.exceptionOrNull() is UpdateFailure) {
                            lastError = res.exceptionOrNull()
                        }
                    }
                }

                if (fallbackSuccess != null) return@coroutineScope fallbackSuccess
                synchronized(lock) { if (generation != id) throw CancellationException("obsolete update") }
                throw (lastError ?: UpdateFailure("更新服务暂不可用，请检查网络或切换加速线路后重试。"))
            } finally {
                jobs.forEach { it.cancel() }
                outcomes.close()
            }
        }
    }

    private suspend fun fetch(id: Long, url: String, limit: Long, expectedBytes: Long? = null): ByteArray =
        withResponse(id, url) { remote ->
            if (remote.contentLength != null && (remote.contentLength!! > limit ||
                    expectedBytes != null && remote.contentLength != expectedBytes)) throw UpdateFailure("更新附件大小无效。")
            val output = java.io.ByteArrayOutputStream()
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

    private suspend fun <T> withResponse(
        id: Long,
        url: String,
        rangeStart: Long = 0L,
        block: suspend (UpdateResponse) -> T,
    ): T {
        val remote = transport.open(url, rangeStart)
        val completionHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
            if (cause != null) runCatching { remote.cancel() }
        }
        try {
            synchronized(lock) {
                if (generation != id) {
                    runCatching { remote.cancel() }
                    throw CancellationException("obsolete request")
                }
                activeResponses.add(remote)
            }
            return block(remote)
        } finally {
            completionHandle.dispose()
            runCatching { remote.close() }
            synchronized(lock) { activeResponses.remove(remote) }
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

    private fun discardVerified(id: Long) {
        val old = synchronized(lock) {
            if (generation != id) throw CancellationException("obsolete operation")
            verified.also { verified = null }
        }
        old?.file?.let { file -> file.delete(); file.parentFile?.delete() }
    }

    private fun publish(id: Long, transform: (UpdateUiState) -> UpdateUiState) {
        synchronized(lock) {
            if (generation != id) throw CancellationException("obsolete update")
            mutableState.value = transform(mutableState.value).copy(routeMode = routeMode)
        }
    }

    private fun publishIfCurrent(id: Long, transform: (UpdateUiState) -> UpdateUiState) {
        synchronized(lock) {
            if (generation == id) {
                mutableState.value = transform(mutableState.value).copy(routeMode = routeMode)
            }
        }
    }

    private companion object {
        const val HEDGE_DELAY_MS = 1_200L
        const val CDN_FRESHNESS_GRACE_MS = 800L
        const val UI_PROGRESS_INTERVAL_NANOS = 150_000_000L
        const val SPEED_WINDOW_NANOS = 1_000_000_000L
        const val SLOW_GRACE_NANOS = 3_000_000_000L
        const val SLOW_FAILOVER_DURATION_NANOS = 8_000_000_000L
        const val SLOW_SPEED_THRESHOLD_BPS = 40L * 1024L
    }
}
