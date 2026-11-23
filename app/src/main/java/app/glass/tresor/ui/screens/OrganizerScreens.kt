package app.glass.tresor.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.glass.tresor.App
import app.glass.tresor.Screen
import app.glass.tresor.chat.VoicePlayer
import app.glass.tresor.chat.VoiceRecorder
import app.glass.tresor.ui.ConfirmSheet
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassField
import app.glass.tresor.ui.GlassIconButton
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.PillButton
import app.glass.tresor.ui.SectionLabel
import app.glass.tresor.ui.Sheet
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq
import app.glass.tresor.vault.CalEvent
import app.glass.tresor.vault.Memo
import app.glass.tresor.vault.Organizer
import app.glass.tresor.vault.PwEntry
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ───────────────────────── Gemeinsames ─────────────────────────

@Composable
private fun OrgPage(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(WALL)) {
        Wallpaper()
        content()
    }
}

@Composable
private fun OrgHeader(trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        GlassIconButton(Ic.back, "Zurück") { App.nav.pop() }
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun OrgEmpty(title: String, text: String) {
    Column(Modifier.fillMaxWidth().glass(sq(22)).padding(18.dp)) {
        Text(title, style = G.headline.copy(fontSize = 16.sp))
        Spacer(Modifier.height(4.dp))
        Text(text, style = G.callout.copy(color = G.dim))
    }
}

@Composable
private fun OrgDivider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))

@Composable
private fun TextAction(text: String, bold: Boolean = false, onClick: () -> Unit) {
    Text(
        text, style = G.body.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold, color = Color.White),
        modifier = Modifier.pressable(Pill) { onClick() }.padding(horizontal = 8.dp, vertical = 10.dp)
    )
}

/** Geheimes in die Zwischenablage: als „sensibel“ markiert und nach 45 Sekunden wieder gelöscht. */
private fun copySecret(ctx: Context, label: String, text: String) {
    if (text.isEmpty()) return
    val cm = ctx.getSystemService(ClipboardManager::class.java) ?: return
    val clip = ClipData.newPlainText(label, text)
    clip.description.extras = PersistableBundle().apply { putBoolean("android.content.extra.IS_SENSITIVE", true) }
    cm.setPrimaryClip(clip)
    App.toast("$label kopiert – wird in 45 Sekunden gelöscht")
    Handler(Looper.getMainLooper()).postDelayed({
        try {
            val cur = try { cm.primaryClip } catch (e: Exception) { null }
            val same = cur == null || (cur.itemCount > 0 && cur.getItemAt(0).text?.toString() == text)
            if (same) cm.clearPrimaryClip()
        } catch (_: Exception) {
        }
    }, 45_000)
}

// ───────────────────────── Passwörter ─────────────────────────

@Composable
private fun PwTile(title: String, size: Int) {
    val col = contactColor(title.ifBlank { "?" })
    Box(
        Modifier.size(size.dp).clip(sq((size * 0.26f).toInt()))
            .background(Brush.linearGradient(listOf(col, col.copy(alpha = 0.65f))))
            .border(1.dp, Color.White.copy(alpha = 0.25f), sq((size * 0.26f).toInt())),
        contentAlignment = Alignment.Center
    ) { Text(title.trim().take(1).uppercase().ifEmpty { "•" }, style = G.headline.copy(fontSize = (size * 0.45f).sp, color = Color.White)) }
}

private fun pwHost(url: String): String =
    url.trim().removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')

