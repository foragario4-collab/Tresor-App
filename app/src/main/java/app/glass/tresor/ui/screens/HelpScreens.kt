package app.glass.tresor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glass.tresor.App
import app.glass.tresor.ui.G
import app.glass.tresor.ui.GlassBackground
import app.glass.tresor.ui.Ic
import app.glass.tresor.ui.Pill
import app.glass.tresor.ui.TopBar
import app.glass.tresor.ui.glass
import app.glass.tresor.ui.pressable
import app.glass.tresor.ui.sq

/** Einmalige Tipps beim ersten Mal. */
object Tips {
    fun seen(key: String) = App.prefs.getBoolean("tip_$key", false)
    fun markSeen(key: String) = App.prefs.edit().putBoolean("tip_$key", true).apply()
    fun resetAll() {
        val e = App.prefs.edit()
        App.prefs.all.keys.filter { it.startsWith("tip_") }.forEach { e.remove(it) }
        e.apply()
    }
}

/** Kleine Tipp-Karte, die nur einmal erscheint („Verstanden“ blendet sie für immer aus). */
@Composable
fun TipCard(key: String, title: String, text: String, modifier: Modifier = Modifier) {
    var show by remember(key) { mutableStateOf(!Tips.seen(key)) }
    AnimatedVisibility(show, modifier = modifier, exit = shrinkVertically() + fadeOut()) {
        Row(
            Modifier.fillMaxWidth().glass(sq(20), G.accent.copy(alpha = 0.18f)).padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Ic.info, null, tint = G.accentText, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = G.headline.copy(fontSize = 15.sp))
                Spacer(Modifier.height(2.dp))
                Text(text, style = G.footnote.copy(color = Color.White.copy(alpha = 0.85f)))
                Spacer(Modifier.height(8.dp))
                Text(
                    "Verstanden", style = G.callout.copy(color = G.accentText),
                    modifier = Modifier.pressable(Pill) { Tips.markSeen(key); show = false }.padding(vertical = 4.dp)
                )
            }
        }
    }
}

private data class Chapter(val icon: ImageVector, val title: String, val lines: List<String>)

