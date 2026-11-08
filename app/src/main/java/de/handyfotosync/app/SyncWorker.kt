package de.handyfotosync.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import jcifs.smb.SmbFileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream

/**
 * Überträgt neue Fotos/Videos auf die FRITZ!NAS-Festplatte.
 * Ablage: alles direkt in <Ordner>, z. B. Handy-Fotos-Videos/IMG_1234.jpg (keine Unterordner)
 */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private enum class Outcome { UPLOADED, ALREADY_THERE }

    private var isForegroundActive = false

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo("Fotos werden übertragen …")

    override suspend fun doWork(): Result {
        val result = if (lock.tryLock()) {
            try {
                withContext(Dispatchers.IO) { runSync() }
            } finally {
                lock.unlock()
            }
        } else {
            Result.success() // es läuft bereits eine Übertragung
        }
        // Den Auslöser „neues Foto“ für das nächste Mal wieder scharf schalten
        val s = Settings(applicationContext)
        if (tags.contains(Scheduler.TAG_TRIGGER) && s.autoSync) {
            Scheduler.enqueueOnNewMedia(applicationContext)
        }
        return result
    }

    private suspend fun runSync(): Result {
        val ctx = applicationContext
        val s = Settings(ctx)

        if (!s.isConfigured) {
            s.report("Noch nicht eingerichtet", "Bitte die App öffnen und einrichten.")
            return Result.success()
        }
        if (!Net.isOnWifi(ctx)) {
            s.report("Kein WLAN", "Übertragung übersprungen – wird im WLAN nachgeholt.")
            return Result.success()
        }
        if (!Perms.hasMedia(ctx)) {
            s.report("Keine Foto-Berechtigung", PERM_HINT)
            return Result.success()
        }

        updateNotification("Suche neue Fotos …")

        val ledger = Ledger(ctx)
        if (s.ledgerTarget != s.targetId) {
            // Ziel wurde geändert → Merkliste neu aufbauen (Festplatte wird ohnehin geprüft)
            ledger.clear()
            s.ledgerTarget = s.targetId
        }

        val items = MediaScanner.scan(ctx, s.includeVideos, s.onlyCamera)
        if (items.isEmpty()) {
            s.report("Keine Fotos gefunden", "Die App sieht keine Fotos auf dem Handy. $PERM_HINT")
            return Result.success()
        }
        val todo = items.filter { !ledger.contains(it.key) }
        if (todo.isEmpty()) {
            s.report("Alles aktuell", "${items.size} Dateien geprüft.")
            return Result.success()
        }

        var uploaded = 0
        var already = 0
        var failed = 0
        var lastError = ""

        val client = try {
            SmbClient.open(s)
        } catch (e: Exception) {
            val msg = SmbClient.friendlyError(e)
            s.report("FRITZ!Box nicht erreichbar", msg)
            s.log("Verbindung fehlgeschlagen: $msg")
            return Result.success() // beim nächsten Mal erneut versuchen
        }

        client.use { smb ->
            // Zielordner anlegen
            try {
                val root = smb.dir(s.folder)
                if (!root.exists()) root.mkdirs()
            } catch (e: Exception) {
                val msg = SmbClient.friendlyError(e)
                s.report("FRITZ!Box nicht erreichbar", msg)
                s.log("Verbindung fehlgeschlagen: $msg")
                return Result.success() // beim nächsten Mal erneut versuchen
            }

            val knownDirs = HashSet<String>()
            s.startProgress(todo.size)
            try {
            for ((i, item) in todo.withIndex()) {
                if (isStopped) break
                if (!Net.isOnWifi(ctx)) {
                    s.log("WLAN getrennt – Übertragung angehalten.")
                    break
                }
                if (i % 5 == 0) updateNotification("Übertrage ${i + 1} von ${todo.size} …")

                try {
                    val outcome = try {
                        uploadOne(smb, s, item, knownDirs)
                    } catch (first: Exception) {
                        // Einmal kurz warten und erneut versuchen (z. B. Festplatte war zu langsam)
                        if (isStopped || !Net.isOnWifi(ctx)) throw first
                        delay(5000)
                        uploadOne(smb, s, item, knownDirs)
                    }
                    when (outcome) {
                        Outcome.UPLOADED -> uploaded++
                        Outcome.ALREADY_THERE -> already++
                    }
                    ledger.add(item.key)
                } catch (e: Exception) {
                    failed++
                    lastError = SmbClient.friendlyError(e)
                    s.log("Fehler bei ${item.name}: $lastError")
                    if (failed >= 5 && uploaded == 0 && already == 0) break // vermutlich grundsätzliches Problem
                }
                s.setProgress(i + 1, uploaded, already, failed)
            }
            } finally {
                s.running = false
            }
        }

        val summary = "$uploaded neu, $already schon da, $failed Fehler"
        s.report(
            if (failed > 0) "Fertig mit $failed Fehler" + (if (failed == 1) "" else "n") else "Fertig",
            if (failed > 0) "Letzter Fehler: $lastError\nFehlgeschlagene Dateien werden beim nächsten Mal erneut versucht." else "$uploaded neu übertragen."
        )
        if (uploaded > 0 || failed > 0) s.log(summary)
        return Result.success()
    }

    private fun uploadOne(smb: SmbClient, s: Settings, item: MediaItem, knownDirs: MutableSet<String>): Outcome {
        // Alles direkt in den Zielordner, ohne Unterordner
        val sub = s.folder.trim('/')
        if (knownDirs.add(sub)) {
            val d = smb.dir(sub)
            if (!d.exists()) d.mkdirs()
        }

        val safe = sanitize(item.name)
        val dot = safe.lastIndexOf('.')
        val stem = if (dot > 0) safe.substring(0, dot) else safe
        val ext = if (dot > 0) safe.substring(dot) else ""

        // Gleicher Name + gleiche Größe = schon vorhanden. Gleicher Name, andere Größe = anderes Foto → _1, _2 …
        var n = 0
        while (n < 1000) {
            val candidate = if (n == 0) safe else "${stem}_$n$ext"
            val target = smb.file("$sub/$candidate")
            if (target.exists()) {
                if (target.length() == item.size) return Outcome.ALREADY_THERE
                n++
                continue
            }
            upload(smb, item, sub, candidate)
            return Outcome.UPLOADED
        }
        throw IOException("Zu viele Dateien mit dem Namen ${item.name}")
    }

    private fun upload(smb: SmbClient, item: MediaItem, sub: String, name: String) {
        // Erst als versteckte .part-Datei schreiben, dann umbenennen → keine halben Fotos auf der Platte
        val tmp = smb.file("$sub/.$name.part")
        if (tmp.exists()) tmp.delete()
        openInput(item).use { input ->
            SmbFileOutputStream(tmp).use { out -> input.copyTo(out, 256 * 1024) }
        }
        val target = smb.file("$sub/$name")
        tmp.renameTo(target)
        try {
            smb.file("$sub/$name").setLastModified(item.dateMs) // Aufnahmedatum als Dateidatum
        } catch (_: Exception) {
        }
    }

    private fun openInput(item: MediaItem): InputStream {
        val cr = applicationContext.contentResolver
        var stream: InputStream? = null
        if (Perms.hasMediaLocation(applicationContext)) {
            // Original inkl. GPS-Daten lesen
            stream = try {
                cr.openInputStream(MediaStore.setRequireOriginal(item.uri))
            } catch (_: Exception) {
                null
            }
        }
        if (stream == null) stream = cr.openInputStream(item.uri)
        return stream ?: throw IOException("Datei nicht lesbar: ${item.name}")
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|#%]"), "_").trim().ifEmpty { "datei" }

    // ---------- Benachrichtigung / Vordergrund ----------

    private suspend fun updateNotification(text: String) {
        try {
            setForeground(foregroundInfo(text))
            isForegroundActive = true
        } catch (_: Exception) {
            // Android erlaubt den Vordergrundmodus nicht immer – dann läuft es normal weiter
            // und setzt beim nächsten Durchlauf fort.
        }
    }

    private fun foregroundInfo(text: String): ForegroundInfo {
        val ctx = applicationContext
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm?.getNotificationChannel(CHANNEL) == null) {
            nm?.createNotificationChannel(
                NotificationChannel(CHANNEL, "Foto-Übertragung", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Handy-Fotos → FRITZ!NAS")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return ForegroundInfo(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    companion object {
        private val lock = Mutex()
        private const val CHANNEL = "sync"
        private const val NOTIF_ID = 4711
        const val PERM_HINT = "Einstellungen → Apps → Fotos → FRITZ!NAS → Berechtigungen → Fotos und Videos → „Immer alle erlauben“."
    }
}
