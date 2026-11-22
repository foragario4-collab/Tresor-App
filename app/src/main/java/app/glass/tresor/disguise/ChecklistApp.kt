package app.glass.tresor.disguise

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glass.tresor.App
import app.glass.tresor.ui.Blob
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
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

private val listBlobs = listOf(
    Blob(Color(0xFF2F9E5B), -0.1f, 0.05f, 0.95f, 0.35f),
    Blob(Color(0xFF14B8A6), 1.1f, 0.8f, 0.9f, 0.3f),
)

private data class Todo(val id: Long, val text: String, val done: Boolean)

private fun loadTodos(): List<Todo> = try {
    val arr = JSONArray(App.dprefs.getString("todos", "[]"))
    (0 until arr.length()).map { val o = arr.getJSONObject(it); Todo(o.getLong("i"), o.getString("t"), o.getBoolean("d")) }
} catch (e: Exception) {
    emptyList()
}

private fun saveTodos(l: List<Todo>) {
    val arr = JSONArray()
    l.forEach { arr.put(JSONObject().put("i", it.id).put("t", it.text).put("d", it.done)) }
    App.dprefs.edit().putString("todos", arr.toString()).apply()
}

/** Funktionierende Checkliste. Geheim: deinen Code als Eintrag eingeben. */
@Composable
fun ChecklistApp() {
    val scope = rememberCoroutineScope()
    var todos by remember { mutableStateOf(loadTodos()) }
    var input by remember { mutableStateOf("") }

    fun add() {
        val t = input.trim()
        if (t.isEmpty()) return
        input = ""
        if (t.length in 4..12 && t.all { it.isDigit() }) {
            // Könnte der Code sein – still prüfen. Klappt es nicht, wird ganz normal ein Eintrag angelegt.
            scope.launch {
                val ok = App.tryUnlock(t, silent = true)
                if (!ok) { todos = listOf(Todo(System.nanoTime(), t, false)) + todos; saveTodos(todos) }
            }
            return
        }
        todos = listOf(Todo(System.nanoTime(), t, false)) + todos
        saveTodos(todos)
    }

    GlassBackground(listBlobs) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                TopBar(onBack = null, horizontalPadding = 0.dp)
                Spacer(Modifier.height(14.dp))
                Text("Checkliste", style = G.largeTitle)
                val open = todos.count { !it.done }
                Text(if (open == 1) "1 offen" else "$open offen", style = G.footnote)
                Spacer(Modifier.height(10.dp))
            }
            items(todos, key = { it.id }) { t ->
                Box(Modifier.animateItem()) {
                    TodoRow(t,
                        onToggle = { todos = todos.map { if (it.id == t.id) it.copy(done = !it.done) else it }; saveTodos(todos) },
                        onDelete = { todos = todos.filterNot { it.id == t.id }; saveTodos(todos) })
                }
            }
            if (todos.isEmpty()) item {
                Text("Alles erledigt", style = G.callout.copy(color = G.dim), modifier = Modifier.padding(top = 24.dp))
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .height(56.dp)
                .glass(Pill, G.fillStrong)
                .padding(start = 20.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f)) {
                if (input.isEmpty()) Text("Neuer Eintrag", style = G.body.copy(color = G.faint))
                BasicTextField(
                    value = input, onValueChange = { input = it }, singleLine = true,
                    textStyle = G.body, cursorBrush = SolidColor(G.accentText),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { add() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Box(
                Modifier.size(44.dp).pressable(CircleShape, scaleTo = 0.9f, highlight = 0f, haptic = Haptic.CONFIRM) { add() }
                    .clip(CircleShape).background(Color(0xFF2F9E5B)),
                contentAlignment = Alignment.Center
            ) { Icon(Ic.plus, "Hinzufügen", tint = Color.White, modifier = Modifier.size(22.dp)) }
        }
    }
}

@Composable
private fun TodoRow(t: Todo, onToggle: () -> Unit, onDelete: () -> Unit) {
    val ring by animateColorAsState(if (t.done) Color(0xFF2F9E5B) else Color.White.copy(alpha = 0.5f), label = "ring")
    val check by animateFloatAsState(if (t.done) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "chk")
    Row(
        Modifier.fillMaxWidth().pressable(sq(20), haptic = Haptic.CONFIRM, onLongClick = onDelete, onClick = onToggle)
            .glass(sq(20)).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp).clip(CircleShape).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
            Box(
                Modifier.size(24.dp).graphicsLayer { scaleX = check; scaleY = check }.clip(CircleShape).background(Color(0xFF2F9E5B)),
                contentAlignment = Alignment.Center
            ) { Icon(Ic.check, null, tint = Color.White, modifier = Modifier.size(13.dp)) }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            t.text,
            style = G.body.copy(
                fontSize = 16.sp,
                color = if (t.done) G.faint else Color.White,
                textDecoration = if (t.done) TextDecoration.LineThrough else null
            )
        )
    }
}