private val handbook = listOf(
    Chapter(Ic.info, "Erste Schritte", listOf(
        "Beim ersten Start legst du deinen Tresor-Code fest (6–12 Ziffern). Er ist der Hauptschlüssel – ohne ihn kommt niemand an deine Daten, auch wir nicht.",
        "Alles im Tresor ist verschlüsselt (AES-256), auch Vorschaubilder und Namen. Nichts geht in eine Cloud, es gibt kein Backup.",
        "Wichtig: Die App nie deinstallieren – dann ist der Tresor-Inhalt unwiderruflich weg. Updates installierst du einfach drüber.",
        "Mehrere geheime Tresore: Einstellungen → „Weiterer geheimer Tresor“. Jeder Code öffnet nur seinen eigenen Tresor. Von innen sieht man nicht, ob es andere gibt.",
    )),
    Chapter(Ic.lock, "Öffnen & Sperren", listOf(
        "Öffnen mit deinem Code oder – wenn eingeschaltet – mit Fingerabdruck/Gesicht (nur wenn das Handy die Gesichtserkennung als „stark“ einstuft).",
        "Einstellungen → „Sperren“: sofort beim Verlassen, bei Displaysperre, nach 1, 5 oder 15 Minuten oder erst beim Schließen der App.",
        "Zusätzlich: Sperre bei Bewegung (Empfindlichkeit per Regler) und beim Umdrehen (Display nach unten).",
        "Nach mehreren falschen Codes wird die Eingabe kurz gebremst. Optional: Alles löschen nach 10 Fehlversuchen.",
        "Screenshots und Bildschirmaufnahmen sind gesperrt (abschaltbar zum Testen).",
    )),
    Chapter(Ic.eyeOff, "Tarnung & unsichtbare App", listOf(
        "Einstellungen → Tarnung. Alle Tarn-Apps funktionieren wirklich – so fällt nichts auf.",
        "Rechner: Code eintippen (ohne Rechenzeichen), dann „=“.",
        "Kalender: das heutige Datum lange drücken. Checkliste: Code als neuen Eintrag eingeben. News: den Titel lange drücken.",
        "Musik und Notizen: Code ins Suchfeld, dann „Suchen“. Wetter: Code in die Städtesuche oder den Stadtnamen lange drücken (mit Widget für den Startbildschirm). Browser: Code in die Adresszeile.",
        "„App verstecken“ entfernt das Symbol ganz. Geöffnet wird dann über die Telefon-App mit *#*#8737#*#* – oder deinem eigenen Telefoncode.",
        "Eigener Telefoncode: Einstellungen → Tarnung → Telefoncode → „Ändern“. Die App prüft, ob der Code schon vom System oder einer anderen App belegt ist. Vor dem Verstecken einmal testen – bis dahin gilt der alte Code weiter.",
    )),
    Chapter(Ic.grid, "Kategorien mit eigenem Schlüssel", listOf(
        "Fotos & Videos, Dateien, Notizen und Musik können je einen eigenen Schlüssel bekommen: Tresor-PIN, eigenes Passwort oder Fingerabdruck/Gesicht.",
        "Dazu die Kategorie auf der Startseite lange drücken. Ohne diesen Schlüssel ist der Inhalt auch bei offenem Tresor unlesbar und unsichtbar.",
        "Beim Verlassen der Kategorie schließt sie sich wieder.",
    )),
    Chapter(Ic.folder, "Ordner schützen & verstecken", listOf(
        "Ordner lange drücken → Schutz: Tresor-PIN, eigenes Passwort oder Systemsperre (Fingerabdruck; der Tresor-Code funktioniert immer als Notfall).",
        "„Ordner verstecken“ macht ihn komplett unsichtbar. Hervorholen: unten die Leiste „Neuer Ordner“ lange drücken, Code eingeben – es erscheinen genau die Ordner mit diesem Code.",
        "Mit Fingerabdruck öffnen sich alle Ordner mit Systemsperre auf einmal.",
        "Ein falscher Code zeigt nur „Kein Ordner gefunden“ – niemand erkennt, ob es weitere versteckte Ordner gibt.",
        "Wieder verstecken: das Schloss am Ordner tippen oder einfach die Übersicht verlassen.",
    )),
    Chapter(Ic.camera, "Fotos, Videos & Kamera", listOf(
        "Die eingebaute Kamera speichert Fotos und Videos direkt verschlüsselt – nie in der normalen Galerie. Ortsdaten werden entfernt.",
        "Beim Import aus der Galerie fragt Android, ob die Originale gelöscht werden dürfen.",
        "Videos werden direkt aus der verschlüsselten Datei abgespielt, ohne entschlüsselte Kopie.",
    )),
    Chapter(Ic.music, "Dateien, Notizen & Musik", listOf(
        "Dateien jeder Art, verschlüsselte Notizen und eigene Musik (MP3, M4A, FLAC …) mit Player im Apple-Stil.",
        "„Musik im Hintergrund“: Das aktuelle Lied läuft weiter, auch wenn der Tresor sperrt. Die Mitteilung zeigt keinen Titel.",
    )),
    Chapter(Ic.chat, "Chat", listOf(
        "Chat ohne Server und ohne Telefonnummer: Jedes Handy ist über eine eigene Tor-Adresse erreichbar.",
        "Kontakt hinzufügen: unten „Kontakt hinzufügen“. Oben die Kamera, darunter dein QR-Code und dein Code als Text. Am besten scannt ihr euch gegenseitig persönlich.",
        "„Nicht geprüft“: Kontakte, die nur per Text-Code hinzugefügt wurden. Im Gespräch auf den Namen tippen, Sicherheitsnummer persönlich oder am Telefon vergleichen, dann „Nummer stimmt überein“.",
        "Mehrere Konten (z. B. „Arbeit“): oben auf das Konto tippen. Konten lassen sich wie Ordner schützen und verstecken. Versteckte Konten: unten „Kontakt hinzufügen“ lange drücken und Code eingeben.",
        "Gemeinsame Einstellungen (gelten für beide, der andere muss zustimmen): Löschen für beide, selbstlöschende Nachrichten, Anrufe, Sprachnachrichten.",
        "Sprachnachrichten: bei leerem Textfeld die Mikrofon-Taste.",
    )),
    Chapter(Ic.refresh, "Zustellung ohne Server", listOf(
        "Ist der andere offline, wartet die Nachricht verschlüsselt auf deinem Handy (Uhr-Symbol).",
        "Kommt er wieder online, meldet sich sein Handy kurz bei seinen Kontakten („Ich bin wieder da“) – deins schickt sofort nach.",
        "Zusätzlich wird es immer wieder versucht und sofort, wenn das Internet wieder da ist. Doppelte Nachrichten erscheinen nie doppelt.",
        "Damit es im Hintergrund zuverlässig läuft: Chat → Tipps → Akku-Optimierung für die App ausschalten.",
    )),
    Chapter(Ic.phone, "Anrufe", listOf(
        "Im Gespräch oben auf das Telefon tippen. Der Anruf läuft direkt von Handy zu Handy über Tor.",
        "Für jeden Anruf entstehen neue Einmal-Schlüssel – nach dem Auflegen sind sie weg.",
        "Über Tor kommt die Sprache etwa 0,5–2 Sekunden verzögert an. Das ist der Preis für die Anonymität.",
    )),
    Chapter(Ic.shield, "Verschlüsselung", listOf(
        "Jede Nachricht hat einen eigenen Einmal-Schlüssel und ist mit AES-256-GCM verschlüsselt. Nur das Handy des Empfängers kann sie lesen.",
        "Wechselnde Schlüssel: Jedes Handy erzeugt täglich einen neuen Empfangsschlüssel und löscht alte nach 30 Tagen. Selbst wer später einen Schlüssel stiehlt, kann alte Nachrichten nicht öffnen.",
        "Alle Nachrichten werden auf feste Größen aufgefüllt – man sieht nicht, ob es Text oder eine Sprachnachricht war.",
        "Grenze: Gegen ein entsperrt abgenommenes oder verseuchtes Handy hilft keine Verschlüsselung.",
    )),
    Chapter(Ic.flake, "Tor & Snowflake", listOf(
        "Einstellungen → Tor-Verbindung: „Direkt“ (am schnellsten), „Snowflake“ oder „Eigene Brücken“.",
        "Snowflake lässt Tor wie einen Videoanruf aussehen. Dein Internetanbieter erkennt dann nicht einmal, dass du Tor benutzt. Etwas langsamer, erster Start dauert 1–2 Minuten.",
        "Eigene Brücken (obfs4, webtunnel) bekommst du auf bridges.torproject.org – für Länder mit starker Zensur.",
        "Läuft Tor, gehen auch Update-Prüfung, Wetter, News und Bilder über Tor.",
        "Im Chat auf „Tor verbunden“ tippen zeigt, über welche Länder und Knoten die Verbindung gerade läuft.",
        "Ein VPN brauchst du für die App nicht. Für andere Apps auf dem Handy kann es trotzdem sinnvoll sein.",
    )),
    Chapter(Ic.globe, "Privater Browser", listOf(
        "Mehrere Tabs, Favoriten, Werbeblocker (Listen wie bei uBlock und Brave). Kein Verlauf – Cookies werden beim Sperren gelöscht.",
        "Menü ⋯ → „Über Tor surfen“: Webseiten sehen deine IP nicht, .onion-Seiten funktionieren. Vor der Tor-Verbindung lädt nichts, WebRTC ist gesperrt.",
        "„Neue Tor-Identität“ baut neue Wege auf. JavaScript lässt sich abschalten. Für höchste Anonymität: „Im echten Tor Browser öffnen“.",
        "Geheime Bereiche: oben auf den Bereichsnamen tippen → „Neuer Bereich“, mit „Schutz & Verstecken“ sichern. Lange auf den Namen drücken holt versteckte Bereiche hervor.",
        "Downloads landen verschlüsselt in Dateien → Downloads.",
    )),
    Chapter(Ic.keyboard, "Eigene Tastatur & Code-Eingabe", listOf(
        "Codes und Passwörter tippst du über die eingebaute Tastatur – keine fremde Tastatur-App kann mitlesen. Abschaltbar unter „Sichere Tastatur“.",
        "Bei versteckten Ordnern erscheint ein Ziffernblock wie am iPhone. Unten links: Fingerabdruck. „Code mit Buchstaben“ wechselt zur vollen Tastatur.",
    )),
    Chapter(Ic.trash, "Notfall-Löschcode", listOf(
        "Universeller Löschcode: löscht alle Tresore – überall eingegeben, wo ein Code verlangt wird, auch in den Tarn-Apps.",
        "Zusätzlich kann jeder Tresor einen eigenen Löschcode haben, der nur diesen Tresor löscht.",
        "Danach öffnet sich ein leerer Tresor – von außen sieht alles normal aus.",
    )),
    Chapter(Ic.refresh, "Updates", listOf(
        "Einstellungen → Updates → „Prüfen“ → „Installieren“. Beim ersten Mal „Apps installieren“ für die Tresor-App erlauben.",
        "Es wird nur eine wirklich neue Version angeboten – gleicher Code wird erkannt, auch wenn er neu gebaut wurde.",
        "Privates GitHub-Repo: einen Lese-Token unter „Privates Repo → Token“ eintragen.",
    )),
)

