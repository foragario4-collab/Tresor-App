# Tresor

Verschlüsselter Tresor für Fotos, Videos, Dateien und Notizen, getarnt als Rechner, Kalender, Checkliste oder News-App. Liquid-Glass-Design.

## APK bauen (GitHub)

1. Den gesamten Inhalt dieses Ordners in ein GitHub-Repository hochladen (z. B. `Tresor-App`).
2. GitHub baut automatisch: Reiter **Actions** → „APK bauen“.
3. Nach ca. 5–8 Minuten unten bei **Artifacts** → `Tresor-APK` herunterladen, entpacken, APK installieren.

## Signierschlüssel (einmalig einrichten)

Der Schlüssel, mit dem die APK signiert wird, liegt **nicht** im Repo, sondern als GitHub Secret.
Repo → Settings → Secrets and variables → Actions → „New repository secret“:
- `KEYSTORE_PASSWORD`
- `KEYSTORE_BASE64`

Die Werte stehen in der separaten Datei `GitHub-Secrets.txt` (nie ins Repo hochladen!).
Weil jede Version mit demselben Schlüssel signiert ist, lassen sich Updates einfach drüber installieren.
**Wichtig:** Die App nie deinstallieren, sonst ist der Tresor-Inhalt weg.

## Erster Start

- Code festlegen (6–12 Ziffern).
- Die App heißt „Tresor“ mit eigenem Logo. Tarnung bei Bedarf unter Einstellungen → Tarnung.

## Geheime Zugänge

| Tarnung | So öffnet sich der Tresor |
|---|---|
| Rechner | Code eintippen, dann `=` |
| Kalender | Das heutige Datum lange drücken |
| Checkliste | Den Code als neuen Eintrag eingeben |
| News | Den Titel „News“ lange drücken |

Tarnung wechseln: Tresor → Einstellungen → Tarnung.

## Geschützte Ordner

Ordner lange drücken → Schutz wählen: Tresor-PIN, eigenes Passwort oder Systemsperre (Fingerabdruck, die PIN funktioniert immer als Notfall).
„Ordner verstecken“ macht ihn komplett unsichtbar. Hervorholen: lange auf den Titel drücken (Fotos / Videos / Dateien / Notizen) und den Code eingeben.

- Ein Code öffnet genau die Ordner, die diesen Code als Passwort haben (z. B. 4711 → „Urlaub“ und „Privat“, 9999 → nur „Geheim“).
- „Mit Fingerabdruck“ öffnet mit einem einzigen Abdruck alle Ordner mit Systemsperre.
- Der Tresor-Code holt Ordner mit „Tresor-PIN“ und versteckte Ordner ohne extra Schutz hervor.
- Ein falscher Code zeigt nur „Kein Ordner gefunden“, man sieht nicht, ob es weitere versteckte Ordner gibt.
- Beim Verlassen der Übersicht verschwinden sie wieder. Nach mehreren Fehlversuchen wird die Eingabe kurz gebremst.
- Werden die Fingerabdrücke im System geändert, gehen Systemsperre-Ordner weiter mit dem Tresor-Code.

## Mehrere geheime Tresore

Einstellungen → „Weiterer geheimer Tresor“ → eigenen Code festlegen.
Jeder Code öffnet nur seinen eigenen Tresor. Von innen sieht man nicht, ob oder wie viele andere es gibt.
Nur ein Tresor kann per Fingerabdruck öffnen (der, in dem die Systemsperre zuletzt eingeschaltet wurde).

## Chat (über Tor, ohne Server)

Startseite → Chat → Anzeigename → „Chat einrichten“. Tor startet (beim ersten Mal 30–60 Sekunden).
Kontakt hinzufügen: QR-Code des anderen scannen oder seinen Text-Code einfügen. Der andere bekommt eine Anfrage und nimmt sie an.
Nachrichten kommen an, sobald beide Handys online sind. Im Gespräch auf den Namen tippen → Sicherheitsnummer vergleichen.
Damit es im Hintergrund zuverlässig läuft: Chat → Tipps → Akku-Optimierung ausschalten.

**Mehrere Chat-Konten:** Im Chat oben auf das Konto tippen → „Neues Konto“ (z. B. „Arbeit“). Jedes Konto hat eigenen Namen, eigene Tor-Adresse, eigene Kontakte und einen eigenen QR-Code. Über „Schutz & Verstecken“ lässt sich ein Konto wie ein Galerie-Ordner mit Passwort, Tresor-PIN oder Systemsperre schützen und verstecken. Versteckte Konten holst du hervor, indem du lange auf „Chat“ drückst und den Code eingibst. Die Daten geschützter Konten sind mit dem eigenen Konto-Schlüssel verschlüsselt. Nachrichten an ein gesperrtes Konto werden gesammelt und erscheinen nach dem Öffnen.

**Kontakt hinzufügen:** ein Bildschirm, immer hochkant. Oben die Kamera, darunter dein eigener QR-Code, so könnt ihr euch gleichzeitig scannen.

**Gemeinsame Chat-Einstellungen** (im Gespräch auf den Namen tippen → Chat-Einstellungen): Löschen für beide, selbstlöschende Nachrichten (1 Std. / 1 Tag / 1 Woche), Anrufe, Sprachnachrichten. Sie gelten immer für beide. Nach „Kontakt benachrichtigen“ bekommt der andere ein Fenster mit Annehmen/Ablehnen, bis dahin ruht der Chat.

**Kontakt entfernen:** Der andere bekommt den Hinweis „… hat dich als Kontakt entfernt“. Ist „Löschen für beide“ vereinbart, wird der Chat auch bei ihm gelöscht. Pakete und Anrufe entfernter Kontakte werden schon im Hintergrund verworfen, das Handy vibriert also nicht mehr.

