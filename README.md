# Tresor

Verschlüsselter Tresor für Fotos, Videos, Dateien, Notizen und Musik, mit Chat und Anrufen über Tor. Getarnt als Rechner, Kalender, Checkliste, News, Musik, Notizen, Wetter oder Browser. Liquid-Glass-Design. Ein Handbuch ist in der App eingebaut (Einstellungen → Handbuch).

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

## Oberfläche: das „zweite Handy“

Nach dem Entsperren sieht der Tresor aus wie ein eigenes Handy:
- **Startbildschirm** mit 3D-Symbolen, Uhr-Widget (live), Favoriten-Widget (Kontakte aus dem Chat), Suche und Dock (Telefon, Nachrichten, Kamera, Browser).
- Symbol einer Kategorie **lange drücken** = eigener Schlüssel (Fotos & Videos, Dateien, Notizen, Musik). Ein kleines Schloss zeigt geschützte Kategorien.
- Kamera-Symbol: tippen = Foto, lange drücken = Video. Das Schloss oben rechts sperrt sofort.
- **Telefon-App** (Dock): Favoriten, Zuletzt (alle Anrufe), Kontakte, Sprachnachrichten – alles über den Tor-Chat, ohne Telefonnummer.
- **Kontakte-App**: A–Z mit Suche, „Meine Karte“ (QR-Code), Kontakt mit Nachricht/Anrufen/Sprache/Favorit, eigenem Spitznamen, Geburtstag und Notiz (nur verschlüsselt bei dir, wird nie verschickt) und Sicherheitsnummer.
- **Kontrollzentrum** (oben nach unten wischen): Browser über Tor, Snowflake, Chat im Hintergrund, Kamera, Musik, Bewegungs-/Umdreh-Sperre, eigene Tastatur, Bewegungs-Empfindlichkeit, Jetzt sperren.
- **Sperrbildschirm** wie am iPhone: Datum, große Uhrzeit, runde Glastasten mit Buchstaben, Fingerabdruck unten links.
- Kalender, Passwörter und Sprachmemo sind als Symbole schon da und kommen mit den nächsten Updates.

## Erster Start

- Code festlegen (6–12 Ziffern).
- Die App heißt „Tresor“ mit eigenem Logo. Tarnung bei Bedarf unter Einstellungen → Tarnung.

## Geheime Zugänge

| Tarnung | So öffnet sich der Tresor |
|---|---|
| Rechner | Code eintippen (ohne Rechenzeichen), dann `=` |
| Kalender | Das heutige Datum lange drücken |
| Checkliste | Den Code als neuen Eintrag eingeben |
| News | Den Titel „News“ lange drücken |
| Musik | Code ins Suchfeld, dann „Suchen“ |
| Notizen | Code ins Suchfeld, dann „Suchen“ |
| Wetter | Code in die Städtesuche, oder den Stadtnamen lange drücken |
| Browser | Code in die Adresszeile, dann „Los“ |

Tarnung wechseln: Tresor → Einstellungen → Tarnung. Alle Tarn-Apps funktionieren wirklich:
- **Rechner** wie beim Samsung: ganzer Rechenweg mit Klammern und Prozent, das Ergebnis erscheint schon beim Tippen.
- **News** mit Bildern (ORF, Der Standard, Tagesschau).
- **Wetter** (Open-Meteo, ohne Konto): Stadt suchen, optional „Mit GPS orten“ (weniger privat, braucht die Standort-Erlaubnis). Dazu gibt es ein Widget für den Startbildschirm, solange die Wetter-Tarnung aktiv ist.
- **Browser** mit Tabs, Favoriten und Werbeblocker.

**App komplett verstecken:** Einstellungen → „App verstecken“. Das Symbol verschwindet ganz. Öffnen über die Telefon-App: `*#*#8737#*#*` wählen (manche Handys zeigen stattdessen eine Mitteilung „Tippen zum Öffnen“).

## Kategorien mit eigenem Schlüssel

Fotos & Videos, Dateien, Notizen und Musik können je einen eigenen Schlüssel bekommen: PIN, Passwort oder Fingerabdruck/Gesicht (Gesicht nur, wenn das Handy es als „stark“ einstuft). Dazu die Kategorie auf der Startseite lange drücken. Ohne diesen Schlüssel ist der Inhalt auch bei offenem Tresor nicht lesbar.

## Geschützte Ordner

