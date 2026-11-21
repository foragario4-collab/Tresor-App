package app.glass.tresor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import app.glass.tresor.App
import app.glass.tresor.Disguise
import app.glass.tresor.ui.AppTile
import app.glass.tresor.ui.Auth
import app.glass.tresor.ui.ConfirmSheet
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.GlassField
import app.glass.tresor.ui.GlassSwitch
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.PillButton
import app.glass.tresor.ui.RadioDot
import app.glass.tresor.ui.SectionLabel
import app.glass.tresor.ui.SettingRow
import app.glass.tresor.ui.Sheet
import app.glass.tresor.ui.SheetHeader
import app.glass.tresor.ui.TopBar
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq
import app.glass.tresor.vault.Protection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GlassGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().glass(sq(24)).padding(horizontal = 16.dp, vertical = 2.dp), content = content)
}

@Composable
private fun ChoiceRow(icon: ImageVector, color: Color, title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(sq(16), onClick = onClick).padding(vertical = 10.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppTile(icon, color, size = 46.dp, radius = 13, iconSize = 24.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = G.headline.copy(fontSize = 16.sp))
            Text(sub, style = G.footnote)
        }
        RadioDot(selected)
    }
}

private enum class PinSheet { NONE, CHANGE, NEWVAULT, PANIC }

