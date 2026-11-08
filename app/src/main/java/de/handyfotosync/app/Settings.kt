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
