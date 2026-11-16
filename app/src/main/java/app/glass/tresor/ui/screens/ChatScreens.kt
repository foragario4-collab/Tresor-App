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
import android.content.pm.PackageManager
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.core.content.ContextCompat
import app.glass.tresor.chat.Calls
import app.glass.tresor.chat.ChatAccounts
import app.glass.tresor.vault.Folder
import app.glass.tresor.vault.FolderKind
import app.glass.tresor.vault.Protection
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import app.glass.tresor.chat.ChatEngine
import app.glass.tresor.chat.ChatService
import app.glass.tresor.chat.ChatSettings
import app.glass.tresor.chat.Contact
import app.glass.tresor.chat.Msg
import app.glass.tresor.chat.VoicePlayer
import app.glass.tresor.chat.VoiceRecorder
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
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
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
    val engine = remember(vault, ChatAccounts.selected, vault.unlockedFolders.size) { ChatAccounts.engine(vault) }
    val ctx = LocalContext.current
    val view = LocalView.current
    var status by remember { mutableStateOf(ChatService.state) }
    var name by remember(engine) { mutableStateOf("") }
    var request by remember { mutableStateOf<Contact?>(null) }
    var accountsSheet by remember { mutableStateOf(false) }
    var codeSheet by remember { mutableStateOf(false) }
    var newAccount by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var unlockAcc by remember { mutableStateOf<Folder?>(null) }
    var confirmDelAcc by remember { mutableStateOf(false) }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { App.suppressLock = false }

    if (engine.isReady) SyncLoop(engine) { status = it }

    GlassBackground {
        if (!engine.isReady) {
            ChatSetup(name, { name = it }, account = engine.accountName, onAccounts = { accountsSheet = true }) {
                engine.setUp(name.ifBlank { "Ich" })
                if (Build.VERSION.SDK_INT >= 33) {
                    App.suppressLock = true
                    notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else ChatListContent(engine, status, onRequest = { request = it }, onAccounts = { accountsSheet = true }) {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            codeSheet = true
        }

        // ── Konten ──
        Sheet(accountsSheet, { accountsSheet = false }) {
            SheetHeader(Ic.person, "Chat-Konten", "Jedes Konto hat eigene Kontakte und eine eigene Tor-Adresse. Konten kannst du schützen und verstecken wie Ordner.")
            Column(Modifier.fillMaxWidth().glass(sq(20)).padding(horizontal = 6.dp, vertical = 4.dp)) {
                AccountRow("Hauptkonto", null, ChatAccounts.selected == ChatEngine.MAIN) {
                    ChatAccounts.selected = ChatEngine.MAIN; accountsSheet = false
                }
                ChatAccounts.visible(vault).forEach { f ->
                    val open = vault.isFolderUnlocked(f.id)
                    AccountRow(f.name, if (!open) Ic.lock else if (f.hidden) Ic.eyeOff else null, ChatAccounts.selected == f.id) {
                        if (open) { ChatAccounts.selected = f.id; accountsSheet = false }
                        else { accountsSheet = false; unlockAcc = f }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            PillButton("Neues Konto", icon = Ic.plus, modifier = Modifier.fillMaxWidth()) { accountsSheet = false; newName = ""; newAccount = true }
            if (ChatAccounts.selected != ChatEngine.MAIN) {
                Spacer(Modifier.height(10.dp))
                PillButton("Schutz & Verstecken für „${engine.accountName}“", icon = Ic.lock, modifier = Modifier.fillMaxWidth()) {
                    accountsSheet = false; App.nav.push(Screen.FolderSettings(ChatAccounts.selected))
                }
                Spacer(Modifier.height(10.dp))
                PillButton("Konto löschen", textColor = G.red, modifier = Modifier.fillMaxWidth()) { accountsSheet = false; confirmDelAcc = true }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
        Sheet(newAccount, { newAccount = false }) {
            SheetHeader(Ic.plus, "Neues Chat-Konto", "Zum Beispiel „Arbeit“ oder „Privat“. Nur du siehst diesen Namen. Deinen Anzeigenamen für Kontakte wählst du gleich danach.")
            GlassField(newName, { newName = it.take(40) }, "Name des Kontos", autoFocus = newAccount)
            Spacer(Modifier.height(12.dp))
            PillButton("Anlegen", primary = true, enabled = newName.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                val f = ChatAccounts.create(vault, newName)
                ChatAccounts.selected = f.id
                newAccount = false
            }
            Spacer(Modifier.navigationBarsPadding())
        }
        FolderUnlockSheet(vault, unlockAcc, { unlockAcc = null }) { f ->
            unlockAcc = null
            ChatAccounts.selected = f.id
        }
        FolderCodeSheet(vault, FolderKind.CHAT, codeSheet, { codeSheet = false }) { opened ->
            opened.firstOrNull()?.let { ChatAccounts.selected = it }
        }
        ConfirmSheet(
            visible = confirmDelAcc, title = "Konto „${engine.accountName}“ löschen?",
            text = "Alle Kontakte und Nachrichten dieses Kontos werden endgültig gelöscht. Die Tor-Adresse wird abgemeldet.",
            confirm = "Löschen", destructive = true, onDismiss = { confirmDelAcc = false }
        ) {
            val id = ChatAccounts.selected
            confirmDelAcc = false
            if (id != ChatEngine.MAIN) {
                ChatEngine.forgetAccount(vault, id)
                ChatAccounts.selected = ChatEngine.MAIN
                vault.deleteFolder(id)
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

@Composable
private fun AccountRow(name: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(sq(14)) { onClick() }.padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(name)
        Spacer(Modifier.width(12.dp))
        Text(name, style = G.headline.copy(fontSize = 16.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (icon != null) Icon(icon, null, tint = G.dim, modifier = Modifier.size(16.dp))
        if (selected) { Spacer(Modifier.width(8.dp)); Icon(Ic.check, "gewählt", tint = G.accentText, modifier = Modifier.size(18.dp)) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ChatListContent(
    engine: ChatEngine,
    status: String,
    onRequest: (Contact) -> Unit,
    onAccounts: () -> Unit,
    onTitleLong: () -> Unit
) {
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
                    // Lange auf den Titel drücken: Code oder Fingerabdruck für versteckte Konten
                    Text(
                        "Chat", style = G.largeTitle,
                        modifier = Modifier.weight(1f).combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null, onClick = {}, onLongClick = onTitleLong
                        )
                    )
                    StatusPill(status)
                }
                Text("Direkt von Handy zu Handy über Tor · Ende-zu-Ende verschlüsselt", style = G.footnote)
                Spacer(Modifier.height(10.dp))
                AccountPill(engine.accountName, engine.myName, onAccounts)
                Spacer(Modifier.height(10.dp))
            }
            if (requests.isNotEmpty()) {
                item { SectionLabel("Kontaktanfragen") }
                items(requests, key = { "r" + it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().pressable(sq(20)) { onRequest(c) }.glass(sq(20)).padding(14.dp),
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
            PillButton("Kontakt hinzufügen", icon = Ic.plus, height = 44.dp) { App.nav.push(Screen.ChatAdd) }
        }
}

/** Zeigt, in welchem Konto man gerade ist – Tippen öffnet die Konto-Auswahl. */
@Composable
private fun AccountPill(account: String, myName: String, onClick: () -> Unit) {
    Row(
        Modifier.pressable(Pill) { onClick() }.glass(Pill).padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(G.accent), contentAlignment = Alignment.Center) {
            Text(myName.ifBlank { account }.take(1).uppercase(), style = G.footnote.copy(color = Color.White, fontWeight = FontWeight.SemiBold))
        }
        Spacer(Modifier.width(8.dp))
        Text(account, style = G.callout.copy(color = Color.White))
        if (myName.isNotBlank()) Text(" · $myName", style = G.callout.copy(color = G.dim), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(6.dp))
        Icon(Ic.chevronDown, "Konto wechseln", tint = G.dim, modifier = Modifier.size(14.dp))
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
private fun ChatSetup(name: String, onName: (String) -> Unit, account: String, onAccounts: () -> Unit, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        TopBar(onBack = { App.nav.pop() }, horizontalPadding = 0.dp)
        Spacer(Modifier.height(14.dp))
        Text("Privater Chat", style = G.largeTitle)
        Spacer(Modifier.height(8.dp))
        AccountPill(account, "", onAccounts)
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
    val engine = remember(vault, ChatAccounts.selected) { ChatAccounts.engine(vault) }
    val contact = engine.contacts.firstOrNull { it.id == contactId }
    var text by remember { mutableStateOf("") }
    var info by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Msg?>(null) }
    var rename by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val ctx = LocalContext.current
    val recorder = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    var recMs by remember { mutableLongStateOf(0L) }
    var permFor by remember { mutableStateOf(0) } // 1 = Sprachnachricht, 2 = Anruf
    var settingsSheet by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(ChatSettings()) }

    fun startRec() {
        VoicePlayer.stop()
        if (recorder.start()) { recording = true; recMs = 0 } else App.toast("Aufnahme nicht möglich")
    }

    fun sendRec() {
        val c = engine.contacts.firstOrNull { it.id == contactId } ?: return
        recording = false
        val r = recorder.stop()
        if (r == null) App.toast("Zu kurz – zum Aufnehmen etwas länger sprechen")
        else if (!engine.sendVoice(c, r.first, r.second)) App.toast("Sprachnachricht zu groß")
    }

    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        App.suppressLock = false
        if (!granted) {
            App.toast("Mikrofon ist nicht erlaubt")
        } else {
            val c = engine.contacts.firstOrNull { it.id == contactId }
            when (permFor) {
                1 -> startRec()
                2 -> if (c != null) Calls.call(engine, c)
            }
        }
    }

    fun withMic(what: Int, action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) action()
        else {
            permFor = what
            App.suppressLock = true
            micPerm.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Aufnahme-Zeit anzeigen, bei 5 Minuten automatisch senden
    LaunchedEffect(recording) {
        while (recording) {
            recMs = recorder.elapsed
            if (recMs >= ChatEngine.MAX_VOICE_MS - 300) { sendRec(); break }
            delay(200)
        }
    }
    DisposableEffect(Unit) {
        onDispose { recorder.cancel(); VoicePlayer.stop() }
    }

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
                if (contact.settings.calls && contact.accepted && !contact.paused) {
                    GlassIconButton(Ic.phone, "Anrufen") { withMic(2) { Calls.call(engine, contact) } }
                    Spacer(Modifier.width(8.dp))
                }
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
                items(thread, key = { it.id }) { m ->
                    when (m.kind) {
                        Msg.KIND_CALL -> CallLine(m)
                        Msg.KIND_SYS -> SysLine(m)
                        Msg.KIND_VOICE -> VoiceBubble(m, engine) { toDelete = m }
                        else -> Bubble(m) { toDelete = m }
                    }
                }
            }
            PauseBanner(engine, contact)
            if (!contact.paused) Row(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                if (recording) {
                    // Aufnahme läuft
                    Row(
                        Modifier.weight(1f).height(48.dp).glass(sq(24)).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(G.red))
                        Spacer(Modifier.width(10.dp))
                        Text(Calls.duration(recMs), style = G.body.copy(fontFamily = FontFamily.Monospace))
                        Spacer(Modifier.width(10.dp))
                        Text("Aufnahme …", style = G.footnote, modifier = Modifier.weight(1f), maxLines = 1)
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(48.dp).pressable(CircleShape, scaleTo = 0.9f, highlight = 0f) { recording = false; recorder.cancel() }
                            .clip(CircleShape).background(Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Ic.trash, "Verwerfen", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(48.dp).pressable(CircleShape, scaleTo = 0.9f, highlight = 0f, haptic = Haptic.CONFIRM) { sendRec() }
                            .clip(CircleShape).background(G.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Ic.send, "Senden", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                } else {
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
                    val canSend = text.isNotBlank()
                    Box(
                        Modifier.size(48.dp)
                            .pressable(CircleShape, scaleTo = 0.9f, highlight = 0f, haptic = Haptic.CONFIRM) {
                                if (canSend) { if (engine.send(contact, text)) text = "" }
                                else if (contact.settings.voice) withMic(1) { startRec() }
                            }
                            .clip(CircleShape).background(if (canSend) G.accent else Color.White.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (canSend) Icon(Ic.send, "Senden", tint = Color.White, modifier = Modifier.size(22.dp))
                        else if (contact.settings.voice) Icon(Ic.mic, "Sprachnachricht aufnehmen", tint = Color.White, modifier = Modifier.size(22.dp))
                        else Icon(Ic.send, "Senden", tint = Color.White.copy(alpha = 0.4f), modifier = Modifier.size(22.dp))
                    }
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
            if (contact.accepted && !contact.removedByPeer) {
                PillButton("Chat-Einstellungen", icon = Ic.settings, modifier = Modifier.fillMaxWidth()) {
                    draft = contact.proposalOut?.settings ?: contact.settings
                    info = false; settingsSheet = true
                }
                Spacer(Modifier.height(10.dp))
            }
            PillButton(if (contact.settings.deleteBoth) "Verlauf für beide löschen" else "Verlauf löschen", modifier = Modifier.fillMaxWidth()) {
                engine.clearThread(contact); info = false
            }
            Spacer(Modifier.height(10.dp))
            PillButton("Kontakt entfernen", textColor = G.red, modifier = Modifier.fillMaxWidth()) { info = false; confirmRemove = true }
        }
        ConfirmSheet(
            visible = confirmRemove, title = "Kontakt entfernen?",
            text = if (contact.settings.deleteBoth) "Der Kontakt wird entfernt und der Chat bei euch beiden gelöscht. ${contact.name} bekommt einen Hinweis."
            else "Der Kontakt und der Verlauf werden bei dir gelöscht. ${contact.name} bekommt einen Hinweis, dass du ihn entfernt hast.",
            confirm = "Entfernen", onDismiss = { confirmRemove = false }
        ) {
            confirmRemove = false
            engine.removeContact(contact)
            App.nav.pop()
        }
        Sheet(settingsSheet, { settingsSheet = false }) {
            SheetHeader(
                Ic.settings, "Chat-Einstellungen",
                "Gelten immer für euch beide. Nach einer Änderung muss ${contact.name} zustimmen – bis dahin ruht der Chat."
            )
            Column(Modifier.fillMaxWidth().glass(sq(20)).padding(horizontal = 14.dp, vertical = 2.dp)) {
                SettingRow("Löschen für beide", "Entfernen und Verlauf löschen gilt auch beim anderen") {
                    GlassSwitch(draft.deleteBoth, "Löschen für beide") { draft = draft.copy(deleteBoth = it) }
                }
                SettingRow("Anrufe", null, showDivider = true) {
                    GlassSwitch(draft.calls, "Anrufe") { draft = draft.copy(calls = it) }
                }
                SettingRow("Sprachnachrichten", null, showDivider = true) {
                    GlassSwitch(draft.voice, "Sprachnachrichten") { draft = draft.copy(voice = it) }
                }
                SettingRow("Selbstlöschend", ChatSettings.autoDeleteLabel(draft.autoDeleteSec), showDivider = true) {
                    PillButton("Ändern", height = 36.dp) {
                        val all = ChatSettings.AUTO_DELETE
                        draft = draft.copy(autoDeleteSec = all[(all.indexOf(draft.autoDeleteSec) + 1) % all.size])
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            val changes = draft.changesFrom(contact.settings)
            PillButton(
                "Kontakt benachrichtigen", icon = Ic.send, primary = true,
                enabled = changes.isNotEmpty() && contact.proposalIn == null,
                modifier = Modifier.fillMaxWidth()
            ) {
                engine.propose(contact, draft); settingsSheet = false
            }
            if (contact.proposalIn != null) {
                Spacer(Modifier.height(8.dp))
                Text("Beantworte zuerst den Vorschlag von ${contact.name}.", style = G.footnote, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
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

@Composable
private fun VoiceBubble(m: Msg, engine: ChatEngine, onLong: () -> Unit) {
    val playing = VoicePlayer.playingId == m.id
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        progress = 0f
        while (playing) {
            progress = VoicePlayer.progress()
            delay(100)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.width(250.dp)
                .pressable(sq(20), scaleTo = 0.98f, highlight = 0.08f, haptic = Haptic.NONE, onLongClick = onLong) {
                    VoicePlayer.toggle(m.id) { engine.voiceData(m) }
                }
                .then(if (m.mine) Modifier.clip(sq(20)).background(G.accent) else Modifier.glass(sq(20)))
                .padding(start = 10.dp, end = 14.dp, top = 9.dp, bottom = 9.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (playing) Ic.pause else Ic.play, if (playing) "Pause" else "Abspielen", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f).height(4.dp).clip(Pill).background(Color.White.copy(alpha = 0.3f))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).clip(Pill).background(Color.White))
                }
                Spacer(Modifier.width(10.dp))
                Text(Calls.duration(m.dur), style = G.footnote.copy(color = Color.White))
            }
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

/** Hinweis im Verlauf (Einstellungen, entfernt …). */
@Composable
private fun SysLine(m: Msg) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
        Text(
            m.text + " · " + timeOf(m.ts),
            style = G.footnote.copy(fontSize = 12.sp, color = G.dim), textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 300.dp).glass(sq(14)).padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

/** Unten statt des Eingabefelds, solange der Chat ruht. */
@Composable
private fun PauseBanner(engine: ChatEngine, c: Contact) {
    if (!c.paused) return
    Column(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).glass(sq(24)).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val pin = c.proposalIn
        val pout = c.proposalOut
        when {
            c.removedByPeer -> {
                Text("${c.name} hat dich als Kontakt entfernt", style = G.headline.copy(fontSize = 16.sp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text("Ihr könnt nicht mehr schreiben. Fügt euch neu hinzu, um wieder zu chatten.", style = G.footnote, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                PillButton("Kontakt bei mir löschen", textColor = G.red, modifier = Modifier.fillMaxWidth()) { engine.removeContact(c); App.nav.pop() }
            }
            pin != null -> {
                Text("${c.name} möchte die Chat-Einstellungen ändern", style = G.headline.copy(fontSize = 16.sp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                pin.settings.changesFrom(c.settings).forEach { Text("• $it", style = G.callout) }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    PillButton("Ablehnen", modifier = Modifier.weight(1f)) { engine.answer(c, false) }
                    Spacer(Modifier.width(10.dp))
                    PillButton("Annehmen", primary = true, modifier = Modifier.weight(1f)) { engine.answer(c, true) }
                }
            }
            pout != null -> {
                Text("Warte auf Zustimmung von ${c.name}", style = G.headline.copy(fontSize = 16.sp), textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                pout.settings.changesFrom(c.settings).forEach { Text("• $it", style = G.callout.copy(color = G.dim)) }
                Spacer(Modifier.height(4.dp))
                Text("Bis dahin ruht der Chat.", style = G.footnote)
                Spacer(Modifier.height(12.dp))
                PillButton("Vorschlag zurücknehmen", modifier = Modifier.fillMaxWidth()) { engine.withdraw(c) }
            }
        }
    }
}

/** Anruf im Verlauf: kleine Zeile in der Mitte. */
@Composable
private fun CallLine(m: Msg) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center) {
        Row(Modifier.glass(Pill).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Ic.phone, null, tint = if (m.text.startsWith("Verpasst")) G.red else G.dim, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(m.text + " · " + timeOf(m.ts), style = G.footnote.copy(fontSize = 12.sp))
        }
    }
}

// ───────────────────────── Banner bei neuer Nachricht ─────────────────────────

/**
 * Hält den Chat aktuell, solange der Tresor offen ist, und zeigt oben ein Glas-Banner,
 * wenn eine Nachricht kommt (außer man ist gerade in genau diesem Gespräch).
 */
@Composable
fun ChatBanner(modifier: Modifier) {
    val v = App.vault ?: return
    // Alle offenen Konten im Hintergrund abgleichen (auch versteckte – angezeigt wird aber nur Sichtbares)
    LaunchedEffect(v) {
        while (true) {
            withContext(Dispatchers.IO) {
                try { ChatEngine.openEngines(v).forEach { e -> try { e.sync() } catch (_: Exception) {} } } catch (_: Exception) {}
            }
            delay(3000)
        }
    }
    var shown by remember { mutableStateOf<Pair<String, Msg>?>(null) }
    var last by remember { mutableStateOf<Pair<String, Msg>?>(null) }
    val startTick = remember(v) { ChatEngine.bannerTick }
    LaunchedEffect(ChatEngine.bannerTick) {
        val bm = ChatEngine.bannerMsg ?: return@LaunchedEffect
        if (ChatEngine.bannerTick <= startTick) return@LaunchedEffect
        val (account, m) = bm
        // Versteckte, nicht hervorgeholte Konten verraten sich nicht durch ein Banner
        if (account != ChatEngine.MAIN) {
            val f = v.folder(account) ?: return@LaunchedEffect
            if (f.hidden && f.id !in v.revealed) return@LaunchedEffect
        }
        val top = App.nav.top
        if (top is Screen.ChatThread && top.contactId == m.contact && ChatAccounts.selected == account) return@LaunchedEffect
        last = bm
        shown = bm
        delay(4000)
        if (shown?.second?.id == m.id) shown = null
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = shown != null,
        modifier = modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it },
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it }
    ) {
        val (account, msg) = last ?: return@AnimatedVisibility
        val engine = ChatEngine.of(v, account)
        val c = engine.contacts.firstOrNull { it.id == msg.contact }
        Row(
            Modifier.fillMaxWidth()
                .pressable(sq(22), haptic = Haptic.NONE) {
                    shown = null
                    ChatAccounts.selected = account
                    App.nav.push(Screen.ChatThread(msg.contact))
                }
                .glass(sq(22), G.sheet).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(c?.name ?: "?")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                val acc = if (account == ChatEngine.MAIN) "" else " · " + engine.accountName
                Text((c?.name ?: "Neue Nachricht") + acc, style = G.headline.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(msg.text, style = G.footnote.copy(color = Color.White.copy(alpha = 0.85f)), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ───────────────────────── Kontakt hinzufügen ─────────────────────────

/**
 * Alles auf einem Bildschirm, immer hochkant: oben die Kamera zum Scannen,
 * darunter der eigene QR-Code – so könnt ihr euch gleichzeitig gegenseitig scannen.
 */
@Composable
fun ChatAddScreen() {
    val vault = App.vault ?: return
    val engine = remember(vault, ChatAccounts.selected) { ChatAccounts.engine(vault) }
    val ctx = LocalContext.current
    SyncLoop(engine)
    var camOk by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var pasted by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(false) }
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        App.suppressLock = false
        camOk = ok
        if (!ok) App.toast("Ohne Kamera: Code als Text einfügen")
    }
    LaunchedEffect(Unit) {
        if (!camOk) { App.suppressLock = true; camPerm.launch(Manifest.permission.CAMERA) }
    }
    val code = if (engine.onion != null) engine.myCode() else null
    val qr = remember(code) { code?.let { qrBitmap(it, 600) } }

    fun found(text: String) {
        if (done) return
        if (handleAdd(engine, text)) { done = true; App.nav.pop() }
    }

    GlassBackground {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopBar(onBack = { App.nav.pop() }, horizontalPadding = 0.dp)
            Spacer(Modifier.height(10.dp))
            Text("Kontakt hinzufügen", style = G.largeTitle.copy(fontSize = 30.sp), modifier = Modifier.fillMaxWidth())
            Text("Scannt eure Codes gegenseitig.", style = G.callout.copy(color = G.dim), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(16.dp))

            // Kamera
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(sq(28)).background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.16f), sq(28)),
                contentAlignment = Alignment.Center
            ) {
                if (camOk) {
                    QrScanner(Modifier.fillMaxSize()) { found(it) }
                    ScanFrame(Modifier.fillMaxSize().padding(36.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Ic.camera, null, tint = G.dim, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.height(10.dp))
                        PillButton("Kamera erlauben", height = 42.dp) {
                            App.suppressLock = true; camPerm.launch(Manifest.permission.CAMERA)
                        }
                    }
                }
            }
            Spacer(Modifier.height(22.dp))

            // Eigener Code
            Text("DEIN CODE", style = G.caption, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().glass(sq(24)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(132.dp).clip(sq(18)).background(Color.White).padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qr != null) Image(qr, "Mein QR-Code", modifier = Modifier.fillMaxSize())
                    else CircularProgressIndicator(color = Color(0xFF0B0D12), strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(engine.myName.ifBlank { "Ich" }, style = G.headline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(if (qr != null) "Lass diesen Code scannen." else "Tor startet …", style = G.footnote)
                    if (code != null) {
                        Spacer(Modifier.height(10.dp))
                        PillButton("Kopieren", icon = Ic.copy, height = 38.dp) {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Tresor-Code", code))
                            App.toast("Code kopiert")
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            // Ohne Kamera
            Text("ODER CODE EINFÜGEN", style = G.caption, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            GlassField(pasted, { pasted = it }, "TRESOR1:…")
            Spacer(Modifier.height(10.dp))
            PillButton("Hinzufügen", enabled = pasted.trim().startsWith("TRESOR1:"), modifier = Modifier.fillMaxWidth()) { found(pasted) }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** Kamera-Scanner direkt im Bildschirm (kein eigenes Fenster, kein Querformat). */
@Composable
private fun QrScanner(modifier: Modifier, onCode: (String) -> Unit) {
    val ctx = LocalContext.current
    val view = remember {
        BarcodeView(ctx).apply {
            setUseTextureView(true) // sonst ignoriert die Kamera die abgerundeten Ecken
            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
        }
    }
    DisposableEffect(view) {
        view.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                result.text?.let { t -> if (t.startsWith("TRESOR1:")) onCode(t) }
            }

            override fun possibleResultPoints(resultPoints: List<ResultPoint>) {}
        })
        try { view.resume() } catch (_: Exception) {}
        onDispose {
            try { view.pause() } catch (_: Exception) {}
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}

/** Sucher-Ecken im Apple-Stil. */
@Composable
private fun ScanFrame(modifier: Modifier) {
    Canvas(modifier) {
        val l = size.minDimension * 0.18f
        val w = 4.dp.toPx()
        val c = Color.White
        val x0 = 0f
        val y0 = 0f
        val x1 = size.width
        val y1 = size.height
        // vier Ecken
        drawLine(c, Offset(x0, y0 + l), Offset(x0, y0), w, StrokeCap.Round); drawLine(c, Offset(x0, y0), Offset(x0 + l, y0), w, StrokeCap.Round)
        drawLine(c, Offset(x1 - l, y0), Offset(x1, y0), w, StrokeCap.Round); drawLine(c, Offset(x1, y0), Offset(x1, y0 + l), w, StrokeCap.Round)
        drawLine(c, Offset(x0, y1 - l), Offset(x0, y1), w, StrokeCap.Round); drawLine(c, Offset(x0, y1), Offset(x0 + l, y1), w, StrokeCap.Round)
        drawLine(c, Offset(x1 - l, y1), Offset(x1, y1), w, StrokeCap.Round); drawLine(c, Offset(x1, y1), Offset(x1, y1 - l), w, StrokeCap.Round)
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
    val engine = remember(vault, ChatAccounts.selected) { ChatAccounts.engine(vault) }
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
                TipLine(Ic.mic, "Sprachnachrichten", "Mikrofon-Taste tippen, sprechen, senden. Bis zu 5 Minuten. Die Aufnahme liegt danach nur verschlüsselt im Tresor.")
                TipLine(Ic.phone, "Anrufe über Tor", "Ohne Server und ohne Telefonnummer. Über Tor kommt die Sprache etwas verzögert an, ungefähr eine Sekunde. Ein Anruf klingelt auch bei gesperrtem Tresor, annehmen kannst du ihn nach dem Entsperren. Verlässt du die App, endet das Gespräch.")
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