@Composable
fun SettingsScreen() {
    val vault = App.vault ?: return
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    var bio by remember { mutableStateOf(vault.bioEnabled()) }
    var pinSheet by remember { mutableStateOf(PinSheet.NONE) }
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var p3 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmDeleteThis by remember { mutableStateOf(false) }
    var panicOn by remember { mutableStateOf(App.panicEnabled()) }
    var delOrig by remember { mutableStateOf(App.prefs.getBoolean("deleteOriginals", true)) }

    fun closeSheet() {
        pinSheet = PinSheet.NONE; p1 = ""; p2 = ""; p3 = ""; err = null
    }

    GlassBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(onBack = { App.nav.pop() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Einstellungen", style = G.largeTitle)

                SectionLabel("Tarnung")
                Text("So erscheint die App auf dem Startbildschirm.", style = G.footnote, modifier = Modifier.padding(start = 4.dp))
                GlassGroup {
                    ChoiceRow(Ic.lock, Color(0xFF5B4BFF), "Keine Tarnung", "App heißt „Tresor“ mit eigenem Logo", App.disguise == Disguise.TRESOR) { App.applyDisguise(Disguise.TRESOR) }
                    ChoiceRow(Ic.calc, Color(0xFF3A3F4B), "Rechner", "Code eintippen, dann =", App.disguise == Disguise.RECHNER) { App.applyDisguise(Disguise.RECHNER) }
                    ChoiceRow(Ic.calendar, Color(0xFFE5484D), "Kalender", "Heutiges Datum lange drücken", App.disguise == Disguise.KALENDER) { App.applyDisguise(Disguise.KALENDER) }
                    ChoiceRow(Ic.checklist, Color(0xFF2F9E5B), "Checkliste", "Eintrag mit deinem Code anlegen", App.disguise == Disguise.CHECKLISTE) { App.applyDisguise(Disguise.CHECKLISTE) }
                    ChoiceRow(Ic.news, G.blue, "News", "Titel „News“ lange drücken", App.disguise == Disguise.NEWS) { App.applyDisguise(Disguise.NEWS) }
                }
                Text(
                    "Das neue Icon erscheint nach ein paar Sekunden. Manche Startbildschirme brauchen einen Neustart des Launchers.",
                    style = G.footnote.copy(color = G.faint), modifier = Modifier.padding(start = 4.dp)
                )

                SectionLabel("Entsperren")
                GlassGroup {
                    if (Auth.available(activity)) {
                        SettingRow("Systemsperre", "Fingerabdruck oder Geräte-PIN") {
                            GlassSwitch(bio, "Systemsperre") { on ->
                                if (on) {
                                    val c = try { vault.bioEncryptCipher() } catch (e: Exception) { null }
                                    if (c == null) { App.toast("Nicht verfügbar"); return@GlassSwitch }
                                    Auth.prompt(activity, "Systemsperre aktivieren", null, c, onSuccess = { ok ->
                                        // Nur ein Tresor kann per Fingerabdruck öffnen – dieser hier
                                        App.vaults.filter { it !== vault }.forEach { if (it.bioEnabled()) it.disableBio() }
                                        vault.enableBio(ok); App.setBioSlot(vault.slot); bio = true
                                    }, onFail = {})
                                } else {
                                    vault.disableBio(); bio = false
                                }
                            }
                        }
                    }
                    SettingRow("Code ändern", null, showDivider = Auth.available(activity)) {
                        PillButton("Ändern", height = 38.dp) { pinSheet = PinSheet.CHANGE }
                    }
                    run {
                        SettingRow(
                            "Weiterer geheimer Tresor",
                            "Eigener Code öffnet einen eigenen, getrennten Tresor",
                            showDivider = true
                        ) {
                            PillButton("Anlegen", height = 38.dp) { pinSheet = PinSheet.NEWVAULT }
                        }
                        SettingRow(
                            "Notfall-Löschcode",
                            if (panicOn) "Aktiv – löscht alles und zeigt einen leeren Tresor" else "Ein Code, der sofort alles löscht",
                            showDivider = true
                        ) {
                            PillButton(if (panicOn) "Ändern" else "Einrichten", height = 38.dp) { pinSheet = PinSheet.PANIC }
                        }
                    }
                }
                run {
                    Text(
                        "Jeder Code öffnet nur seinen eigenen Tresor. Von innen sieht man nicht, ob oder wie viele andere es gibt. Der Löschcode funktioniert überall, wo du sonst deinen Code eingibst – auch im Rechner und während einer Sperrzeit. Alles wird sofort unwiderruflich vernichtet.",
                        style = G.footnote.copy(color = G.faint), modifier = Modifier.padding(start = 4.dp)
                    )
                }

                SectionLabel("Schutz")
                GlassGroup {
                    SettingRow("Screenshots blockieren", "Auch Bildschirmaufnahme und Vorschau in „Letzte Apps“") {
                        GlassSwitch(App.secure, "Screenshots") { App.changeSecure(it) }
                    }
                    SettingRow("Sperre bei Bewegung", "Sperrt sofort bei ruckartiger Bewegung", showDivider = true) {
                        GlassSwitch(App.motionLock, "Bewegung") { App.setMotion(it) }
                    }
                    if (App.motionLock) {
                        Column(Modifier.padding(vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Empfindlichkeit", style = G.body.copy(fontSize = 16.sp), modifier = Modifier.weight(1f))
                                Text(
                                    when {
                                        App.motionSens < 0.25f -> "Nur starkes Rucken"
                                        App.motionSens < 0.55f -> "Mittel"
                                        App.motionSens < 0.8f -> "Empfindlich"
                                        else -> "Sehr empfindlich"
                                    },
                                    style = G.footnote
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            app.glass.tresor.ui.GlassSlider(
                                App.motionSens, { App.setMotionSens(it, persist = false) },
                                onDone = { App.setMotionSens(App.motionSens, persist = true) }
                            )
                            Row {
                                Text("Weniger", style = G.footnote.copy(color = G.faint), modifier = Modifier.weight(1f))
                                Text("Mehr", style = G.footnote.copy(color = G.faint))
                            }
                        }
                    }
                    SettingRow("Sperre beim Umdrehen", "Display nach unten legen sperrt sofort", showDivider = true) {
                        GlassSwitch(App.flipLock, "Umdrehen") { App.changeFlipLock(it) }
                    }
                    SettingRow("Originale nach Import löschen", "Android fragt nach, bevor Fotos aus der Galerie gelöscht werden", showDivider = true) {
                        GlassSwitch(delOrig, "Originale löschen") { delOrig = it; App.prefs.edit().putBoolean("deleteOriginals", it).apply() }
                    }
                    SettingRow("Nach 10 Fehlversuchen löschen", "Löscht alle Tresore unwiderruflich", showDivider = true) {
                        GlassSwitch(App.wipeAfter10, "Selbstlöschung") { App.setWipe(it) }
                    }
                }

                SectionLabel("Updates")
                UpdateSection()

                SectionLabel("Sicherheit")
                GlassGroup {
                    InfoRow("Verschlüsselung", "AES-256-GCM")
                    InfoRow("Schlüssel aus Code", "PBKDF2-SHA512, 210.000 Runden", true)
                    InfoRow("Hardware-Schutz", "Android Keystore", true)
                    InfoRow("Internet", "Nur Browser, Chat (Tor), News, Updates", true)
                }

                Spacer(Modifier.height(6.dp))
                PillButton("Diesen Tresor löschen", textColor = G.red, modifier = Modifier.fillMaxWidth()) { confirmDeleteThis = true }
                PillButton("Alles löschen (alle Tresore)", textColor = G.red, modifier = Modifier.fillMaxWidth()) { confirmWipe = true }
            }
        }

        Sheet(pinSheet != PinSheet.NONE, { closeSheet() }) {
            val decoy = pinSheet == PinSheet.NEWVAULT
            val panic = pinSheet == PinSheet.PANIC
            val newOnly = decoy || panic
            SheetHeader(
                if (panic) Ic.trash else Ic.keypad,
                when { decoy -> "Neuer geheimer Tresor"; panic -> "Notfall-Löschcode"; else -> "Code ändern" },
                err ?: when {
                    decoy -> "Mit diesem Code öffnet sich ein eigener, leerer Tresor. Nutze ihn z. B. als harmlosen Schein-Tresor."
                    panic -> "Wer diesen Code eingibt, löscht sofort alles. Danach erscheint ein leerer Tresor."
                    else -> null
                }
            )
            if (!newOnly) {
                GlassField(p1, { p1 = it; err = null }, "Aktueller Code", password = true, numeric = true, autoFocus = true)
                Spacer(Modifier.height(10.dp))
            }
            GlassField(
                p2, { p2 = it; err = null },
                when { decoy -> "Code für den neuen Tresor"; panic -> "Löschcode"; else -> "Neuer Code" },
                password = true, numeric = true, autoFocus = newOnly
            )
            Spacer(Modifier.height(10.dp))
            GlassField(p3, { p3 = it; err = null }, "Wiederholen", password = true, numeric = true)
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                else PillButton("Speichern", primary = true, modifier = Modifier.fillMaxWidth(), enabled = p2.length >= (if (panic) 4 else 6) && p3.isNotEmpty()) {
                    if (p2 != p3) { err = "Stimmt nicht überein."; return@PillButton }
                    if (!p2.all { it.isDigit() } || p2.length > 12) { err = if (panic) "4 bis 12 Ziffern." else "6 bis 12 Ziffern."; return@PillButton }
                    busy = true
                    scope.launch {
                        val msg = withContext(Dispatchers.Default) {
                            if (panic) {
                                if (App.vaults.any { it.exists() && it.verifyPin(p2.toCharArray()) }) "Dieser Code öffnet schon einen Tresor."
                                else { App.setPanicCode(p2.toCharArray()); null }
                            } else if (App.isPanicCode(p2.toCharArray())) {
                                "Das ist dein Löschcode."
                            } else if (decoy) {
                                if (App.createVault(p2.toCharArray()) == null) "Dieser Code ist schon vergeben." else null
                            } else {
                                if (App.vaults.any { it !== vault && it.exists() && it.verifyPin(p2.toCharArray()) }) "Dieser Code ist schon vergeben."
                                else if (vault.changePin(p1.toCharArray(), p2.toCharArray())) null
                                else "Aktueller Code ist falsch."
                            }
                        }
                        busy = false
                        if (msg != null) err = msg else {
                            if (panic) panicOn = true
                            closeSheet()
                            App.toast(when { decoy -> "Neuer Tresor angelegt"; panic -> "Löschcode eingerichtet"; else -> "Code geändert" })
                        }
                    }
                }
            }
            if (panic && panicOn) {
                Spacer(Modifier.height(10.dp))
                PillButton("Löschcode entfernen", textColor = G.red, modifier = Modifier.fillMaxWidth()) {
                    App.removePanicCode(); panicOn = false; closeSheet()
                    App.toast("Löschcode entfernt")
                }
            }
        }

        ConfirmSheet(
            visible = confirmDeleteThis,
            title = "Diesen Tresor löschen?",
            text = "Alles in diesem Tresor wird unwiderruflich gelöscht. Andere Tresore bleiben unberührt.",
            confirm = "Löschen",
            onDismiss = { confirmDeleteThis = false }
        ) {
            confirmDeleteThis = false
            if (vault === App.vaultA) {
                scope.launch {
                    withContext(Dispatchers.IO) { vault.resetContents() }
                    App.toast("Tresor geleert")
                    App.nav.pop()
                }
            } else App.deleteCurrentVault()
        }

        ConfirmSheet(
            visible = confirmWipe,
            title = "Alles löschen?",
            text = "Alle Tresore mit allen Fotos, Videos, Dateien, Notizen und Chats werden unwiderruflich gelöscht.",
            confirm = "Endgültig löschen",
            onDismiss = { confirmWipe = false }
        ) {
            confirmWipe = false
            App.wipeEverything()
        }
    }
}

