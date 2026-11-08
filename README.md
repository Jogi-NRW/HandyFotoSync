# Fotos → FRITZ!NAS

Die App kopiert Fotos und Videos von deinem Android-Handy automatisch auf die Festplatte an deiner FRITZ!Box.

- ✅ **Nur im WLAN.** Es werden nie mobile Daten verbraucht.
- ✅ **Nur neue Fotos.** Was schon auf der Festplatte liegt, wird übersprungen.
- ✅ **Automatisch.** Kurz nach jedem neuen Foto und zusätzlich einmal pro Stunde.
- ✅ **Alles in einem Ordner:** `Handy-Fotos-Videos`, ohne Unterordner.
- ✅ **Nichts wird gelöscht**, weder auf dem Handy noch auf der Festplatte.

**Du brauchst:** ein Android-Handy mit Android 10 oder neuer und eine FRITZ!Box mit angeschlossener USB-Festplatte.

---

## Schritt 1: FRITZ!Box einrichten

Am PC im Browser **http://fritz.box** öffnen und mit dem FRITZ!Box-Kennwort anmelden.

**1. Festplatte prüfen**
*Heimnetz → USB / Speicher*: Die Festplatte muss in der Liste stehen, und die **Speicher-(NAS)-Funktion** muss aktiv sein.

**2. Netzlaufwerk einschalten**
Gleiche Seite, Abschnitt **Heimnetzfreigabe**:
- Haken setzen bei **„Zugriff über ein Netzlaufwerk (SMB) aktiv“**
- Freigabename **FRITZ.NAS** nicht ändern
- „Unterstützung für SMBv1“ bleibt **aus**
- **Übernehmen** klicken

**3. Benutzer fürs Handy anlegen**
*System → FRITZ!Box-Benutzer → Benutzer hinzufügen*:
- Name: z. B. **handy**
- Kennwort: ein eigenes (aufschreiben!)
- Berechtigungen: **nur „Zugang zu NAS-Inhalten“** anhaken
- **Verzeichnis hinzufügen** → „Alle an der FRITZ!Box verfügbaren Speicher“ → **Lesen und Schreiben**
- **Übernehmen** klicken (eventuell an der FRITZ!Box per Taste bestätigen)

> 💡 **Gegenprobe am PC:** Im Windows-Explorer oben `\\fritz.box\FRITZ.NAS` eingeben und mit dem Benutzer *handy* anmelden. Siehst du deine Festplatte, ist die FRITZ!Box richtig eingestellt.

---

## Schritt 2: APK herunterladen und installieren

**1. Herunterladen**
- **https://github.com/Jogi-NRW/HandyFotoSync/actions** öffnen und bei GitHub anmelden.
- Den obersten Lauf mit grünem Haken ✅ anklicken.
- Ganz unten unter **Artifacts** auf **FotoSync-APK** tippen. Es wird eine **ZIP-Datei** geladen.

**2. Entpacken**
- Die ZIP in der App **„Eigene Dateien“** (Samsung) bzw. **„Dateien“** öffnen und **Entpacken** wählen.
- Darin liegt **`app-release.apk`**.

**3. Installieren**
- Auf **`app-release.apk`** tippen und dann auf **Installieren**.
- Fragt Android nach „unbekannten Apps“: **Einstellungen → „Aus dieser Quelle zulassen“**, dann zurück.
- Warnt Play Protect: **„Weitere Details“ → „Trotzdem installieren“**. Das ist normal, weil die App nicht aus dem Play Store kommt.

> ⚠️ **Samsung:** Ist die **„Automatische Sperre“** an, lässt sich die APK nicht installieren.
> *Einstellungen → Sicherheit und Datenschutz → Automatische Sperre → Aus.* Nach der Installation kannst du sie wieder einschalten.

**Updates:** Neue Versionen werden einfach über die alte installiert, ohne vorher zu deinstallieren. Deine Einstellungen bleiben erhalten.

---

## Schritt 3: App einrichten

