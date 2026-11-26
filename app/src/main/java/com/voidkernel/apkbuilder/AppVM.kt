package com.voidkernel.apkbuilder

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

const val WF = "void-apk-build.yml"
const val WF_PATH = ".github/workflows/void-apk-build.yml"

class AppVM(app: Application) : AndroidViewModel(app) {
    private val ctx: Context = app.applicationContext
    val vault = Vault(ctx)
    val prefs = Prefs(ctx)
    val console = ConsoleStore(File(ctx.filesDir, "console.json"))
    val gh = GitHub { vault.token() }
    private val outFile = File(ctx.filesDir, "outputs.json")

    var connected by mutableStateOf(vault.hasToken())
    var login by mutableStateOf(prefs.login)
    var repos by mutableStateOf<List<Repo>>(emptyList())
    var reposLoading by mutableStateOf(false)
    var repo by mutableStateOf<Repo?>(null)
    var details by mutableStateOf<List<String>>(emptyList())
    var ws by mutableStateOf<Workspace?>(null)
    var dir by mutableStateOf("")
    var entries by mutableStateOf<List<Entry>>(emptyList())
    var pending by mutableStateOf(0)
    var busy by mutableStateOf<String?>(null)
    var verify by mutableStateOf<List<Pair<Boolean, String>>>(emptyList())
    var build by mutableStateOf(BuildState())
    val outputs = mutableStateListOf<OutputItem>()
    var importPlan by mutableStateOf<List<ImportItem>?>(null)
    var importConflicts by mutableStateOf<List<String>>(emptyList())
    var askConflicts by mutableStateOf<List<String>?>(null)
    var askWorkflow by mutableStateOf(false)
    var editing by mutableStateOf<String?>(null)
    var banner by mutableStateOf("")
    val toasts = MutableSharedFlow<String>(extraBufferCapacity = 16)

    private var decision: CompletableDeferred<Boolean>? = null
    @Volatile private var cancelRequested = false

    init {
        loadOutputs()
        val d = prefs.defaultRepo
        if (connected && d.contains("/")) {
            val o = d.substringBefore("/")
            val n = d.substringAfter("/")
            openRepo(Repo(d, n, o, false, prefs.repoBranch, "https://github.com/$d", "", true, false, ""), false)
            viewModelScope.launch(Dispatchers.IO) { try { refreshDetails() } catch (_: Exception) {} }
        }
    }

    // ---------- helpers ----------
    fun toast(s: String) { toasts.tryEmit(U.scrub(s)) }
    fun err(cat: String, step: String, msg: String, bid: String = "general") = console.add("ERROR", cat, step, msg, bid)
    fun ok(cat: String, step: String, msg: String, bid: String = "general") = console.add("SUCCESS", cat, step, msg, bid)
    fun branch(): String = prefs.branchOverride.ifBlank { repo?.branch ?: "main" }
    private fun join(a: String, b: String) = if (a.isEmpty()) b else "$a/$b"

    private fun runBusy(msg: String, block: suspend () -> Unit) {
        if (busy != null) { toast("Please wait: $busy"); return }
        busy = msg
        viewModelScope.launch(Dispatchers.IO) {
            try { block() } catch (e: Exception) {
                val m = e.message ?: e.toString()
                err("Operation", msg, m); toast(m)
            } finally {
                busy = null
                refreshNow()
            }
        }
    }

    fun refreshNow() {
        val w = ws ?: return
        try {
            entries = w.list(dir)
            pending = w.pending().total
        } catch (_: Exception) {}
    }

    // ---------- GitHub connection ----------
    fun connect(token: String) {
        val t = token.trim()
        if (t.isEmpty()) { toast("Paste a token first"); return }
        runBusy("Verifying token...") {
            val tmp = GitHub { t }
            val r = tmp.get("/user")
            if (!r.ok) {
                val m = tmp.explain(r, "Connecting to GitHub")
                err("GitHub", "Connect", m); toast(m)
            } else {
                vault.save(t)
                val l = r.obj().str("login")
                prefs.login = l; login = l; connected = true
                ok("GitHub", "Connect", "GitHub connected as $l")
                loadRepos()
            }
        }
    }

    fun disconnect() {
        vault.clear()
        connected = false; login = ""; prefs.login = ""
        repos = emptyList(); repo = null; ws = null; entries = emptyList(); verify = emptyList()
        ok("GitHub", "Disconnect", "Token removed from this device")
        toast("Disconnected")
    }

