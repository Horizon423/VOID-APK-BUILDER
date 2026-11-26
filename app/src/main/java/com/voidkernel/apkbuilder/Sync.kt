package com.voidkernel.apkbuilder

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.URLEncoder
import java.util.zip.ZipInputStream

fun enc(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
fun refPath(b: String): String = b.split('/').joinToString("/") { enc(it) }

class PullResult(val added: Int, val updated: Int, val removed: Int, val kept: List<String>, val empty: Boolean)
class PushResult(val committed: Boolean, val sha: String?, val count: Int, val conflicts: List<String>)

/** Pull / commit logic built on the GitHub REST (git data) API. */
class Sync(private val gh: GitHub, private val ws: Workspace, private val full: String, private val cache: File) {
    private val maxFile = 50L * 1024 * 1024

    fun remoteTree(commitSha: String): Map<String, String> {
        val c = gh.get("/repos/$full/git/commits/$commitSha")
        if (!c.ok) throw ApiException(gh.explain(c, "Reading commit"))
        val treeSha = c.obj().getJSONObject("tree").getString("sha")
        val t = gh.get("/repos/$full/git/trees/$treeSha?recursive=1")
        if (!t.ok) throw ApiException(gh.explain(t, "Reading repository tree"))
        val o = t.obj()
        if (o.optBoolean("truncated")) throw ApiException("The repository tree is too large for the GitHub tree API (truncated).")
        val a = o.getJSONArray("tree")
        val m = LinkedHashMap<String, String>()
        for (i in 0 until a.length()) {
            val e = a.getJSONObject(i)
            if (e.getString("type") == "blob") m[e.getString("path")] = e.getString("sha")
        }
        return m
    }

    private fun readCapped(i: InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(65536)
        var total = 0L
        var over = false
        while (true) {
            val n = i.read(buf)
            if (n < 0) break
            total += n
            if (total > maxFile) over = true
            if (!over) out.write(buf, 0, n)
        }
        return if (over) null else out.toByteArray()
    }

    /** Merges the remote branch into the working copy without discarding local edits. */
    fun pull(branch: String): PullResult {
        val ref = gh.get("/repos/$full/git/ref/heads/${refPath(branch)}")
        if (ref.code == 409) {
            ws.branch = branch; ws.head = null; ws.save()
            return PullResult(0, 0, 0, emptyList(), true)
        }
        if (!ref.ok) throw ApiException(gh.explain(ref, "Reading branch '$branch'"))
        val head = ref.obj().getJSONObject("object").getString("sha")
        val remote = remoteTree(head)
        val zip = File(cache, "void-pull.zip")
        gh.downloadTo("/repos/$full/zipball/$head", zip)
        val nb = HashMap<String, String>()
        val kept = ArrayList<String>()
        val seen = HashSet<String>()
        var added = 0
        var updated = 0
        var removed = 0
        try {
            ZipInputStream(zip.inputStream().buffered()).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    if (!e.isDirectory) {
                        val p = U.cleanPath(e.name.substringAfter('/', ""))
                        val rsha = if (p != null) remote[p] else null
                        if (p != null && rsha != null) {
                            val bytes = readCapped(zin)
                            if (bytes != null) {
                                seen.add(p)
                                val f = ws.resolve(p)
                                val bsha = ws.base[p]
                                if (!f.exists()) {
                                    if (bsha == null) {
                                        ws.write(p, bytes); nb[p] = rsha; added++
                                    } else {
                                        nb[p] = bsha
                                        if (rsha != bsha) kept.add(p)
                                    }
                                } else {
                                    val lsha = U.gitSha(f.readBytes())
                                    when {
                                        lsha == rsha -> nb[p] = rsha
                                        bsha != null && lsha == bsha -> {
                                            ws.write(p, bytes); nb[p] = rsha; updated++
                                        }
                                        else -> {
                                            if (bsha != null) nb[p] = bsha
                                            if (bsha == null || rsha != bsha) kept.add(p)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    e = zin.nextEntry
                }
            }
        } finally {
            zip.delete()
        }
        for (p in remote.keys) if (p !in seen) ws.base[p]?.let { nb[p] = it }
        for (p in ws.base.keys.toList()) {
            if (p in remote) continue
            val f = ws.resolve(p)
            if (f.exists() && U.gitSha(f.readBytes()) == ws.base[p]) {
                f.delete(); removed++
            }
        }
        ws.base.clear()
        ws.base.putAll(nb)
        ws.head = head
        ws.branch = branch
        ws.save()
        return PullResult(added, updated, removed, kept, false)
    }

    /** Commits all pending local changes in a single commit. Returns conflicts instead of overwriting. */
    fun push(branch: String, message: String, force: Boolean): PushResult {
        val ch = ws.pending()
        if (ch.empty) return PushResult(false, null, 0, emptyList())
        val ref = gh.get("/repos/$full/git/ref/heads/${refPath(branch)}")
        if (ref.code == 409) return bootstrap(branch, message, ch)
        if (!ref.ok) throw ApiException(gh.explain(ref, "Reading branch '$branch'"))
        val head = ref.obj().getJSONObject("object").getString("sha")

        val remote = remoteTree(head)
        if (head != ws.head && !force) {
            val conflicts = (ch.upserts + ch.deletes).filter { p ->
                val r = remote[p]
                if (r == ws.base[p]) false
                else if (p in ch.deletes) r != null
                else r != U.gitSha(ws.resolve(p).readBytes())
            }
            if (conflicts.isNotEmpty()) return PushResult(false, null, 0, conflicts)
        }
        // Only send what really differs from GitHub; deleting a path GitHub does not have causes HTTP 422.
        val ups = ArrayList<String>()
        for (p in ch.upserts) {
            val sha = U.gitSha(ws.resolve(p).readBytes())
            if (remote[p] == sha) ws.base[p] = sha else ups.add(p)
        }
        val dels = ch.deletes.filter { it in remote }
        for (p in ch.deletes) if (p !in remote) ws.base.remove(p)
        if (ups.isEmpty() && dels.isEmpty()) {
            ws.head = head; ws.branch = branch; ws.save()
            return PushResult(false, null, 0, emptyList())
        }

        val headCommit = gh.get("/repos/$full/git/commits/$head")
        if (!headCommit.ok) throw ApiException(gh.explain(headCommit, "Reading head commit"))
        val baseTree = headCommit.obj().getJSONObject("tree").getString("sha")

        val items = JSONArray()
        val newShas = HashMap<String, String>()
        for (p in ups) {
            val bytes = ws.resolve(p).readBytes()
            if (bytes.size > maxFile) throw ApiException("$p is larger than 50 MB and cannot be committed through the API.")
            val b = gh.call(
                "POST", "/repos/$full/git/blobs",
                JSONObject().put("content", Base64.encodeToString(bytes, Base64.NO_WRAP)).put("encoding", "base64")
            )
            if (!b.ok) throw ApiException(gh.explain(b, "Uploading $p"))
            val sha = b.obj().getString("sha")
            newShas[p] = sha
            val mode = if (p.substringAfterLast('/') == "gradlew") "100755" else "100644"
            items.put(JSONObject().put("path", p).put("mode", mode).put("type", "blob").put("sha", sha))
        }
        for (p in dels) {
            items.put(JSONObject().put("path", p).put("mode", "100644").put("type", "blob").put("sha", JSONObject.NULL))
        }
        val tree = gh.call("POST", "/repos/$full/git/trees", JSONObject().put("base_tree", baseTree).put("tree", items))
        if (!tree.ok) throw ApiException(gh.explain(tree, "Creating tree"))
        val commit = gh.call(
            "POST", "/repos/$full/git/commits",
            JSONObject().put("message", message).put("tree", tree.obj().getString("sha")).put("parents", JSONArray().put(head))
        )
        if (!commit.ok) throw ApiException(gh.explain(commit, "Creating commit"))
        val csha = commit.obj().getString("sha")
        val upd = gh.call("PATCH", "/repos/$full/git/refs/heads/${refPath(branch)}", JSONObject().put("sha", csha))
        if (!upd.ok) {
            throw ApiException(
                gh.explain(upd, "Updating branch '$branch'") +
                    " If the branch is protected, direct pushes may be blocked; use another branch in Settings."
            )
        }
        for ((p, s) in newShas) ws.base[p] = s
        for (p in dels) ws.base.remove(p)
        ws.head = csha
        ws.branch = branch
        ws.save()
        return PushResult(true, csha, ups.size + dels.size, emptyList())
    }

    private fun bootstrap(branch: String, message: String, ch: Changes): PushResult {
        val p = ch.upserts.firstOrNull()
            ?: throw ApiException("The repository is empty and there is no file to create the first commit from.")
        val bytes = ws.resolve(p).readBytes()
        val body = JSONObject().put("message", message).put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
        val r = gh.call("PUT", "/repos/$full/contents/" + p.split('/').joinToString("/") { enc(it) }, body)
        if (!r.ok) throw ApiException(gh.explain(r, "Creating the first commit"))
        val o = r.obj()
        ws.base[p] = o.getJSONObject("content").getString("sha")
        ws.head = o.getJSONObject("commit").getString("sha")
        ws.branch = branch
        ws.save()
        if (ch.upserts.size == 1 && ch.deletes.isEmpty()) return PushResult(true, ws.head, 1, emptyList())
        val more = push(branch, message, true)
        return PushResult(true, more.sha ?: ws.head, 1 + more.count, more.conflicts)
    }
}
