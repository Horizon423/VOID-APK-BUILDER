package com.voidkernel.apkbuilder

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object U {
    fun ts(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

    fun size(n: Long): String = when {
        n < 1024 -> "$n B"
        n < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", n / 1024.0)
        else -> String.format(Locale.US, "%.1f MB", n / 1048576.0)
    }

    fun gitSha(data: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${data.size}\u0000".toByteArray(Charsets.UTF_8))
        md.update(data)
        return md.digest().joinToString("") { String.format("%02x", it) }
    }

    fun sha256(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { String.format("%02x", it) }

    /** Returns a normalized, safe relative path or null when the path is unsafe. */
    fun cleanPath(raw: String): String? {
        if (raw.isEmpty() || raw.length > 240) return null
        if (raw.contains('\\') || raw.contains('\u0000') || raw.startsWith("/")) return null
        val parts = raw.trimEnd('/').split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) return null
        if (parts[0] == ".git") return null
        return parts.joinToString("/")
    }

    fun isoToMillis(s: String?): Long = try {
        val f = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        f.timeZone = TimeZone.getTimeZone("UTC")
        f.parse(s ?: "")!!.time
    } catch (e: Exception) {
        0L
    }

    /** Removes anything that looks like a GitHub token before text is stored or shown. */
    fun scrub(s: String): String = s
        .replace(Regex("github_pat_[A-Za-z0-9_]+"), "***")
        .replace(Regex("gh[pousr]_[A-Za-z0-9]{20,}"), "***")
        .replace(Regex("(?i)(authorization:?\\s*(bearer|token)\\s+)\\S+"), "$1***")
}