@Composable
fun PasswordsScreen() {
    val vault = App.vault ?: return
    val list = remember(vault) { mutableStateListOf<PwEntry>().apply { addAll(Organizer.passwords(vault)) } }
    // Beim Zurückkommen aus dem Eintrag neu laden
    LaunchedEffect(App.nav.top) { list.clear(); list.addAll(Organizer.passwords(vault)) }
    var query by remember { mutableStateOf("") }
    val shown = if (query.isBlank()) list else list.filter { (it.title + " " + it.user + " " + it.url).contains(query.trim(), ignoreCase = true) }

    OrgPage {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            OrgHeader { GlassIconButton(Ic.plus, "Neues Passwort") { App.nav.push(Screen.PasswordEntry(null)) } }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Passwörter", style = G.largeTitle)
                Spacer(Modifier.height(12.dp))
                SearchPill(query) { query = it }
                Spacer(Modifier.height(14.dp))
                if (list.isEmpty()) {
                    OrgEmpty("Noch keine Passwörter", "Oben auf + tippen. Alles liegt verschlüsselt nur in diesem Tresor – keine Cloud, kein Konto. Auf Wunsch erzeugt die App ein starkes Passwort für dich.")
                } else if (shown.isEmpty()) {
                    Text("Keine Treffer", style = G.callout.copy(color = G.dim), modifier = Modifier.padding(top = 8.dp, start = 6.dp))
                } else {
                    val groups = shown.groupBy { it.title.trim().take(1).uppercase().let { l -> if (l.isNotEmpty() && l[0].isLetter()) l else "#" } }
                    groups.forEach { (letter, items) ->
                        Text(letter, style = G.footnote.copy(fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f)), modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 6.dp))
                        Column(Modifier.fillMaxWidth().glass(sq(20)).padding(horizontal = 14.dp, vertical = 2.dp)) {
                            items.forEachIndexed { i, e ->
                                Row(
                                    Modifier.fillMaxWidth().pressable(sq(12)) { App.nav.push(Screen.PasswordEntry(e.id)) }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PwTile(e.title, 38)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(e.title.ifBlank { pwHost(e.url) }, style = G.headline.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(e.user.ifEmpty { pwHost(e.url).ifEmpty { "–" } }, style = G.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Icon(Ic.forward, null, tint = G.faint, modifier = Modifier.size(14.dp))
                                }
                                if (i < items.size - 1) OrgDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchPill(query: String, onChange: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).glass(Pill).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Ic.search, null, tint = G.dim, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Suchen", style = G.body.copy(fontSize = 16.sp, color = G.faint))
            app.glass.tresor.ui.TresorTextField(
                value = query, onValueChange = { onChange(it.take(40)) }, singleLine = true,
                textStyle = G.body.copy(fontSize = 16.sp), modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Box(Modifier.size(28.dp).pressable(CircleShape, haptic = Haptic.NONE) { onChange("") }, contentAlignment = Alignment.Center) {
                Icon(Ic.close, "Leeren", tint = G.dim, modifier = Modifier.size(14.dp))
            }
        }
    }
}

private val STRENGTH = listOf(
    "Sehr schwach" to Color(0xFFFF6B70), "Schwach" to Color(0xFFFF9F5A), "Mittel" to Color(0xFFFFD34D),
    "Stark" to Color(0xFF7DDC8A), "Sehr stark" to Color(0xFF3FD69B)
)

@Composable
private fun StrengthBar(pw: String) {
    val s = Organizer.strength(pw)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0..4) Box(Modifier.weight(1f).height(4.dp).clip(Pill).background(if (pw.isNotEmpty() && i <= s) STRENGTH[s].second else Color.White.copy(alpha = 0.15f)))
        }
        Spacer(Modifier.width(10.dp))
        Text(if (pw.isEmpty()) "–" else STRENGTH[s].first, style = G.footnote.copy(color = if (pw.isEmpty()) G.dim else STRENGTH[s].second, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun PasswordEntryScreen(id: String?) {
    val vault = App.vault ?: return
    val ctx = LocalContext.current
    val existing = remember(id) { id?.let { i -> Organizer.passwords(vault).firstOrNull { it.id == i } } }
    var entry by remember { mutableStateOf(existing) }
    var editing by remember { mutableStateOf(existing == null) }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var user by remember { mutableStateOf(existing?.user ?: "") }
    var pass by remember { mutableStateOf(existing?.pass ?: "") }
    var url by remember { mutableStateOf(existing?.url ?: "") }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    var show by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun save() {
        val t = title.trim().ifEmpty { pwHost(url) }
        if (t.isEmpty()) { App.toast("Bitte einen Titel oder eine Website eingeben"); return }
        val e = PwEntry(entry?.id ?: Organizer.newId(), t.take(60), user.trim().take(120), pass.take(200), url.trim().take(300), note.trim().take(2000))
        try {
            Organizer.putPassword(vault, e)
        } catch (x: Exception) {
            App.toast("Speichern fehlgeschlagen"); return
        }
        entry = e
        title = e.title
        editing = false
        App.toast("Gesichert")
    }

    OrgPage {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing && entry != null) TextAction("Abbrechen") {
                    entry?.let { e -> title = e.title; user = e.user; pass = e.pass; url = e.url; note = e.note }
                    editing = false
                } else GlassIconButton(Ic.back, "Zurück") { App.nav.pop() }
                Spacer(Modifier.weight(1f))
                if (editing) TextAction("Sichern", bold = true) { save() } else TextAction("Bearbeiten") { editing = true }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PwTile(title.ifBlank { pwHost(url) }, 72)
                Spacer(Modifier.height(10.dp))
                if (!editing) {
                    val e = entry ?: return@Column
                    Text(e.title, style = G.title2, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    if (e.url.isNotEmpty()) Text(pwHost(e.url), style = G.callout.copy(color = G.dim), maxLines = 1)
                    Spacer(Modifier.height(18.dp))
                    Column(Modifier.fillMaxWidth().glass(sq(22)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                        SecretRow("Benutzername", e.user.ifEmpty { "–" }, mono = false, actions = {
                            if (e.user.isNotEmpty()) SmallIcon(Ic.copy, "Benutzername kopieren") { copySecret(ctx, "Benutzername", e.user) }
                        })
                        OrgDivider()
                        SecretRow("Passwort", if (e.pass.isEmpty()) "–" else if (show) e.pass else "•".repeat(minOf(e.pass.length, 16)), mono = show, actions = {
                            if (e.pass.isNotEmpty()) {
                                SmallIcon(if (show) Ic.eyeOff else Ic.eye, if (show) "Verbergen" else "Anzeigen") { show = !show }
                                SmallIcon(Ic.copy, "Passwort kopieren") { copySecret(ctx, "Passwort", e.pass) }
                            }
                        })
                        if (e.pass.isNotEmpty()) Box(Modifier.padding(bottom = 12.dp)) { StrengthBar(e.pass) }
                        if (e.url.isNotEmpty()) {
                            OrgDivider()
                            SecretRow("Website", e.url, mono = false, actions = { SmallIcon(Ic.copy, "Website kopieren") { copySecret(ctx, "Website", e.url) } })
                        }
                        if (e.note.isNotEmpty()) {
                            OrgDivider()
                            SecretRow("Notiz", e.note, mono = false, actions = {})
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Geändert am " + SimpleDateFormat("d. MMMM yyyy, HH:mm", Locale.GERMANY).format(Date(e.modified)),
                        style = G.footnote.copy(color = G.faint), modifier = Modifier.fillMaxWidth().padding(start = 6.dp)
                    )
                    Spacer(Modifier.height(20.dp))
                    PillButton("Passwort löschen", textColor = G.red, modifier = Modifier.fillMaxWidth()) { confirmDelete = true }
                } else {
                    Text(if (entry == null) "Neues Passwort" else "Bearbeiten", style = G.title2)
                    Spacer(Modifier.height(16.dp))
                    GlassField(title, { title = it.take(60) }, "Titel (z. B. Bank, E-Mail)", autoFocus = entry == null)
                    Spacer(Modifier.height(10.dp))
                    GlassField(user, { user = it.take(120) }, "Benutzername oder E-Mail", keyboardType = KeyboardType.Email)
                    Spacer(Modifier.height(10.dp))
                    GlassField(pass, { pass = it.take(200) }, "Passwort", password = true)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { StrengthBar(pass) }
                        Spacer(Modifier.width(12.dp))
                        PillButton("Erzeugen", icon = Ic.refresh, height = 38.dp) { pass = Organizer.generatePassword(); App.toast("Starkes Passwort erzeugt – zum Ansehen auf das Auge tippen") }
                    }
                    Spacer(Modifier.height(10.dp))
                    GlassField(url, { url = it.take(300) }, "Website (optional)", keyboardType = KeyboardType.Uri)
                    Spacer(Modifier.height(10.dp))
                    GlassField(note, { note = it.take(2000) }, "Notiz (optional)", singleLine = false)
                    Spacer(Modifier.height(18.dp))
                    PillButton("Sichern", primary = true, modifier = Modifier.fillMaxWidth()) { save() }
                }
            }
        }
        ConfirmSheet(
            visible = confirmDelete, title = "Passwort löschen?", text = "„${entry?.title ?: ""}“ wird endgültig aus dem Tresor entfernt.",
            confirm = "Löschen", onDismiss = { confirmDelete = false }
        ) {
            confirmDelete = false
            entry?.let { Organizer.deletePassword(vault, it.id) }
            App.nav.pop()
        }
    }
}

@Composable
private fun SecretRow(label: String, value: String, mono: Boolean, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = G.footnote)
            Spacer(Modifier.height(2.dp))
            Text(value, style = G.body.copy(fontSize = 16.sp, fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default))
        }
        actions()
    }
}

@Composable
private fun SmallIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(start = 6.dp).size(36.dp).pressable(CircleShape, scaleTo = 0.9f) { onClick() }.clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(17.dp)) }
}

