package de.handyfotosync.app

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbFile
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Properties

/** Verbindung zur FRITZ!NAS-Freigabe per SMB 2/3 (SMB 1 bleibt aus). */
class SmbClient(private val s: Settings) : AutoCloseable {
    private val base: BaseContext
    private val ctx: CIFSContext

    init {
        val props = Properties()
        props.setProperty("jcifs.smb.client.minVersion", "SMB202")
        props.setProperty("jcifs.smb.client.maxVersion", "SMB311")
        props.setProperty("jcifs.resolveOrder", "DNS,BCAST")
        props.setProperty("jcifs.smb.client.connTimeout", "10000")
        props.setProperty("jcifs.smb.client.responseTimeout", "30000")
        props.setProperty("jcifs.smb.client.soTimeout", "35000")
        base = BaseContext(PropertyConfiguration(props))
        ctx = base.withCredentials(NtlmPasswordAuthenticator("", s.user, s.password))
    }

    private fun url(path: String): String {
        val parts = (listOf(s.share) + path.split('/'))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return "smb://${s.host.trim()}/" + parts.joinToString("/")
    }

    /** Ordner (URL muss mit "/" enden). */
    fun dir(path: String): SmbFile = SmbFile(url(path).trimEnd('/') + "/", ctx)

    /** Datei. */
    fun file(path: String): SmbFile = SmbFile(url(path), ctx)

    override fun close() {
        try {
            base.close()
        } catch (_: Exception) {
        }
    }

    companion object {
        /** Übersetzt technische Fehler in verständliche Hinweise. */
        fun friendlyError(e: Throwable): String {
            var c: Throwable? = e
            while (c != null) {
                when (c) {
                    is SmbAuthException ->
                        return "Anmeldung abgelehnt: Benutzername/Kennwort prüfen und beim FRITZ!Box-Benutzer „Zugang zu NAS-Inhalten“ aktivieren."
                    is UnknownHostException ->
                        return "FRITZ!Box nicht gefunden – bist du im Heim-WLAN? (Alternativ als Adresse 192.168.178.1 eintragen.)"
                    is ConnectException, is SocketTimeoutException ->
                        return "Keine Verbindung zur FRITZ!Box – ist „Zugriff über ein Netzlaufwerk (SMB)“ aktiviert und bist du im Heim-WLAN?"
                }
                val msg = c.message ?: ""
                if (msg.contains("denied", ignoreCase = true)) {
                    return "Zugriff verweigert: Der FRITZ!Box-Benutzer braucht Lese- UND Schreibrechte auf den Speicher."
                }
                if (msg.contains("cannot find the path", ignoreCase = true) ||
                    msg.contains("cannot find the file", ignoreCase = true) ||
                    msg.contains("bad network name", ignoreCase = true)
                ) {
                    return "Freigabe oder Pfad nicht gefunden – Freigabename (meist FRITZ.NAS) und Speicher-Ordner prüfen."
                }
                c = c.cause
            }
            return e.message ?: e.javaClass.simpleName
        }
    }
}
