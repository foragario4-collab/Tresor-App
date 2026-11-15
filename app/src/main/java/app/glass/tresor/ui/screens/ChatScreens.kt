package app.glass.tresor.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glass.tresor.App
import app.glass.tresor.Screen
import app.glass.tresor.chat.ChatEngine
import app.glass.tresor.chat.ChatService
import app.glass.tresor.chat.Contact
import app.glass.tresor.chat.Msg
import app.glass.tresor.ui.ConfirmSheet
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.GlassField
import app.glass.tresor.ui.GlassIconButton
import app.glass.tresor.ui.GlassSwitch
import app.glass.tresor.ui.Haptic
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.PillButton
import app.glass.tresor.ui.SectionLabel
import app.glass.tresor.ui.SettingRow
import app.glass.tresor.ui.Sheet
import app.glass.tresor.ui.SheetHeader
import app.glass.tresor.ui.TopBar
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Hält den Chat aktuell, solange ein Chat-Bildschirm offen ist. */
@Composable
private fun SyncLoop(engine: ChatEngine, onStatus: (String) -> Unit = {}) {
    LaunchedEffect(engine) {
        while (true) {
            withContext(Dispatchers.IO) { try { engine.sync() } catch (_: Exception) {} }
            onStatus(ChatService.state)
            delay(2000)
        }
    }
}

@Composable
private fun StatusPill(state: String) {
    val color = when (state) {
        "Bereit" -> G.green
        "Aus" -> G.red
        else -> Color(0xFFFFB020)
    }
    Row(Modifier.glass(Pill).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(if (state == "Bereit") "Tor verbunden" else if (state == "Aus") "Offline" else "Verbindet …", style = G.footnote.copy(color = Color.White))
    }
}

// ───────────────────────── Kontaktliste ─────────────────────────

