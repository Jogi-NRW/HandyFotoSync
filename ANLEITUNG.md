# Fotos → FRITZ!NAS – Anleitung

Die App kopiert Fotos (und auf Wunsch Videos) von deinem Android-Handy auf die Festplatte an deiner FRITZ!Box:

- **nur im WLAN** – nie über mobile Daten
- **nur neue Dateien** – was schon auf der Festplatte liegt, wird übersprungen (Prüfung über Dateiname + Größe direkt auf der FRITZ!Box)
- **automatisch** – stündlich und zusätzlich kurz nach jedem neuen Foto, sobald du im WLAN bist
- Ablage: alles direkt in einem Ordner, ohne Unterordner: `Handy-Fotos-Videos/IMG_1234.jpg`. Haben zwei verschiedene Fotos denselben Namen, bekommt das zweite einen Zusatz (`IMG_1234_1.jpg`), damit nichts überschrieben wird.
- Nichts wird gelöscht, weder auf dem Handy noch auf der Festplatte

Voraussetzung: Android 10 oder neuer.

---

## Teil 1: FRITZ!Box einrichten (am PC unter http://fritz.box)

1. **Speicher aktivieren:** *Heimnetz → USB / Speicher*. Die Festplatte muss dort erscheinen, und die Speicher-(NAS)-Funktion muss aktiv sein.
2. **Netzlaufwerk einschalten:** Im Reiter bzw. Abschnitt *Heimnetzfreigabe* einen Haken bei **„Zugriff über ein Netzlaufwerk (SMB) aktiv“** setzen. Der Freigabename bleibt **FRITZ.NAS**. „SMBv1“ bleibt **aus**, das wird nicht gebraucht. → *Übernehmen*
3. **Eigenen Benutzer fürs Handy anlegen:** *System → FRITZ!Box-Benutzer → Benutzer hinzufügen*
   - Name z. B. `handy`, ein eigenes Kennwort vergeben
   - Bei den Berechtigungen **nur „Zugang zu NAS-Inhalten“** anhaken
   - Unter „Verzeichnis hinzufügen“: „Alle an der FRITZ!Box verfügbaren Speicher“ oder nur deine Festplatte, mit **Lese- und Schreibrecht**
   - *Übernehmen*

Den Ordner **Handy-Fotos-Videos** musst du nicht selbst anlegen, das macht die App.

---

## Teil 2: App bauen (einmalig)

### Weg A: Android Studio (am PC)
1. Android Studio installieren (kostenlos: developer.android.com/studio).
2. Den entpackten Ordner `HandyFotoSync` über *File → Open* öffnen und warten, bis „Gradle sync“ fertig ist (beim ersten Mal ein paar Minuten).
3. Am Handy die **Entwickleroptionen** und darin **USB-Debugging** einschalten (Einstellungen → Telefoninfo → 7× auf „Build-Nummer“ tippen).
4. Handy per USB anschließen, oben das Handy auswählen und auf den grünen ▶-Knopf klicken. Die App wird installiert und startet.

   *Alternativ:* *Build → Build App Bundle(s) / APK(s) → Build APK(s)* und die APK-Datei aufs Handy kopieren und dort öffnen. Dabei die Installation aus „unbekannten Quellen“ erlauben.

### Weg B: ohne Android Studio, über GitHub
Den Ordner in ein (privates) GitHub-Repository hochladen. Der mitgelieferte Ablauf unter `.github/workflows/build.yml` baut die APK automatisch. Du findest sie unter *Actions → letzter Lauf → FotoSync-APK* zum Herunterladen.

---

## Teil 3: App einrichten (am Handy, im Heim-WLAN)

1. App **„Fotos → FRITZ!NAS“** öffnen und die Berechtigungen erlauben. Bei Fotos **„Alle erlauben“** wählen, nicht nur ausgewählte Fotos.
2. Benutzer und Kennwort aus Teil 1 eintragen (Adresse `fritz.box` und Freigabe `FRITZ.NAS` stehen schon drin).
3. **„Festplatte auswählen …“** antippen und deine Festplatte wählen. Der Zielordner wird automatisch zu `<Festplatte>/Handy-Fotos-Videos`.
4. Die App testet die Verbindung, legt den Ordner an und fragt, ob sie gleich alle vorhandenen Fotos übertragen soll.
5. Schalter nach Wunsch:
   - *Videos auch übertragen* (Standard: an)
   - *Nur Kamera-Fotos*, also ohne Screenshots, WhatsApp-Bilder usw. (Standard: aus)
   - *Automatisch übertragen, sobald ich im WLAN bin* (Standard: an)

Fertig. Ab jetzt läuft alles von selbst.

### Tipp: Akku-Optimierung
Manche Hersteller (v. a. Samsung, Xiaomi, Huawei) bremsen Hintergrund-Apps stark. Wenn Übertragungen nur passieren, während die App offen ist:
*Einstellungen → Apps → Fotos → FRITZ!NAS → Akku → „Nicht eingeschränkt“*.

---

## Häufige Meldungen

| Meldung | Lösung |
|---|---|
| Anmeldung abgelehnt | Benutzer/Kennwort prüfen; beim FRITZ!Box-Benutzer „Zugang zu NAS-Inhalten“ anhaken |
| Zugriff verweigert | Dem Benutzer **Schreibrecht** auf den Speicher geben |
| FRITZ!Box nicht gefunden | Im Heim-WLAN? Sonst als Adresse `192.168.178.1` eintragen |
| Keine Verbindung | „Zugriff über ein Netzlaufwerk (SMB)“ aktivieren |
| Kein WLAN – übersprungen | Normal unterwegs: es wird automatisch nachgeholt |

**Merkliste zurücksetzen:** Die App merkt sich, was schon übertragen wurde. Setzt du die Liste zurück, prüft sie beim nächsten Lauf wieder alles gegen die Festplatte. Doppelte Kopien entstehen dabei nicht.

**Hinweis zur Sicherheit:** Das Kennwort wird nur im privaten App-Speicher auf dem Handy abgelegt. Darum ein eigener FRITZ!Box-Benutzer, der nur NAS-Zugriff hat.