// ───────────────────────── Kalender ─────────────────────────

private val EVENT_COLORS = listOf(Color(0xFF5B9CFF), Color(0xFFFF6B70), Color(0xFF3FBF8F), Color(0xFFFFA94D), Color(0xFFA77BFF), Color(0xFFFF5E8A))
private val WD_HEAD = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
private fun dayKey(c: Calendar) = "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
private fun parseDay(k: String): Calendar = Calendar.getInstance().apply {
    clear()
    val p = k.split('-')
    set(p[0].toInt(), p[1].toInt() - 1, p[2].toInt())
}

@Composable
fun CalendarScreen() {
    val vault = App.vault ?: return
    val today = remember { dayKey(Calendar.getInstance()) }
    val events = remember(vault) { mutableStateListOf<CalEvent>().apply { addAll(Organizer.events(vault)) } }
    var month by remember { mutableStateOf(Calendar.getInstance().let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }) }
    var selected by remember { mutableStateOf(today) }
    var editing by remember { mutableStateOf<CalEvent?>(null) }
    var sheet by remember { mutableStateOf(false) }

    fun reload() { events.clear(); events.addAll(Organizer.events(vault)) }
    fun shiftMonth(d: Int) { month += d }
    val days = events.map { it.date }.toSet()

    OrgPage {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            OrgHeader {
                GlassIconButton(Ic.plus, "Neuer Termin") { editing = null; sheet = true }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                val y = month / 12
                val m = month % 12
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(MONTHS[m], style = G.largeTitle, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                    Text("$y", style = G.largeTitle.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = G.dim), modifier = Modifier.padding(bottom = 4.dp))
                    Spacer(Modifier.weight(1f))
                    GlassIconButton(Ic.back, "Voriger Monat", size = 38.dp, iconSize = 16.dp) { shiftMonth(-1) }
                    Spacer(Modifier.width(8.dp))
                    GlassIconButton(Ic.forward, "Nächster Monat", size = 38.dp, iconSize = 16.dp) { shiftMonth(1) }
                }
                Spacer(Modifier.height(14.dp))
                // Monatsraster (wischen = Monat wechseln)
                Column(
                    Modifier.fillMaxWidth().glass(sq(26)).padding(horizontal = 8.dp, vertical = 12.dp)
                        .pointerInput(Unit) {
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = { if (total > 80f) shiftMonth(-1) else if (total < -80f) shiftMonth(1) },
                            ) { change, dx -> total += dx; change.consume() }
                        }
                ) {
                    Row(Modifier.fillMaxWidth()) {
                        WD_HEAD.forEachIndexed { i, d ->
                            Text(d, style = G.caption.copy(color = if (i >= 5) G.faint else G.dim), textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    val first = Calendar.getInstance().apply { clear(); set(y, m, 1) }
                    val lead = (first.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Montag = 0
                    val start = (first.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, -lead) }
                    for (w in 0 until 6) {
                        Row(Modifier.fillMaxWidth()) {
                            for (d in 0 until 7) {
                                val c = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, w * 7 + d) }
                                val k = dayKey(c)
                                val inMonth = c.get(Calendar.MONTH) == m
                                val isSel = k == selected
                                val isToday = k == today
                                Column(
                                    Modifier.weight(1f).height(46.dp).pressable(CircleShape, scaleTo = 0.9f, haptic = Haptic.TICK) {
                                        selected = k
                                        if (!inMonth) month = c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
                                    },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        Modifier.size(34.dp).clip(CircleShape).background(
                                            when {
                                                isSel -> Color.White
                                                isToday -> G.accent
                                                else -> Color.Transparent
                                            }
                                        ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${c.get(Calendar.DAY_OF_MONTH)}",
                                            style = G.body.copy(
                                                fontSize = 16.sp,
                                                fontWeight = if (isSel || isToday) FontWeight.Bold else FontWeight.Normal,
                                                color = when {
                                                    isSel -> Color(0xFF141838)
                                                    !inMonth -> Color.White.copy(alpha = 0.3f)
                                                    else -> Color.White
                                                }
                                            )
                                        )
                                    }
                                    Spacer(Modifier.height(3.dp))
                                    Box(Modifier.size(5.dp).clip(CircleShape).background(if (k in days) Color.White.copy(alpha = if (inMonth) 0.85f else 0.3f) else Color.Transparent))
                                }
                            }
                        }
                    }
                }
                if (selected != today || month != Calendar.getInstance().let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }) {
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        PillButton("Heute", height = 36.dp) {
                            selected = today
                            month = Calendar.getInstance().let { it.get(Calendar.YEAR) * 12 + it.get(Calendar.MONTH) }
                        }
                    }
                }
                val sc = parseDay(selected)
                SectionLabel("${WEEKDAYS[sc.get(Calendar.DAY_OF_WEEK) - 1]}, ${sc.get(Calendar.DAY_OF_MONTH)}. ${MONTHS[sc.get(Calendar.MONTH)]}", Modifier.padding(top = 10.dp))
                val dayEvents = events.filter { it.date == selected }
                if (dayEvents.isEmpty()) {
                    Row(
                        Modifier.fillMaxWidth().pressable(sq(22)) { editing = null; sheet = true }.glass(sq(22)).padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Keine Termine", style = G.callout.copy(color = G.dim), modifier = Modifier.weight(1f))
                        Icon(Ic.plus, "Termin hinzufügen", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Column(Modifier.fillMaxWidth().glass(sq(22)).padding(horizontal = 14.dp, vertical = 2.dp)) {
                        dayEvents.forEachIndexed { i, e ->
                            Row(
                                Modifier.fillMaxWidth().pressable(sq(12)) { editing = e; sheet = true }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.width(4.dp).height(36.dp).clip(Pill).background(EVENT_COLORS[e.color.coerceIn(0, EVENT_COLORS.size - 1)]))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, style = G.headline.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        (if (e.time.isEmpty()) "Ganztägig" else e.time + " Uhr") + if (e.note.isNotEmpty()) " · " + e.note.lineSequence().first() else "",
                                        style = G.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (i < dayEvents.size - 1) OrgDivider()
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Termine liegen verschlüsselt nur in diesem Tresor. Es gibt bewusst keine Erinnerungen – eine Mitteilung könnte verraten, was im Tresor steht.", style = G.footnote.copy(color = G.faint), modifier = Modifier.padding(horizontal = 6.dp))
            }
        }
        EventSheet(
            visible = sheet,
            event = editing,
            date = selected,
            onDismiss = { sheet = false },
            onSave = { e ->
                try { Organizer.putEvent(vault, e) } catch (x: Exception) { App.toast("Speichern fehlgeschlagen") }
                reload(); selected = e.date; sheet = false
            },
            onDelete = { e ->
                Organizer.deleteEvent(vault, e.id); reload(); sheet = false
            }
        )
    }
}

@Composable
private fun BoxScope.EventSheet(visible: Boolean, event: CalEvent?, date: String, onDismiss: () -> Unit, onSave: (CalEvent) -> Unit, onDelete: (CalEvent) -> Unit) {
    var title by remember(visible, event) { mutableStateOf(event?.title ?: "") }
    var time by remember(visible, event) { mutableStateOf(event?.time?.replace(":", "") ?: "") }
    var note by remember(visible, event) { mutableStateOf(event?.note ?: "") }
    var color by remember(visible, event) { mutableStateOf(event?.color ?: 0) }
    var day by remember(visible, event, date) { mutableStateOf(event?.date ?: date) }
    Sheet(visible, onDismiss) {
        Text(if (event == null) "Neuer Termin" else "Termin", style = G.title2, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        val dc = parseDay(day)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            GlassIconButton(Ic.back, "Tag zurück", size = 34.dp, iconSize = 14.dp) { day = dayKey((parseDay(day)).apply { add(Calendar.DAY_OF_MONTH, -1) }) }
            Text(
                "${WEEKDAYS[dc.get(Calendar.DAY_OF_WEEK) - 1]}, ${dc.get(Calendar.DAY_OF_MONTH)}. ${MONTHS[dc.get(Calendar.MONTH)]} ${dc.get(Calendar.YEAR)}",
                style = G.callout.copy(fontWeight = FontWeight.SemiBold), textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
            )
            GlassIconButton(Ic.forward, "Tag vor", size = 34.dp, iconSize = 14.dp) { day = dayKey((parseDay(day)).apply { add(Calendar.DAY_OF_MONTH, 1) }) }
        }
        Spacer(Modifier.height(14.dp))
        GlassField(title, { title = it.take(80) }, "Titel", autoFocus = visible && event == null)
        Spacer(Modifier.height(10.dp))
        GlassField(
            time, { time = it.filter { c -> c.isDigit() }.take(4) },
            "Uhrzeit, z. B. 1830 (leer = ganztägig)", numeric = true
        )
        val norm = Organizer.normTime(time)
        if (time.isNotEmpty()) Text(
            if (norm == null) "Ungültige Uhrzeit" else "$norm Uhr",
            style = G.footnote.copy(color = if (norm == null) G.red else G.dim), modifier = Modifier.padding(start = 20.dp, top = 4.dp)
        )
        Spacer(Modifier.height(10.dp))
        GlassField(note, { note = it.take(500) }, "Notiz (optional)", singleLine = false)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            EVENT_COLORS.forEachIndexed { i, c ->
                Box(
                    Modifier.padding(horizontal = 6.dp).size(30.dp).pressable(CircleShape, scaleTo = 0.85f) { color = i }
                        .clip(CircleShape).background(c)
                        .border(if (color == i) 3.dp else 0.dp, Color.White, CircleShape)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        PillButton("Sichern", primary = true, modifier = Modifier.fillMaxWidth()) {
            val t = title.trim()
            val n = Organizer.normTime(time)
            when {
                t.isEmpty() -> App.toast("Bitte einen Titel eingeben")
                n == null -> App.toast("Uhrzeit bitte als 4 Ziffern, z. B. 0930")
                else -> onSave(CalEvent(event?.id ?: Organizer.newId(), t, day, n, note.trim(), color))
            }
        }
        if (event != null) {
            Spacer(Modifier.height(10.dp))
            PillButton("Termin löschen", textColor = G.red, modifier = Modifier.fillMaxWidth()) { onDelete(event) }
        }
    }
}

// ───────────────────────── Sprachmemos ─────────────────────────

private const val MEMO_MAX_MS = 30 * 60_000L

private fun fmtDur(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun MemosScreen() {
    val vault = App.vault ?: return
    val ctx = LocalContext.current
    val memos = remember(vault) { mutableStateListOf<Memo>().apply { addAll(Organizer.memos(vault)) } }
    val recorder = remember { VoiceRecorder(ctx, MEMO_MAX_MS, "memo.tmp") }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }
    val levels = remember { mutableStateListOf<Float>() }
    var open by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<Memo?>(null) }
    var newName by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<Memo?>(null) }

    fun reload() { memos.clear(); memos.addAll(Organizer.memos(vault)) }
    fun stopAndSave() {
        if (!recording) return
        recording = false
        val r = recorder.stop()
        if (r == null) { App.toast("Aufnahme zu kurz"); return }
        try {
            val m = Organizer.addMemo(vault, r.first, r.second)
            reload()
            open = m.id
            App.toast("„${m.name}“ gesichert")
        } catch (e: Exception) {
            App.toast("Speichern fehlgeschlagen")
        }
    }
    fun start() {
        VoicePlayer.stop()
        if (recorder.start()) { recording = true; levels.clear(); elapsed = 0L } else App.toast("Mikrofon nicht verfügbar")
    }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        App.suppressLock = false
        if (ok) start() else App.toast("Mikrofon ist nicht erlaubt")
    }
    // Beim Sperren: laufende Aufnahme noch schnell verschlüsselt sichern
    DisposableEffect(Unit) {
        val hook: () -> Unit = { if (recording) stopAndSave() }
        App.beforeLock.add(hook)
        onDispose {
            App.beforeLock.remove(hook)
            if (recording) { recording = false; recorder.cancel() }
            VoicePlayer.stop()
        }
    }
    LaunchedEffect(recording) {
        while (recording) {
            elapsed = recorder.elapsed
            levels.add(recorder.level())
            if (levels.size > 48) levels.removeAt(0)
            if (recorder.limitReached || elapsed >= MEMO_MAX_MS) { stopAndSave(); break }
            delay(90)
        }
    }

    OrgPage {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            OrgHeader()
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                Text("Sprachmemos", style = G.largeTitle)
                Spacer(Modifier.height(14.dp))
                if (memos.isEmpty()) {
                    OrgEmpty("Noch keine Aufnahmen", "Unten auf den roten Knopf tippen und sprechen. Die Aufnahme liegt danach nur verschlüsselt im Tresor – nie in einer normalen Datei.")
                } else Column(Modifier.fillMaxWidth().glass(sq(22)).padding(horizontal = 14.dp, vertical = 2.dp)) {
                    memos.forEachIndexed { i, m ->
                        MemoRow(
                            m, expanded = open == m.id,
                            onToggleOpen = { open = if (open == m.id) null else m.id },
                            onPlay = { VoicePlayer.toggle("memo-" + m.id) { Organizer.memoAudio(vault, m.id) } },
                            onRename = { renaming = m; newName = m.name },
                            onDelete = { deleting = m },
                        )
                        if (i < memos.size - 1) OrgDivider()
                    }
                }
            }
            // Aufnahme-Leiste
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp).glass(RoundedCornerShape(32.dp)).padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (recording) {
                    Text(fmtDur(elapsed) + ",${(elapsed / 100) % 10}", style = G.title2.copy(fontSize = 26.sp, fontFamily = FontFamily.Monospace))
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.height(36.dp).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (i in 0 until 48) {
                            val lv = levels.getOrNull(i - (48 - levels.size)) ?: 0f
                            Box(Modifier.weight(1f).fillMaxHeight(0.08f + 0.92f * kotlin.math.sqrt(lv)).clip(Pill).background(Color(0xFFFF5E5E)))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                } else {
                    Text("Bis zu 30 Minuten", style = G.footnote)
                    Spacer(Modifier.height(10.dp))
                }
                Box(
                    Modifier.size(72.dp).pressable(CircleShape, scaleTo = 0.9f, haptic = Haptic.CONFIRM) {
                        if (recording) stopAndSave()
                        else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                        else { App.suppressLock = true; perm.launch(Manifest.permission.RECORD_AUDIO) }
                    }.border(4.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (recording) Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFFFF3B30)))
                    else Box(Modifier.size(58.dp).clip(CircleShape).background(Color(0xFFFF3B30)))
                }
                Spacer(Modifier.height(6.dp))
                Text(if (recording) "Stopp – wird gesichert" else "Aufnehmen", style = G.footnote.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
            }
        }
        Sheet(renaming != null, { renaming = null }) {
            Text("Umbenennen", style = G.title2, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(14.dp))
            GlassField(newName, { newName = it.take(60) }, "Name", autoFocus = renaming != null, onDone = {
                renaming?.let { Organizer.renameMemo(vault, it.id, newName); reload() }
                renaming = null
            })
            Spacer(Modifier.height(14.dp))
            PillButton("Sichern", primary = true, modifier = Modifier.fillMaxWidth()) {
                renaming?.let { Organizer.renameMemo(vault, it.id, newName); reload() }
                renaming = null
            }
        }
        ConfirmSheet(
            visible = deleting != null, title = "Aufnahme löschen?", text = "„${deleting?.name ?: ""}“ wird endgültig entfernt.",
            confirm = "Löschen", onDismiss = { deleting = null }
        ) {
            deleting?.let {
                if (VoicePlayer.playingId == "memo-" + it.id) VoicePlayer.stop()
                Organizer.deleteMemo(vault, it.id)
                reload()
            }
            deleting = null
        }
    }
}

