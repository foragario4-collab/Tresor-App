package app.glass.tresor.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glass.tresor.App
import app.glass.tresor.ui.ConfirmSheet
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.GlassIconButton
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.TopBar
import app.glass.tresor.ui.dateFmt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun NoteEditScreen(fid: String, iid: String?) {
    val vault = App.vault ?: return
    val scope = rememberCoroutineScope()
    var id by remember { mutableStateOf(iid) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var loaded by remember { mutableStateOf(iid == null) }
    var dirty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(iid) {
        if (iid != null) {
            val (t, b) = withContext(Dispatchers.IO) { vault.readNote(fid, iid) }
            title = t; body = b; loaded = true
        }
    }

    fun save() {
        if (!dirty || !vault.isFolderUnlocked(fid)) return
        if (id == null && title.isBlank() && body.isBlank()) return
        val t = title.ifBlank { body.lineSequence().firstOrNull { it.isNotBlank() }?.take(40) ?: "Notiz" }
        val b = body
        val cur = id
        dirty = false
        val saved = vault.saveNote(fid, cur, t, b)
        id = saved.id
    }

    // Automatisch speichern, kurz nach dem Tippen
    LaunchedEffect(title, body) {
        if (!dirty) return@LaunchedEffect
        delay(700)
        withContext(Dispatchers.IO) { save() }
    }
    // Beim Verlassen (auch beim Sperren) sofort speichern
    DisposableEffect(Unit) {
        val hook: () -> Unit = { try { save() } catch (_: Exception) {} }
        App.beforeLock.add(hook)
        onDispose {
            App.beforeLock.remove(hook)
            hook()
        }
    }

    GlassBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar(onBack = { App.nav.pop() }) {
                if (id != null) GlassIconButton(Ic.trash, "Löschen", tint = G.red) { confirmDelete = true }
            }
            if (loaded) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 16.dp)
                ) {
                    vault.item(fid, id ?: "")?.let {
                        Text("Bearbeitet am ${dateFmt.format(Date(it.modified))}", style = G.footnote)
                        Spacer(Modifier.height(6.dp))
                    }
                    Box {
                        if (title.isEmpty()) Text("Titel", style = G.largeTitle.copy(fontSize = 28.sp, color = G.faint))
                        BasicTextField(
                            value = title, onValueChange = { title = it; dirty = true },
                            textStyle = G.largeTitle.copy(fontSize = 28.sp), cursorBrush = SolidColor(G.accentText),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Box {
                        if (body.isEmpty()) Text("Schreib etwas …", style = G.body.copy(color = G.faint))
                        BasicTextField(
                            value = body, onValueChange = { body = it; dirty = true },
                            textStyle = G.body.copy(lineHeight = 25.sp), cursorBrush = SolidColor(G.accentText),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 420.dp)
                        )
                    }
                }
            }
        }
        ConfirmSheet(
            visible = confirmDelete, title = "Notiz löschen?", text = "Wird endgültig entfernt.",
            confirm = "Löschen", onDismiss = { confirmDelete = false }
        ) {
            confirmDelete = false
            val target = id ?: return@ConfirmSheet
            dirty = false
            id = null
            scope.launch {
                withContext(Dispatchers.IO) { vault.deleteItems(fid, setOf(target)) }
                App.nav.pop()
            }
        }
    }
}
