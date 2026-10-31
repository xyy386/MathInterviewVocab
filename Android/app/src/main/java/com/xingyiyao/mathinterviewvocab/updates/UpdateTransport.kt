package com.xingyiyao.mathinterviewvocab.updates

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.Authenticator
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

internal interface UpdateResponse : Closeable {
    val input: InputStream
    val contentLength: Long?
    fun cancel()
}

internal fun interface UpdateTransport {
    suspend fun open(url: String): UpdateResponse
}

/** 禁止自动重定向，每一跳都重新验证 HTTPS 主机。 */
internal class GitHubUpdateTransport(builder: OkHttpClient.Builder = OkHttpClient.Builder()) : UpdateTransport {
    private val client = builder
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .cookieJar(CookieJar.NO_COOKIES)
        .authenticator(Authenticator.NONE)
        .proxyAuthenticator(Authenticator.NONE)
        .cache(null)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.MINUTES)
        .build()

    override suspend fun open(url: String): UpdateResponse {
        var current = url
        var redirects = 0
        while (true) {
            UpdatePolicy.checkedUrl(current)
            val request = Request.Builder().url(current)
                .header("Accept", "application/vnd.github+json, application/octet-stream")
                .header("Accept-Encoding", "identity")
                .header("User-Agent", "MathInterviewVocab-Android-Updater")
                .header("Cache-Control", "no-cache")
                .build()
            val call = client.newCall(request)
            val response = execute(call)
            if (response.code in setOf(301, 302, 303, 307, 308)) {
                response.use {
                    val location = it.header("Location") ?: throw UpdateFailure("更新重定向缺少目标地址。")
                    current = UpdatePolicy.redirectUrl(current, location, redirects++)
                }
                continue
            }
            if (response.code != 200) {
                val code = response.code
                response.close()
                throw UpdateFailure(when (code) {
                    403, 429 -> "GitHub 暂时限制请求，请稍后重试。"
                    404 -> "尚无可获取的正式版本或附件。"
                    else -> "更新服务返回错误（HTTP $code），请稍后重试。"
                })
            }
            if (response.header("Content-Encoding")?.lowercase() !in setOf(null, "identity")) {
                response.close()
                throw UpdateFailure("下载内容编码异常，拒绝使用该附件。")
            }
            val body = response.body
            if (body == null) {
                response.close()
                throw UpdateFailure("更新服务返回空内容。")
            }
            return object : UpdateResponse {
                override val input: InputStream = body.byteStream()
                override val contentLength: Long? = body.contentLength().takeIf { it >= 0 }
                override fun close() = response.close()
                override fun cancel() { call.cancel(); response.close() }
            }
        }
    }

    private suspend fun execute(call: Call): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response) { _, value, _ -> value.close() }
            }
        })
    }
}