@Composable
private fun MemoRow(m: Memo, expanded: Boolean, onToggleOpen: () -> Unit, onPlay: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val playing = VoicePlayer.playingId == "memo-" + m.id
    var pos by remember { mutableStateOf(0L) }
    var prog by remember { mutableStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) { prog = 0f; pos = 0L }
        while (playing) {
            prog = VoicePlayer.progress(); pos = VoicePlayer.position()
            delay(100)
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().pressable(sq(12)) { onToggleOpen() }.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(m.name, style = G.headline.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(SimpleDateFormat("d. MMM yyyy, HH:mm", Locale.GERMANY).format(Date(m.ts)), style = G.footnote, maxLines = 1)
            }
            Text(fmtDur(m.dur), style = G.footnote.copy(fontFamily = FontFamily.Monospace))
        }
        if (expanded) {
            Box(Modifier.fillMaxWidth().height(4.dp).clip(Pill).background(Color.White.copy(alpha = 0.15f))) {
                Box(Modifier.fillMaxWidth(prog.coerceIn(0f, 1f)).fillMaxHeight().clip(Pill).background(Color.White))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(fmtDur(pos), style = G.footnote.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace))
                Spacer(Modifier.weight(1f))
                Text("-" + fmtDur((m.dur - pos).coerceAtLeast(0L)), style = G.footnote.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace))
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallIcon(Ic.edit, "Umbenennen") { onRename() }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(52.dp).pressable(CircleShape, scaleTo = 0.9f, haptic = Haptic.TICK) { onPlay() }.clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center
                ) { Icon(if (playing) Ic.pause else Ic.play, if (playing) "Pause" else "Abspielen", tint = Color(0xFF141838), modifier = Modifier.size(24.dp)) }
                Spacer(Modifier.weight(1f))
                SmallIcon(Ic.trash, "Löschen") { onDelete() }
            }
        }
    }
}
