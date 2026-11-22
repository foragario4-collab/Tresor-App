package app.glass.tresor.disguise

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glass.tresor.App
import app.glass.tresor.ui.Blob
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.GlassIconButton
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.TopBar
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val notesBlobs = listOf(
    Blob(Color(0xFFF5B83D), -0.1f, 0.05f, 0.95f, 0.30f),
    Blob(Color(0xFFE07A2F), 1.1f, 0.85f, 0.9f, 0.24f),
)
private val NOTE_YELLOW = Color(0xFFF5C451)

private data class DNote(val id: Long, val text: String, val changed: Long)

private fun loadNotes(): List<DNote> = try {
    val arr = JSONArray(App.dprefs.getString("dnotes", "[]"))
    (0 until arr.length()).map { val o = arr.getJSONObject(it); DNote(o.getLong("i"), o.getString("t"), o.getLong("c")) }
} catch (e: Exception) {
    emptyList()
}

private fun saveNotes(l: List<DNote>) {
    val arr = JSONArray()
    l.forEach { arr.put(JSONObject().put("i", it.id).put("t", it.text).put("c", it.changed)) }
    App.dprefs.edit().putString("dnotes", arr.toString()).apply()
}

private fun noteDate(t: Long): String {
    val now = System.currentTimeMillis()
    return if (now - t < 24 * 3600_000L) SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(t))
    else SimpleDateFormat("d. MMM yyyy", Locale.GERMANY).format(Date(t))
}

/** Funktionierende Notizen-App. Geheim: den Code ins Suchfeld tippen und „Suchen“ drücken. */
@Composable
fun NotesApp() {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var notes by remember { mutableStateOf(loadNotes().sortedByDescending { it.changed }) }
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<DNote?>(null) }

    fun search() {
        val q = query.trim()
        focus.clearFocus()
        if (q.length in 4..12 && q.all { it.isDigit() }) {
            // Sieht aus wie eine Suche – passt der Code, öffnet sich der Tresor
            scope.launch { if (App.tryUnlock(q, silent = true)) query = "" }
        }
    }

    val e = editing
    if (e != null) {
        NoteEditor(e, onDone = { text ->
            val t = text.trim()
            notes = (if (t.isEmpty()) notes.filterNot { it.id == e.id }
            else listOf(DNote(e.id, t, if (t == e.text) e.changed else System.currentTimeMillis())) + notes.filterNot { it.id == e.id })
                .sortedByDescending { it.changed }
            saveNotes(notes)
            editing = null
        }, onDelete = {
            notes = notes.filterNot { it.id == e.id }
            saveNotes(notes)
            editing = null
        })
        return
    }

    val shown = if (query.isBlank()) notes else notes.filter { it.text.contains(query.trim(), ignoreCase = true) }

    GlassBackground(notesBlobs) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                TopBar(onBack = null, horizontalPadding = 0.dp)
                Spacer(Modifier.height(14.dp))
                Text("Notizen", style = G.largeTitle)
                Text(if (notes.size == 1) "1 Notiz" else "${notes.size} Notizen", style = G.footnote)
                Spacer(Modifier.height(12.dp))
                // Suchfeld
                Row(
                    Modifier.fillMaxWidth().height(46.dp).glass(Pill, G.fill).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Ic.search, null, tint = G.dim, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Suchen", style = G.body.copy(color = G.faint, fontSize = 16.sp))
                        BasicTextField(
                            value = query, onValueChange = { query = it.take(60) }, singleLine = true,
                            textStyle = G.body.copy(fontSize = 16.sp), cursorBrush = SolidColor(NOTE_YELLOW),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { search() }, onDone = { search() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (query.isNotEmpty()) {
                        Box(Modifier.size(32.dp).pressable(CircleShape) { query = "" }, contentAlignment = Alignment.Center) {
                            Icon(Ic.close, "Leeren", tint = G.dim, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            items(shown, key = { it.id }) { n ->
                val title = n.text.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: "Neue Notiz"
                val rest = n.text.lines().dropWhile { it.isBlank() }.drop(1).joinToString(" ") { it.trim() }.trim()
                Column(
                    Modifier.fillMaxWidth().pressable(sq(20)) { editing = n }.glass(sq(20)).padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(title, style = G.headline.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        noteDate(n.changed) + "  " + rest.ifEmpty { "Kein zusätzlicher Text" },
                        style = G.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (shown.isEmpty()) item {
                Text(
                    if (query.isBlank()) "Keine Notizen" else "Keine Treffer",
                    style = G.callout.copy(color = G.dim), modifier = Modifier.padding(top = 24.dp)
                )
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(24.dp).size(58.dp)
                .pressable(CircleShape, scaleTo = 0.9f, highlight = 0f, haptic = Haptic.CONFIRM) {
                    editing = DNote(System.nanoTime(), "", System.currentTimeMillis())
                }
                .clip(CircleShape).background(NOTE_YELLOW),
            contentAlignment = Alignment.Center
        ) { Icon(Ic.edit, "Neue Notiz", tint = Color(0xFF2A2000), modifier = Modifier.size(24.dp)) }
    }
}

@Composable
private fun NoteEditor(note: DNote, onDone: (String) -> Unit, onDelete: () -> Unit) {
    var text by remember(note.id) { mutableStateOf(note.text) }
    BackHandler { onDone(text) }
    GlassBackground(notesBlobs) {
        Column(Modifier.fillMaxSize().imePadding()) {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    Modifier.pressable(Pill) { onDone(text) }.padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Ic.back, null, tint = NOTE_YELLOW, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Notizen", style = G.body.copy(color = NOTE_YELLOW, fontSize = 16.sp))
                }
                Spacer(Modifier.weight(1f))
                if (note.text.isNotEmpty()) GlassIconButton(Ic.trash, "Löschen") { onDelete() }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.pressable(Pill) { onDone(text) }.padding(horizontal = 12.dp, vertical = 8.dp)
                ) { Text("Fertig", style = G.body.copy(color = NOTE_YELLOW, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)) }
            }
            Text(
                noteDate(note.changed),
                style = G.footnote, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
                if (text.isEmpty()) Text("Schreib etwas …", style = G.body.copy(color = G.faint))
                BasicTextField(
                    value = text, onValueChange = { text = it },
                    textStyle = G.body.copy(lineHeight = 24.sp), cursorBrush = SolidColor(NOTE_YELLOW),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp)
                )
            }
        }
    }
}