@Composable
fun ChatListScreen() {
    val vault = App.vault ?: return
    val engine = remember(vault) { ChatEngine.of(vault) }
    val ctx = LocalContext.current
    var status by remember { mutableStateOf(ChatService.state) }
    var addSheet by remember { mutableStateOf(false) }
    var pasted by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var request by remember { mutableStateOf<Contact?>(null) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { App.suppressLock = false }
    val scanner = rememberLauncherForActivityResult(ScanContract()) { r ->
        App.suppressLock = false
        val code = r.contents ?: return@rememberLauncherForActivityResult
        handleAdd(engine, code)
        addSheet = false
    }

    if (engine.isReady) SyncLoop(engine) { status = it }

    GlassBackground {
        if (!engine.isReady) {
            ChatSetup(name, { name = it }) {
                engine.setUp(name.ifBlank { "Ich" })
                if (Build.VERSION.SDK_INT >= 33) {
                    App.suppressLock = true
                    notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            return@GlassBackground
        }
        val requests = engine.contacts.filter { !it.accepted }
        val friends = engine.contacts.filter { it.accepted }
            .sortedByDescending { engine.lastOf(it.id)?.ts ?: it.added }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                TopBar(onBack = { App.nav.pop() }, horizontalPadding = 0.dp) {
                    GlassIconButton(Ic.info, "Tipps") { App.nav.push(Screen.ChatTips) }
                    GlassIconButton(Ic.qr, "Mein Code") { App.nav.push(Screen.ChatCode) }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Chat", style = G.largeTitle, modifier = Modifier.weight(1f))
                    StatusPill(status)
                }
                Text("Direkt von Handy zu Handy über Tor · Ende-zu-Ende verschlüsselt", style = G.footnote)
                Spacer(Modifier.height(10.dp))
            }
            if (requests.isNotEmpty()) {
                item { SectionLabel("Kontaktanfragen") }
                items(requests, key = { "r" + it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().pressable(sq(20)) { request = c }.glass(sq(20)).padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(c.name)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = G.headline.copy(fontSize = 16.sp))
                            Text("möchte mit dir schreiben", style = G.footnote)
                        }
                        Text("Ansehen", style = G.callout.copy(color = G.accentText))
                    }
                }
                item { Spacer(Modifier.height(6.dp)) }
            }
            if (friends.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().glass(sq(24)).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Ic.qr, null, tint = Color.White, modifier = Modifier.size(36.dp))
                        Spacer(Modifier.height(10.dp))
                        Text("Noch keine Kontakte", style = G.headline)
                        Spacer(Modifier.height(4.dp))
                        Text("Scannt gegenseitig eure QR-Codes – oder einer scannt, der andere bestätigt die Anfrage.", style = G.footnote, textAlign = TextAlign.Center)
                    }
                }
            }
            items(friends, key = { it.id }) { c ->
                val last = engine.lastOf(c.id)
                Row(
                    Modifier.fillMaxWidth().pressable(sq(20)) { App.nav.push(Screen.ChatThread(c.id)) }.glass(sq(20)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(c.name)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = G.headline.copy(fontSize = 16.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            last?.let { (if (it.mine) "Du: " else "") + it.text } ?: "Noch keine Nachrichten",
                            style = G.footnote, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (last != null) Text(timeOf(last.ts), style = G.footnote.copy(color = G.faint))
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 18.dp)
                .height(56.dp).glass(Pill, G.fillStrong).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PillButton("Kontakt hinzufügen", icon = Ic.plus, height = 44.dp) { addSheet = true }
        }

        Sheet(addSheet, { addSheet = false; pasted = "" }) {
            SheetHeader(Ic.person, "Kontakt hinzufügen", "Scanne den QR-Code deines Kontakts oder füge seinen Code ein.")
            PillButton("QR-Code scannen", icon = Ic.qr, primary = true, modifier = Modifier.fillMaxWidth()) {
                App.suppressLock = true
                scanner.launch(
                    ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        .setPrompt("QR-Code des Kontakts scannen")
                        .setBeepEnabled(false)
                        .setOrientationLocked(false)
                )
            }
            Spacer(Modifier.height(12.dp))
            GlassField(pasted, { pasted = it }, "Code einfügen (TRESOR1:…)")
            Spacer(Modifier.height(10.dp))
            PillButton("Hinzufügen", enabled = pasted.startsWith("TRESOR1:"), modifier = Modifier.fillMaxWidth()) {
                if (handleAdd(engine, pasted)) { addSheet = false; pasted = "" }
            }
        }

        Sheet(request != null, { request = null }) {
            val c = request ?: return@Sheet
            SheetHeader(Ic.person, "„${c.name}“ möchte mit dir schreiben", "Nimm nur Anfragen von Personen an, denen du deinen Code selbst gegeben hast.")
            Text("Sicherheitsnummer", style = G.caption)
            Text(engine.safetyNumber(c), style = G.body.copy(fontFamily = FontFamily.Monospace, fontSize = 15.sp), modifier = Modifier.padding(vertical = 6.dp))
            Spacer(Modifier.height(10.dp))
            PillButton("Annehmen", primary = true, modifier = Modifier.fillMaxWidth()) { engine.accept(c); request = null }
            Spacer(Modifier.height(10.dp))
            PillButton("Ablehnen", textColor = G.red, modifier = Modifier.fillMaxWidth()) { engine.removeContact(c); request = null }
        }
    }
}

private fun handleAdd(engine: ChatEngine, code: String): Boolean {
    return when (engine.addContact(code)) {
        ChatEngine.AddResult.Ok -> { App.toast("Kontakt hinzugefügt – Anfrage wird zugestellt"); true }
        ChatEngine.AddResult.Exists -> { App.toast("Kontakt gibt es schon"); true }
        ChatEngine.AddResult.Self -> { App.toast("Das ist dein eigener Code"); false }
        ChatEngine.AddResult.NotReady -> { App.toast("Chat ist noch nicht bereit"); false }
        ChatEngine.AddResult.Invalid -> { App.toast("Kein gültiger Tresor-Code"); false }
    }
}

