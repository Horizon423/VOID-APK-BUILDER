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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun WorkspaceScreen(vm: AppVM) {
    val repo = vm.repo
    if (repo == null || vm.ws == null) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            Brand("Workspace")
            NeonCard(border = VC.purple) { Title("No repository selected"); Muted("Pick a repository in the Repos tab. All uploads, edits and builds target the selected repository.") }
        }
        return
    }
    var dlgUpload by remember { mutableStateOf(false) }
    var dlgCreate by remember { mutableStateOf(false) }
    var dlgCommit by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<Entry?>(null) }
    var rename by remember { mutableStateOf<Entry?>(null) }
    var del by remember { mutableStateOf<Entry?>(null) }
    var keepName by remember { mutableStateOf(true) }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { if (it.isNotEmpty()) vm.stageFiles(it) }
    val pickTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { if (it != null) vm.stageTree(it, keepName) }
    val pickVip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) vm.stageVipnnb(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Brand("Workspace")
        NeonCard(border = VC.purple) {
            Title(repo.full)
            Muted("Branch ${vm.branch()}  |  ${vm.pending} pending change(s)  |  folders are tracked by Git only when they contain files")
            vm.details.forEach { Muted(it) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NeonButton("Pull", color = VC.blue, icon = Icons.Default.CloudDownload) { vm.pull() }
                NeonButton("Commit", color = VC.blue, enabled = vm.pending > 0, icon = Icons.Default.CloudUpload) { dlgCommit = true }
            }
        }
        if (vm.banner.isNotBlank()) NeonCard(border = VC.green) { Text(vm.banner, color = VC.green, fontSize = 13.sp) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton("Upload", Modifier.weight(1f), icon = Icons.Default.UploadFile) { dlgUpload = true }
            NeonButton("Create", Modifier.weight(1f), icon = Icons.Default.CreateNewFolder) { dlgCreate = true }
            NeonButton(".vipnnb", Modifier.weight(1f), color = VC.blue, icon = Icons.Default.Archive) { pickVip.launch(arrayOf("*/*")) }
        }
        NeonButton("RUN ALL", Modifier.fillMaxWidth().height(58.dp), color = VC.green, enabled = !vm.build.running, icon = Icons.Default.PlayArrow) { vm.runAll() }
        BuildCard(vm)

        NeonCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vm.dir.isNotEmpty()) IconButton({ vm.up() }) { Icon(Icons.Default.ArrowUpward, "Up", tint = VC.blue) }
                Text("/" + vm.dir, color = VC.blue, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            }
            if (vm.entries.isEmpty()) Muted("Empty. Upload, create files, or pull from GitHub.")
            vm.entries.forEach { e ->
                var m by remember(e.rel) { mutableStateOf(false) }
                Row(Modifier.fillMaxWidth().clickable { if (e.isDir) vm.openDir(e.rel) else target = e }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (e.isDir) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile, null, tint = if (e.isDir) VC.purple else VC.muted, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { Text(e.name, color = VC.text, fontSize = 14.sp); if (!e.isDir) Muted(U.size(e.size)) }
                    Column {
                        IconButton({ m = true }) { Icon(Icons.Default.MoreVert, "Menu", tint = VC.muted) }
                        DropdownMenu(m, { m = false }) {
                            DropdownMenuItem(text = { Text("Rename") }, onClick = { m = false; rename = e })
                            DropdownMenuItem(text = { Text("Delete") }, onClick = { m = false; del = e })
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (dlgUpload) AlertDialog(
        onDismissRequest = { dlgUpload = false }, containerColor = VC.card, title = { Text("Upload to /${vm.dir}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NeonButton("Files (one or many)", Modifier.fillMaxWidth()) { dlgUpload = false; pickFiles.launch(arrayOf("*/*")) }
            NeonButton("Folder (keep folder name)", Modifier.fillMaxWidth(), color = VC.blue) { dlgUpload = false; keepName = true; pickTree.launch(null) }
            NeonButton("Android project (folder contents here)", Modifier.fillMaxWidth(), color = VC.blue) { dlgUpload = false; keepName = false; pickTree.launch(null) }
            Muted("Build output folders, .git, .gradle, .idea and local.properties are skipped. Existing files are never overwritten without asking.")
        } },
        confirmButton = {}, dismissButton = { TextButton({ dlgUpload = false }) { Text("Cancel") } }
    )

    if (dlgCreate) {
        var name by remember { mutableStateOf("") }
        var folder by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { dlgCreate = false }, containerColor = VC.card, title = { Text("Create in /${vm.dir}") },
            text = { Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!folder, { folder = false }, label = { Text("File") })
                    FilterChip(folder, { folder = true }, label = { Text("Folder") })
                }
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name or nested path (a/b/c.txt)") })
            } },
            confirmButton = { TextButton({ vm.createEntry(name, folder); dlgCreate = false }, enabled = name.isNotBlank()) { Text("Create") } },
            dismissButton = { TextButton({ dlgCreate = false }) { Text("Cancel") } }
        )
    }

    if (dlgCommit) {
        var msg by remember { mutableStateOf("Update from VOID APK BUILDER") }
        AlertDialog(
            onDismissRequest = { dlgCommit = false }, containerColor = VC.card, title = { Text("Commit ${vm.pending} change(s)") },
            text = { OutlinedTextField(msg, { msg = it }, label = { Text("Commit message") }) },
            confirmButton = { TextButton({ vm.commit(msg); dlgCommit = false }) { Text("Commit") } },
            dismissButton = { TextButton({ dlgCommit = false }) { Text("Cancel") } }
        )
    }

    target?.let { e ->
        AlertDialog(
            onDismissRequest = { target = null }, containerColor = VC.card, title = { Text(e.name) },
            text = { Muted("/${e.rel}") },
            confirmButton = { TextButton({ vm.editing = e.rel; target = null }) { Text("Open to Code Editor") } },
            dismissButton = { TextButton({ target = null }) { Text("Cancel") } }
        )
    }
    rename?.let { e ->
        var n by remember(e.rel) { mutableStateOf(e.name) }
        AlertDialog(onDismissRequest = { rename = null }, containerColor = VC.card, title = { Text("Rename") },
            text = { OutlinedTextField(n, { n = it }, singleLine = true) },
            confirmButton = { TextButton({ vm.renameEntry(e, n); rename = null }) { Text("Rename") } },
            dismissButton = { TextButton({ rename = null }) { Text("Cancel") } })
    }
    del?.let { e ->
        AlertDialog(onDismissRequest = { del = null }, containerColor = VC.card, title = { Text("Delete ${e.name}?") },
            text = { Text(if (e.isDir) "The folder and everything in it will be removed from the local workspace." else "The file will be removed from the local workspace.") },
            confirmButton = { TextButton({ vm.deleteEntry(e); del = null }) { Text("Delete", color = VC.red) } },
            dismissButton = { TextButton({ del = null }) { Text("Cancel") } })
    }
    val conflicts = vm.importConflicts
    if (conflicts.isNotEmpty()) AlertDialog(
        onDismissRequest = { vm.cancelImport() }, containerColor = VC.card, title = { Text("${conflicts.size} file(s) already exist") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(conflicts.take(25).joinToString("\n") + if (conflicts.size > 25) "\n..." else "", fontSize = 12.sp, fontFamily = FontFamily.Monospace) } },
        confirmButton = { TextButton({ vm.executeImport(true) }) { Text("Overwrite", color = VC.red) } },
        dismissButton = { Row { TextButton({ vm.executeImport(false) }) { Text("Keep existing") }; TextButton({ vm.cancelImport() }) { Text("Cancel") } } }
    )
    val pc = vm.askConflicts
    if (pc != null) AlertDialog(
        onDismissRequest = { vm.answerConflict(false) }, containerColor = VC.card, title = { Text("Changed on GitHub too") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("These files changed on GitHub since your last pull and also differ locally. Overwriting replaces the GitHub version with yours.", fontSize = 13.sp)
            Text(pc.take(25).joinToString("\n"), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        } },
        confirmButton = { TextButton({ vm.answerConflict(true) }) { Text("Overwrite GitHub", color = VC.red) } },
        dismissButton = { TextButton({ vm.answerConflict(false) }) { Text("Cancel") } }
    )
    if (vm.askWorkflow) AlertDialog(
        onDismissRequest = { vm.answerConflict(false) }, containerColor = VC.card, title = { Text("Existing workflow file") },
        text = { Text("$WF_PATH already exists and was not created by VOID APK BUILDER. Replace it with the VOID build workflow?") },
        confirmButton = { TextButton({ vm.answerConflict(true) }) { Text("Replace", color = VC.red) } },
        dismissButton = { TextButton({ vm.answerConflict(false) }) { Text("Cancel") } }
    )
}

@Composable
fun BuildCard(vm: AppVM) {
    val b = vm.build
    if (b.buildId.isEmpty()) return
    val now = remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(b.running) { while (vm.build.running) { now.longValue = System.currentTimeMillis(); delay(1000) } }
    val end = if (b.running) now.longValue else b.endedAt
    val secs = ((end - b.startedAt) / 1000).coerceAtLeast(0)
    val done = b.steps.count { it.status == "completed" }
    NeonCard(border = if (b.running) VC.blue else if (b.stage.startsWith("Finished")) VC.green else VC.red) {
        Title("Build status")
        Text(b.stage, color = VC.blue, fontSize = 13.sp)
        Muted("${b.buildId}  |  elapsed ${secs / 60}m ${secs % 60}s" + (if (b.runId != 0L) "  |  run #${b.runId}" else "") + (if (b.runStatus.isNotEmpty()) "  |  ${b.runStatus}" else "") + (if (b.conclusion.isNotEmpty()) " / ${b.conclusion}" else ""))
        if (b.steps.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { done.toFloat() / b.steps.size }, modifier = Modifier.fillMaxWidth(), color = VC.green)
            Muted("$done of ${b.steps.size} workflow steps completed (from the GitHub jobs API)")
            b.steps.forEach { Text((if (it.status == "completed") (if (it.conclusion == "success" || it.conclusion == "skipped") "[ok] " else "[x] ") else if (it.status == "in_progress") "[..] " else "[ ] ") + it.name, color = if (it.conclusion == "failure") VC.red else VC.muted, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
        }
        if (b.logTail.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Muted("Latest log lines (fetched by polling, not live streaming):")
            Column(Modifier.fillMaxWidth().height(140.dp).verticalScroll(rememberScrollState())) { Text(b.logTail, color = VC.text, fontSize = 10.sp, fontFamily = FontFamily.Monospace) }
        }
        if (b.running && b.runId != 0L) { Spacer(Modifier.height(6.dp)); NeonButton("Cancel run", color = VC.red) { vm.cancelBuild() } }
    }
}