@Composable
private fun InfoRow(title: String, value: String, divider: Boolean = false) {
    SettingRow(title, null, showDivider = divider) { Text(value, style = G.footnote) }
}

// ───────────────────────── Ordner-Einstellungen ─────────────────────────

@Composable
fun FolderSettingsScreen(fid: String) {
    val vault = App.vault ?: return
    val activity = LocalContext.current as FragmentActivity
    val scope = rememberCoroutineScope()
    val f = vault.folder(fid)
    var target by remember { mutableStateOf<Protection?>(null) }
    var s1 by remember { mutableStateOf("") }
    var s2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }

    if (f == null || !vault.isFolderUnlocked(fid)) {
        GlassBackground { TopBar(onBack = { App.nav.pop() }) }
        return
    }

    fun close() { target = null; s1 = ""; s2 = ""; err = null }
    val isChat = f.kind == app.glass.tresor.vault.FolderKind.CHAT
    val what = if (isChat) "Konto" else "Ordner"

    fun apply(p: Protection, secret: String?, sys: javax.crypto.Cipher?) {
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.Default) { vault.setProtection(fid, p, secret?.toCharArray(), sys) }
            busy = false
            if (ok) {
                close()
                App.toast("Schutz geändert")
            } else err = if (p == Protection.PASSWORD) "Passwort ungültig." else "Tresor-Code ist falsch."
        }
    }

    GlassBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(onBack = { App.nav.pop() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Schutz für „${f.name}“", style = G.largeTitle.copy(fontSize = 30.sp))
                Text(if (isChat) "Wie dieses Chat-Konto geöffnet wird." else "Wie dieser Ordner geöffnet wird.", style = G.callout.copy(color = G.dim))
                Spacer(Modifier.height(4.dp))
                GlassGroup {
                    ProtRow(Ic.folder, "Kein extra Schutz", "Offen, sobald der Tresor offen ist", f.protection == Protection.NONE) {
                        if (f.protection != Protection.NONE) apply(Protection.NONE, null, null)
                    }
                    ProtRow(Ic.keypad, "Tresor-PIN", "Derselbe Code wie beim Öffnen", f.protection == Protection.PIN) { target = Protection.PIN }
                    ProtRow(Ic.keyboard, "Eigenes Passwort", "Buchstaben, Zahlen, Zeichen", f.protection == Protection.PASSWORD) { target = Protection.PASSWORD }
                    if (Auth.available(activity)) {
                        ProtRow(Ic.fingerprint, "Systemsperre", "Fingerabdruck – PIN geht immer als Notfall", f.protection == Protection.SYSTEM) { target = Protection.SYSTEM }
                    }
                }
                GlassGroup {
                    SettingRow("$what verstecken", if (isChat) "Unsichtbar. Im Chat lange auf „Chat“ drücken und Code eingeben" else "Unsichtbar. Erscheint nur mit seinem Code oder Fingerabdruck") {
                        GlassSwitch(f.hidden, "Ausblenden") { vault.setHidden(fid, it) }
                    }
                    if (f.isProtected) {
                        SettingRow("Sofort wieder sperren", "Beim Verlassen des Ordners", showDivider = true) {
                            GlassSwitch(f.relock, "Wieder sperren") { vault.setRelock(fid, it) }
                        }
                    }
                }
                Text(
                    "Geschützte Ordner haben einen eigenen Schlüssel. Auch bei offenem Tresor bleiben die Dateien verschlüsselt, bis der Ordner entsperrt wird.\n\nVersteckte Ordner hervorholen: in der Ordner-Übersicht lange auf den Titel drücken und den Code eingeben. Es erscheinen genau die Ordner mit diesem Code. Ordner mit gleichem Passwort öffnen sich zusammen, „Mit Fingerabdruck“ öffnet alle mit Systemsperre. Versteckte Ordner ohne extra Schutz erscheinen mit dem Tresor-Code.",
                    style = G.footnote.copy(color = G.faint), modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(Modifier.height(6.dp))
                PillButton("Umbenennen", modifier = Modifier.fillMaxWidth()) { newName = f.name; rename = true }
                PillButton("$what löschen", textColor = G.red, modifier = Modifier.fillMaxWidth()) { confirmDelete = true }
            }
        }

        Sheet(target != null, { close() }) {
            val t = target ?: Protection.PIN
            val isPw = t == Protection.PASSWORD
            SheetHeader(
                if (isPw) Ic.keyboard else if (t == Protection.SYSTEM) Ic.fingerprint else Ic.keypad,
                if (isPw) "Ordner-Passwort" else "Mit Tresor-Code bestätigen",
                err ?: if (isPw) "Ohne dieses Passwort kann niemand den Ordner öffnen – auch du nicht. Gut merken!" else null
            )
            GlassField(s1, { s1 = it; err = null }, if (isPw) "Neues Passwort" else "Tresor-Code", password = true, numeric = !isPw, autoFocus = true)
            if (isPw) {
                Spacer(Modifier.height(10.dp))
                GlassField(s2, { s2 = it; err = null }, "Wiederholen", password = true)
            }
            Spacer(Modifier.height(14.dp))
            Box(Modifier.fillMaxWidth().height(52.dp), contentAlignment = Alignment.Center) {
                if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                else PillButton("Übernehmen", primary = true, modifier = Modifier.fillMaxWidth(), enabled = s1.length >= 4) {
                    if (isPw && s1 != s2) { err = "Stimmt nicht überein."; return@PillButton }
                    if (t == Protection.SYSTEM) {
                        val c = vault.sysGroupCipher()
                        if (c == null) { err = "Systemsperre nicht verfügbar."; return@PillButton }
                        val secret = s1
                        Auth.prompt(activity, "Systemsperre für Ordner", null, c,
                            onSuccess = { ok -> apply(Protection.SYSTEM, secret, ok) }, onFail = {})
                    } else apply(t, s1, null)
                }
            }
        }

        Sheet(rename, { rename = false }) {
            SheetHeader(Ic.edit, "Umbenennen", null)
            GlassField(newName, { newName = it }, "Name", autoFocus = rename)
            Spacer(Modifier.height(14.dp))
            PillButton("Speichern", primary = true, enabled = newName.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                vault.renameFolder(fid, newName.trim()); rename = false
            }
        }

        ConfirmSheet(
            visible = confirmDelete,
            title = "$what löschen?",
            text = if (isChat) "Alle Kontakte und Nachrichten dieses Kontos werden endgültig gelöscht." else "Der Ordner und sein gesamter Inhalt werden endgültig gelöscht.",
            confirm = "Löschen",
            onDismiss = { confirmDelete = false }
        ) {
            confirmDelete = false
            App.nav.pop()
            if (App.nav.top is app.glass.tresor.Screen.FolderView) App.nav.pop()
            if (isChat) {
                app.glass.tresor.chat.ChatEngine.forgetAccount(vault, fid)
                app.glass.tresor.chat.ChatAccounts.reset()
            }
            scope.launch { withContext(Dispatchers.IO) { vault.deleteFolder(fid) } }
        }
    }
}

