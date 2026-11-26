package com.voidkernel.apkbuilder

import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class LogEntry(
    val ts: String,
    val kind: String,
    val category: String,
    val step: String,
    val message: String,
    val buildId: String
)

/** Persistent store behind the ERROR and SUCCESS consoles. */
class ConsoleStore(private val file: File) {
    val entries = mutableStateListOf<LogEntry>()

    init {
        try {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                entries.add(
                    LogEntry(
                        o.getString("ts"), o.getString("kind"), o.getString("cat"),
                        o.getString("step"), o.getString("msg"), o.getString("build")
                    )
                )
            }
        } catch (_: Exception) {
        }
    }

    @Synchronized
    fun add(kind: String, category: String, step: String, message: String, buildId: String) {
        entries.add(LogEntry(U.ts(), kind, category, step, U.scrub(message).take(4000), buildId))
        while (entries.size > 1500) entries.removeAt(0)
        save()
    }

    @Synchronized
    fun clear(kind: String, buildId: String?) {
        entries.removeAll { it.kind == kind && (buildId == null || it.buildId == buildId) }
        save()
    }

    private fun save() {
        try {
            val arr = JSONArray()
            entries.toList().forEach {
                arr.put(
                    JSONObject().put("ts", it.ts).put("kind", it.kind).put("cat", it.category)
                        .put("step", it.step).put("msg", it.message).put("build", it.buildId)
                )
            }
            file.writeText(arr.toString())
        } catch (_: Exception) {
        }
    }
}
