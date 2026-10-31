package com.xingyiyao.mathinterviewvocab.updates

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateEngineTest {
    @get:Rule val temporary = TemporaryFolder()

    private class Response(
        val bytes: ByteArray,
        override val contentLength: Long? = bytes.size.toLong(),
        override val input: InputStream = ByteArrayInputStream(bytes),
    ) : UpdateResponse {
        var closed = false
        var cancelled = false
        override fun close() { closed = true; input.close() }
        override fun cancel() { cancelled = true; close() }
    }

    private class Transport : UpdateTransport {
        val routes = mutableMapOf<String, () -> UpdateResponse>()
        val requests = mutableListOf<String>()
        val responses = mutableListOf<UpdateResponse>()
        override suspend fun open(url: String): UpdateResponse {
            synchronized(this) { requests.add(url) }
            return (routes[url]?.invoke() ?: throw IOException("fixture missing")).also { synchronized(this) { responses.add(it) } }
        }
    }

    private fun transport(
        apk: ByteArray = UpdateFixtures.apk,
        checksum: ByteArray = UpdateFixtures.checksum,
        metadata: ByteArray = UpdateFixtures.metadata(),
    ): Transport = Transport().apply {
        routes[UpdatePolicy.LATEST_URL] = { Response(metadata) }
        routes[UpdateFixtures.release().checksums.url] = { Response(checksum) }
        routes[UpdateFixtures.release().apk.url] = { Response(apk) }
    }

    private fun engine(
        remote: Transport,
        identity: ApkIdentity = UpdateFixtures.candidate,
        directory: File = File(temporary.root, "updates"),
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    ) = UpdateEngine("1.2.0", directory, { UpdateFixtures.installed }, ApkInspector { identity }, remote, scope)

    @Test fun initializationNeverRequestsNetwork() {
        val remote = transport()
        assertEquals("idle", engine(remote).state.value.status)
        assertTrue(remote.requests.isEmpty())
        assertFalse(File(temporary.root, "updates").exists())
    }

    @Test fun checkOnlyReadsReleaseMetadata() = runBlocking {
        val remote = transport()
        val engine = engine(remote)
        engine.check()!!.join()
        assertEquals(listOf(UpdatePolicy.LATEST_URL), remote.requests)
        assertEquals("available", engine.state.value.status)
        assertTrue(engine.state.value.canDownload)
        assertFalse(engine.state.value.canInstall)
        assertFalse(File(temporary.root, "updates").exists())
        assertTrue(remote.responses.all { (it as Response).closed })
    }

    @Test fun validArchiveIsReadyOnlyAfterEveryValidation() = runBlocking {
        val remote = transport()
        val engine = engine(remote)
        engine.check()!!.join()
        engine.download()!!.join()
        assertEquals("ready", engine.state.value.status)
        assertTrue(engine.state.value.canInstall)
        val update = engine.verifiedUpdate()!!
        assertTrue(update.file.readBytes().contentEquals(UpdateFixtures.apk))
        assertEquals(UpdateFixtures.APK_NAME, update.file.name)
        assertEquals(3, remote.requests.size)
        assertEquals(update, engine.verifyForInstall())
        assertTrue(remote.responses.all { (it as Response).closed })
        assertFalse(File(update.file.parentFile, "download.partial").exists())
    }

    @Test fun missingPlatformReleaseNeverDownloadsDesktopFile() = runBlocking {
        val remote = transport(metadata = UpdateFixtures.metadata(assets = emptyList()))
        val engine = engine(remote)
        engine.check()!!.join()
        assertEquals("unavailable", engine.state.value.status)
        assertNull(engine.download())
        assertEquals(1, remote.requests.size)
    }

    @Test fun alreadyCurrentReleaseNeedsNoAndroidAssets() = runBlocking {
        val remote = transport(metadata = UpdateFixtures.metadata("1.2.0", emptyList()))
        val engine = engine(remote)
        engine.check()!!.join()
        assertEquals("current", engine.state.value.status)
        assertNull(engine.download())
        assertEquals(1, remote.requests.size)
    }

    @Test fun corruptApkIsRemovedAndRetryCanSucceed() = runBlocking {
        val remote = transport(apk = ByteArray(UpdateFixtures.apk.size) { 4 })
        val engine = engine(remote)
        engine.check()!!.join()
        engine.download()!!.join()
        assertEquals("error", engine.state.value.status)
        assertNull(engine.verifiedUpdate())
        assertTrue(engine.state.value.canDownload)
        assertTrue(File(temporary.root, "updates").walkTopDown().none { it.isFile })
        remote.routes[UpdateFixtures.release().apk.url] = { Response(UpdateFixtures.apk) }
        engine.download()!!.join()
        assertEquals("ready", engine.state.value.status)
    }

    @Test fun wrongApkLengthCannotBecomeReady() = runBlocking {
        for (bytes in listOf(UpdateFixtures.apk + byteArrayOf(1), UpdateFixtures.apk.dropLast(1).toByteArray())) {
            val remote = transport(apk = bytes)
            val engine = engine(remote)
            engine.check()!!.join()
            engine.download()!!.join()
            assertEquals("error", engine.state.value.status)
            assertNull(engine.verifiedUpdate())
            assertFalse(engine.state.value.canInstall)
        }
    }

    @Test fun streamingUnknownLengthStillEnforcesSize() = runBlocking {
        val remote = transport()
        remote.routes[UpdateFixtures.release().apk.url] = { Response(UpdateFixtures.apk + byteArrayOf(1), contentLength = null) }
        val engine = engine(remote)
        engine.check()!!.join()
        engine.download()!!.join()
        assertEquals("error", engine.state.value.status)
        assertNull(engine.verifiedUpdate())
        assertTrue(File(temporary.root, "updates").walkTopDown().none { it.isFile })
    }

    @Test fun truncatedUnknownLengthApkIsRejected() = runBlocking {
        val remote = transport()
        remote.routes[UpdateFixtures.release().apk.url] = { Response(byteArrayOf(1), contentLength = null) }
        val engine = engine(remote)
        engine.check()!!.join()
        engine.download()!!.join()
        assertEquals("error", engine.state.value.status)
        assertNull(engine.verifiedUpdate())
    }

    @Test fun checksumDigestMismatchNeverFetchesApk() = runBlocking {
        val remote = transport(checksum = ByteArray(UpdateFixtures.checksum.size) { 32 })
        val engine = engine(remote)
        engine.check()!!.join()
        engine.download()!!.join()
        assertEquals("error", engine.state.value.status)
        assertEquals(2, remote.requests.size)
        assertNull(engine.verifiedUpdate())
    }

    @Test fun contradictoryApiAndChecksumHashesAreRejected() = runBlocking {
        val checksum = "${"b".repeat(64)}  ${UpdateFixtures.APK_NAME}\n".toByteArray()
        val metadata = UpdateFixtures.metadata(assets = listOf(
            UpdateFixtures.asset(UpdateFixtures.APK_NAME, UpdateFixtures.apk),
            UpdateFixtures.asset(UpdatePolicy.CHECKSUM_NAME, checksum),
        ))
        val remote = transport(checksum = checksum, metadata = metadata)
        val engine = engine(remote)
        engine.check()!!.join()
        engine.download()!!.join()
        assertEquals("error", engine.state.value.status)
        assertEquals(2, remote.requests.size)
    }

    @Test fun invalidPackageOrSignerDoesNotPublishArchive() = runBlocking {
        for (identity in listOf(UpdateFixtures.candidate.copy(packageName = "bad.package"), UpdateFixtures.candidate.copy(certificates = setOf("b".repeat(64))), UpdateFixtures.candidate.copy(versionCode = 1), UpdateFixtures.candidate.copy(versionName = "1.3.1"))) {
            val engine = engine(transport(), identity)
            engine.check()!!.join()
            engine.download()!!.join()
            assertEquals("error", engine.state.value.status)
            assertNull(engine.verifiedUpdate())
            assertTrue(File(temporary.root, "updates").walkTopDown().none { it.isFile })
        }
    }

    @Test fun parserExceptionFailsClosedAndDeletesPartial() = runBlocking {
        val remote = transport()
        val engine = UpdateEngine("1.2.0", File(temporary.root, "updates"), { UpdateFixtures.installed }, ApkInspector { throw IOException("parser failed") }, remote)
        engine.check()!!.join()
        engine.download()!!.join()
        assertEquals("error", engine.state.value.status)
        assertNull(engine.verifiedUpdate())
        assertTrue(File(temporary.root, "updates").walkTopDown().none { it.isFile })
    }

    @Test fun alteredReadyFileFailsReverification() = runBlocking {
        val engine = engine(transport())
        engine.check()!!.join()
        engine.download()!!.join()
        val file = engine.verifiedUpdate()!!.file
        file.writeBytes(ByteArray(UpdateFixtures.apk.size) { 3 })
        var rejected = false
        try { engine.verifyForInstall() } catch (_: UpdateFailure) { rejected = true }
        assertTrue(rejected)
    }

    @Test fun filesystemFailureNeverClaimsReady() = runBlocking {
        val blocked = File(temporary.root, "not-a-directory").also { it.writeText("keep") }
        val engine = engine(transport(), directory = blocked)
        engine.check()!!.join()
        engine.download()!!.join()
        assertEquals("error", engine.state.value.status)
        assertEquals("keep", blocked.readText())
        assertNull(engine.verifiedUpdate())
    }

    @Test fun cancelClosesOwnResponseAndAllowsFreshCheck() = runBlocking {
        val entered = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val slow = object : InputStream() {
            override fun read(): Int {
                entered.countDown()
                if (!cancelled.await(5, TimeUnit.SECONDS)) throw IOException("fixture timeout")
                throw IOException("cancelled")
            }
            override fun close() { cancelled.countDown() }
        }
        val response = Response(byteArrayOf(), contentLength = null, input = slow)
        val remote = transport().apply { routes[UpdatePolicy.LATEST_URL] = { response } }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val engine = engine(remote, scope = scope)
            val job = engine.check()!!
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertNull(engine.check())
            engine.cancel()
            withTimeout(5_000) { job.join() }
            assertTrue(response.cancelled)
            assertEquals("idle", engine.state.value.status)
            remote.routes[UpdatePolicy.LATEST_URL] = { Response(UpdateFixtures.metadata()) }
            engine.check()!!.join()
            assertEquals("available", engine.state.value.status)
        } finally { scope.cancel() }
    }

    @Test fun cancelDuringDownloadRemovesOnlyOwnPartial() = runBlocking {
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val slow = object : InputStream() {
            override fun read(): Int {
                entered.countDown()
                released.await(5, TimeUnit.SECONDS)
                throw IOException("cancelled")
            }
            override fun close() { released.countDown() }
        }
        val remote = transport()
        remote.routes[UpdateFixtures.release().apk.url] = { Response(byteArrayOf(), contentLength = null, input = slow) }
        val root = File(temporary.root, "updates").also { it.mkdir() }
        val preserved = File(root, "unrelated.txt").also { it.writeText("preserve") }
        val engine = engine(remote)
        engine.check()!!.join()
        val job = engine.download()!!
        assertTrue(entered.await(5, TimeUnit.SECONDS))
        engine.cancel()
        withTimeout(5_000) { job.join() }
        assertEquals("available", engine.state.value.status)
        assertNull(engine.verifiedUpdate())
        assertEquals(listOf(preserved), root.walkTopDown().filter { it.isFile }.toList())
    }

    @Test fun backgroundCancellationDoesNotChangeInstallationState() {
        val engine = engine(transport())
        engine.updateInstallState { it.copy(status = "installing", needsUserConfirmation = true) }
        engine.cancel()
        assertEquals("installing", engine.state.value.status)
        assertTrue(engine.state.value.needsUserConfirmation)
    }

    @Test fun downloadRequiresExplicitPriorCheck() {
        val remote = transport()
        assertNull(engine(remote).download())
        assertTrue(remote.requests.isEmpty())
    }

    @Test fun nextCheckDiscardsOnlyOwnedVerifiedArchive() = runBlocking {
        val remote = transport()
        val engine = engine(remote)
        engine.check()!!.join()
        engine.download()!!.join()
        val previous = engine.verifiedUpdate()!!.file
        assertNotNull(engine.releasePage())
        engine.check()!!.join()
        assertFalse(previous.exists())
        assertNull(engine.verifiedUpdate())
        assertEquals("available", engine.state.value.status)
    }

    @Test fun sessionCopyValidatesActualWrittenBytes() = runBlocking {
        val output = ByteArrayOutputStream()
        copyVerifiedArchive(ByteArrayInputStream(UpdateFixtures.apk), output, UpdateFixtures.apk.size.toLong(), UpdatePolicy.sha256(UpdateFixtures.apk))
        assertTrue(output.toByteArray().contentEquals(UpdateFixtures.apk))
    }

    @Test fun sessionCopyRejectsSameLengthChangedFileAndWrongSize() = runBlocking {
        for (bytes in listOf(ByteArray(UpdateFixtures.apk.size) { 2 }, UpdateFixtures.apk + byteArrayOf(1), byteArrayOf(1))) {
            var rejected = false
            try {
                copyVerifiedArchive(ByteArrayInputStream(bytes), ByteArrayOutputStream(), UpdateFixtures.apk.size.toLong(), UpdatePolicy.sha256(UpdateFixtures.apk))
            } catch (_: UpdateFailure) { rejected = true }
            assertTrue(rejected)
        }
    }
}