    fun verifyPermissions() {
        runBusy("Verifying permissions...") {
            val rep = ArrayList<Pair<Boolean, String>>()
            val u = gh.get("/user")
            if (!u.ok) {
                rep.add(false to gh.explain(u, "Token check")); verify = rep
                err("Permissions", "Verify", rep[0].second); return@runBusy
            }
            rep.add(true to "Token is valid (user ${u.obj().str("login")})")
            val scopes = u.headers["x-oauth-scopes"]
            rep.add(true to (if (scopes != null) "Classic token. Scopes: ${scopes.ifBlank { "none" }}" else "Fine-grained token (scopes are checked per repository below)"))
            val r = repo
            if (r == null) rep.add(false to "No repository selected: select one to verify Contents / Actions access")
            else {
                val ri = gh.get("/repos/${r.full}")
                if (!ri.ok) rep.add(false to gh.explain(ri, "Reading ${r.full}"))
                else {
                    val p = ri.obj().optJSONObject("permissions")
                    rep.add((p?.optBoolean("push", false) ?: false) to "Contents: write access to ${r.full} (${if (p?.optBoolean("push", false) == true) "yes" else "NOT confirmed - needs Contents: Read and write"})")
                }
                val wf = gh.get("/repos/${r.full}/actions/workflows")
                rep.add(wf.ok to (if (wf.ok) "Actions: readable, ${wf.obj().optInt("total_count")} workflow(s)" else gh.explain(wf, "Actions access") + " Needs Actions: Read and write, and Actions enabled."))
                rep.add(true to "Workflows: write cannot be probed without writing; it is verified when RUN ALL creates the workflow file.")
            }
            verify = rep
            val allOk = rep.all { it.first }
            if (allOk) ok("Permissions", "Verify", "Permission verification passed") else err("Permissions", "Verify", rep.filter { !it.first }.joinToString(" | ") { it.second })
        }
    }

    fun checkSecrets() {
        val r = repo ?: run { toast("Select a repository first"); return }
        runBusy("Checking signing secrets...") {
            val s = gh.get("/repos/${r.full}/actions/secrets?per_page=100")
            if (!s.ok) {
                val m = gh.explain(s, "Reading Actions secret names") + " (optional permission: Secrets: Read). Release builds still run; they are just unsigned unless the secrets exist."
                verify = listOf(false to m); err("Signing", "Check secrets", m); return@runBusy
            }
            val names = HashSet<String>()
            val a = s.obj().getJSONArray("secrets")
            for (i in 0 until a.length()) names.add(a.getJSONObject(i).getString("name"))
            val need = listOf("VOID_KEYSTORE_BASE64", "VOID_KEYSTORE_PASSWORD", "VOID_KEY_ALIAS", "VOID_KEY_PASSWORD")
            verify = need.map { (it in names) to (if (it in names) "$it present" else "$it missing") }
            if (need.all { it in names }) ok("Signing", "Check secrets", "All four signing secrets exist in ${r.full}")
            else err("Signing", "Check secrets", "Missing: ${need.filter { it !in names }.joinToString()}. Release APK will be unsigned.")
        }
    }

    // ---------- repositories ----------
    fun loadRepos() { runBusyIfFree("Loading repositories...") { loadReposSync() } }

    private fun runBusyIfFree(msg: String, block: suspend () -> Unit) {
        if (busy == null) runBusy(msg, block) else viewModelScope.launch(Dispatchers.IO) { block() }
    }

    private fun loadReposSync() {
        reposLoading = true
        try {
            val all = ArrayList<Repo>()
            for (page in 1..10) {
                val r = gh.get("/user/repos?per_page=100&page=$page&sort=updated&affiliation=owner,collaborator,organization_member")
                if (!r.ok) { err("GitHub", "Fetch repositories", gh.explain(r, "Fetching repositories")); return }
                val a = r.arr()
                for (i in 0 until a.length()) all.add(Repo.from(a.getJSONObject(i)))
                if (a.length() < 100) break
            }
            repos = all
            ok("GitHub", "Fetch repositories", "Fetched ${all.size} repositories")
        } catch (e: Exception) {
            err("GitHub", "Fetch repositories", e.message ?: e.toString())
        } finally { reposLoading = false }
    }

    fun createRepo(name: String, priv: Boolean, desc: String) {
        val n = name.trim()
        if (!Regex("[A-Za-z0-9._-]{1,100}").matches(n)) { toast("Use letters, digits, '.', '-' or '_' only"); return }
        runBusy("Creating $n...") {
            val body = JSONObject().put("name", n).put("private", priv).put("description", desc).put("auto_init", true)
            val r = gh.call("POST", "/user/repos", body)
            if (!r.ok) {
                val m = gh.explain(r, "Creating repository") + " Fine-grained tokens need Administration: Read and write for this."
                err("GitHub", "Create repository", m); toast(m); return@runBusy
            }
            val created = Repo.from(r.obj())
            repos = listOf(created) + repos
            ok("GitHub", "Create repository", "Repository ${created.full} created")
            openRepo(created, true)
        }
    }