@Composable
private fun ChatSetup(name: String, onName: (String) -> Unit, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        TopBar(onBack = { App.nav.pop() }, horizontalPadding = 0.dp)
        Spacer(Modifier.height(14.dp))
        Text("Privater Chat", style = G.largeTitle)
        Spacer(Modifier.height(6.dp))
        Text("Nachrichten gehen direkt von Handy zu Handy über das Tor-Netzwerk. Kein Server, keine Telefonnummer, kein Konto.", style = G.callout.copy(color = G.dim))
        Spacer(Modifier.height(18.dp))
        Column(Modifier.fillMaxWidth().glass(sq(24)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TipLine(Ic.qr, "Kontakt per QR-Code", "Ihr scannt eure Codes – niemand kann sich dazwischenschalten.")
            TipLine(Ic.lock, "Ende-zu-Ende verschlüsselt", "Nur ihr beide könnt die Nachrichten lesen.")
            TipLine(Ic.globe, "Über Tor", "Niemand sieht, wer mit wem schreibt.")
            TipLine(Ic.clock, "Beide müssen online sein", "Nachrichten werden zugestellt, sobald beide Handys erreichbar sind.")
        }
        Spacer(Modifier.height(18.dp))
        GlassField(name, onName, "Dein Anzeigename (frei wählbar)")
        Spacer(Modifier.height(12.dp))
        PillButton("Chat einrichten", primary = true, modifier = Modifier.fillMaxWidth()) { onStart() }
        Spacer(Modifier.height(10.dp))
        Text("Der erste Start von Tor kann 30–60 Sekunden dauern.", style = G.footnote.copy(color = G.faint), modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun TipLine(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(36.dp).glass(CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = G.headline.copy(fontSize = 15.sp))
            Text(text, style = G.footnote)
        }
    }
}

@Composable
private fun Avatar(name: String) {
    val colors = listOf(G.blue, G.purple, G.teal, G.amber, Color(0xFFE5484D))
    Box(
        Modifier.size(42.dp).clip(CircleShape).background(colors[(name.hashCode() and 0x7fffffff) % colors.size]),
        contentAlignment = Alignment.Center
    ) {
        Text(name.take(1).uppercase().ifBlank { "?" }, style = G.headline)
    }
}

private fun timeOf(ts: Long): String {
    val now = System.currentTimeMillis()
    val f = if (now - ts < 24 * 3600_000L) "HH:mm" else "d. MMM"
    return SimpleDateFormat(f, Locale.GERMANY).format(Date(ts))
}

// ───────────────────────── Gespräch ─────────────────────────

@Composable
fun ChatThreadScreen(contactId: String) {
    val vault = App.vault ?: return
    val engine = remember(vault) { ChatEngine.of(vault) }
    val contact = engine.contacts.firstOrNull { it.id == contactId }
    var text by remember { mutableStateOf("") }
    var info by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Msg?>(null) }
    var rename by remember { mutableStateOf("") }
    val list = rememberLazyListState()

    SyncLoop(engine)

    if (contact == null) {
        LaunchedEffect(Unit) { App.nav.pop() }
        return
    }
    val thread = engine.thread(contactId)
    LaunchedEffect(thread.size) { if (thread.isNotEmpty()) list.animateScrollToItem(thread.size - 1) }

    GlassBackground {
        Column(Modifier.fillMaxSize().imePadding()) {
            TopBar(onBack = { App.nav.pop() }) {
                Row(
                    Modifier.pressable(Pill) { rename = contact.name; info = true }.glass(Pill).padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(contact.name, style = G.headline.copy(fontSize = 15.sp), maxLines = 1)
                    Spacer(Modifier.width(6.dp))
                    Icon(Ic.info, null, tint = G.dim, modifier = Modifier.size(16.dp))
                }
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                state = list,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (thread.isEmpty()) item {
                    Text(
                        "Schreib die erste Nachricht. Sie wird zugestellt, sobald beide Handys online sind.",
                        style = G.footnote, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)
                    )
                }
                items(thread, key = { it.id }) { m -> Bubble(m) { toDelete = m } }
            }
            Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).glass(sq(24)).padding(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    if (text.isEmpty()) Text("Nachricht", style = G.body.copy(color = G.faint))
                    BasicTextField(
                        value = text, onValueChange = { text = it.take(5000) },
                        textStyle = G.body, cursorBrush = SolidColor(G.accentText), maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(48.dp)
                        .pressable(CircleShape, enabled = text.isNotBlank(), scaleTo = 0.9f, highlight = 0f, haptic = Haptic.CONFIRM) {
                            engine.send(contact, text); text = ""
                        }
                        .clip(CircleShape).background(if (text.isNotBlank()) G.accent else Color.White.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Ic.send, "Senden", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
        }

        Sheet(info, { info = false }) {
            SheetHeader(Ic.person, contact.name, "Vergleicht die Sicherheitsnummer persönlich. Stimmt sie auf beiden Handys überein, schreibt ihr garantiert miteinander.")
            Text("SICHERHEITSNUMMER", style = G.caption)
            Text(engine.safetyNumber(contact), style = G.body.copy(fontFamily = FontFamily.Monospace, fontSize = 16.sp), modifier = Modifier.padding(vertical = 8.dp))
            GlassField(rename, { rename = it }, "Name ändern")
            Spacer(Modifier.height(10.dp))
            PillButton("Speichern", primary = true, enabled = rename.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                engine.renameContact(contact, rename); info = false
            }
            Spacer(Modifier.height(10.dp))
            PillButton("Verlauf löschen", modifier = Modifier.fillMaxWidth()) { engine.clearThread(contact); info = false }
            Spacer(Modifier.height(10.dp))
            PillButton("Kontakt entfernen", textColor = G.red, modifier = Modifier.fillMaxWidth()) { info = false; confirmRemove = true }
        }
        ConfirmSheet(
            visible = confirmRemove, title = "Kontakt entfernen?", text = "Der Kontakt und der ganze Verlauf werden gelöscht.",
            confirm = "Entfernen", onDismiss = { confirmRemove = false }
        ) {
            confirmRemove = false
            engine.removeContact(contact)
            App.nav.pop()
        }
        ConfirmSheet(
            visible = toDelete != null, title = "Nachricht löschen?", text = "Nur auf deinem Handy.",
            confirm = "Löschen", onDismiss = { toDelete = null }
        ) {
            toDelete?.let { engine.deleteMessage(it) }
            toDelete = null
        }
    }
}

@Composable
private fun Bubble(m: Msg, onLong: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 290.dp)
                .pressable(sq(20), scaleTo = 0.98f, highlight = 0.08f, haptic = Haptic.NONE, onLongClick = onLong) {}
                .then(if (m.mine) Modifier.clip(sq(20)).background(G.accent) else Modifier.glass(sq(20)))
                .padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            Text(m.text, style = G.body.copy(fontSize = 16.sp, lineHeight = 21.sp))
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(timeOf(m.ts), style = G.footnote.copy(fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f)))
                if (m.mine) {
                    Spacer(Modifier.width(4.dp))
                    Icon(if (m.state == 0) Ic.clock else Ic.check, if (m.state == 0) "wartet" else "zugestellt", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

// ───────────────────────── Mein Code ─────────────────────────

private fun qrBitmap(text: String, size: Int = 720): ImageBitmap {
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size)
    for (y in 0 until size) for (x in 0 until size) px[y * size + x] = if (m.get(x, y)) 0xFF0B0D12.toInt() else 0xFFFFFFFF.toInt()
    return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

@Composable
fun ChatCodeScreen() {
    val vault = App.vault ?: return
    val engine = remember(vault) { ChatEngine.of(vault) }
    val ctx = LocalContext.current
    SyncLoop(engine)
    val code = if (engine.onion != null) engine.myCode() else null
    val qr = remember(code) { code?.let { qrBitmap(it) } }

    GlassBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBar(onBack = { App.nav.pop() }, horizontalPadding = 0.dp)
            Spacer(Modifier.height(14.dp))
            Text("Mein Code", style = G.largeTitle, modifier = Modifier.fillMaxWidth())
            Text("Lass diesen Code von deinem Kontakt scannen.", style = G.callout.copy(color = G.dim), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.size(280.dp).clip(sq(32)).background(Color.White).padding(18.dp),
                contentAlignment = Alignment.Center
            ) {
                if (qr != null) Image(qr, "QR-Code", modifier = Modifier.fillMaxSize())
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF0B0D12), strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.height(10.dp))
                    Text("Tor startet …", style = G.footnote.copy(color = Color(0xFF0B0D12)))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(engine.myName.ifBlank { "Ich" }, style = G.title3)
            Spacer(Modifier.height(18.dp))
            if (code != null) {
                PillButton("Code als Text kopieren", icon = Ic.copy, modifier = Modifier.fillMaxWidth()) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Tresor-Code", code))
                    App.toast("Code kopiert")
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Den Code darf jeder sehen, er enthält nur deine öffentliche Adresse und deinen öffentlichen Schlüssel. Wer ihn hat, kann dir aber eine Kontaktanfrage schicken.",
                    style = G.footnote.copy(color = G.faint), textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ───────────────────────── Tipps ─────────────────────────

@Composable
fun ChatTipsScreen() {
    val ctx = LocalContext.current
    var bg by remember { mutableStateOf(App.prefs.getBoolean("chatBackground", true)) }
    var notify by remember { mutableStateOf(App.prefs.getBoolean("chatNotify", true)) }
    GlassBackground {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            TopBar(onBack = { App.nav.pop() }, horizontalPadding = 0.dp)
            Spacer(Modifier.height(14.dp))
            Text("So funktioniert der Chat", style = G.largeTitle.copy(fontSize = 30.sp))
            Spacer(Modifier.height(16.dp))
            Column(Modifier.fillMaxWidth().glass(sq(24)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TipLine(Ic.globe, "Kein Server", "Dein Handy ist über eine geheime Tor-Adresse direkt erreichbar. Es gibt keinen Server, der etwas speichern oder mitlesen könnte.")
                TipLine(Ic.clock, "Beide müssen online sein", "Eine Nachricht kommt an, sobald beide Handys gleichzeitig online sind. Bis dahin wartet sie verschlüsselt auf deinem Handy (Uhr-Symbol).")
                TipLine(Ic.lock, "Sicherheitsnummer vergleichen", "Tippt im Gespräch auf den Namen und vergleicht die Nummer. Stimmt sie überein, schreibt ihr garantiert miteinander.")
                TipLine(Ic.shield, "Jeder Tresor hat seinen eigenen Chat", "Kontakte und Nachrichten eines Tresors sind in den anderen Tresoren unsichtbar.")
            }
            SectionLabel("iPhone")
            Column(Modifier.fillMaxWidth().glass(sq(24)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Auf dem iPhone beendet Apple Apps im Hintergrund nach kurzer Zeit. Ohne Server ist ein iPhone deshalb nur erreichbar, solange die App geöffnet ist.", style = G.callout)
                Text("Tipp: Verabredet euch zum Schreiben oder öffnet die App regelmäßig. Wartende Nachrichten werden dann sofort zugestellt.", style = G.footnote)
            }
            SectionLabel("Damit es zuverlässig läuft")
            Column(Modifier.fillMaxWidth().glass(sq(24)).padding(horizontal = 16.dp, vertical = 4.dp)) {
                SettingRow("Im Hintergrund erreichbar", "Tor läuft weiter, auch wenn der Tresor gesperrt ist") {
                    GlassSwitch(bg, "Hintergrund") {
                        bg = it; App.prefs.edit().putBoolean("chatBackground", it).apply()
                        if (it) ChatService.start(ctx) else ChatService.stop(ctx)
                    }
                }
                SettingRow("Benachrichtigung", "Nur „Neue Nachricht“ – nie Inhalt oder Absender", showDivider = true) {
                    GlassSwitch(notify, "Benachrichtigung") { notify = it; App.prefs.edit().putBoolean("chatNotify", it).apply() }
                }
                SettingRow("Akku-Optimierung", "Für die App ausschalten, sonst stoppt Android den Chat", showDivider = true) {
                    PillButton("Öffnen", height = 38.dp) {
                        App.suppressLock = true
                        try { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) { App.suppressLock = false }
                    }
                }
            }
            Text(
                "Solange der Chat im Hintergrund läuft, zeigt Android die Mitteilung „Synchronisierung aktiv“. Du kannst sie in den Android-Einstellungen ausblenden.",
                style = G.footnote.copy(color = G.faint), modifier = Modifier.padding(4.dp)
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