@Composable
fun HandbookScreen() {
    var open by remember { mutableStateOf(setOf(0)) }
    GlassBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(onBack = { App.nav.pop() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Handbuch", style = G.largeTitle)
                Text("Alle Funktionen und geheimen Zugänge. Tippe auf ein Thema.", style = G.footnote)
                Spacer(Modifier.height(4.dp))
                handbook.forEachIndexed { idx, ch ->
                    val isOpen = idx in open
                    Column(
                        Modifier.fillMaxWidth().glass(sq(22))
                            .pressable(sq(22), scaleTo = 0.99f) { open = if (isOpen) open - idx else open + idx }
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(ch.icon, null, tint = G.accentText, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(ch.title, style = G.headline.copy(fontSize = 17.sp), modifier = Modifier.weight(1f))
                            Icon(Ic.chevronDown, if (isOpen) "Zuklappen" else "Aufklappen", tint = G.dim,
                                modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = if (isOpen) 180f else 0f })
                        }
                        AnimatedVisibility(isOpen, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                            Column(Modifier.padding(top = 4.dp)) {
                                ch.lines.forEach { l ->
                                    Row(Modifier.padding(top = 9.dp)) {
                                        Text("•", style = G.callout.copy(color = G.accentText))
                                        Spacer(Modifier.width(10.dp))
                                        Text(l, style = G.callout.copy(color = Color.White.copy(alpha = 0.9f), lineHeight = 21.sp))
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Alle aufklappen", style = G.callout.copy(color = G.accentText),
                        modifier = Modifier.pressable(Pill) { open = handbook.indices.toSet() }.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                    Text(
                        "Tipps erneut anzeigen", style = G.callout.copy(color = G.accentText),
                        modifier = Modifier.pressable(Pill) { Tips.resetAll(); App.toast("Tipps werden wieder angezeigt") }.padding(vertical = 8.dp, horizontal = 4.dp)
                    )
                }
            }
        }
    }
}
