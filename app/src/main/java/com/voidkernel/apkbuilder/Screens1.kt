package com.voidkernel.apkbuilder

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ReposScreen(vm: AppVM, onOpen: () -> Unit) {
    var showCreate by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Brand("Repositories")
        if (!vm.connected) {
            NeonCard(border = VC.purple) {
                Title("Connect GitHub")
                Muted("Open Settings and add a Personal Access Token to list and manage your repositories.")
            }
            return
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Signed in as ${vm.login}", color = VC.green, fontSize = 13.sp); Muted("${vm.repos.size} repositories") }
            IconButton(onClick = { vm.loadRepos() }) { Icon(Icons.Default.Refresh, "Refresh", tint = VC.blue) }
            NeonButton("New", icon = Icons.Default.Add, onClick = { showCreate = true })
        }
        Spacer(Modifier.height(8.dp))
        if (vm.repos.isEmpty() && !vm.reposLoading) Muted("No repositories loaded yet. Tap refresh.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.repos, key = { it.full }) { r ->
                val sel = vm.repo?.full == r.full
                NeonCard(Modifier.clickable { vm.selectRepo(r); onOpen() }, border = if (sel) VC.purple else VC.line) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (r.priv) { Icon(Icons.Default.Lock, null, tint = VC.muted, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                                Text(r.full, color = VC.text, fontSize = 15.sp)
                            }
                            Muted((if (r.desc.isBlank()) "No description" else r.desc.take(90)) + "  |  ${r.branch}  |  ${r.updated}")
                        }
                        Icon(if (vm.prefs.defaultRepo == r.full) Icons.Default.Star else Icons.Default.StarBorder, "Default", tint = VC.blue)
                    }
                }
            }
        }
    }
    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var desc by remember { mutableStateOf("") }
        var priv by remember { mutableStateOf(true) }
        AlertDialog(
            onDismissRequest = { showCreate = false }, containerColor = VC.card,
            title = { Text("Create repository") },
            text = {
                Column {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(desc, { desc = it }, label = { Text("Description (optional)") }, singleLine = true)
                    Row(verticalAlignment = Alignment.CenterVertically) { Switch(priv, { priv = it }); Spacer(Modifier.width(8.dp)); Text(if (priv) "Private" else "Public") }
                }
            },
            confirmButton = { TextButton({ vm.createRepo(name, priv, desc); showCreate = false }) { Text("Create") } },
            dismissButton = { TextButton({ showCreate = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun SettingsScreen(vm: AppVM) {
    var tok by remember { mutableStateOf("") }
    var showSchema by remember { mutableStateOf(false) }
    var branch by remember { mutableStateOf(vm.prefs.branchOverride) }
    var pdir by remember { mutableStateOf(vm.prefs.projectDir) }
    var task by remember { mutableStateOf(vm.prefs.gradleTask) }
    var jdk by remember { mutableStateOf(vm.prefs.jdk) }
    var bt by remember { mutableStateOf(vm.prefs.buildType) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Brand("Settings")
        NeonCard(border = if (vm.connected) VC.green else VC.purple) {
            Title("GitHub connection")
            Spacer(Modifier.height(6.dp))
            if (vm.connected) Text("Connected as ${vm.login}. Token stored encrypted (Android Keystore) and never shown again.", color = VC.green, fontSize = 13.sp)
            else Muted("Not connected.")
            OutlinedTextField(
                tok, { tok = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                label = { Text(if (vm.connected) "New token (update)" else "Personal Access Token") },
                visualTransformation = PasswordVisualTransformation()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton(if (vm.connected) "Update token" else "Connect", enabled = tok.isNotBlank()) { vm.connect(tok); tok = "" }
                if (vm.connected) NeonButton("Verify", color = VC.blue) { vm.verifyPermissions() }
                if (vm.connected) NeonButton("Disconnect", color = VC.red) { vm.disconnect() }
            }
            vm.verify.forEach { (good, msg) -> Text((if (good) "OK  " else "FAIL  ") + msg, color = if (good) VC.green else VC.red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
            Spacer(Modifier.height(8.dp))
            Muted("Minimum fine-grained token permissions (repository access: the repos you build):\n- Metadata: Read\n- Contents: Read and write\n- Actions: Read and write\n- Workflows: Write\n- Administration: Read and write (only to create repositories)\n- Secrets: Read (optional, to check signing secrets)\nClassic tokens need the repo and workflow scopes.")
        }
        NeonCard {
            Title("Default repository")
            Text(vm.prefs.defaultRepo.ifBlank { "None selected - pick one in Repos" }, color = VC.blue, fontSize = 14.sp)
        }
        NeonCard {
            Title("Build configuration")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("debug", "release").forEach { t ->
                    FilterChip(selected = bt == t, onClick = { bt = t; vm.prefs.buildType = t }, label = { Text(t) })
                }
            }
            OutlinedTextField(branch, { branch = it; vm.prefs.branchOverride = it.trim() }, label = { Text("Branch (blank = default branch)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pdir, { pdir = it; vm.prefs.projectDir = it.trim() }, label = { Text("Project directory (blank = auto-detect)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(task, { task = it; vm.prefs.gradleTask = it.trim() }, label = { Text("Gradle task (blank = assembleDebug/Release)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(jdk, { jdk = it; vm.prefs.jdk = it.trim() }, label = { Text("JDK version") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        NeonCard {
            Title("Release signing")
            Muted("A release APK must be signed with a key you control. Add these repository secrets on GitHub (Settings > Secrets and variables > Actions): VOID_KEYSTORE_BASE64, VOID_KEYSTORE_PASSWORD, VOID_KEY_ALIAS, VOID_KEY_PASSWORD. Secrets are read only inside the workflow and never printed. Without them the release APK stays unsigned.")
            Spacer(Modifier.height(6.dp))
            NeonButton("Check secrets exist", color = VC.blue, enabled = vm.connected && vm.repo != null) { vm.checkSecrets() }
        }
        NeonCard {
            Title(".vipnnb format")
            NeonButton("View schema", color = VC.blue) { showSchema = true }
        }
        Muted("VOID APK BUILDER 1.0.0  |  Developed by Void Kernel 12")
    }
    if (showSchema) AlertDialog(
        onDismissRequest = { showSchema = false }, containerColor = VC.card,
        title = { Text(".vipnnb schema") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { SelectionContainer { Text(Vipnnb.SCHEMA_DOC, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = VC.text) } } },
        confirmButton = { TextButton({ showSchema = false }) { Text("Close") } }
    )
}

@Composable
fun ConsoleScreen(vm: AppVM) {
    var kind by remember { mutableStateOf("ERROR") }
    var filter by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val all = vm.console.entries.toList()
    val builds = all.map { it.buildId }.distinct().reversed()
    val shown = all.filter { it.kind == kind && (filter == null || it.buildId == filter) }.reversed()
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Brand("Consoles")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(kind == "ERROR", { kind = "ERROR" }, label = { Text("Error (${all.count { it.kind == "ERROR" }})") })
            FilterChip(kind == "SUCCESS", { kind = "SUCCESS" }, label = { Text("Success (${all.count { it.kind == "SUCCESS" }})") })
            Spacer(Modifier.weight(1f))
            Column {
                TextButton({ menu = true }) { Text(filter ?: "All builds", fontSize = 11.sp) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("All builds") }, onClick = { filter = null; menu = false })
                    builds.forEach { b -> DropdownMenuItem(text = { Text(b) }, onClick = { filter = b; menu = false }) }
                }
            }
            TextButton({ vm.console.clear(kind, filter) }) { Text("Clear", color = VC.red) }
        }
        if (shown.isEmpty()) Muted("No ${kind.lowercase()} entries.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(shown) { e ->
                NeonCard(border = if (e.kind == "ERROR") VC.red else VC.green) {
                    Text("${e.ts}  |  ${e.category}  |  ${e.step}", color = if (e.kind == "ERROR") VC.red else VC.green, fontSize = 11.sp)
                    SelectionContainer { Text(e.message, color = VC.text, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
                    Muted(e.buildId)
                }
            }
        }
    }
}

@Composable
fun OutputScreen(vm: AppVM) {
    var saving by remember { mutableStateOf<OutputItem?>(null) }
    var rename by remember { mutableStateOf<OutputItem?>(null) }
    var info by remember { mutableStateOf<OutputItem?>(null) }
    var del by remember { mutableStateOf<OutputItem?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
        val o = saving
        if (uri != null && o != null) vm.exportOutput(o, uri)
        saving = null
    }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Brand("APK Output")
        if (vm.outputs.isEmpty()) Muted("No APKs yet. Press RUN ALL in a repository workspace.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.outputs, key = { it.id }) { o ->
                var m by remember { mutableStateOf(false) }
                NeonCard(border = VC.green) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(o.displayName, color = VC.text, fontSize = 15.sp)
                            Muted("${o.buildType.uppercase()}  |  ${U.size(o.size)}  |  ${o.repo}  |  run #${o.runId}")
                            if (o.signedNote.isNotBlank()) Muted(o.signedNote)
                        }
                        Column {
                            IconButton({ m = true }) { Icon(Icons.Default.MoreVert, "Menu", tint = VC.muted) }
                            DropdownMenu(m, { m = false }) {
                                DropdownMenuItem(text = { Text("Download APK") }, onClick = { m = false; saving = o; launcher.launch(o.displayName) })
                                DropdownMenuItem(text = { Text("Rename APK") }, onClick = { m = false; rename = o })
                                DropdownMenuItem(text = { Text("View Build Details") }, onClick = { m = false; info = o })
                                DropdownMenuItem(text = { Text("Delete local output") }, onClick = { m = false; del = o })
                            }
                        }
                    }
                }
            }
        }
    }
    rename?.let { o ->
        var n by remember(o.id) { mutableStateOf(o.displayName) }
        AlertDialog(onDismissRequest = { rename = null }, containerColor = VC.card, title = { Text("Rename (display / download name)") },
            text = { OutlinedTextField(n, { n = it }, singleLine = true) },
            confirmButton = { TextButton({ vm.renameOutput(o, n); rename = null }) { Text("Rename") } },
            dismissButton = { TextButton({ rename = null }) { Text("Cancel") } })
    }
    info?.let { o ->
        AlertDialog(onDismissRequest = { info = null }, containerColor = VC.card, title = { Text("Build details") },
            text = { SelectionContainer { Text("Repository: ${o.repo}\nBranch: ${o.branch}\nCommit: ${o.commit}\nRun: #${o.runId}\n${o.runUrl}\nType: ${o.buildType}\nSize: ${U.size(o.size)}\nSHA-256: ${o.sha256}\nCreated: ${o.createdAt}\n${o.signedNote}", fontSize = 12.sp, fontFamily = FontFamily.Monospace) } },
            confirmButton = { TextButton({ info = null }) { Text("Close") } })
    }
    del?.let { o ->
        AlertDialog(onDismissRequest = { del = null }, containerColor = VC.card, title = { Text("Delete ${o.displayName}?") },
            text = { Text("This removes the local APK file and its entry. The GitHub artifact is not touched.") },
            confirmButton = { TextButton({ vm.deleteOutput(o); del = null }) { Text("Delete", color = VC.red) } },
            dismissButton = { TextButton({ del = null }) { Text("Cancel") } })
    }
}