    fun selectRepo(r: Repo) = openRepo(r, true)

    private fun openRepo(r: Repo, pullNow: Boolean) {
        repo = r
        prefs.defaultRepo = r.full; prefs.repoBranch = r.branch
        val key = r.full.replace('/', '_')
        val w = Workspace(File(ctx.filesDir, "work/$key"), File(ctx.filesDir, "work/$key.meta.json"))
        ws = w; dir = ""; banner = ""
        refreshNow()
        if (pullNow) {
            ok("Repository", "Select", "Repository selected: ${r.full}")
            viewModelScope.launch(Dispatchers.IO) { try { refreshDetails() } catch (_: Exception) {} }
            if (w.head == null) pull()
        }
    }

    fun refreshDetails() {
        val r = repo ?: return
        val ri = gh.get("/repos/${r.full}")
        val lines = ArrayList<String>()
        if (ri.ok) {
            val nr = Repo.from(ri.obj())
            repo = nr; prefs.repoBranch = nr.branch
            lines.add((if (nr.priv) "Private" else "Public") + " repository, default branch ${nr.branch}" + (if (nr.archived) " (ARCHIVED - read only)" else ""))
            if (nr.desc.isNotBlank()) lines.add(nr.desc)
        } else lines.add(gh.explain(ri, "Reading repository"))
        val wf = gh.get("/repos/${r.full}/actions/workflows")
        lines.add(if (wf.ok) "GitHub Actions: available (${wf.obj().optInt("total_count")} workflow(s))" else "GitHub Actions: unavailable - " + gh.explain(wf, "Actions check"))
        details = lines
    }

    // ---------- pull / commit ----------
    fun pull() {
        val r = repo ?: return
        val w = ws ?: return
        runBusy("Pulling ${r.full}...") {
            val res = Sync(gh, w, r.full, ctx.cacheDir).pull(branch())
            if (res.empty) {
                ok("Repository", "Pull", "${r.full} is empty (no commits yet). Add files, then commit.")
                toast("Repository is empty")
            } else {
                ok("Repository", "Pull", "Pulled ${r.full}@${branch()}: ${res.added} added, ${res.updated} updated, ${res.removed} removed")
                if (res.kept.isNotEmpty()) err("Repository", "Pull", "Kept your local version of ${res.kept.size} file(s) that also changed on GitHub: ${res.kept.take(8).joinToString()}")
            }
        }
    }

    private suspend fun decide(paths: List<String>): Boolean {
        val d = CompletableDeferred<Boolean>()
        decision = d; askConflicts = paths
        val r = d.await()
        askConflicts = null
        return r
    }

    fun answerConflict(overwrite: Boolean) { decision?.complete(overwrite) }

    private suspend fun pushPending(message: String, bid: String): Boolean {
        val r = repo ?: return false
        val w = ws ?: return false
        val sync = Sync(gh, w, r.full, ctx.cacheDir)
        var res = sync.push(branch(), message, false)
        if (res.conflicts.isNotEmpty()) {
            err("Git", "Commit", "Files changed on GitHub and locally: ${res.conflicts.take(10).joinToString()}", bid)
            if (!decide(res.conflicts)) { err("Git", "Commit", "Commit cancelled. No remote file was overwritten.", bid); return false }
            res = sync.push(branch(), message, true)
        }
        if (res.committed) ok("Git", "Commit", "Committed ${res.count} change(s) to ${r.full}@${branch()} (${res.sha?.take(7)})", bid)
        return true
    }

    fun commit(message: String) {
        runBusy("Committing...") {
            if (pending == 0) { toast("No pending changes"); return@runBusy }
            val ok = pushPending(message.ifBlank { "Update from VOID APK BUILDER" }, "general")
            if (ok) toast("Committed")
        }
    }

    // ---------- file operations ----------
    fun openDir(rel: String) { dir = rel; refreshNow() }
    fun up() { dir = if (dir.contains("/")) dir.substringBeforeLast("/") else ""; refreshNow() }

