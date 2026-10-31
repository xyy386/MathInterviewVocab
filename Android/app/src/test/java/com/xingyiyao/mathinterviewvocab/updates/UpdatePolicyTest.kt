package com.xingyiyao.mathinterviewvocab.updates

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

internal object UpdateFixtures {
    const val VERSION = "1.3.0"
    const val APK_NAME = "MathInterviewVocab-Android-universal-v1.3.0.apk"
    val apk = "signed-apk-test-fixture".toByteArray()
    val checksum = "${UpdatePolicy.sha256(apk)}  $APK_NAME\n".toByteArray()
    val installed = ApkIdentity("com.xingyiyao.mathinterviewvocab", "1.2.0", 1, setOf("a".repeat(64)))
    val candidate = installed.copy(versionName = VERSION, versionCode = 2)

    fun asset(name: String, bytes: ByteArray, version: String = VERSION): JsonObject = buildJsonObject {
        put("name", name)
        put("state", "uploaded")
        put("size", bytes.size)
        put("browser_download_url", "${UpdatePolicy.REPOSITORY}/releases/download/v$version/$name")
        put("digest", "sha256:${UpdatePolicy.sha256(bytes)}")
    }

    fun metadata(
        version: String = VERSION,
        assets: List<JsonObject> = listOf(asset(APK_NAME, apk), asset(UpdatePolicy.CHECKSUM_NAME, checksum)),
        replace: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    ): ByteArray {
        val source = buildJsonObject {
            put("tag_name", "v$version")
            put("html_url", "${UpdatePolicy.REPOSITORY}/releases/tag/v$version")
            put("draft", false)
            put("prerelease", false)
            put("body", "离线语音与布局改进")
            put("assets", JsonArray(assets))
        }
        return JsonObject(source + replace).toString().toByteArray()
    }

    fun release(): AndroidRelease = (UpdatePolicy.parseRelease(metadata(), "1.2.0") as ReleaseCheck.Available).release
}

class UpdatePolicyTest {
    @Test fun versionsCompareNumerically() {
        assertTrue(StableVersion.parse("1.10.0") > StableVersion.parse("1.9.99"))
        assertTrue(StableVersion.parse("2.0.0") > StableVersion.parse("1.99.99"))
        assertEquals("0.0.0", StableVersion.parse("0.0.0").toString())
    }

    @Test fun invalidOrPrereleaseVersionsAreRejected() {
        for (value in listOf("v1.2.0", "1.2", "1.2.0.0", "01.2.0", "1.02.0", "1.2.00", "1.2.0-beta", "1.2.0+1", " 1.2.0", "１.2.0", "-1.2.0", "99999999999999999999.1.0")) {
            assertThrows(value, UpdateFailure::class.java) { StableVersion.parse(value) }
        }
    }

    @Test fun officialStableReleaseProvidesOnlyAndroidAsset() {
        val result = UpdatePolicy.parseRelease(UpdateFixtures.metadata(), "1.2.0") as ReleaseCheck.Available
        assertEquals(UpdateFixtures.APK_NAME, result.release.apk.name)
        assertEquals(UpdateFixtures.apk.size.toLong(), result.release.apk.bytes)
        assertEquals(UpdatePolicy.CHECKSUM_NAME, result.release.checksums.name)
    }

    @Test fun olderAndEqualVersionsNeedNoAndroidAssets() {
        for (version in listOf("1.1.0", "1.2.0")) {
            assertEquals(ReleaseCheck.Current, UpdatePolicy.parseRelease(UpdateFixtures.metadata(version, emptyList()), "1.2.0"))
        }
    }

    @Test fun newerDesktopOnlyReleaseIsUnavailable() {
        val desktop = UpdateFixtures.asset("MathInterviewVocab-Windows-x64-v1.3.0.zip", byteArrayOf(1))
        assertTrue(UpdatePolicy.parseRelease(UpdateFixtures.metadata(assets = listOf(desktop)), "1.2.0") is ReleaseCheck.Unavailable)
    }

    @Test fun dedicatedChecksumIsRequired() {
        assertTrue(UpdatePolicy.parseRelease(UpdateFixtures.metadata(assets = listOf(UpdateFixtures.asset(UpdateFixtures.APK_NAME, UpdateFixtures.apk))), "1.2.0") is ReleaseCheck.Unavailable)
    }

    @Test fun draftsAndPrereleasesAreRejected() {
        for (field in listOf("draft", "prerelease")) {
            assertThrows(UpdateFailure::class.java) { UpdatePolicy.parseRelease(UpdateFixtures.metadata(replace = mapOf(field to JsonPrimitive(true))), "1.2.0") }
        }
    }