Das Handy muss dabei im **normalen Heim-WLAN** sein, **nicht im Gast-WLAN**.

1. App **„Fotos → FRITZ!NAS“** öffnen.
2. Berechtigungen erlauben. Bei Fotos und Videos **„Alle erlauben“** wählen, **nicht** „Ausgewählte Fotos“.
3. Felder ausfüllen:

   | Feld | Eintrag |
   |---|---|
   | Adresse der FRITZ!Box | **`192.168.178.1`** (empfohlen, siehe Hinweis) |
   | Freigabename | `FRITZ.NAS` |
   | FRITZ!Box-Benutzer | `handy` |
   | Kennwort | dein Kennwort aus Schritt 1 |

4. Auf **„Festplatte auswählen …“** tippen und die Festplatte antippen. Der Zielordner wird automatisch `<Festplatte>/Handy-Fotos-Videos`.
5. Die App testet die Verbindung, legt den Ordner an und fragt: **„Jetzt übertragen?“** → **Ja**.

> 💡 **Warum `192.168.178.1` statt `fritz.box`?**
> Manche Handys, z. B. mit „Privatem DNS“, übersetzen `fritz.box` in eine falsche Internet-Adresse. Die Zahl funktioniert immer.
> Hat deine FRITZ!Box eine andere Adresse, findest du sie unter *Einstellungen → WLAN → Zahnrad bei deinem WLAN → Router/Gateway*.

**Schalter in der App:**

| Schalter | Bedeutung | Standard |
|---|---|---|
| Videos auch übertragen | Videos werden mitkopiert | an |
| Nur Kamera-Fotos | ohne Screenshots, WhatsApp-Bilder usw. | aus |
| Automatisch übertragen, sobald ich im WLAN bin | läuft im Hintergrund | an |

**Wichtig für den Hintergrundbetrieb (vor allem bei Samsung):**
*Einstellungen → Apps → Fotos → FRITZ!NAS → Akku → **„Nicht eingeschränkt“***

---

## Im Alltag

- Du musst **nichts** tun. Neue Fotos werden kurz nach der Aufnahme übertragen, sobald du im Heim-WLAN bist.
- Unterwegs passiert nichts. Alles wird automatisch nachgeholt, wenn du wieder zu Hause bist.
- Während einer Übertragung siehst du eine Benachrichtigung *„Übertrage 12 von 40 …“*.
- In der App zeigt **Status** den letzten Lauf, darunter steht der **Verlauf**.
- **„Jetzt übertragen“** startet sofort eine Übertragung.
- Haben zwei verschiedene Fotos denselben Namen, bekommt das zweite einen Zusatz (`IMG_1234_1.jpg`). Es wird nichts überschrieben.
- **„Merkliste zurücksetzen“**: Die App gleicht dann beim nächsten Mal alles neu mit der Festplatte ab. Dabei entstehen **keine** doppelten Kopien.

---

## Fehler und Lösungen

### Bei der Installation

| Was passiert | Lösung |
|---|---|
| Datei lässt sich nicht öffnen | Du hast die **ZIP** angetippt. Erst entpacken, dann `app-release.apk` antippen. |
| Nichts passiert / Installation blockiert (Samsung) | **Automatische Sperre** ausschalten (siehe Schritt 2). |
| „Aus Sicherheitsgründen … unbekannte Apps“ | „Aus dieser Quelle zulassen“ erlauben. |
| Play Protect warnt | „Weitere Details“ → „Trotzdem installieren“. |
| „App nicht installiert“ beim Update | Alte Version mit anderem Schlüssel: die App **einmal deinstallieren**, dann neu installieren. |
| „App nicht installiert“ / „Fehler beim Parsen“ | Android ist älter als Version 10. Die App läuft darauf leider nicht. |

### Beim Verbinden

