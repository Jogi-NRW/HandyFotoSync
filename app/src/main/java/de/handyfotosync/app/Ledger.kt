package de.handyfotosync.app

import android.content.Context
import java.io.File

/**
 * Merkliste der bereits übertragenen (oder auf der FRITZ!Box schon vorhandenen) Dateien.
 * Sie spart nur Zeit: Ob eine Datei wirklich schon auf der Festplatte liegt,
 * prüft die App zusätzlich direkt auf der FRITZ!Box.
 */
class Ledger(context: Context) {
    private val file = File(context.filesDir, "uebertragen.txt")
    private val keys = HashSet<String>()

    init {
        if (file.exists()) file.forEachLine { if (it.isNotBlank()) keys.add(it) }
    }

    fun contains(key: String) = key in keys

    @Synchronized
    fun add(key: String) {
        if (keys.add(key)) file.appendText(key + "\n")
    }

    val size: Int get() = keys.size

    @Synchronized
    fun clear() {
        keys.clear()
        file.delete()
    }
}
