package com.voidkernel.apkbuilder

import android.net.Uri
import org.json.JSONObject

data class Repo(
    val full: String, val name: String, val owner: String, val priv: Boolean,
    val branch: String, val url: String, val desc: String,
    val canPush: Boolean, val archived: Boolean, val updated: String
) {
    companion object {
        fun from(o: JSONObject): Repo {
            val perms = o.optJSONObject("permissions")
            return Repo(
                o.getString("full_name"), o.getString("name"),
                o.getJSONObject("owner").getString("login"), o.optBoolean("private"),
                o.optString("default_branch", "main"), o.optString("html_url"),
                o.str("description"), perms?.optBoolean("push", true) ?: true,
                o.optBoolean("archived"), o.str("pushed_at").take(10)
            )
        }
    }
}

data class ImportItem(val rel: String, val uri: Uri?, val bytes: ByteArray?, val isDir: Boolean)

data class StepInfo(val job: String, val name: String, val status: String, val conclusion: String)

data class BuildState(
    val buildId: String = "",
    val running: Boolean = false,
    val stage: String = "Idle",
    val detail: String = "",
    val runId: Long = 0,
    val runUrl: String = "",
    val runStatus: String = "",
    val conclusion: String = "",
    val steps: List<StepInfo> = emptyList(),
    val logTail: String = "",
    val startedAt: Long = 0,
    val endedAt: Long = 0,
    val polls: Int = 0
)

data class OutputItem(
    val id: String, val displayName: String, val path: String, val buildType: String,
    val signedNote: String, val runId: Long, val runUrl: String, val repo: String,
    val branch: String, val commit: String, val size: Long, val sha256: String, val createdAt: String
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("name", displayName).put("path", path)
        .put("type", buildType).put("note", signedNote).put("run", runId).put("url", runUrl)
        .put("repo", repo).put("branch", branch).put("commit", commit).put("size", size)
        .put("sha", sha256).put("at", createdAt)

    companion object {
        fun from(o: JSONObject) = OutputItem(
            o.getString("id"), o.getString("name"), o.getString("path"), o.getString("type"),
            o.getString("note"), o.getLong("run"), o.getString("url"), o.getString("repo"),
            o.getString("branch"), o.getString("commit"), o.getLong("size"),
            o.getString("sha"), o.getString("at")
        )
    }
}