    @Test fun foreignPageAndNonCanonicalTagAreRejected() {
        for ((field, value) in listOf("html_url" to "https://github.com/other/repo/releases/tag/v1.3.0", "tag_name" to "1.3.0", "tag_name" to "v1.3.0-beta")) {
            assertThrows(UpdateFailure::class.java) { UpdatePolicy.parseRelease(UpdateFixtures.metadata(replace = mapOf(field to JsonPrimitive(value))), "1.2.0") }
        }
    }

    @Test fun duplicateAndroidAssetsAreRejected() {
        val asset = UpdateFixtures.asset(UpdateFixtures.APK_NAME, UpdateFixtures.apk)
        assertThrows(UpdateFailure::class.java) { UpdatePolicy.parseRelease(UpdateFixtures.metadata(assets = listOf(asset, asset)), "1.2.0") }
    }

    @Test fun malformedAssetMetadataIsRejected() {
        val original = UpdateFixtures.asset(UpdateFixtures.APK_NAME, UpdateFixtures.apk)
        val invalidValues = listOf(
            "state" to JsonPrimitive("new"), "size" to JsonPrimitive(0),
            "size" to JsonPrimitive(UpdatePolicy.MAX_APK + 1), "size" to JsonPrimitive("22"),
            "digest" to JsonPrimitive("md5:${"a".repeat(32)}"),
            "digest" to JsonPrimitive("sha256:${"A".repeat(64)}"),
            "browser_download_url" to JsonPrimitive("${UpdatePolicy.REPOSITORY}/releases/download/v1.3.0/${UpdateFixtures.APK_NAME}?x=1"),
            "browser_download_url" to JsonPrimitive("${UpdatePolicy.REPOSITORY}/releases/download/v1.2.0/${UpdateFixtures.APK_NAME}"),
        )
        for ((key, value) in invalidValues) {
            val changed = JsonObject(original + (key to value))
            assertThrows("$key=$value", UpdateFailure::class.java) { UpdatePolicy.parseRelease(UpdateFixtures.metadata(assets = listOf(changed)), "1.2.0") }
        }
    }

    @Test fun invalidMetadataAndOversizeAreRejected() {
        for (bytes in listOf("{}".toByteArray(), "[]".toByteArray(), byteArrayOf(0xff.toByte()), ByteArray(UpdatePolicy.MAX_METADATA.toInt() + 1))) {
            assertThrows(UpdateFailure::class.java) { UpdatePolicy.parseRelease(bytes, "1.2.0") }
        }
    }

    @Test fun onlyHttpsExactHostsAreAllowed() {
        for (host in listOf("github.com", "api.github.com", "release-assets.githubusercontent.com", "objects.githubusercontent.com")) {
            assertEquals(host, UpdatePolicy.checkedUrl("https://$host/path?signature=opaque").host)
        }
        for (value in listOf("http://github.com/a", "https://github.com.evil.test/a", "https://evilgithub.com/a", "https://user@github.com/a", "https://github.com:444/a", "https://github.com/a#b", "file:///tmp/a", "https://GITHUB.com/a")) {
            assertThrows(value, UpdateFailure::class.java) { UpdatePolicy.checkedUrl(value) }
        }
    }

    @Test fun redirectsAreRecheckedAndBounded() {
        assertEquals("https://github.com/next", UpdatePolicy.redirectUrl("https://github.com/start", "/next", 0))
        assertEquals("https://objects.githubusercontent.com/file", UpdatePolicy.redirectUrl("https://github.com/start", "https://objects.githubusercontent.com/file", 4))
        assertThrows(UpdateFailure::class.java) { UpdatePolicy.redirectUrl("https://github.com/a", "//evil.test/a", 0) }
        assertThrows(UpdateFailure::class.java) { UpdatePolicy.redirectUrl("https://github.com/a", "/a", 5) }
    }

    @Test fun checksumAcceptsTextBinaryAndCrLf() {
        val hash = "a".repeat(64)
        for (marker in listOf("  ", " *")) {
            assertEquals(hash, UpdatePolicy.checksum("\r\n${hash.uppercase()}$marker${UpdateFixtures.APK_NAME}\r\n".toByteArray(), UpdateFixtures.APK_NAME))
        }
    }

    @Test fun checksumRejectsPathsDuplicatesAndInvalidEncoding() {
        val hash = "a".repeat(64)
        val invalid = listOf(
            "$hash  ../${UpdateFixtures.APK_NAME}\n", "$hash  /${UpdateFixtures.APK_NAME}\n",
            "$hash  folder\\${UpdateFixtures.APK_NAME}\n", "$hash  ${UpdateFixtures.APK_NAME}\n$hash  ${UpdateFixtures.APK_NAME}\n",
            "$hash ${UpdateFixtures.APK_NAME}\n", "$hash  other.apk\n", "z${hash.drop(1)}  ${UpdateFixtures.APK_NAME}\n",
        )
        for (value in invalid) assertThrows(UpdateFailure::class.java) { UpdatePolicy.checksum(value.toByteArray(), UpdateFixtures.APK_NAME) }
        assertThrows(UpdateFailure::class.java) { UpdatePolicy.checksum(byteArrayOf(0xff.toByte()), UpdateFixtures.APK_NAME) }
        assertThrows(UpdateFailure::class.java) { UpdatePolicy.checksum(ByteArray(UpdatePolicy.MAX_CHECKSUMS.toInt() + 1), UpdateFixtures.APK_NAME) }
    }