| Meldung in der App | Ursache und Lösung |
|---|---|
| **„Keine Verbindung zur FRITZ!Box …“** | 1. Als Adresse **`192.168.178.1`** eintragen statt `fritz.box`. Das ist die häufigste Ursache.<br>2. Ist das Handy im **Gast-WLAN**? Ins normale WLAN wechseln.<br>3. Läuft eine **VPN-App** oder ein Werbeblocker? Kurz ausschalten.<br>4. In der FRITZ!Box prüfen: **„Zugriff über ein Netzlaufwerk (SMB)“** aktiv? |
| **„FRITZ!Box nicht gefunden …“** | Handy nicht im Heim-WLAN, oder `fritz.box` wird nicht erkannt → **`192.168.178.1`** eintragen. |
| **„Anmeldung abgelehnt …“** | Benutzername oder Kennwort falsch (Groß-/Kleinschreibung beachten!), oder beim Benutzer fehlt **„Zugang zu NAS-Inhalten“**. Nach mehreren Fehlversuchen sperrt die FRITZ!Box kurz. Dann ein paar Minuten warten. |
| **„Zugriff verweigert …“** | Der Benutzer hat nur Leserecht. In der FRITZ!Box beim Benutzer unter „Verzeichnis“ **Lesen und Schreiben** einstellen. |
| **„Freigabe oder Pfad nicht gefunden …“** | Freigabename muss **FRITZ.NAS** heißen, oder der Festplattenname im Zielordner stimmt nicht. Erneut **„Festplatte auswählen …“** tippen. |
| **„Keine Festplatte gefunden“** | Festplatte nicht angeschlossen oder in der FRITZ!Box nicht aktiv, oder der Benutzer hat keinen Zugriff darauf. Den Zielordner notfalls von Hand eintragen: `Festplattenname/Handy-Fotos-Videos`. Den Namen findest du in der FRITZ!Box unter *Heimnetz → USB / Speicher*. |

> 🔍 **Fehler nicht dabei?** Im Fehlerfenster auf **„Technische Details“** tippen. Die App prüft dann Adresse, Netzwerk und Anmeldung, das dauert bis zu 30 Sekunden. Mit **„Kopieren“** kannst du das Ergebnis weitergeben.
> Steht dort bei *aufgelöst* eine Adresse, die **nicht** mit `192.168.` beginnt, ist `fritz.box` falsch aufgelöst worden → `192.168.178.1` eintragen.

### Im Betrieb

| Was passiert | Lösung |
|---|---|
| Status: **„Kein WLAN – übersprungen“** | Normal, wenn du unterwegs bist. Wird automatisch nachgeholt. |
| Status: **„Keine Berechtigung für Fotos“** | App öffnen und den Zugriff auf **alle** Fotos erlauben. |
| Es werden nur einzelne Fotos übertragen | Beim Fotozugriff wurde „Ausgewählte Fotos“ gewählt. Unter *Einstellungen → Apps → Fotos → FRITZ!NAS → Berechtigungen → Fotos und Videos* auf **„Immer alle erlauben“** stellen. |
| Übertragung klappt nur, wenn die App offen ist | Akku auf **„Nicht eingeschränkt“** stellen (siehe Schritt 3). |
| Videos fehlen | Schalter **„Videos auch übertragen“** einschalten und die Berechtigung für Videos erlauben. |
| Screenshots oder WhatsApp-Bilder fehlen | Schalter **„Nur Kamera-Fotos“** ausschalten. |
| Erste Übertragung dauert sehr lange | Normal bei vielen Fotos. Handy im WLAN und am Ladekabel lassen. Bei Unterbrechung geht es beim nächsten Mal an der Stelle weiter. |
| Ein Foto steht im Verlauf als Fehler | Wird beim nächsten Lauf automatisch erneut versucht. |

---

## Sicherheit

- Das Kennwort wird nur im privaten Speicher der App auf dem Handy gespeichert.
- Deshalb gibt es einen **eigenen FRITZ!Box-Benutzer**, der nur auf die Festplatte zugreifen darf und nicht auf die FRITZ!Box-Einstellungen.
- Die App überträgt nur innerhalb deines Heimnetzes. Es geht nichts ins Internet.
