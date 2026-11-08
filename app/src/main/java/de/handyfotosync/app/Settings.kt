package de.handyfotosync.app

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Gespeicherte Einstellungen und Statusmeldungen der App. */
class Settings(context: Context) {
    private val p = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var host: String
        get() = p.getString("host", "fritz.box") ?: "fritz.box"
        set(v) = p.edit().putString("host", v.trim()).apply()

    var share: String
        get() = p.getString("share", "FRITZ.NAS") ?: "FRITZ.NAS"
        set(v) = p.edit().putString("share", v.trim().trim('/')).apply()

    /** Pfad innerhalb der Freigabe, z. B. "Intenso-01/Handy-Fotos-Videos" */
    var folder: String
        get() = p.getString("folder", "") ?: ""
        set(v) = p.edit().putString("folder", v.trim().trim('/')).apply()

    var user: String
        get() = p.getString("user", "") ?: ""
        set(v) = p.edit().putString("user", v.trim()).apply()

    var password: String
        get() = p.getString("password", "") ?: ""
        set(v) = p.edit().putString("password", v).apply()

    var includeVideos: Boolean
        get() = p.getBoolean("videos", true)
        set(v) = p.edit().putBoolean("videos", v).apply()

    var onlyCamera: Boolean
        get() = p.getBoolean("onlyCamera", false)
        set(v) = p.edit().putBoolean("onlyCamera", v).apply()

    var autoSync: Boolean
        get() = p.getBoolean("auto", true)
        set(v) = p.edit().putBoolean("auto", v).apply()

    val isConfigured: Boolean
        get() = host.isNotBlank() && share.isNotBlank() && folder.isNotBlank() && user.isNotBlank()

    /** Ziel-Kennung: ändert sie sich, muss die Merkliste neu aufgebaut werden. */
    val targetId: String
        get() = "$host|$share|$folder|flach".lowercase(Locale.ROOT)

    /** Zuletzt funktionierende SMB-Variante (Index in SmbClient.PROFILES). */
    var smbProfile: Int
        get() = p.getInt("smbProfile", 0).coerceIn(0, SmbClient.PROFILES.size - 1)
        set(v) = p.edit().putInt("smbProfile", v).apply()

    /** Ziel, zu dem die aktuelle Merkliste gehört. */
    var ledgerTarget: String
        get() = p.getString("ledgerTarget", "") ?: ""
        set(v) = p.edit().putString("ledgerTarget", v).apply()

    // ---------- Anzeige auf der Übersicht ----------
    var headline: String
        get() = p.getString("headline", "") ?: ""
        set(v) = p.edit().putString("headline", v).apply()

    var lastRun: String
        get() = p.getString("lastRun", "") ?: ""
        set(v) = p.edit().putString("lastRun", v).apply()

    var running: Boolean
        get() = p.getBoolean("running", false)
        set(v) = p.edit().putBoolean("running", v).apply()

    val done: Int get() = p.getInt("done", 0)
    val total: Int get() = p.getInt("total", 0)
    val cntNew: Int get() = p.getInt("cntNew", 0)
    val cntAlready: Int get() = p.getInt("cntAlready", 0)
    val cntFailed: Int get() = p.getInt("cntFailed", 0)

    fun startProgress(total: Int) {
        p.edit().putBoolean("running", true).putInt("total", total).putInt("done", 0)
            .putInt("cntNew", 0).putInt("cntAlready", 0).putInt("cntFailed", 0).apply()
    }

    fun setProgress(done: Int, new: Int, already: Int, failed: Int) {
        p.edit().putInt("done", done).putInt("cntNew", new)
            .putInt("cntAlready", already).putInt("cntFailed", failed).apply()
    }

    /** Ergebnis eines Laufs: kurze Überschrift + Erklärung. */
    fun report(headline: String, detail: String) {
        p.edit().putString("headline", headline).putString("status", detail)
            .putString("lastRun", now()).apply()
    }

    var status: String
        get() = p.getString("status", "Noch keine Übertragung.") ?: ""
        set(v) = p.edit().putString("status", v).apply()

    val log: String
        get() = p.getString("log", "") ?: ""

    @Synchronized
    fun log(line: String) {
        val stamp = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date())
        val lines = (listOf("$stamp  $line") + log.lines().filter { it.isNotBlank() }).take(60)
        p.edit().putString("log", lines.joinToString("\n")).apply()
    }

    companion object {
        fun now(): String = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())
    }
}
