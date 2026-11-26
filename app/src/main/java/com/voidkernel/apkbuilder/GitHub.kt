package com.voidkernel.apkbuilder

import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class ApiException(msg: String) : Exception(msg)

class ApiResult(val code: Int, val body: String, val headers: Headers) {
    val ok: Boolean get() = code in 200..299
    fun obj(): JSONObject = JSONObject(body)
    fun arr(): JSONArray = JSONArray(body)
    fun message(): String = try {
        JSONObject(body).optString("message", "")
    } catch (e: Exception) {
        body.take(200)
    }
}

fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")

/** Minimal GitHub REST client. The token is read lazily and never logged. */
class GitHub(private val token: () -> String?) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .build()
    private val json = "application/json; charset=utf-8".toMediaType()

    private fun req(method: String, path: String, body: JSONObject?): Request {
        val t = token() ?: throw ApiException("Not connected: no GitHub token saved. Open Settings and connect.")
        val url = if (path.startsWith("http")) path else "https://api.github.com$path"
        val rb = when {
            body != null -> body.toString().toRequestBody(json)
            method == "GET" || method == "HEAD" -> null
            else -> "".toRequestBody(null)
        }
        return Request.Builder().url(url).method(method, rb)
            .header("Authorization", "Bearer $t")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "VOID-APK-BUILDER")
            .build()
    }

    fun call(method: String, path: String, body: JSONObject? = null): ApiResult {
        val attempts = if (method == "GET") 3 else 1
        var last: IOException? = null
        for (i in 1..attempts) {
            try {
                http.newCall(req(method, path, body)).execute().use { r ->
                    return ApiResult(r.code, r.body?.string() ?: "", r.headers)
                }
            } catch (e: IOException) {
                last = e
                Thread.sleep(800L * i)
            }
        }
        throw ApiException("Network error: ${last?.message ?: "unreachable"}")
    }

    fun get(path: String): ApiResult = call("GET", path)

    fun downloadTo(path: String, dest: File): Long {
        http.newCall(req("GET", path, null)).execute().use { r ->
            if (!r.isSuccessful) throw ApiException("Download failed (HTTP ${r.code}) for ${path.substringBefore('?')}")
            val b = r.body ?: throw ApiException("Empty download response")
            dest.outputStream().use { o -> b.byteStream().use { it.copyTo(o) } }
            return dest.length()
        }
    }

    fun explain(r: ApiResult, what: String): String {
        val m = r.message()
        val reset = r.headers["x-ratelimit-reset"]?.toLongOrNull()
            ?.let { SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(it * 1000)) } ?: "later"
        return when {
            r.code == 401 -> "$what failed: the token is invalid, expired or revoked (HTTP 401). Update it in Settings."
            (r.code == 403 || r.code == 429) && r.headers["x-ratelimit-remaining"] == "0" ->
                "$what failed: GitHub API rate limit reached. Try again after $reset."
            r.code == 403 -> "$what failed: permission denied (HTTP 403). $m Check the token permissions listed in Settings."
            r.code == 404 -> "$what failed: not found (HTTP 404). The resource may not exist, or the token has no access to it. $m"
            r.code == 409 -> "$what failed: conflict (HTTP 409). $m"
            r.code == 422 -> "$what failed: GitHub rejected the request (HTTP 422). $m"
            else -> "$what failed (HTTP ${r.code}). $m"
        }
    }
}