@Composable
private fun ProtRow(icon: ImageVector, title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().pressable(sq(16), onClick = onClick).padding(vertical = 12.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).glass(sq(12), Color.White.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = G.headline.copy(fontSize = 16.sp))
            Text(sub, style = G.footnote)
        }
        RadioDot(selected)
    }
}


// ───────────────────────── Updates ─────────────────────────

@Composable
private fun UpdateSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var release by remember { mutableStateOf<app.glass.tresor.Updater.Release?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var showToken by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf(app.glass.tresor.Updater.token()) }
    val up = app.glass.tresor.Updater

    fun check() {
        checking = true; status = null; release = null
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { up.latest() } }
            checking = false
            r.onSuccess { rel ->
                if (rel != null && up.isNewer(rel)) release = rel
                else status = "Du hast die neueste Version."
            }.onFailure { status = it.message ?: "Keine Verbindung." }
        }
    }

    fun installNow(rel: app.glass.tresor.Updater.Release) {
        if (!up.canInstall(ctx)) {
            App.toast("Bitte „Apps installieren“ erlauben und zurückkehren")
            up.openInstallPermission(ctx)
            return
        }
        progress = 0f
        scope.launch {
            val f = withContext(Dispatchers.IO) {
                runCatching { up.download(ctx, rel) { p -> scope.launch { progress = p } } }
            }
            progress = null
            f.onSuccess { apk ->
                if (up.sameSigner(ctx, apk)) up.install(ctx, apk)
                else {
                    release = null
                    status = "Diese Version ist mit einem anderen Schlüssel signiert (seit dem Sicherheits-Update). Android kann sie nicht drüber installieren: Tresor-Inhalt sichern, App einmal deinstallieren und die neue APK von GitHub installieren. Danach klappen Updates wieder."
                }
            }.onFailure { status = it.message ?: "Download fehlgeschlagen." }
        }
    }

    GlassGroup {
        SettingRow("Version", "Build ${up.currentBuild}") {
            Text(up.currentName, style = G.footnote)
        }
        val rel = release
        SettingRow(
            if (rel != null) "Neue Version ${rel.name.ifBlank { "1.${rel.build}" }}" else "Nach Updates suchen",
            when {
                progress != null -> "Wird geladen … ${((progress ?: 0f) * 100).toInt()} %"
                rel != null -> "Bereit zum Installieren · ${app.glass.tresor.ui.formatSize(rel.size)}"
                else -> status
            },
            showDivider = true
        ) {
            when {
                checking || progress != null -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                rel != null -> PillButton("Installieren", primary = true, height = 38.dp) { installNow(rel) }
                else -> PillButton("Prüfen", height = 38.dp) { check() }
            }
        }
        SettingRow("Privates Repo", if (token.isBlank()) "Nur nötig, wenn dein GitHub-Repo privat ist" else "Token hinterlegt", showDivider = true) {
            PillButton(if (showToken) "Fertig" else "Token", height = 38.dp) {
                if (showToken) up.setToken(token)
                showToken = !showToken
            }
        }
        if (showToken) {
            GlassField(token, { token = it }, "GitHub-Token (nur Lesen)", password = true, onDone = { up.setToken(token); showToken = false })
            Spacer(Modifier.height(12.dp))
        }
    }
}
