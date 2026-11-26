package com.voidkernel.apkbuilder

import org.json.JSONObject
import java.io.File

data class Entry(val name: String, val rel: String, val isDir: Boolean, val size: Long)

class Changes(val upserts: List<String>, val deletes: List<String>) {
    val empty: Boolean get() = upserts.isEmpty() && deletes.isEmpty()
    val total: Int get() = upserts.size + deletes.size
}

/** Local working copy of one repository plus the metadata needed to compute pending changes. */
class Workspace(val root: File, private val metaFile: File) {
    var head: String? = null
    var branch: String = "main"
    val base = HashMap<String, String>()
    private val rootCanon: String

    init {
        root.mkdirs()
        rootCanon = root.canonicalPath
        try {
            val o = JSONObject(metaFile.readText())
            head = if (o.isNull("head")) null else o.getString("head")
            branch = o.optString("branch", "main")
            val b = o.getJSONObject("base")
            b.keys().forEach { base[it] = b.getString(it) }
        } catch (_: Exception) {
        }
    }

    fun save() {
        val b = JSONObject()
        base.forEach { (k, v) -> b.put(k, v) }
        val o = JSONObject().put("head", head ?: JSONObject.NULL).put("branch", branch).put("base", b)
        metaFile.parentFile?.mkdirs()
        metaFile.writeText(o.toString())
    }

    fun resolve(rel: String): File {
        if (rel.isEmpty()) return root
        val c = U.cleanPath(rel) ?: throw IllegalArgumentException("Unsafe path: $rel")
        val f = File(root, c)
        val canon = f.canonicalPath
        if (canon != rootCanon && !canon.startsWith(rootCanon + File.separator))
            throw IllegalArgumentException("Path escapes the project: $rel")
        return f
    }

    fun exists(rel: String): Boolean = resolve(rel).exists()

    fun list(dir: String): List<Entry> {
        val kids = resolve(dir).listFiles() ?: return emptyList()
        return kids.map {
            Entry(it.name, if (dir.isEmpty()) it.name else "$dir/${it.name}", it.isDirectory, if (it.isFile) it.length() else 0L)
        }.sortedWith(compareBy({ !it.isDir }, { it.name.lowercase() }))
    }

    fun write(rel: String, bytes: ByteArray) {
        val f = resolve(rel)
        f.parentFile?.mkdirs()
        f.writeBytes(bytes)
    }

    fun mkdir(rel: String) {
        resolve(rel).mkdirs()
    }

    fun delete(rel: String) {
        resolve(rel).deleteRecursively()
    }

    fun rename(rel: String, newName: String): Boolean {
        val f = resolve(rel)
        val parent = f.parentFile ?: return false
        val t = File(parent, newName)
        if (t.exists()) return false
        return f.renameTo(t)
    }

    fun allFiles(): Map<String, File> {
        val out = LinkedHashMap<String, File>()
        root.walkTopDown().forEach {
            if (it.isFile) out[it.relativeTo(root).path.replace(File.separatorChar, '/')] = it
        }
        return out
    }

    fun pending(): Changes {
        val files = allFiles()
        val up = ArrayList<String>()
        for ((p, f) in files) if (base[p] != U.gitSha(f.readBytes())) up.add(p)
        val del = base.keys.filter { it !in files }
        return Changes(up.sorted(), del.sorted())
    }
}
