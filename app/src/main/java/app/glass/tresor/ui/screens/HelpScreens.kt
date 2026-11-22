package app.glass.tresor.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
    Chapter(Ic.lock, "Öffnen & Sperren", listOf(
        "Der Tresor öffnet sich nur mit deinem Code (6–12 Ziffern) oder per Fingerabdruck/Gesicht, wenn eingeschaltet.",
        "Wann gesperrt wird, stellst du unter Einstellungen → „Sperren“ ein: sofort, bei Displaysperre, nach 1/5/15 Minuten oder erst beim Schließen.",
        "Optional sperrt die App bei Bewegung oder wenn du das Handy umdrehst.",
    )),
    Chapter(Ic.eyeOff, "Tarnung & unsichtbare App", listOf(
        "Einstellungen → Tarnung: Rechner, Kalender, Checkliste, News, Musik, Notizen, Wetter oder Browser. Alle funktionieren wirklich.",
        "Rechner: Code eintippen, dann „=“. Notizen/Musik: Code ins Suchfeld. Wetter: Code in die Städtesuche oder Stadtnamen lange drücken. Browser: Code in die Adresszeile.",
        "„App verstecken“ entfernt das Symbol ganz. Öffnen dann über die Telefon-App: *#*#8737#*#* wählen – oder deinen eigenen Code, den du in den Einstellungen festlegst. Die App prüft, ob er schon belegt ist.",
    )),
    Chapter(Ic.grid, "Kategorien schützen", listOf(
        "Fotos & Videos, Dateien, Notizen und Musik können jeweils einen eigenen Schlüssel bekommen: PIN, Passwort oder Fingerabdruck/Gesicht.",
        "Dazu die Kategorie auf der Startseite lange drücken. Ohne den Schlüssel ist ihr Inhalt auch bei offenem Tresor nicht lesbar.",
    )),
    Chapter(Ic.folder, "Ordner schützen & verstecken", listOf(
        "Ordner lange drücken → Schutz: Tresor-PIN, eigenes Passwort oder Systemsperre.",
        "„Ordner verstecken“ macht ihn unsichtbar. Hervorholen: unten die Leiste „Neuer Ordner“ lange drücken und den Code eingeben.",
        "Ein offener versteckter Ordner verschwindet wieder beim Verlassen – oder sofort über das Schloss-Symbol am Ordner.",
    )),
    Chapter(Ic.camera, "Eigene Kamera", listOf(
        "Fotos und Videos aus der eingebauten Kamera landen direkt verschlüsselt im Tresor – nie in der Galerie.",
        "Ortsdaten (EXIF) werden entfernt.",
    )),
    Chapter(Ic.chat, "Chat & Anrufe über Tor", listOf(
        "Nachrichten und Anrufe gehen direkt von Handy zu Handy über Tor – ohne Server, ohne Telefonnummer.",
        "Kontakt hinzufügen: unten auf „Kontakt hinzufügen“ – Kamera, darunter dein QR-Code und dein Code als Text zum Kopieren.",
        "Auf „Tor verbunden“ tippen zeigt, über welche Länder und Knoten die Verbindung gerade läuft.",
        "Versteckte Chat-Konten: unten „Kontakt hinzufügen“ lange drücken oder lange auf „Chat“ drücken und den Code eingeben.",
    )),
    Chapter(Ic.globe, "Privater Browser", listOf(
        "Mehrere Tabs, Favoriten, Werbeblocker. Kein Verlauf, Cookies werden beim Sperren gelöscht.",
        "Menü ⋯ → „Über Tor surfen“: Webseiten sehen deine IP nicht, .onion-Seiten funktionieren. WebRTC ist gesperrt, damit nichts an Tor vorbei geht.",
        "Für höchste Anonymität: „Im echten Tor Browser öffnen“ (die App muss installiert sein).",
        "Geheime Bereiche: oben auf den Bereichsnamen tippen → „Neuer Bereich“, dann über „Schutz & Verstecken“ sichern. Lange auf den Namen drücken holt versteckte Bereiche hervor.",
        "VPN: Proton VPN lässt sich nicht einbauen. Läuft die Proton-App, geht auch der Browser darüber – das Menü zeigt „VPN aktiv“.",
    )),
    Chapter(Ic.keyboard, "Eigene Tastatur", listOf(
        "Codes und Passwörter tippst du über die eingebaute Tastatur. So kann keine fremde Tastatur-App mitlesen.",
        "Abschaltbar unter Einstellungen → „Sichere Tastatur“.",
    )),
    Chapter(Ic.trash, "Notfall-Löschcode", listOf(
        "Universeller Löschcode: löscht alle Tresore. Zusätzlich hat jeder Tresor einen eigenen Löschcode, der nur diesen Tresor löscht.",
        "Nach dem Löschen öffnet sich ein leerer Tresor – von außen sieht alles normal aus.",
    )),
)

@Composable
fun HandbookScreen() {
    GlassBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(onBack = { App.nav.pop() })
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 14.dp, bottom = 40.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Handbuch", style = G.largeTitle)
                Text("Alles Wichtige auf einen Blick.", style = G.footnote)
                handbook.forEach { ch ->
                    Column(Modifier.fillMaxWidth().glass(sq(22)).padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(ch.icon, null, tint = G.accentText, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(ch.title, style = G.headline.copy(fontSize = 17.sp))
                        }
                        ch.lines.forEach { l ->
                            Row(Modifier.padding(top = 8.dp)) {
                                Text("•", style = G.callout.copy(color = G.dim))
                                Spacer(Modifier.width(8.dp))
                                Text(l, style = G.callout.copy(color = Color.White.copy(alpha = 0.88f)))
                            }
                        }
                    }
                }
                Text(
                    "Tipps erneut anzeigen", style = G.callout.copy(color = G.accentText),
                    modifier = Modifier.pressable(Pill) { Tips.resetAll(); App.toast("Tipps werden wieder angezeigt") }.padding(vertical = 8.dp, horizontal = 4.dp)
                )
            }
        }
    }
}