    @Test fun validIdentityHasSameCurrentSignersAndHigherCode() {
        UpdatePolicy.validateIdentity(UpdateFixtures.candidate, UpdateFixtures.installed, UpdateFixtures.release())
    }

    @Test fun identityRejectsPackageVersionCodeAndCertificateMismatch() {
        val candidate = UpdateFixtures.candidate
        for (identity in listOf(candidate.copy(packageName = "evil.test"), candidate.copy(versionName = "1.3.1"), candidate.copy(versionCode = 1), candidate.copy(versionCode = 0), candidate.copy(certificates = emptySet()), candidate.copy(certificates = setOf("b".repeat(64))), candidate.copy(certificates = candidate.certificates + "b".repeat(64)))) {
            assertThrows(UpdateFailure::class.java) { UpdatePolicy.validateIdentity(identity, UpdateFixtures.installed, UpdateFixtures.release()) }
        }
        assertThrows(UpdateFailure::class.java) { UpdatePolicy.validateIdentity(candidate, UpdateFixtures.installed.copy(certificates = emptySet()), UpdateFixtures.release()) }
    }

    @Test fun userStateHasNoInitialNetworkOrInstallAction() {
        val state = UpdateUiState("1.2.0")
        assertEquals("idle", state.status)
        assertFalse(state.canDownload || state.canInstall || state.needsUserConfirmation || state.needsInstallPermission)
    }
}

/** 拦截器模拟响应，不访问网络或依赖本机代理。 */
class UpdateTransportTest {
    private fun transport(reply: (okhttp3.Request) -> Response): GitHubUpdateTransport =
        GitHubUpdateTransport(OkHttpClient.Builder().addInterceptor { chain -> reply(chain.request()) })

    private fun response(request: okhttp3.Request, code: Int = 200, location: String? = null, encoding: String? = null): Response {
        return Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .body("payload".toResponseBody()).apply {
                if (location != null) header("Location", location)
                if (encoding != null) header("Content-Encoding", encoding)
            }.build()
    }

    @Test fun outgoingRequestsHaveNoCredentialsAndUseIdentityEncoding() = runBlocking {
        val remote = transport { request ->
            assertEquals("GET", request.method)
            assertNull(request.header("Cookie"))
            assertNull(request.header("Authorization"))
            assertNull(request.header("Proxy-Authorization"))
            assertEquals("identity", request.header("Accept-Encoding"))
            response(request)
        }
        remote.open(UpdatePolicy.LATEST_URL).use { assertEquals("payload", String(it.input.readBytes())) }
    }

    @Test fun redirectedRequestsAreExplicitAndEveryHostIsChecked() = runBlocking {
        val seen = mutableListOf<String>()
        val remote = transport { request ->
            seen.add(request.url.host)
            if (request.url.host == "github.com") response(request, 302, "https://objects.githubusercontent.com/file") else response(request)
        }
        remote.open("https://github.com/file").close()
        assertEquals(listOf("github.com", "objects.githubusercontent.com"), seen)
    }

    @Test fun untrustedRedirectIsRejectedBeforeRequest() = runBlocking {
        var count = 0
        val remote = transport { request -> count++; response(request, 302, "https://attacker.test/file") }
        var failed = false
        try { remote.open("https://github.com/file") } catch (_: UpdateFailure) { failed = true }
        assertTrue(failed)
        assertEquals(1, count)
    }

    @Test fun redirectLoopIsBounded() = runBlocking {
        var count = 0
        val remote = transport { request -> count++; response(request, 302, "/loop") }
        var failed = false
        try { remote.open("https://github.com/loop") } catch (_: UpdateFailure) { failed = true }
        assertTrue(failed)
        assertEquals(UpdatePolicy.MAX_REDIRECTS + 1, count)
    }

    @Test fun compressedAndHttpFailureResponsesAreRejected() = runBlocking {
        for (code in listOf(403, 404, 429, 500)) {
            val remote = transport { response(it, code) }
            var failed = false
            try { remote.open(UpdatePolicy.LATEST_URL) } catch (_: UpdateFailure) { failed = true }
            assertTrue(failed)
        }
        var failed = false
        try { transport { response(it, encoding = "gzip") }.open(UpdatePolicy.LATEST_URL) } catch (_: UpdateFailure) { failed = true }
        assertTrue(failed)
    }
}