Ordner lange drücken → Schutz wählen: Tresor-PIN, eigenes Passwort oder Systemsperre (Fingerabdruck, die PIN funktioniert immer als Notfall).
„Ordner verstecken“ macht ihn komplett unsichtbar. Hervorholen: unten die Leiste „Neuer Ordner“ lange drücken (gut mit dem Daumen erreichbar) oder lange auf den Titel drücken und den Code eingeben – auf einem Ziffernblock wie am iPhone (unten links Fingerabdruck, „Code mit Buchstaben“ für Passwörter). Das Schloss am Ordner versteckt ihn sofort wieder.

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
Nachrichten kommen automatisch an, ganz ohne Server: Ist der andere offline, wartet die Nachricht verschlüsselt auf deinem Handy (Uhr-Symbol). Kommt er wieder online, gibt sein Handy allen Kontakten kurz Bescheid („Ich bin wieder da“, ohne Inhalt) und deins schickt sofort nach. Zusätzlich wird es pro Kontakt immer wieder versucht (nach 10 s, 20 s, 40 s … höchstens alle 5 Minuten) und sofort, wenn das Internet wieder da ist (z. B. Wechsel WLAN ↔ Mobilfunk). Ein Kontakt, der offline ist, hält die Zustellung an andere nicht auf. Im Gespräch auf den Namen tippen → Sicherheitsnummer vergleichen.
Damit es im Hintergrund zuverlässig läuft: Chat → Tipps → Akku-Optimierung ausschalten.

**Verschlüsselung (Version 2):**
- Jede Nachricht hat einen eigenen Einmal-Schlüssel (X25519) und ist mit AES-256-GCM verschlüsselt. Absender und Empfänger werden über ihre festen Schlüssel bestätigt, Manipulationen werden erkannt.
- **Wechselnde Empfangsschlüssel:** Jedes Handy erzeugt täglich einen neuen Empfangsschlüssel (solange der Tresor offen ist) und teilt ihn den Kontakten mit. Ersetzte Schlüssel werden nach 30 Tagen gelöscht. Wird später ein Schlüssel gestohlen, lassen sich mitgeschnittene alte Nachrichten trotzdem nicht öffnen (Vorwärts-Geheimhaltung).
- **Aufgefüllte Größe:** Jede Nachricht wird vor dem Verschlüsseln auf feste Größenstufen aufgefüllt (512 B, 2 KB, 8 KB … ab 1 MB volle MB). Man sieht nicht, ob es Text oder eine Sprachnachricht war.
- Ältere App-Versionen bleiben kompatibel: Bis ein Kontakt seinen ersten Empfangsschlüssel geschickt hat, wird wie bisher verschlüsselt.
- **„Nicht geprüft“:** Kontakte, die nur per kopiertem Text-Code hinzugefügt wurden, sind als nicht geprüft markiert. Im Gespräch auf den Namen tippen, die Sicherheitsnummer persönlich oder am Telefon vergleichen, dann „Nummer stimmt überein“. Per QR-Code gescannte Kontakte gelten als geprüft.
- Grenzen: Ein kompromittiertes (z. B. entsperrt abgenommenes oder verseuchtes) Handy kann keine Verschlüsselung schützen. Der Code ist nicht unabhängig geprüft (Audit empfohlen vor Verkauf).

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

## Tor-Verbindung & Snowflake

Einstellungen → „Tor-Verbindung“:
- **Direkt:** am schnellsten. Der Internetanbieter sieht, dass Tor benutzt wird, aber nicht wofür.
- **Snowflake:** Tor-Verkehr sieht aus wie ein Videoanruf (WebRTC). Niemand erkennt, dass Tor benutzt wird. Erster Start 1–2 Minuten.
- **Eigene Brücken:** obfs4-/webtunnel-Zeilen von bridges.torproject.org einfügen (eine pro Zeile).

Gilt für Chat, Anrufe und den Tor-Modus im Browser. Läuft Tor, gehen auch Update-Prüfung, Wetter, News und Bilder über Tor (inklusive Namensauflösung). Ein VPN ist für die App nicht nötig.
Technik: IPtProxy (Snowflake/Lyrebird) des Guardian Project. Die APK enthält nur noch ARM-Prozessorteile (alle echten Handys), das hält sie kleiner.

## Musik

