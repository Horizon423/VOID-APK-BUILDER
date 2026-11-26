package com.voidkernel.apkbuilder

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * .vipnnb - a declarative, JSON-based project package. It contains a manifest, a list of
 * folders and a list of files. Nothing in it is ever executed.
 */
object Vipnnb {
    const val MAX_BYTES = 10L * 1024 * 1024
    const val MAX_FILES = 5000
    const val MAX_FILE = 5L * 1024 * 1024

    class Parsed(val name: String, val description: String, val folders: List<String>, val files: List<Pair<String, ByteArray>>)

    const val SCHEMA_DOC = """.vipnnb package format (version 1)

A .vipnnb file is a UTF-8 JSON document. It is declarative data only: VOID APK BUILDER writes the listed folders and files into the selected repository workspace and never executes anything from it.

{
  "format": "vipnnb",
  "version": 1,
  "manifest": {
    "name": "My Android App",
    "description": "optional text",
    "createdBy": "optional text"
  },
  "folders": ["app/src/main/java/com/example/app", "app/src/main/res/values"],
  "files": [
    { "path": "settings.gradle.kts", "content": "rootProject.name = \"demo\"" },
    { "path": "app/src/main/res/raw/logo.bin", "encoding": "base64", "content": "AAEC..." }
  ]
}

Rules
- Paths are relative to the repository root, use "/" separators, and must not be absolute, contain "..", empty segments or backslashes, or start with ".git".
- "encoding" is "utf8" (default) or "base64".
- Limits: 10 MB per package, 5000 entries, 5 MB per file.
- Duplicate paths are rejected. Existing files are never overwritten without confirmation.
- ZIP containers are not supported and are rejected."""

    private fun bad(msg: String): Nothing = throw IllegalArgumentException(msg)

    fun parse(raw: ByteArray): Parsed {
        if (raw.size > MAX_BYTES) bad("Package is larger than 10 MB.")
        if (raw.size >= 4 && raw[0] == 'P'.code.toByte() && raw[1] == 'K'.code.toByte()) {
            bad("This looks like a ZIP container. Only the JSON .vipnnb format is supported.")
        }
        val root = try {
            JSONObject(String(raw, Charsets.UTF_8))
        } catch (e: Exception) {
            bad("Not valid JSON: ${e.message}")
        }
        if (root.optString("format") != "vipnnb") bad("Missing or wrong \"format\" (expected \"vipnnb\").")
        if (root.optInt("version", -1) != 1) bad("Unsupported \"version\" (expected 1).")
        val man = root.optJSONObject("manifest") ?: bad("Missing \"manifest\" object.")
        val name = man.optString("name").trim()
        if (name.isEmpty()) bad("manifest.name is required.")
        val folders = ArrayList<String>()
        val fa = root.optJSONArray("folders") ?: JSONArray()
        for (i in 0 until fa.length()) {
            val p = fa.optString(i, "")
            folders.add(U.cleanPath(p) ?: bad("Unsafe folder path: \"$p\""))
        }
        val files = ArrayList<Pair<String, ByteArray>>()
        val seen = HashSet<String>()
        val arr = root.optJSONArray("files") ?: bad("Missing \"files\" array.")
        if (arr.length() + folders.size > MAX_FILES) bad("Too many entries (limit $MAX_FILES).")
        for (i in 0 until arr.length()) {
            val f = arr.optJSONObject(i) ?: bad("files[$i] is not an object.")
            val path = f.optString("path", "")
            val clean = U.cleanPath(path) ?: bad("Unsafe file path: \"$path\"")
            if (!seen.add(clean)) bad("Duplicate path: $clean")
            val content = f.optString("content", "")
            val bytes = when (val e = f.optString("encoding", "utf8")) {
                "utf8" -> content.toByteArray(Charsets.UTF_8)
                "base64" -> try {
                    Base64.decode(content, Base64.DEFAULT)
                } catch (x: Exception) {
                    bad("Invalid base64 in $clean")
                }
                else -> bad("Unknown encoding \"$e\" for $clean")
            }
            if (bytes.size > MAX_FILE) bad("$clean is larger than 5 MB.")
            files.add(clean to bytes)
        }
        return Parsed(name, man.optString("description"), folders, files)
    }
}
