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
„Ordner ausblenden“ versteckt ihn komplett. Sichtbar wird er erst durch langes Drücken auf den Titel (Fotos / Videos / Dateien / Notizen).

## Mehrere geheime Tresore

Einstellungen → „Weiterer geheimer Tresor“ → eigenen Code festlegen.
Jeder Code öffnet nur seinen eigenen Tresor. Von innen sieht man nicht, ob oder wie viele andere es gibt.
Nur ein Tresor kann per Fingerabdruck öffnen (der, in dem die Systemsperre zuletzt eingeschaltet wurde).

## Chat (über Tor, ohne Server)

Startseite → Chat → Anzeigename → „Chat einrichten“. Tor startet (beim ersten Mal 30–60 Sekunden).
Kontakt hinzufügen: QR-Code des anderen scannen oder seinen Text-Code einfügen. Der andere bekommt eine Anfrage und nimmt sie an.
Nachrichten kommen an, sobald beide Handys online sind. Im Gespräch auf den Namen tippen → Sicherheitsnummer vergleichen.
Damit es im Hintergrund zuverlässig läuft: Chat → Tipps → Akku-Optimierung ausschalten.

## Privater Browser

Startseite → Browser. Kein Verlauf, Cookies und Website-Daten werden beim Sperren gelöscht.
Lesezeichen sind verschlüsselt, Downloads landen verschlüsselt unter Dateien → Downloads.

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
- Sperre beim Verlassen der App und bei Displaysperre, optional bei Bewegung
- Screenshot-Sperre, Tarn-PIN mit Schein-Tresor, optional Löschen nach 10 Fehlversuchen
- Kein Backup, keine Cloud, keine Tracker. Nur die News-Tarnung nutzt das Internet (RSS).

## Testen mit scrcpy

Die Screenshot-Sperre blockiert auch die Bildschirmübertragung (scrcpy zeigt dann schwarz).
Zum Testen: Tresor → Einstellungen → „Screenshots blockieren“ ausschalten.
