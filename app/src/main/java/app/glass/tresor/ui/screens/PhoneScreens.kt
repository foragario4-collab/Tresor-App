package app.glass.tresor.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import app.glass.tresor.App
import app.glass.tresor.Screen
import app.glass.tresor.chat.Calls
import app.glass.tresor.chat.ChatAccounts
import app.glass.tresor.chat.ChatEngine
import app.glass.tresor.chat.Contact
import app.glass.tresor.chat.Msg
import app.glass.tresor.ui.ConfirmSheet
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassField
import app.glass.tresor.ui.GlassIconButton
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.PillButton
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ───────────────────────── Gemeinsame Bausteine ─────────────────────────

private val AVATAR_COLORS = listOf(
    Color(0xFFFF7A59), Color(0xFF3FBF8F), Color(0xFFA77BFF), Color(0xFF5B9CFF), Color(0xFFE0A21B), Color(0xFFFF5E8A)
)

fun contactColor(name: String): Color = AVATAR_COLORS[(name.hashCode() and 0x7fffffff) % AVATAR_COLORS.size]

@Composable
fun ContactAvatar(name: String, size: Dp, fontSize: Int = (size.value * 0.42f).toInt()) {
    Box(
        Modifier.size(size).clip(CircleShape).background(contactColor(name))
            .border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
        contentAlignment = Alignment.Center
    ) { Text(name.trim().take(1).uppercase(), style = G.headline.copy(fontSize = fontSize.sp, color = Color.White)) }
}

/** Kurze Zeitangabe wie beim iPhone: Uhrzeit heute, „Gestern“, Wochentag, sonst Datum. */
fun shortTime(ts: Long): String {
    val now = Calendar.getInstance()
    val t = Calendar.getInstance().apply { timeInMillis = ts }
    val days = ((now.timeInMillis - ts) / 86_400_000L).toInt()
    return when {
        now.get(Calendar.YEAR) == t.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(ts))
        days <= 1 -> "Gestern"
        days < 7 -> SimpleDateFormat("EEE", Locale.GERMANY).format(Date(ts)).removeSuffix(".") + "."
        else -> SimpleDateFormat("d.M.yy", Locale.GERMANY).format(Date(ts))
    }
}

/** Anruf starten – vorher Mikrofon-Erlaubnis holen. */
@Composable
private fun rememberCaller(engine: ChatEngine): (Contact) -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf<Contact?>(null) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        App.suppressLock = false
        val c = pending
        pending = null
        if (!ok) App.toast("Mikrofon ist nicht erlaubt") else if (c != null) Calls.call(engine, c)
    }
    return { c ->
        when {
            !engine.callsAllowed(c) -> App.toast(
                if (!c.accepted) "${c.name} hat die Anfrage noch nicht angenommen"
                else if (c.paused) "Der Chat mit ${c.name} ruht gerade"
                else "Anrufe sind mit ${c.name} ausgeschaltet (Chat-Einstellungen)"
            )
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> Calls.call(engine, c)
            else -> { pending = c; App.suppressLock = true; perm.launch(Manifest.permission.RECORD_AUDIO) }
        }
    }
}

@Composable
private fun AppBackdrop(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(WALL)) {
        Wallpaper()
        content()
    }
}

@Composable
private fun NotSetUp(title: String) {
    AppBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { GlassIconButton(Ic.back, "Zurück") { App.nav.pop() } }
            Spacer(Modifier.height(20.dp))
            Text(title, style = G.largeTitle)
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().glass(sq(24)).padding(18.dp)) {
                Text("Noch nicht eingerichtet", style = G.headline)
                Spacer(Modifier.height(4.dp))
                Text("Telefon und Kontakte laufen über den Tor-Chat. Richte ihn einmal ein – ohne Nummer, ohne Konto.", style = G.callout.copy(color = G.dim))
                Spacer(Modifier.height(14.dp))
                PillButton("Chat einrichten", primary = true, modifier = Modifier.fillMaxWidth()) { App.nav.push(Screen.Chat) }
            }
        }
    }
}

@Composable
private fun TorDot() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF7DFFCF)))
        Spacer(Modifier.width(6.dp))
        Text("über Tor", style = G.footnote.copy(color = Color(0xFF7DFFCF), fontWeight = FontWeight.SemiBold))
    }
}