Neue Kategorie „Musik“ auf der Startseite: eigene Ordner, Import von MP3, M4A, FLAC, OGG und anderen Formaten (Titel, Interpret und Cover werden übernommen). Originale können wie bei Fotos danach gelöscht werden.
- Player im Apple-Stil: Cover, Fortschritt zum Ziehen, Zurück, Abspielen/Pause, Weiter, Zufall, Wiederholen (alle / ein Lied). Unten erscheint ein Mini-Player.
- Das laufende Lied wird nur im Arbeitsspeicher entschlüsselt, nie als Datei.
- „Musik im Hintergrund“ (Einstellungen, standardmäßig aus): Das aktuelle Lied läuft weiter, auch wenn der Tresor sperrt. Die Mitteilung zeigt nur „Wiedergabe“, ohne Titel. Das nächste Lied gibt es erst nach dem Entsperren.
- Tarnung „Musik“: ein echter Musikplayer für die normale Musik auf dem Handy. Code ins Suchfeld tippen und „Suchen“ drücken öffnet den Tresor.

## Privater Browser

Startseite → Browser. Kein Verlauf, Cookies und Website-Daten werden beim Sperren gelöscht. Mehrere Tabs (Symbol mit der Zahl unten rechts), Favoriten, JavaScript abschaltbar.

**Über Tor surfen** (Menü ⋯): Alle Seiten laufen über Tor, Webseiten sehen deine IP nicht, .onion-Adressen funktionieren. Solange Tor nicht verbunden ist, lädt der Browser gar nichts, damit nie etwas am Tor vorbei geht. WebRTC (kann sonst die echte IP verraten) ist gesperrt. „Neue Tor-Identität“ baut neue Wege auf. Für höchste Anonymität gibt es „Im echten Tor Browser öffnen“ (die Tor-Browser-App muss installiert sein).

**VPN:** Proton VPN lässt sich nicht in die App einbauen. Läuft die Proton-App, geht der Browser automatisch darüber, das Menü zeigt dann „VPN aktiv“.

**Geheime Bereiche:** Oben auf den Bereichsnamen tippen → „Neuer Bereich“. Jeder Bereich hat eigene Tabs und Favoriten und lässt sich über „Schutz & Verstecken“ wie ein Ordner schützen und verstecken. Lange auf den Bereichsnamen drücken holt versteckte Bereiche per Code hervor.

Lesezeichen sind verschlüsselt, Downloads landen verschlüsselt unter Dateien → Downloads.
Werbeblocker: blockiert Werbung und Tracker mit EasyList und EasyPrivacy (dieselben Listen wie uBlock und Brave). GitHub lädt sie bei jedem Build frisch herunter. Das Schild oben zeigt, wie viel blockiert wurde. Dort kannst du den Blocker pro Seite oder ganz ausschalten.

## Chat-Details

- Auf „Tor verbunden“ tippen zeigt, über welche Knoten (Land, Name, IP) die Verbindungen gerade laufen.
- „Kontakt hinzufügen“: oben die Kamera, darunter dein QR-Code und dein Code als Text zum Kopieren.

## Eigene Kamera und eigene Tastatur

- Fotos und Videos aus der eingebauten Kamera landen direkt verschlüsselt im Tresor, nie in der Galerie. Ortsdaten werden entfernt.
- **Tresor-Tastatur:** Alle Eingaben im Tresor (Chat, Notizen, Suche, Adresszeile, Codes, Passwörter) laufen über die eingebaute Tastatur. Die System-Tastatur wird für diese Felder gar nicht gestartet – keine fremde Tastatur-App liest mit. Umlaute per langem Druck, Ziffern/Zeichen, Emojis. In der Tarnung erscheint bewusst die normale Tastatur. Abschaltbar.

## Sperren

Einstellungen → „Sperren“: sofort beim Verlassen, bei Displaysperre, nach 1 / 5 / 15 Minuten oder erst beim Schließen der App.

## Notfall-Löschcode

Einstellungen → „Notfall-Löschcode“. Wird dieser Code irgendwo eingegeben (Sperrbildschirm, Rechner, Checkliste – auch während einer Sperrzeit), passiert Folgendes:
1. Die Schlüssel im Hardware-Chip werden gelöscht. Damit sind alle Daten sofort unlesbar.
2. Alle Dateien werden gelöscht.
3. Ein neuer, leerer Tresor öffnet sich. Sein Code ist der Löschcode, von außen sieht es aus wie ein ganz normaler leerer Tresor.

Zusätzlich kann jeder Tresor einen eigenen Löschcode haben, der nur diesen einen Tresor löscht.

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
- Kein Backup, keine Cloud, keine Tracker. Internet nutzen nur Chat/Tor, Browser, News (RSS) und Wetter (Open-Meteo).

## Testen mit scrcpy

Die Screenshot-Sperre blockiert auch die Bildschirmübertragung (scrcpy zeigt dann schwarz).
Zum Testen: Tresor → Einstellungen → „Screenshots blockieren“ ausschalten.
