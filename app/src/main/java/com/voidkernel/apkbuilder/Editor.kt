package com.voidkernel.apkbuilder

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val KW = Regex("\\b(package|import|class|interface|object|fun|val|var|if|else|when|for|while|return|private|public|protected|internal|override|data|sealed|enum|abstract|open|companion|null|true|false|this|super|try|catch|finally|throw|new|static|final|void|int|long|boolean|extends|implements|in|is|as|by|suspend|plugins|android|dependencies|repositories)\\b")
private val STR = Regex("\"(\\\\.|[^\"\\\\\\n])*\"")
private val NUM = Regex("\\b\\d+(\\.\\d+)?\\b")
private val COM = Regex("//[^\\n]*|/\\*[\\s\\S]*?\\*/")
private val XTAG = Regex("</?[\\w:.-]+|/?>")
private val XATTR = Regex("[\\w:.-]+(?==)")
private val XCOM = Regex("<!--[\\s\\S]*?-->")
private val JKEY = Regex("\"[^\"\\n]*\"(?=\\s*:)")
private val JLIT = Regex("\\b(true|false|null)\\b")
private val YKEY = Regex("(?m)^[ \\t-]*[\\w.-]+(?=:)")
private val HCOM = Regex("#[^\\n]*")

fun highlight(t: String, ext: String): AnnotatedString {
    if (t.length > 120_000) return AnnotatedString(t)
    return buildAnnotatedString {
        append(t)
        fun paint(r: Regex, c: Color) { r.findAll(t).forEach { addStyle(SpanStyle(color = c), it.range.first, it.range.last + 1) } }
        when (ext) {
            "kt", "kts", "java", "gradle" -> { paint(KW, VC.purple); paint(NUM, VC.blue); paint(STR, VC.green); paint(COM, VC.muted) }
            "xml" -> { paint(XTAG, VC.purple); paint(XATTR, VC.blue); paint(STR, VC.green); paint(XCOM, VC.muted) }
            "json" -> { paint(NUM, VC.blue); paint(JLIT, VC.blue); paint(STR, VC.green); paint(JKEY, VC.purple) }
            "yml", "yaml", "properties", "toml" -> { paint(YKEY, VC.purple); paint(STR, VC.green); paint(HCOM, VC.muted) }
        }
    }
}

private class Highlighter(val ext: String) : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(highlight(text.text, ext), OffsetMapping.Identity)
}

@Composable
fun EditorScreen(vm: AppVM, rel: String, onClose: () -> Unit) {
    var loaded by remember(rel) { mutableStateOf(false) }
    var unreadable by remember(rel) { mutableStateOf(false) }
    var tv by remember(rel) { mutableStateOf(TextFieldValue("")) }
    var saved by remember(rel) { mutableStateOf("") }
    val undo = remember(rel) { ArrayDeque<TextFieldValue>() }
    val redo = remember(rel) { ArrayDeque<TextFieldValue>() }
    var query by remember(rel) { mutableStateOf("") }
    var search by remember(rel) { mutableStateOf(false) }
    var confirmClose by remember(rel) { mutableStateOf(false) }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()

    LaunchedEffect(rel) {
        val t = withContext(Dispatchers.IO) { vm.readText(rel) }
        if (t == null) unreadable = true else { tv = TextFieldValue(t); saved = t }
        loaded = true
    }
    val dirty = loaded && !unreadable && tv.text != saved
    BackHandler { if (dirty) confirmClose = true else onClose() }

    fun change(n: TextFieldValue) {
        if (n.text != tv.text) {
            undo.addLast(tv); if (undo.size > 200) undo.removeFirst(); redo.clear()
        }
        tv = n
    }
    fun find(forward: Boolean) {
        if (query.isEmpty()) return
        val t = tv.text
        val from = if (forward) tv.selection.max else tv.selection.min - 1
        var i = if (forward) t.indexOf(query, from.coerceAtLeast(0), true) else if (from >= 0) t.lastIndexOf(query, from, true) else -1
        if (i < 0) i = if (forward) t.indexOf(query, 0, true) else t.lastIndexOf(query, t.length, true)
        if (i >= 0) tv = tv.copy(selection = TextRange(i, i + query.length))
    }

    Column(Modifier.fillMaxSize().background(VC.bg).imePadding()) {
        Row(Modifier.fillMaxWidth().background(VC.surf).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ if (dirty) confirmClose = true else onClose() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = VC.text) }
            Column(Modifier.weight(1f)) {
                Text(rel.substringAfterLast('/') + if (dirty) "  *" else "", color = if (dirty) VC.blue else VC.text, fontSize = 15.sp, maxLines = 1)
                Text("/$rel" + if (dirty) "  (unsaved changes)" else "", color = VC.muted, fontSize = 10.sp, maxLines = 1)
            }
            IconButton({ search = !search }) { Icon(Icons.Default.Search, "Search", tint = VC.blue) }
            IconButton({ if (undo.isNotEmpty()) { redo.addLast(tv); tv = undo.removeLast() } }, enabled = undo.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Undo, "Undo", tint = if (undo.isNotEmpty()) VC.text else VC.line) }
            IconButton({ if (redo.isNotEmpty()) { undo.addLast(tv); tv = redo.removeLast() } }, enabled = redo.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Redo, "Redo", tint = if (redo.isNotEmpty()) VC.text else VC.line) }
            IconButton({ if (vm.saveText(rel, tv.text)) { saved = tv.text; vm.toast("Saved") } }, enabled = dirty) { Icon(Icons.Default.Save, "Save", tint = if (dirty) VC.green else VC.line) }
        }
        if (search) Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text("Find") }, modifier = Modifier.weight(1f))
            TextButton({ find(false) }) { Text("Prev") }
            TextButton({ find(true) }) { Text("Next") }
        }
        when {
            !loaded -> Text("Loading...", color = VC.muted, modifier = Modifier.padding(16.dp))
            unreadable -> Text("This file is binary or larger than 2 MB, so it cannot be edited here.", color = VC.red, modifier = Modifier.padding(16.dp))
            else -> {
                val lines = remember(tv.text.count { it == '\n' }) { (1..(tv.text.count { it == '\n' } + 1)).joinToString("\n") }
                val ext = rel.substringAfterLast('.', "").lowercase()
                val style = TextStyle(color = VC.text, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)
                Row(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)) {
                    Text(lines, style = style.copy(color = VC.muted, textAlign = TextAlign.End), modifier = Modifier.width(46.dp).padding(end = 8.dp, top = 8.dp))
                    Box(Modifier.weight(1f).horizontalScroll(hScroll)) {
                        BasicTextField(
                            value = tv, onValueChange = { change(it) },
                            textStyle = style, cursorBrush = SolidColor(VC.purple),
                            visualTransformation = Highlighter(ext),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrect = false, keyboardType = KeyboardType.Text),
                            modifier = Modifier.padding(top = 8.dp, end = 24.dp).widthIn(min = 320.dp)
                        )
                    }
                }
            }
        }
    }
    if (confirmClose) AlertDialog(
        onDismissRequest = { confirmClose = false }, containerColor = VC.card, title = { Text("Unsaved changes") },
        text = { Text("Save changes to ${rel.substringAfterLast('/')} before closing?") },
        confirmButton = { TextButton({ if (vm.saveText(rel, tv.text)) { confirmClose = false; onClose() } }) { Text("Save") } },
        dismissButton = { Row { TextButton({ confirmClose = false; onClose() }) { Text("Discard", color = VC.red) }; TextButton({ confirmClose = false }) { Text("Keep editing") } } }
    )
}