// ───────────────────────── Telefon ─────────────────────────

@Composable
fun PhoneScreen() {
    val vault = App.vault ?: return
    val engine = remember(vault, ChatAccounts.selected, vault.unlockedFolders.size) { ChatAccounts.engine(vault) }
    if (!engine.isReady) { NotSetUp("Telefon"); return }
    SyncLoop(engine)
    val call = rememberCaller(engine)
    var tab by remember { mutableStateOf(1) } // 0 Favoriten, 1 Zuletzt, 2 Kontakte, 3 Sprachnachrichten

    val contacts = engine.contacts.filter { !it.removedByPeer }
    val byId = contacts.associateBy { it.id }

    AppBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Ic.back, "Zurück") { App.nav.pop() }
                Spacer(Modifier.weight(1f))
                GlassIconButton(Ic.plus, "Kontakt hinzufügen") { App.nav.push(Screen.ChatAdd) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
                Text(listOf("Favoriten", "Telefon", "Kontakte", "Sprachnachrichten")[tab], style = G.largeTitle, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.padding(bottom = 8.dp)) { TorDot() }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
                when (tab) {
                    0 -> FavoritesTab(contacts.filter { it.fav && it.accepted }, call)
                    1 -> RecentsTab(engine, byId, call)
                    2 -> ContactList(contacts, onOpen = { App.nav.push(Screen.ContactDetail(it.id)) })
                    else -> VoiceTab(engine, byId)
                }
            }
            // Tab-Leiste
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp)
                    .glass(RoundedCornerShape(30.dp)).padding(6.dp)
            ) {
                val tabs = listOf(Ic.star to "Favoriten", Ic.clock to "Zuletzt", Ic.person to "Kontakte", Ic.voicemail to "Sprachnachr.")
                tabs.forEachIndexed { i, (icon, label) ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(24.dp))
                            .background(if (tab == i) Color.White.copy(alpha = 0.18f) else Color.Transparent)
                            .pressable(RoundedCornerShape(24.dp), haptic = Haptic.TICK) { tab = i }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(icon, null, tint = if (tab == i) Color.White else G.dim, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.height(3.dp))
                        Text(label, style = G.caption.copy(fontSize = 10.5.sp, letterSpacing = 0.sp, color = if (tab == i) Color.White else G.dim), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoritesTab(favs: List<Contact>, call: (Contact) -> Unit) {
    if (favs.isEmpty()) {
        EmptyCard("Noch keine Favoriten", "Öffne einen Kontakt und tippe auf den Stern. Favoriten rufst du hier mit einem Tipp an.")
        return
    }
    favs.chunked(4).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            row.forEach { c ->
                Column(
                    Modifier.weight(1f).pressable(sq(16), scaleTo = 0.92f) { call(c) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ContactAvatar(c.name, 64.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(c.name, style = G.footnote.copy(color = Color.White, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun RecentsTab(engine: ChatEngine, byId: Map<String, Contact>, call: (Contact) -> Unit) {
    val calls = engine.messages.filter { it.kind == Msg.KIND_CALL && byId.containsKey(it.contact) }.sortedByDescending { it.ts }.take(100)
    if (calls.isEmpty()) {
        EmptyCard("Noch keine Anrufe", "Anrufe laufen direkt von Handy zu Handy über Tor – ohne Telefonnummer. Wähle unten „Kontakte“, um jemanden anzurufen.")
        return
    }
    Column(Modifier.fillMaxWidth().glass(sq(22)).padding(horizontal = 14.dp, vertical = 2.dp)) {
        calls.forEachIndexed { i, m ->
            val c = byId[m.contact] ?: return@forEachIndexed
            val missed = m.text.startsWith("Verpasst")
            Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.pressable(CircleShape) { App.nav.push(Screen.ContactDetail(c.id)) }) { ContactAvatar(c.name, 40.dp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = G.headline.copy(fontSize = 15.sp, color = if (missed) Color(0xFFFF8A80) else Color.White), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (missed) "Verpasst" else if (m.mine) "Ausgehend" else "Eingehend") + m.text.substringAfter("Anruf", "").let { if (it.isNotBlank() && !missed) " $it".replace("  ", " ") else "" },
                        style = G.footnote, maxLines = 1
                    )
                }
                Text(shortTime(m.ts), style = G.footnote)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(36.dp).pressable(CircleShape, scaleTo = 0.9f) { call(c) }.clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) { Icon(Ic.phone, "${c.name} anrufen", tint = Color.White, modifier = Modifier.size(17.dp)) }
            }
            if (i < calls.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
        }
    }
}

@Composable
private fun VoiceTab(engine: ChatEngine, byId: Map<String, Contact>) {
    val voices = engine.messages.filter { it.kind == Msg.KIND_VOICE && !it.mine && byId.containsKey(it.contact) }.sortedByDescending { it.ts }.take(100)
    if (voices.isEmpty()) {
        EmptyCard("Keine Sprachnachrichten", "Sprachnachrichten deiner Kontakte erscheinen hier. Abspielen kannst du sie im Gespräch.")
        return
    }
    Column(Modifier.fillMaxWidth().glass(sq(22)).padding(horizontal = 14.dp, vertical = 2.dp)) {
        voices.forEachIndexed { i, m ->
            val c = byId[m.contact] ?: return@forEachIndexed
            Row(
                Modifier.fillMaxWidth().pressable(sq(12)) { App.nav.push(Screen.ChatThread(c.id)) }.padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ContactAvatar(c.name, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = G.headline.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Sprachnachricht · ${m.dur / 60000}:${"%02d".format((m.dur / 1000) % 60)}", style = G.footnote)
                }
                Text(shortTime(m.ts), style = G.footnote)
                Spacer(Modifier.width(8.dp))
                Icon(Ic.forward, null, tint = G.dim, modifier = Modifier.size(14.dp))
            }
            if (i < voices.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
        }
    }
}

@Composable
private fun EmptyCard(title: String, text: String) {
    Column(Modifier.fillMaxWidth().glass(sq(22)).padding(18.dp)) {
        Text(title, style = G.headline.copy(fontSize = 16.sp))
        Spacer(Modifier.height(4.dp))
        Text(text, style = G.callout.copy(color = G.dim))
    }
}

// ───────────────────────── Kontakte ─────────────────────────

/** Alphabetische Liste mit Buchstaben-Gruppen (wie die iPhone-Kontakte). */
@Composable
private fun ContactList(contacts: List<Contact>, onOpen: (Contact) -> Unit) {
    if (contacts.isEmpty()) {
        EmptyCard("Noch keine Kontakte", "Oben auf + tippen und den QR-Code des anderen scannen. Am sichersten persönlich.")
        return
    }
    val groups = contacts.sortedBy { it.name.lowercase() }.groupBy { it.name.trim().take(1).uppercase().ifEmpty { "#" }.let { l -> if (l[0].isLetter()) l else "#" } }
    groups.forEach { (letter, list) ->
        Text(letter, style = G.footnote.copy(fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f)), modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 6.dp))
        Column(Modifier.fillMaxWidth().glass(sq(20)).padding(horizontal = 14.dp, vertical = 2.dp)) {
            list.forEachIndexed { i, c ->
                Row(
                    Modifier.fillMaxWidth().pressable(sq(12)) { onOpen(c) }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContactAvatar(c.name, 38.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name + if (c.nick.isNotEmpty()) " (${c.nick})" else "", style = G.headline.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            when {
                                !c.accepted -> "Anfrage wartet"
                                c.verified -> "Sicherheitsnummer geprüft"
                                else -> "nicht geprüft"
                            },
                            style = G.footnote.copy(color = if (c.verified) Color(0xFF7DFFCF) else if (!c.accepted) G.dim else Color(0xFFFFC56B)),
                            maxLines = 1
                        )
                    }
                    if (c.fav) Icon(Ic.star, "Favorit", tint = Color(0xFFFFD34D), modifier = Modifier.size(16.dp))
                }
                if (i < list.size - 1) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
            }
        }
    }
}

@Composable
fun ContactsScreen() {
    val vault = App.vault ?: return
    val engine = remember(vault, ChatAccounts.selected, vault.unlockedFolders.size) { ChatAccounts.engine(vault) }
    if (!engine.isReady) { NotSetUp("Kontakte"); return }
    SyncLoop(engine)
    var query by remember { mutableStateOf("") }
    val all = engine.contacts.filter { !it.removedByPeer }
    val shown = if (query.isBlank()) all else all.filter { (it.name + " " + it.nick).contains(query.trim(), ignoreCase = true) }

    AppBackdrop {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Ic.back, "Zurück") { App.nav.pop() }
                Spacer(Modifier.weight(1f))
                GlassIconButton(Ic.plus, "Kontakt hinzufügen") { App.nav.push(Screen.ChatAdd) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
                Text("Kontakte", style = G.largeTitle)
                Spacer(Modifier.height(12.dp))
                // Suche
                Row(
                    Modifier.fillMaxWidth().height(44.dp).glass(Pill).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Ic.search, null, tint = G.dim, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Suchen", style = G.body.copy(fontSize = 16.sp, color = G.faint))
                        BasicTextField(
                            value = query, onValueChange = { query = it.take(40) }, singleLine = true,
                            textStyle = G.body.copy(fontSize = 16.sp), cursorBrush = SolidColor(G.accentText),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Meine Karte
                Row(
                    Modifier.fillMaxWidth().pressable(sq(22)) { App.nav.push(Screen.ChatCode) }.glass(sq(22)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ContactAvatar(engine.myName.ifBlank { "Ich" }, 54.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(engine.myName.ifBlank { "Meine Karte" }, style = G.headline.copy(fontSize = 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Meine Karte · QR-Code zeigen", style = G.footnote)
                    }
                    Icon(Ic.qr, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(6.dp))
                if (query.isNotBlank() && shown.isEmpty()) {
                    Text("Keine Treffer", style = G.callout.copy(color = G.dim), modifier = Modifier.padding(top = 16.dp))
                } else ContactList(shown, onOpen = { App.nav.push(Screen.ContactDetail(it.id)) })
            }
        }
    }
}

// ───────────────────────── Kontakt im Detail ─────────────────────────

@Composable
fun ContactDetailScreen(contactId: String) {
    val vault = App.vault ?: return
    val engine = remember(vault, ChatAccounts.selected, vault.unlockedFolders.size) { ChatAccounts.engine(vault) }
    val c = engine.contacts.firstOrNull { it.id == contactId }
    if (c == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { App.nav.pop() }
        return
    }
    val call = rememberCaller(engine)
    var editing by remember { mutableStateOf(false) }
    var nick by remember(c.id) { mutableStateOf(c.nick) }
    var bday by remember(c.id) { mutableStateOf(c.birthday) }
    var note by remember(c.id) { mutableStateOf(c.note) }
    var confirmRemove by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color(0xFF140C26))) {
        // Hintergrund in der Farbe des Kontakts
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val col = contactColor(c.name)
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(col.copy(alpha = 0.55f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, 0f), radius = size.width * 0.9f),
                radius = size.width * 0.9f, center = androidx.compose.ui.geometry.Offset(size.width * 0.5f, 0f)
            )
            drawCircle(
                androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFF5B4CFF).copy(alpha = 0.45f), Color.Transparent), center = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.7f), radius = size.width * 0.8f),
                radius = size.width * 0.8f, center = androidx.compose.ui.geometry.Offset(size.width, size.height * 0.7f)
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                GlassIconButton(Ic.back, "Zurück") { App.nav.pop() }
                Spacer(Modifier.weight(1f))
                Text(
                    if (editing) "Fertig" else "Bearbeiten",
                    style = G.body.copy(fontWeight = FontWeight.SemiBold, color = Color.White),
                    modifier = Modifier.pressable(Pill) {
                        if (editing) engine.setContactInfo(c, nick, bday, note)
                        editing = !editing
                    }.padding(horizontal = 8.dp, vertical = 10.dp)
                )
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ContactAvatar(c.name, 104.dp)
                Spacer(Modifier.height(10.dp))
                Text(c.name, style = G.title2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (c.nick.isNotEmpty()) Text(c.nick, style = G.callout.copy(color = G.dim))
                Spacer(Modifier.height(4.dp))
                Text(
                    if (!c.accepted) "Anfrage wartet" else if (c.paused) "Chat ruht" else "über Tor erreichbar",
                    style = G.footnote.copy(color = if (c.accepted && !c.paused) Color(0xFF7DFFCF) else G.dim, fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.height(18.dp))
                // Aktionen
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ActionTile(Ic.chat, "Nachricht", Modifier.weight(1f)) { App.nav.push(Screen.ChatThread(c.id)) }
                    ActionTile(Ic.phone, "Anrufen", Modifier.weight(1f), enabled = engine.callsAllowed(c)) { call(c) }
                    ActionTile(Ic.mic, "Sprache", Modifier.weight(1f), enabled = c.accepted && c.settings.voice) { App.nav.push(Screen.ChatThread(c.id)) }
                    ActionTile(Ic.star, if (c.fav) "Favorit ✓" else "Favorit", Modifier.weight(1f), tint = if (c.fav) Color(0xFFFFD34D) else Color.White) {
                        engine.setFavorite(c, !c.fav)
                    }
                }
                Spacer(Modifier.height(16.dp))
                // Eigene Angaben (nur für dich)
                Column(Modifier.fillMaxWidth().glass(sq(22)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                    if (editing) {
                        Spacer(Modifier.height(8.dp))
                        GlassField(nick, { nick = it.take(40) }, "Spitzname")
                        Spacer(Modifier.height(8.dp))
                        GlassField(bday, { bday = it.take(20) }, "Geburtstag (z. B. 14. März)")
                        Spacer(Modifier.height(8.dp))
                        GlassField(note, { note = it.take(500) }, "Notiz – nur für dich", singleLine = false)
                        Spacer(Modifier.height(10.dp))
                    } else {
                        InfoLine("Spitzname", c.nick.ifEmpty { "–" }, true)
                        InfoLine("Geburtstag", c.birthday.ifEmpty { "–" }, true)
                        InfoLine("Notiz (nur für dich)", c.note.ifEmpty { "–" }, false)
                    }
                }
                Text(
                    "Diese Angaben liegen nur verschlüsselt in deinem Tresor und werden nie verschickt.",
                    style = G.footnote.copy(color = G.faint), modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp)
                )
                Spacer(Modifier.height(14.dp))
                // Sicherheitsnummer
                Column(Modifier.fillMaxWidth().glass(sq(22)).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(34.dp).clip(sq(10)).background((if (c.verified) Color(0xFF7DFFCF) else Color(0xFFFFC56B)).copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Ic.shield, null, tint = if (c.verified) Color(0xFF7DFFCF) else Color(0xFFFFC56B), modifier = Modifier.size(18.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (c.verified) "Sicherheitsnummer geprüft" else "Nicht geprüft", style = G.headline.copy(fontSize = 15.sp))
                            Text(engine.safetyNumber(c), style = G.footnote.copy(fontFamily = FontFamily.Monospace), maxLines = 2)
                        }
                    }
                    if (!c.verified && c.accepted) {
                        Spacer(Modifier.height(10.dp))
                        Text("Vergleicht die Nummer persönlich oder am Telefon – nicht über den Chat selbst.", style = G.footnote.copy(color = G.dim))
                        Spacer(Modifier.height(10.dp))
                        PillButton("Nummer stimmt überein", icon = Ic.check, modifier = Modifier.fillMaxWidth()) { engine.setVerified(c, true) }
                    }
                }
                Spacer(Modifier.height(20.dp))
                PillButton("Kontakt entfernen", textColor = G.red, modifier = Modifier.fillMaxWidth()) { confirmRemove = true }
            }
        }
        ConfirmSheet(
            visible = confirmRemove,
            title = "${c.name} entfernen?",
            text = if (c.settings.deleteBoth) "Der Chat wird auf beiden Handys gelöscht. ${c.name} bekommt einen Hinweis." else "Der Chat wird bei dir gelöscht. ${c.name} bekommt einen Hinweis.",
            confirm = "Entfernen",
            onDismiss = { confirmRemove = false }
        ) {
            confirmRemove = false
            engine.removeContact(c)
            App.nav.pop()
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, label: String, modifier: Modifier, enabled: Boolean = true, tint: Color = Color.White, onClick: () -> Unit) {
    Column(
        modifier.pressable(sq(20), enabled = enabled, scaleTo = 0.94f) { onClick() }.glass(sq(20)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = if (enabled) tint else G.faint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, style = G.caption.copy(fontSize = 12.sp, letterSpacing = 0.sp, color = if (enabled) Color.White else G.faint, fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
private fun InfoLine(label: String, value: String, divider: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(label, style = G.footnote)
        Spacer(Modifier.height(2.dp))
        Text(value, style = G.body.copy(fontSize = 15.sp, color = Color.White))
    }
    if (divider) Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))
}