**Banner:** Kommt eine Nachricht, während der Tresor offen ist, erscheint oben ein Banner. Tippen öffnet den Chat.

**Sprachnachrichten:** Im Gespräch bei leerem Textfeld die Mikrofon-Taste tippen, sprechen, auf Senden tippen (bis 5 Minuten, Opus). Die Aufnahme liegt nur kurz im privaten App-Speicher und wird danach überschrieben und gelöscht. Danach existiert sie nur verschlüsselt im Tresor und wird direkt aus dem Speicher abgespielt.

**Anrufe:** Im Gespräch oben auf das Telefon tippen. Die Verbindung läuft direkt von Handy zu Handy über Tor, ohne Server.
- Für jeden Anruf entstehen neue Einmal-Schlüssel (X25519), nach dem Auflegen sind sie weg.
- Das Gespräch ist mit AES-256-GCM verschlüsselt, ein Zähler schützt gegen Wiederholung.
- Nur bestätigte Kontakte können anrufen.
- Sprache: 8 kHz µ-law (Telefonqualität). Über Tor kommt sie etwa 0,5 bis 2 Sekunden verzögert an.
- Ein Anruf klingelt auch bei gesperrtem Tresor (Mitteilung „Eingehender Anruf“, ohne Namen). Annehmen kannst du ihn nach dem Entsperren des richtigen Tresors, innerhalb von 45 Sekunden.
- Wenn du die App verlässt oder mit der Seitentaste sperrst, endet das Gespräch. Am Ohr schaltet der Näherungssensor den Bildschirm ab, das Gespräch läuft dabei weiter.

## Musik

Neue Kategorie „Musik“ auf der Startseite: eigene Ordner, Import von MP3, M4A, FLAC, OGG und anderen Formaten (Titel, Interpret und Cover werden übernommen). Originale können wie bei Fotos danach gelöscht werden.
- Player im Apple-Stil: Cover, Fortschritt zum Ziehen, Zurück, Abspielen/Pause, Weiter, Zufall, Wiederholen (alle / ein Lied). Unten erscheint ein Mini-Player.
- Das laufende Lied wird nur im Arbeitsspeicher entschlüsselt, nie als Datei.
- „Musik im Hintergrund“ (Einstellungen, standardmäßig aus): Das aktuelle Lied läuft weiter, auch wenn der Tresor sperrt. Die Mitteilung zeigt nur „Wiedergabe“, ohne Titel. Das nächste Lied gibt es erst nach dem Entsperren.
- Tarnung „Musik“: ein echter Musikplayer für die normale Musik auf dem Handy. Code ins Suchfeld tippen und „Suchen“ drücken öffnet den Tresor.

## Privater Browser

Startseite → Browser. Kein Verlauf, Cookies und Website-Daten werden beim Sperren gelöscht.
Lesezeichen sind verschlüsselt, Downloads landen verschlüsselt unter Dateien → Downloads.
Werbeblocker: blockiert Werbung und Tracker mit EasyList und EasyPrivacy (dieselben Listen wie uBlock und Brave). GitHub lädt sie bei jedem Build frisch herunter. Das Schild oben zeigt, wie viel blockiert wurde. Dort kannst du den Blocker pro Seite oder ganz ausschalten.

## Notfall-Löschcode

Einstellungen → „Notfall-Löschcode“. Wird dieser Code irgendwo eingegeben (Sperrbildschirm, Rechner, Checkliste – auch während einer Sperrzeit), passiert Folgendes:
1. Die Schlüssel im Hardware-Chip werden gelöscht. Damit sind alle Daten sofort unlesbar.
2. Alle Dateien werden gelöscht.
3. Ein neuer, leerer Tresor öffnet sich. Sein Code ist der Löschcode, von außen sieht es aus wie ein ganz normaler leerer Tresor.

## In-App-Updates

Bei jedem Upload zu GitHub baut Actions automatisch eine neue Version und veröffentlicht sie als **Release**.
In der App: Einstellungen → Updates → „Prüfen“ → „Installieren“.
- Beim ersten Mal fragt Android, ob die App andere Apps installieren darf: erlauben und zurückkehren.
- **Öffentliches Repo:** funktioniert direkt.
- **Privates Repo:** auf GitHub einen Token erstellen (Settings → Developer settings → Fine-grained tokens → nur dieses Repo, „Contents: Read-only“) und in der App unter „Privates Repo → Token“ eintragen.

## Sicherheit

- AES-256-GCM für alle Inhalte, auch Vorschaubilder und Namen
- Schlüssel aus dem Code: PBKDF2-HMAC-SHA512, 210.000 Runden, plus Hardware-Schicht im Android Keystore (StrongBox, falls vorhanden)
- Jeder geschützte Ordner hat einen eigenen Schlüssel
- Videos werden blockweise direkt aus der verschlüsselten Datei abgespielt
- Sperre beim Verlassen der App und bei Displaysperre, optional bei Bewegung (Empfindlichkeit per Regler) und beim Umdrehen (Display nach unten)
- Screenshot-Sperre, Tarn-PIN mit Schein-Tresor, optional Löschen nach 10 Fehlversuchen
- Kein Backup, keine Cloud, keine Tracker. Nur die News-Tarnung nutzt das Internet (RSS).

## Testen mit scrcpy

Die Screenshot-Sperre blockiert auch die Bildschirmübertragung (scrcpy zeigt dann schwarz).
Zum Testen: Tresor → Einstellungen → „Screenshots blockieren“ ausschalten.