    fun createEntry(path: String, folder: Boolean) {
        val w = ws ?: return
        val rel = U.cleanPath(join(dir, path.trim())) ?: run { toast("Invalid name or path"); return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (w.exists(rel)) { toast("$rel already exists"); return@launch }
                if (folder) w.mkdir(rel) else w.write(rel, ByteArray(0))
                ok("Files", if (folder) "Create folder" else "Create file", "Created $rel")
                refreshNow()
            } catch (e: Exception) { err("Files", "Create", e.message ?: e.toString()); toast("Could not create: ${e.message}") }
        }
    }

    fun renameEntry(e: Entry, newName: String) {
        val w = ws ?: return
        val n = newName.trim()
        if (n.isEmpty() || n.contains('/') || n.contains('\\') || n == "." || n == "..") { toast("Invalid name"); return }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (w.rename(e.rel, n)) { ok("Files", "Rename", "Renamed ${e.rel} to $n"); refreshNow() }
                else toast("A file named $n already exists")
            } catch (x: Exception) { err("Files", "Rename", x.message ?: x.toString()) }
        }
    }

    fun deleteEntry(e: Entry) {
        val w = ws ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try { w.delete(e.rel); ok("Files", "Delete", "Deleted ${e.rel} locally (commit to remove it on GitHub)"); refreshNow() }
            catch (x: Exception) { err("Files", "Delete", x.message ?: x.toString()) }
        }
    }

    fun readText(rel: String): String? {
        val f = ws?.resolve(rel) ?: return null
        if (!f.isFile || f.length() > 2_000_000) return null
        val b = f.readBytes()
        if (b.take(8000).any { it == 0.toByte() }) return null
        return String(b, Charsets.UTF_8)
    }

    fun saveText(rel: String, text: String): Boolean = try {
        ws?.write(rel, text.toByteArray(Charsets.UTF_8))
        ok("Files", "Save", "Saved $rel")
        viewModelScope.launch(Dispatchers.IO) { refreshNow() }
        true
    } catch (e: Exception) { err("Files", "Save", e.message ?: e.toString()); false }

    // ---------- uploads ----------
    private fun displayName(u: Uri): String? =
        ctx.contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }

    fun stageFiles(uris: List<Uri>) {
        val items = uris.mapNotNull { u ->
            val n = displayName(u) ?: return@mapNotNull null
            ImportItem(join(dir, n), u, null, false)
        }
        startImport(items)
    }

    fun stageTree(uri: Uri, keepName: Boolean) {
        runBusy("Scanning folder...") {
            val root = DocumentFile.fromTreeUri(ctx, uri) ?: throw IllegalStateException("Cannot open that folder")
            val base = if (keepName) join(dir, root.name ?: "folder") else dir
            val items = ArrayList<ImportItem>()
            walk(root, base, items, 0)
            startImport(items)
        }
    }

    private val skipDirs = setOf(".git", ".gradle", ".idea", "node_modules")

    private fun walk(d: DocumentFile, base: String, out: MutableList<ImportItem>, depth: Int) {
        if (depth > 30) return
        val kids = d.listFiles()
        val names = kids.mapNotNull { it.name }.toSet()
        for (k in kids) {
            val n = k.name ?: continue
            val rel = join(base, n)
            if (k.isDirectory) {
                if (n in skipDirs || (n == "build" && ("build.gradle" in names || "build.gradle.kts" in names))) continue
                out.add(ImportItem(rel, null, null, true))
                walk(k, rel, out, depth + 1)
            } else if (n != "local.properties") out.add(ImportItem(rel, k.uri, null, false))
        }
    }

    fun stageVipnnb(uri: Uri) {
        runBusy("Reading .vipnnb package...") {
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("Cannot read the file")
            try {
                val p = Vipnnb.parse(bytes)
                val items = ArrayList<ImportItem>()
                p.folders.forEach { items.add(ImportItem(it, null, null, true)) }
                p.files.forEach { items.add(ImportItem(it.first, null, it.second, false)) }
                ok("Files", ".vipnnb", "Package '${p.name}' validated: ${p.files.size} file(s), ${p.folders.size} folder(s)")
                banner = "Package '${p.name}' loaded. Review/edit the files, then press RUN ALL."
                startImport(items)
            } catch (e: IllegalArgumentException) {
                err("Files", ".vipnnb", "Rejected package: ${e.message}"); toast("Rejected: ${e.message}")
            }
        }
    }

    private fun startImport(raw: List<ImportItem>) {
        val w = ws ?: return
        var bad = 0
        val items = raw.mapNotNull {
            val p = U.cleanPath(it.rel)
            if (p == null) { bad++; null } else it.copy(rel = p)
        }
        if (bad > 0) err("Files", "Upload", "Skipped $bad item(s) with unsafe or invalid paths")
        if (items.isEmpty()) { toast("Nothing to import"); return }
        val conflicts = items.filter { !it.isDir && w.exists(it.rel) }.map { it.rel }
        importPlan = items
        if (conflicts.isEmpty()) executeImport(true) else importConflicts = conflicts
    }

    fun cancelImport() { importPlan = null; importConflicts = emptyList() }

    fun executeImport(overwrite: Boolean) {
        val items = importPlan ?: return
        val w = ws ?: return
        importPlan = null; importConflicts = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            busy = "Importing ${items.size} item(s)..."
            var n = 0; var skipped = 0; var failed = 0
            try {
                for (item in items) {
                    try {
                        if (item.isDir) { w.mkdir(item.rel); continue }
                        if (w.exists(item.rel) && !overwrite) { skipped++; continue }
                        val b = item.bytes ?: ctx.contentResolver.openInputStream(item.uri!!)?.use { s -> s.readBytes() } ?: throw IllegalStateException("unreadable")
                        if (b.size > 50L * 1024 * 1024) { failed++; err("Files", "Upload", "${item.rel} exceeds 50 MB"); continue }
                        w.write(item.rel, b); n++
                    } catch (e: Exception) { failed++; err("Files", "Upload", "${item.rel}: ${e.message}") }
                }
                if (n > 0 || skipped > 0) ok("Files", "Upload", "Imported $n file(s)" + (if (skipped > 0) ", kept $skipped existing" else "") + (if (failed > 0) ", $failed failed" else ""))
                toast("Imported $n file(s)")
            } finally { busy = null; refreshNow() }
        }
    }

    // ---------- RUN ALL ----------
    fun cancelBuild() {
        cancelRequested = true
        val r = repo ?: return
        val id = build.runId
        if (id == 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            val c = gh.call("POST", "/repos/${r.full}/actions/runs/$id/cancel")
            if (c.ok) toast("Cancel requested") else err("Actions", "Cancel", gh.explain(c, "Cancelling run"), build.buildId)
        }
    }

    fun runAll() {
        val r = repo
        val w = ws
        if (r == null || w == null) { toast("Select a repository first"); return }
        if (build.running || busy != null) { toast("Another operation is running"); return }
        val bid = "build-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        cancelRequested = false
        build = BuildState(buildId = bid, running = true, stage = "Validating", startedAt = System.currentTimeMillis())
        viewModelScope.launch(Dispatchers.IO) {
            var good = false
            try { good = doRunAll(r, w, bid) } catch (e: Exception) {
                err("Build", build.stage, e.message ?: e.toString(), bid)
            }
            build = build.copy(running = false, endedAt = System.currentTimeMillis(),
                stage = if (good) "Finished - APK ready in Output" else "Stopped - see Error Console")
            refreshNow()
        }
    }

    private fun st(s: String, d: String = "") { build = build.copy(stage = s, detail = d) }

    private fun validateProject(w: Workspace): List<String> {
        val keys = w.allFiles().keys
        val settings = keys.filter { val n = it.substringAfterLast('/'); (n == "settings.gradle" || n == "settings.gradle.kts") && it.count { c -> c == '/' } <= 4 }
        if (settings.isEmpty()) return listOf("No settings.gradle(.kts) found. Upload a Gradle Android project or a .vipnnb package first.")
        val d = settings[0].substringBeforeLast('/', "")
        val prefix = if (d.isEmpty()) "" else "$d/"
        val hasBuild = keys.any { it.startsWith(prefix) && it.substringAfterLast('/').let { n -> n == "build.gradle" || n == "build.gradle.kts" } }
        val problems = ArrayList<String>()
        if (!hasBuild) problems.add("No build.gradle(.kts) found next to or below ${settings[0]}")
        if (keys.none { it.startsWith(prefix) && it.endsWith("AndroidManifest.xml") }) problems.add("No AndroidManifest.xml found in the project")
        return problems
    }

    private suspend fun ensureWorkflow(r: Repo, br: String, bid: String): Boolean {
        val want = ctx.assets.open("void-apk-build.yml").bufferedReader().use { it.readText() }
        val g = gh.get("/repos/${r.full}/contents/$WF_PATH?ref=${enc(br)}")
        var sha: String? = null
        if (g.code == 200) {
            val o = g.obj()
            sha = o.getString("sha")
            val cur = String(Base64.decode(o.getString("content").replace("\n", ""), Base64.DEFAULT), Charsets.UTF_8)
            if (cur == want) { ok("Workflow", "Verify", "Build workflow is up to date", bid); return true }
            if (!cur.contains("void-apk-builder:managed")) {
                err("Workflow", "Conflict", "$WF_PATH exists and was not created by VOID APK BUILDER.", bid)
                askWorkflow = true
                val allow = CompletableDeferred<Boolean>().also { decision = it }.await()
                askWorkflow = false
                if (!allow) { err("Workflow", "Conflict", "Cancelled. Existing workflow file left untouched.", bid); return false }
            }
        } else if (g.code != 404) { err("Workflow", "Check", gh.explain(g, "Checking workflow file"), bid); return false }
        val body = JSONObject().put("message", "VOID APK BUILDER: create/update build workflow")
            .put("content", Base64.encodeToString(want.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)).put("branch", br)
        if (sha != null) body.put("sha", sha)
        val p = gh.call("PUT", "/repos/${r.full}/contents/$WF_PATH", body)
        if (!p.ok) {
            err("Workflow", "Create", gh.explain(p, "Writing $WF_PATH") + " Needs Contents: write and Workflows: write; branch protection can also block this.", bid)
            return false
        }
        ok("Workflow", "Create", "Build workflow written to $WF_PATH", bid)
        return true
    }

    private fun stripTs(l: String) = l.replace(Regex("^\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z\\s?"), "").take(300)

    private fun tail(text: String, n: Int): String =
        U.scrub(text.lines().takeLast(n).joinToString("\n") { stripTs(it) })

    /** Short, readable failure summary: GitHub error annotations first, then the last few lines. */
    private fun excerpt(text: String): String {
        val lines = text.lines().map { stripTs(it) }
        val errs = lines.filter { it.contains("##[error]") || it.contains("Error:") || it.contains("FAILURE") || it.contains("What went wrong") }.takeLast(12)
        val last = lines.filter { it.isNotBlank() }.takeLast(12)
        return U.scrub((errs + listOf("--- last lines ---") + last).joinToString("\n"))
    }

    private suspend fun doRunAll(r: Repo, w: Workspace, bid: String): Boolean {
        val full = r.full
        val br = branch()
        st("1/8 Validating GitHub connection")
        val me = gh.get("/user")
        if (!me.ok) { err("GitHub", "Validate connection", gh.explain(me, "Validating token"), bid); return false }
        val ri = gh.get("/repos/$full")
        if (!ri.ok) { err("GitHub", "Validate repository", gh.explain(ri, "Reading $full"), bid); return false }
        val perms = ri.obj().optJSONObject("permissions")
        if (perms != null && !perms.optBoolean("push", false)) {
            err("Permissions", "Validate repository", "No write access to $full. Grant Contents: Read and write on this repository.", bid); return false
        }
        if (ri.obj().optBoolean("archived")) { err("Repository", "Validate", "$full is archived (read only).", bid); return false }
        val wfl = gh.get("/repos/$full/actions/workflows")
        if (!wfl.ok) { err("Actions", "Validate Actions", gh.explain(wfl, "Checking GitHub Actions") + " Enable Actions for the repository and grant Actions: Read and write.", bid); return false }
        ok("GitHub", "Validate", "Token, repository write access and Actions access verified for $full", bid)

        st("2/8 Validating project structure")
        val problems = validateProject(w)
        if (problems.isNotEmpty()) { err("Project", "Validate structure", problems.joinToString(" | "), bid); return false }
        ok("Project", "Validate structure", "Gradle Android project structure found", bid)

        st("3/8 Committing pending changes")
        if (!pushPending("VOID APK BUILDER: update project before build", bid)) return false

        st("4/8 Preparing build workflow")
        if (!ensureWorkflow(r, br, bid)) return false

        st("5/8 Checking for running builds")
        for (s in listOf("in_progress", "queued")) {
            val a = gh.get("/repos/$full/actions/workflows/$WF/runs?status=$s&per_page=5")
            if (a.ok && a.obj().optInt("total_count") > 0) {
                err("Actions", "Concurrency", "A VOID build is already $s in $full. Wait for it to finish or cancel it on GitHub.", bid); return false
            }
        }

        st("6/8 Dispatching workflow")
        val inputs = JSONObject().put("build_type", prefs.buildType).put("project_dir", prefs.projectDir)
            .put("gradle_task", prefs.gradleTask).put("jdk", prefs.jdk)
        var dr: ApiResult? = null
        for (i in 1..6) {
            dr = gh.call("POST", "/repos/$full/actions/workflows/$WF/dispatches", JSONObject().put("ref", br).put("inputs", inputs))
            if (dr.code != 404) break
            delay(5000)
        }
        if (dr == null || dr.code != 204) {
            err("Actions", "Dispatch workflow", (if (dr != null) gh.explain(dr, "Dispatching workflow") else "Dispatch failed") + " Needs Actions: Read and write; the branch '$br' must exist.", bid)
            return false
        }
        val dispatchMs = dr.headers.getDate("Date")?.time ?: System.currentTimeMillis()
        ok("Actions", "Dispatch", "GitHub accepted the workflow dispatch (HTTP 204). Waiting for the run to be created...", bid)

        st("7/8 Waiting for the workflow run to appear")
        var runId = 0L
        for (i in 1..30) {
            val l = gh.get("/repos/$full/actions/workflows/$WF/runs?event=workflow_dispatch&branch=${enc(br)}&per_page=10")
            if (l.ok) {
                val a = l.obj().getJSONArray("workflow_runs")
                var best = 0L
                for (k in 0 until a.length()) {
                    val o = a.getJSONObject(k)
                    val c = U.isoToMillis(o.str("created_at"))
                    if (c >= dispatchMs - 10_000 && c >= best) { best = c; runId = o.getLong("id"); build = build.copy(runUrl = o.str("html_url")) }
                }
            }
            if (runId != 0L) break
            delay(4000)
        }
        if (runId == 0L) { err("Actions", "Find run", "GitHub returned 204 but no workflow run appeared within 2 minutes. Check the Actions tab of $full.", bid); return false }
        build = build.copy(runId = runId)
        ok("Actions", "Run created", "GitHub Actions run #$runId created: ${build.runUrl}", bid)

        st("Build running on GitHub Actions (status polled every 6 s)")
        var failures = 0
        var lastLog = 0L
        val deadline = System.currentTimeMillis() + 120L * 60_000
        var conclusion = ""
        var headSha = ""
        while (System.currentTimeMillis() < deadline) {
            val rr = gh.get("/repos/$full/actions/runs/$runId")
            if (!rr.ok) {
                if (++failures > 5) { err("Actions", "Poll", gh.explain(rr, "Polling run"), bid); return false }
                delay(6000); continue
            }
            failures = 0
            val ro = rr.obj()
            val status = ro.str("status")
            conclusion = ro.str("conclusion")
            headSha = ro.str("head_sha")
            val steps = ArrayList<StepInfo>()
            var curJob = 0L
            val jr = gh.get("/repos/$full/actions/runs/$runId/jobs?per_page=20")
            if (jr.ok) {
                val ja = jr.obj().getJSONArray("jobs")
                for (j in 0 until ja.length()) {
                    val jo = ja.getJSONObject(j)
                    curJob = jo.getLong("id")
                    val sa = jo.optJSONArray("steps") ?: JSONArray()
                    for (s in 0 until sa.length()) {
                        val so = sa.getJSONObject(s)
                        steps.add(StepInfo(jo.str("name"), so.str("name"), so.str("status"), so.str("conclusion")))
                    }
                }
            }
            var log = build.logTail
            if (curJob != 0L && System.currentTimeMillis() - lastLog > 15_000) {
                lastLog = System.currentTimeMillis()
                val lg = try { gh.get("/repos/$full/actions/jobs/$curJob/logs") } catch (_: Exception) { null }
                if (lg != null && lg.ok) log = tail(lg.body, 40)
            }
            build = build.copy(runStatus = status, conclusion = conclusion, steps = steps, logTail = log, polls = build.polls + 1)
            if (status == "completed") break
            delay(6000)
        }
        if (conclusion.isEmpty()) { err("Actions", "Poll", "Timed out waiting for the run to finish.", bid); return false }
        if (conclusion != "success") {
            val failed = build.steps.filter { it.conclusion == "failure" }.joinToString { it.name }
            var logText = build.logTail
            val jr = gh.get("/repos/$full/actions/runs/$runId/jobs?per_page=20")
            if (jr.ok) {
                val ja = jr.obj().getJSONArray("jobs")
                if (ja.length() > 0) {
                    val lg = gh.get("/repos/$full/actions/jobs/${ja.getJSONObject(ja.length() - 1).getLong("id")}/logs")
                    if (lg.ok) logText = excerpt(lg.body)
                }
            }
            build = build.copy(logTail = logText)
            err("Workflow", "Run finished: $conclusion", "Run #$runId concluded '$conclusion'." + (if (failed.isNotEmpty()) " Failed step: $failed." else "") + "\n" + logText, bid)
            return false
        }
        ok("Workflow", "Run finished", "Run #$runId completed successfully", bid)

        st("8/8 Retrieving APK artifact")
        return fetchArtifacts(r, br, runId, headSha, bid)
    }

    private fun fetchArtifacts(r: Repo, br: String, runId: Long, headSha: String, bid: String): Boolean {
        val full = r.full
        val ar = gh.get("/repos/$full/actions/runs/$runId/artifacts")
        if (!ar.ok) { err("Artifact", "List artifacts", gh.explain(ar, "Listing artifacts"), bid); return false }
        val arts = ar.obj().getJSONArray("artifacts")
        var got = 0
        for (i in 0 until arts.length()) {
            val a = arts.getJSONObject(i)
            if (a.optBoolean("expired")) continue
            val aid = a.getLong("id")
            val zip = File(ctx.cacheDir, "artifact-$aid.zip")
            try {
                gh.downloadTo("/repos/$full/actions/artifacts/$aid/zip", zip)
            } catch (e: Exception) { err("Artifact", "Download", e.message ?: e.toString(), bid); continue }
            val outDir = File(ctx.filesDir, "outputs/$runId-$aid").apply { mkdirs() }
            var note = ""
            val apks = ArrayList<File>()
            try {
                ZipInputStream(zip.inputStream().buffered()).use { z ->
                    var e = z.nextEntry
                    while (e != null) {
                        val name = e.name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_")
                        if (!e.isDirectory && name.endsWith(".apk")) {
                            val f = File(outDir, name)
                            f.outputStream().use { o -> z.copyTo(o) }
                            apks.add(f)
                        } else if (!e.isDirectory && name == "void-build-info.json") {
                            note = try { JSONObject(String(z.readBytes())).optString("note") } catch (_: Exception) { "" }
                        }
                        e = z.nextEntry
                    }
                }
            } catch (e: Exception) { err("Artifact", "Extract", e.message ?: e.toString(), bid) } finally { zip.delete() }
            for (f in apks) {
                val valid = try { ZipFile(f).use { it.getEntry("AndroidManifest.xml") != null } } catch (_: Exception) { false }
                if (!valid) { err("Artifact", "Validate APK", "${f.name} is not a valid APK (no AndroidManifest.xml)", bid); f.delete(); continue }
                val type = if (a.str("name").contains("release") || f.name.contains("release")) "release" else "debug"
                val item = OutputItem(
                    "$runId-$aid-${f.name}", f.name, f.absolutePath, type,
                    note.ifBlank { if (f.name.contains("unsigned")) "Unsigned release APK" else "" },
                    runId, build.runUrl, full, br, headSha.take(7), f.length(), U.sha256(f.readBytes()),
                    U.ts()
                )
                outputs.add(0, item); saveOutputs()
                ok("APK", "Generated", "APK generated by run #$runId: ${f.name} (${U.size(f.length())})", bid)
                ok("APK", "Artifact downloaded", "Artifact downloaded and verified: ${f.name}", bid)
                got++
            }
        }
        if (got == 0) { err("Artifact", "Retrieve APK", "The run succeeded but no APK could be retrieved from its artifacts.", bid); return false }
        return true
    }

    // ---------- outputs ----------
    private fun loadOutputs() {
        try {
            val a = JSONArray(outFile.readText())
            for (i in 0 until a.length()) {
                val it = OutputItem.from(a.getJSONObject(i))
                if (File(it.path).exists()) outputs.add(it)
            }
        } catch (_: Exception) {}
    }

    private fun saveOutputs() {
        try { outFile.writeText(JSONArray().also { a -> outputs.toList().forEach { a.put(it.toJson()) } }.toString()) } catch (_: Exception) {}
    }

    fun renameOutput(o: OutputItem, name: String) {
        var n = name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (n.isEmpty()) return
        if (!n.endsWith(".apk")) n += ".apk"
        val i = outputs.indexOfFirst { it.id == o.id }
        if (i >= 0) { outputs[i] = o.copy(displayName = n); saveOutputs(); ok("APK", "Rename", "Output renamed to $n (file content untouched)") }
    }

    fun deleteOutput(o: OutputItem) {
        File(o.path).delete()
        outputs.removeAll { it.id == o.id }
        saveOutputs()
        ok("APK", "Delete", "Removed local output ${o.displayName}")
    }

    fun exportOutput(o: OutputItem, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.use { out -> File(o.path).inputStream().use { it.copyTo(out) } }
                ok("APK", "Download", "Saved ${o.displayName} to the location you chose")
                toast("APK saved")
            } catch (e: Exception) { err("APK", "Download", e.message ?: e.toString()); toast("Save failed: ${e.message}") }
        }
    }
}
